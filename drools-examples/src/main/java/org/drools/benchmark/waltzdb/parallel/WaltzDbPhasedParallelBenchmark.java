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
package org.drools.benchmark.waltzdb.parallel;

import org.drools.benchmark.waltzdb.*;
import org.drools.benchmark.waltzdb.partition.WaltzDbGraphPartitioner;
import org.drools.benchmark.waltzdb.partition.WaltzDbPartition;
import org.drools.core.impl.RuleBaseFactory;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.drools.util.IoUtils;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.definition.KiePackage;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Phased Parallel Execution with Synchronization Barriers (PPESB)
 * 
 * A novel approach to parallelizing the WaltzDB benchmark:
 * - Phase 1 (PARALLEL): DUPLICATE stage - convert Lines to Edges
 * - Phase 2 (PARALLEL): DETECT_JUNCTIONS stage - create Junctions from Edges
 * - Phase 3 (SEQUENTIAL): Remaining stages - requires global fact visibility
 * 
 * Uses graph-based clustering for intelligent data partitioning.
 */
public class WaltzDbPhasedParallelBenchmark {

    private static final String PHASE1_DRL = "org/drools/benchmark/waltzdb/parallel/waltzdb_phase1.drl";
    private static final String PHASE2_DRL = "org/drools/benchmark/waltzdb/parallel/waltzdb_phase2.drl";
    private static final String FULL_DRL = "waltzdb.drl";

    private final String dataFile;
    private final int numThreads;
    private final boolean verbose;

    public WaltzDbPhasedParallelBenchmark(String dataFile, int numThreads, boolean verbose) {
        this.dataFile = dataFile;
        this.numThreads = numThreads;
        this.verbose = verbose;
    }

