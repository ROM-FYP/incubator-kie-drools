package org.drools.benchmark.waltzdb;

import org.drools.benchmark.waltzdb.partition.WaltzDbGraphPartitioner;
import org.drools.benchmark.waltzdb.partition.WaltzDbPartition;
import org.drools.core.impl.RuleBaseFactory;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.definition.KiePackage;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * JMH Benchmark for Partitioned WaltzDB - Research-grade parallel execution measurement.
 */
@Fork(3)
@State(Scope.Thread)
@BenchmarkMode(Mode.SingleShotTime)
@Warmup(iterations = 5)
@Measurement(iterations = 20)
@OutputTimeUnit(java.util.concurrent.TimeUnit.MILLISECONDS)
public class WaltzDbPartitionedJmhBenchmark {

    @Param({"waltzdb16.dat"})
    private String dataFile;

    @Param({"1", "2", "4", "8"})
    private int numThreads;

    private InternalKnowledgeBase kbase;
    private List<WaltzDbPartition> partitions;
    private ExecutorService executor;

    @Setup(Level.Trial)
    public void setupKnowledgeBase() {
        // Build knowledge base
        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(ResourceFactory.newClassPathResource("waltzdb.drl",
            WaltzDbBenchmark.class), ResourceType.DRL);

        if (kbuilder.hasErrors()) {
            throw new RuntimeException("DRL compilation errors: " + kbuilder.getErrors());
        }

        Collection<KiePackage> pkgs = kbuilder.getKnowledgePackages();

        KieBaseConfiguration kbaseConfiguration = RuleBaseFactory.newKnowledgeBaseConfiguration();
        kbaseConfiguration.setProperty("drools.removeIdentities", "true");

        kbase = KnowledgeBaseFactory.newKnowledgeBase(kbaseConfiguration);
        kbase.addPackages(pkgs);

        // Partition data
        WaltzDbGraphPartitioner partitioner = new WaltzDbGraphPartitioner(dataFile);
        partitions = partitioner.partition(numThreads);
        WaltzDbGraphPartitioner.printPartitionStats(partitions);
    }

    @Setup(Level.Invocation)
    public void setupExecutor() {
        executor = Executors.newFixedThreadPool(numThreads);
    }

    @TearDown(Level.Invocation)
    public void tearDownExecutor() throws InterruptedException {
        if (executor != null) {
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.MINUTES);
        }
    }

    @Benchmark
    public long runPartitionedWaltzDb(Blackhole bh) throws Exception {
        List<Future<Long>> futures = new ArrayList<>();

        for (WaltzDbPartition partition : partitions) {
            futures.add(executor.submit(() -> runPartitionSession(partition)));
        }

        long totalRulesFired = 0;
        for (Future<Long> future : futures) {
            totalRulesFired += future.get();
        }

        bh.consume(totalRulesFired);
        return totalRulesFired;
    }

    private long runPartitionSession(WaltzDbPartition partition) {
        KieSession ksession = kbase.newKieSession();
        try {
            for (Line line : partition.getLines()) {
                ksession.insert(line);
            }
            for (Line line : partition.getBridgeLines()) {
                ksession.insert(line);
            }
            for (Label label : partition.getLabels()) {
                ksession.insert(label);
            }
            ksession.insert(new Stage(Stage.DUPLICATE));
            return ksession.fireAllRules();
        } finally {
            ksession.dispose();
        }
    }
}