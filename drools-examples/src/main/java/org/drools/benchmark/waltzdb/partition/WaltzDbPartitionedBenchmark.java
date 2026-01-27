/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.drools.benchmark.waltzdb.partition;

import org.drools.benchmark.waltzdb.Label;
import org.drools.benchmark.waltzdb.Line;
import org.drools.benchmark.waltzdb.Stage;
import org.drools.benchmark.waltzdb.WaltzDbBenchmark;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * Runs WaltzDB benchmark with graph-based partitioning and parallel execution.
 * 
 * This benchmark:
 * 1. Partitions the WaltzDB graph using Label Propagation Clustering
 * 2. Bins micro-clusters into balanced partitions
 * 3. Runs separate KieSessions in parallel threads
 * 4. Measures execution time and speedup
 */
public class WaltzDbPartitionedBenchmark {

    private final InternalKnowledgeBase kbase;
    private final String dataFile;

    public WaltzDbPartitionedBenchmark(String dataFile) {
        this.dataFile = dataFile;
        this.kbase = buildKnowledgeBase();
    }

    private InternalKnowledgeBase buildKnowledgeBase() {
        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(ResourceFactory.newClassPathResource("waltzdb.drl",
                WaltzDbBenchmark.class), ResourceType.DRL);

        if (kbuilder.hasErrors()) {
            throw new RuntimeException("DRL compilation errors: " + kbuilder.getErrors());
        }

        Collection<KiePackage> pkgs = kbuilder.getKnowledgePackages();

        KieBaseConfiguration kbaseConfiguration = RuleBaseFactory.newKnowledgeBaseConfiguration();
        kbaseConfiguration.setProperty("drools.removeIdentities", "true");

        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase(kbaseConfiguration);
        kbase.addPackages(pkgs);
        return kbase;
    }

    /**
     * Run the partitioned benchmark with specified number of threads.
     *
     * @param numThreads Number of parallel threads/partitions
     * @param iterations Number of iterations for timing
     * @return Average execution time in milliseconds
     */
    public double runPartitioned(int numThreads, int iterations) throws Exception {
        // 1. Partition the data
        WaltzDbGraphPartitioner partitioner = new WaltzDbGraphPartitioner(dataFile);
        List<WaltzDbPartition> partitions = partitioner.partition(numThreads);

        WaltzDbGraphPartitioner.printPartitionStats(partitions);

        // 2. Create thread pool
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        double totalTime = 0;
        List<Long> times = new ArrayList<>();

        // Warmup iterations (not measured)
        System.out.println("Warming up...");
        for (int w = 0; w < 3; w++) {
            List<Future<Long>> warmupFutures = new ArrayList<>();
            for (WaltzDbPartition partition : partitions) {
                warmupFutures.add(executor.submit(() -> runPartitionSession(partition)));
            }
            for (Future<Long> future : warmupFutures) {
                future.get();
            }
        }

        System.out.println("Running " + iterations + " measured iterations...");

        for (int iter = 0; iter < iterations; iter++) {
            long startTime = System.nanoTime();

            // 3. Submit tasks for each partition
            List<Future<Long>> futures = new ArrayList<>();
            for (WaltzDbPartition partition : partitions) {
                futures.add(executor.submit(() -> runPartitionSession(partition)));
            }

            // 4. Wait for all to complete
            long totalRulesFired = 0;
            for (Future<Long> future : futures) {
                totalRulesFired += future.get();
            }

            long endTime = System.nanoTime();
            double elapsedMs = (endTime - startTime) / 1_000_000.0;
            totalTime += elapsedMs;
            times.add((long) elapsedMs);

            System.out.printf("  Iteration %d: %.2f ms, Rules fired: %d\n",
                    iter + 1, elapsedMs, totalRulesFired);
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        // Calculate statistics
        double avgTime = totalTime / iterations;
        double variance = 0;
        for (Long t : times) {
            variance += Math.pow(t - avgTime, 2);
        }
        double stdDev = Math.sqrt(variance / iterations);

        System.out.printf("\n>>> Results with %d thread(s):\n", numThreads);
        System.out.printf("    Average time: %.2f ms\n", avgTime);
        System.out.printf("    Std deviation: %.2f ms\n", stdDev);

        return avgTime;
    }

    /**
     * Run a single partition in its own KieSession.
     */
    private long runPartitionSession(WaltzDbPartition partition) {
        KieSession ksession = kbase.newKieSession();

        try {
            // Insert internal lines
            for (Line line : partition.getLines()) {
                ksession.insert(line);
            }

            // Insert bridge lines (for completeness - these cross partitions)
            for (Line line : partition.getBridgeLines()) {
                ksession.insert(line);
            }

            // Insert labels (shared across all partitions)
            for (Label label : partition.getLabels()) {
                ksession.insert(label);
            }

            // Insert initial stage
            ksession.insert(new Stage(Stage.DUPLICATE));

            // Fire all rules
            return ksession.fireAllRules();
        } finally {
            ksession.dispose();
        }
    }

    /**
     * Main method for standalone testing.
     */
    public static void main(String[] args) throws Exception {
        String dataFile = "waltzdb16.dat";
        int iterations = 5;
        int[] threadCounts = { 1, 2, 4, 8 };

        System.out.println("==============================================");
        System.out.println("  WaltzDB Partitioned Benchmark");
        System.out.println("==============================================");
        System.out.println("Data file: " + dataFile);
        System.out.println("Iterations: " + iterations);
        System.out.println();

        WaltzDbPartitionedBenchmark benchmark = new WaltzDbPartitionedBenchmark(dataFile);

        // Store results for comparison
        double[] results = new double[threadCounts.length];

        for (int i = 0; i < threadCounts.length; i++) {
            int threads = threadCounts[i];
            System.out.println("\n========================================");
            System.out.println("Running with " + threads + " thread(s)...");
            System.out.println("========================================");

            results[i] = benchmark.runPartitioned(threads, iterations);
        }

        // Print summary
        System.out.println("\n==============================================");
        System.out.println("  SUMMARY");
        System.out.println("==============================================");
        System.out.printf("%-10s %-15s %-10s\n", "Threads", "Avg Time (ms)", "Speedup");
        System.out.println("----------------------------------------------");

        double baselineTime = results[0];
        for (int i = 0; i < threadCounts.length; i++) {
            double speedup = baselineTime / results[i];
            System.out.printf("%-10d %-15.2f %-10.2fx\n",
                    threadCounts[i], results[i], speedup);
        }

        System.out.println("\n=== Experiment Complete ===");
    }
}