    /**
     * Execute the full PPESB benchmark.
     * 
     * @return Total execution time in milliseconds
     */
    public BenchmarkResult execute() throws Exception {
        long totalStart = System.nanoTime();
        long totalRulesFired = 0;

        // Load initial data
        List<Line> lines = loadLines();
        List<Label> labels = loadLabels();

        if (verbose) {
            System.out.println("Loaded " + lines.size() + " lines and " + labels.size() + " labels");
        }

        // ========== PHASE 1: DUPLICATE (PARALLEL) ==========
        if (verbose)
            System.out.println("\n>>> PHASE 1: DUPLICATE (Parallel with " + numThreads + " threads)");
        long phase1Start = System.nanoTime();

        // Partition lines for parallel processing
        List<List<Line>> linePartitions = partitionLines(lines, numThreads);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<PhaseResult>> phase1Futures = new ArrayList<>();

        for (int i = 0; i < linePartitions.size(); i++) {
            List<Object> inputFacts = new ArrayList<>(linePartitions.get(i));
            PhaseExecutor phaseExecutor = new PhaseExecutor(i, PHASE1_DRL, inputFacts);
            phase1Futures.add(executor.submit(phaseExecutor));
        }

        // Collect and consolidate Phase 1 results
        FactConsolidator phase1Consolidator = new FactConsolidator();
        for (Future<PhaseResult> future : phase1Futures) {
            phase1Consolidator.mergeResult(future.get());
        }

        long phase1Time = (System.nanoTime() - phase1Start) / 1_000_000;
        totalRulesFired += phase1Consolidator.getTotalRulesFired();

        if (verbose) {
            phase1Consolidator.printStats("Phase 1");
            System.out.println("Phase 1 time: " + phase1Time + " ms");
        }

        // ========== SYNCHRONIZATION BARRIER 1 ==========
        Set<Edge> allEdges = phase1Consolidator.getEdges();
        if (verbose) {
            System.out.println("\n--- BARRIER 1: Consolidated " + allEdges.size() + " edges ---");
        }

        // ========== PHASE 2: DETECT_JUNCTIONS (PARALLEL) ==========
        if (verbose)
            System.out.println("\n>>> PHASE 2: DETECT_JUNCTIONS (Parallel with graph partitioning)");
        long phase2Start = System.nanoTime();

        // Use graph partitioning to group edges by connected components
        Map<Integer, List<Edge>> edgePartitions = partitionEdgesByBasePoint(allEdges, numThreads);

        List<Future<PhaseResult>> phase2Futures = new ArrayList<>();
        int partitionId = 0;
        for (List<Edge> edgePartition : edgePartitions.values()) {
            List<Object> inputFacts = new ArrayList<>(edgePartition);
            PhaseExecutor phaseExecutor = new PhaseExecutor(partitionId++, PHASE2_DRL, inputFacts);
            phase2Futures.add(executor.submit(phaseExecutor));
        }

        // Collect and consolidate Phase 2 results
        FactConsolidator phase2Consolidator = new FactConsolidator();
        for (Future<PhaseResult> future : phase2Futures) {
            phase2Consolidator.mergeResult(future.get());
        }

        long phase2Time = (System.nanoTime() - phase2Start) / 1_000_000;
        totalRulesFired += phase2Consolidator.getTotalRulesFired();

        if (verbose) {
            phase2Consolidator.printStats("Phase 2");
            System.out.println("Phase 2 time: " + phase2Time + " ms");
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        // ========== SYNCHRONIZATION BARRIER 2 ==========
        Set<Edge> consolidatedEdges = phase2Consolidator.getEdges();
        Set<Junction> consolidatedJunctions = phase2Consolidator.getJunctions();

        if (verbose) {
            System.out.println("\n--- BARRIER 2: Consolidated " + consolidatedEdges.size() +
                    " edges, " + consolidatedJunctions.size() + " junctions ---");
        }

        // ========== PHASE 3: REMAINING STAGES (SEQUENTIAL) ==========
        if (verbose)
            System.out.println("\n>>> PHASE 3: Remaining stages (Sequential)");
        long phase3Start = System.nanoTime();

        // Build knowledge base with full rule set for remaining stages
        InternalKnowledgeBase kbase = buildFullKnowledgeBase();
        KieSession ksession = kbase.newKieSession();

        try {
            // Insert consolidated facts
            for (Edge edge : consolidatedEdges) {
                ksession.insert(edge);
            }
            for (Junction junction : consolidatedJunctions) {
                ksession.insert(junction);
            }
            for (Label label : labels) {
                ksession.insert(label);
            }

            // Start at FIND_INITIAL_BOUNDARY (skip DUPLICATE and DETECT_JUNCTIONS)
            ksession.insert(new Stage(Stage.FIND_INITIAL_BOUNDARY));

            // Fire all remaining rules
            long phase3Rules = ksession.fireAllRules();
            totalRulesFired += phase3Rules;

            if (verbose) {
                System.out.println("Phase 3 rules fired: " + phase3Rules);
            }
        } finally {
            ksession.dispose();
        }

        long phase3Time = (System.nanoTime() - phase3Start) / 1_000_000;
        long totalTime = (System.nanoTime() - totalStart) / 1_000_000;

        if (verbose) {
            System.out.println("Phase 3 time: " + phase3Time + " ms");
            System.out.println("\n=== PPESB Benchmark Complete ===");
            System.out.println("Total time: " + totalTime + " ms");
            System.out.println("Total rules fired: " + totalRulesFired);
        }

        return new BenchmarkResult(totalTime, phase1Time, phase2Time, phase3Time, totalRulesFired);
    }

    /**
     * Partition lines evenly across threads.
     */
    private List<List<Line>> partitionLines(List<Line> lines, int partitions) {
        List<List<Line>> result = new ArrayList<>();
        int size = lines.size();
        int partitionSize = (size + partitions - 1) / partitions;

        for (int i = 0; i < partitions; i++) {
            int start = i * partitionSize;
            int end = Math.min(start + partitionSize, size);
            if (start < size) {
                result.add(new ArrayList<>(lines.subList(start, end)));
            }
        }
        return result;
    }

    /**
     * Partition edges by basePoint (p1) for junction detection.
     * Edges with the same p1 must be in the same partition for correct junction
     * detection.
     */
    private Map<Integer, List<Edge>> partitionEdgesByBasePoint(Set<Edge> edges, int targetPartitions) {
        // Group edges by p1 (basePoint)
        Map<Integer, List<Edge>> byBasePoint = new HashMap<>();
        for (Edge edge : edges) {
            byBasePoint.computeIfAbsent(edge.getP1(), k -> new ArrayList<>()).add(edge);
        }

        // Bin basePoint groups into target partitions using greedy bin-packing
        List<Map.Entry<Integer, List<Edge>>> groups = new ArrayList<>(byBasePoint.entrySet());
        groups.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));

        Map<Integer, List<Edge>> partitions = new HashMap<>();
        int[] partitionSizes = new int[targetPartitions];

        for (int i = 0; i < targetPartitions; i++) {
            partitions.put(i, new ArrayList<>());
        }

        for (Map.Entry<Integer, List<Edge>> group : groups) {
            // Find smallest partition
            int minIdx = 0;
            for (int i = 1; i < targetPartitions; i++) {
                if (partitionSizes[i] < partitionSizes[minIdx]) {
                    minIdx = i;
                }
            }
            partitions.get(minIdx).addAll(group.getValue());
            partitionSizes[minIdx] += group.getValue().size();
        }

        return partitions;
    }

    private InternalKnowledgeBase buildFullKnowledgeBase() {
        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(ResourceFactory.newClassPathResource(FULL_DRL, WaltzDbBenchmark.class), ResourceType.DRL);

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

    // Data loading methods
    private List<Line> loadLines() {
        List<Line> result = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(WaltzDbBenchmark.class.getResourceAsStream("data/" + dataFile),
                            IoUtils.UTF8_CHARSET));
            Pattern pat = Pattern.compile(".*make line \\^p1 ([0-9]*) \\^p2 ([0-9]*).*");
            String line = reader.readLine();
            while (line != null) {
                Matcher m = pat.matcher(line);
                if (m.matches()) {
                    Line l = new Line(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
                    result.add(l);
                }
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read file: " + dataFile, e);
        }
        return result;
    }

    private List<Label> loadLabels() {
        List<Label> result = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(WaltzDbBenchmark.class.getResourceAsStream("data/" + dataFile),
                            IoUtils.UTF8_CHARSET));
            Pattern pat = Pattern.compile(
                    ".*make label \\^type ([0-9a-z]*) \\^name ([0-9a-zA-Z]*) \\^id ([0-9]*) \\^n1 ([B+-]*) \\^n2 ([B+-]*)( \\^n3 ([B+-]*))?.*");
            String line = reader.readLine();
            while (line != null) {
                Matcher m = pat.matcher(line);
                if (m.matches()) {
                    Label l = new Label(m.group(1), m.group(2), m.group(3), m.group(4), m.group(5), m.group(7));
                    result.add(l);
                }
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read file: " + dataFile, e);
        }
        return result;
    }

    /**
     * Benchmark result container.
     */
    public static class BenchmarkResult {
        public final long totalTimeMs;
        public final long phase1TimeMs;
        public final long phase2TimeMs;
        public final long phase3TimeMs;
        public final long totalRulesFired;

        public BenchmarkResult(long totalTimeMs, long phase1TimeMs, long phase2TimeMs,
                long phase3TimeMs, long totalRulesFired) {
            this.totalTimeMs = totalTimeMs;
            this.phase1TimeMs = phase1TimeMs;
            this.phase2TimeMs = phase2TimeMs;
            this.phase3TimeMs = phase3TimeMs;
            this.totalRulesFired = totalRulesFired;
        }

        @Override
        public String toString() {
            return String.format("BenchmarkResult[total=%dms, phase1=%dms, phase2=%dms, phase3=%dms, rules=%d]",
                    totalTimeMs, phase1TimeMs, phase2TimeMs, phase3TimeMs, totalRulesFired);
        }
    }

    /**
     * Main method for standalone testing.
     */
    public static void main(String[] args) throws Exception {
        String dataFile = "waltzdb16_original_50.dat";
        int[] threadCounts = { 1, 2, 4, 8 };
        int iterations = 50;

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PPESB: Phased Parallel Execution with Sync Barriers         ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println("Data file: " + dataFile);
        System.out.println("Iterations per config: " + iterations);

        // Warmup
        System.out.println("\n>>> Warming up...");
        WaltzDbPhasedParallelBenchmark warmup = new WaltzDbPhasedParallelBenchmark(dataFile, 2, false);
        for (int i = 0; i < 10; i++) {
            warmup.execute();
        }

        // Run benchmarks
        System.out.println("\n>>> Running benchmarks...\n");
        System.out.printf("%-10s %-12s %-12s %-12s %-12s %-12s\n",
                "Threads", "Total(ms)", "Phase1(ms)", "Phase2(ms)", "Phase3(ms)", "Rules");
        System.out.println("-".repeat(70));

        for (int threads : threadCounts) {
            long totalSum = 0, p1Sum = 0, p2Sum = 0, p3Sum = 0, rulesSum = 0;

            for (int i = 0; i < iterations; i++) {
                WaltzDbPhasedParallelBenchmark benchmark = new WaltzDbPhasedParallelBenchmark(dataFile, threads, false);
                BenchmarkResult result = benchmark.execute();

                totalSum += result.totalTimeMs;
                p1Sum += result.phase1TimeMs;
                p2Sum += result.phase2TimeMs;
                p3Sum += result.phase3TimeMs;
                rulesSum += result.totalRulesFired;
            }

            System.out.printf("%-10d %-12.1f %-12.1f %-12.1f %-12.1f %-12d\n",
                    threads,
                    totalSum / (double) iterations,
                    p1Sum / (double) iterations,
                    p2Sum / (double) iterations,
                    p3Sum / (double) iterations,
                    rulesSum / iterations);
        }

        System.out.println("\n=== Benchmark Complete ===");
    }
}
