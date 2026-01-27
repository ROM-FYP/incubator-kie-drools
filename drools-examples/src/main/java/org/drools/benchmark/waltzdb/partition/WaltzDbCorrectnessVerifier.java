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

import org.drools.benchmark.waltzdb.*;
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
 * Verifies correctness of the partitioned WaltzDB approach by comparing
 * its results against the baseline non-partitioned execution.
 *
 * Correctness is verified by comparing:
 * 1. Total number of rules fired
 * 2. Final Edge facts (count and content)
 * 3. Final Junction facts (count and content)
 */
public class WaltzDbCorrectnessVerifier {

    private final String dataFile;
    private final InternalKnowledgeBase kbase;

    public WaltzDbCorrectnessVerifier(String dataFile) {
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
     * Holds the execution results for comparison.
     */
    public static class ExecutionResult {
        public final long rulesFired;
        public final Set<Edge> edges;
        public final Set<Junction> junctions;
        public final Set<EdgeLabel> edgeLabels;
        public final Stage finalStage;

        public ExecutionResult(long rulesFired, Set<Edge> edges, Set<Junction> junctions,
                               Set<EdgeLabel> edgeLabels, Stage finalStage) {
            this.rulesFired = rulesFired;
            this.edges = edges;
            this.junctions = junctions;
            this.edgeLabels = edgeLabels;
            this.finalStage = finalStage;
        }
    }

    /**
     * Run the baseline (non-partitioned) execution.
     */
    public ExecutionResult runBaseline() {
        List<Line> lines = loadLines();
        List<Label> labels = loadLabels();

        KieSession ksession = kbase.newKieSession();
        try {
            for (Line line : lines) {
                ksession.insert(line);
            }
            for (Label label : labels) {
                ksession.insert(label);
            }
            ksession.insert(new Stage(Stage.DUPLICATE));

            long rulesFired = ksession.fireAllRules();

            // Collect final facts
            Set<Edge> edges = new HashSet<>();
            Set<Junction> junctions = new HashSet<>();
            Set<EdgeLabel> edgeLabels = new HashSet<>();
            Stage finalStage = null;

            for (Object fact : ksession.getObjects()) {
                if (fact instanceof Edge) {
                    edges.add((Edge) fact);
                } else if (fact instanceof Junction) {
                    junctions.add((Junction) fact);
                } else if (fact instanceof EdgeLabel) {
                    edgeLabels.add((EdgeLabel) fact);
                } else if (fact instanceof Stage) {
                    finalStage = (Stage) fact;
                }
            }

            return new ExecutionResult(rulesFired, edges, junctions, edgeLabels, finalStage);
        } finally {
            ksession.dispose();
        }
    }

    /**
     * Run the partitioned execution with specified number of threads.
     */
    public ExecutionResult runPartitioned(int numThreads) throws Exception {
        WaltzDbGraphPartitioner partitioner = new WaltzDbGraphPartitioner(dataFile);
        List<WaltzDbPartition> partitions = partitioner.partition(numThreads);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // Collect results from all partitions
        List<Future<PartitionResult>> futures = new ArrayList<>();
        for (WaltzDbPartition partition : partitions) {
            futures.add(executor.submit(() -> runPartitionSession(partition)));
        }

        long totalRulesFired = 0;
        Set<Edge> allEdges = new HashSet<>();
        Set<Junction> allJunctions = new HashSet<>();
        Set<EdgeLabel> allEdgeLabels = new HashSet<>();

        for (Future<PartitionResult> future : futures) {
            PartitionResult result = future.get();
            totalRulesFired += result.rulesFired;
            allEdges.addAll(result.edges);
            allJunctions.addAll(result.junctions);
            allEdgeLabels.addAll(result.edgeLabels);
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        return new ExecutionResult(totalRulesFired, allEdges, allJunctions, allEdgeLabels, null);
    }

    private static class PartitionResult {
        long rulesFired;
        Set<Edge> edges;
        Set<Junction> junctions;
        Set<EdgeLabel> edgeLabels;
    }

    private PartitionResult runPartitionSession(WaltzDbPartition partition) {
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

            long rulesFired = ksession.fireAllRules();

            PartitionResult result = new PartitionResult();
            result.rulesFired = rulesFired;
            result.edges = new HashSet<>();
            result.junctions = new HashSet<>();
            result.edgeLabels = new HashSet<>();

            for (Object fact : ksession.getObjects()) {
                if (fact instanceof Edge) {
                    result.edges.add((Edge) fact);
                } else if (fact instanceof Junction) {
                    result.junctions.add((Junction) fact);
                } else if (fact instanceof EdgeLabel) {
                    result.edgeLabels.add((EdgeLabel) fact);
                }
            }

            return result;
        } finally {
            ksession.dispose();
        }
    }

    /**
     * Compare two execution results and report differences.
     */
    public void compareResults(ExecutionResult baseline, ExecutionResult partitioned,
                               int numThreads, boolean verbose) {
        System.out.println("\n" + "=".repeat(60));
        System.out.printf("CORRECTNESS VERIFICATION: Baseline vs %d-Thread Partitioned\n", numThreads);
        System.out.println("=".repeat(60));

        boolean allPassed = true;

        // 1. Compare rules fired
        System.out.printf("\n%-30s %s\n", "Metric", "Status");
        System.out.println("-".repeat(60));

        boolean rulesFiredMatch = baseline.rulesFired == partitioned.rulesFired;
        System.out.printf("%-30s %s (Baseline: %d, Partitioned: %d)\n",
                "Rules Fired",
                rulesFiredMatch ? "✓ MATCH" : "✗ MISMATCH",
                baseline.rulesFired,
                partitioned.rulesFired);
        allPassed &= rulesFiredMatch;

        // 2. Compare Edge counts
        boolean edgeCountMatch = baseline.edges.size() == partitioned.edges.size();
        System.out.printf("%-30s %s (Baseline: %d, Partitioned: %d)\n",
                "Edge Count",
                edgeCountMatch ? "✓ MATCH" : "✗ MISMATCH",
                baseline.edges.size(),
                partitioned.edges.size());
        allPassed &= edgeCountMatch;

        // 3. Compare Junction counts
        boolean junctionCountMatch = baseline.junctions.size() == partitioned.junctions.size();
        System.out.printf("%-30s %s (Baseline: %d, Partitioned: %d)\n",
                "Junction Count",
                junctionCountMatch ? "✓ MATCH" : "✗ MISMATCH",
                baseline.junctions.size(),
                partitioned.junctions.size());
        allPassed &= junctionCountMatch;

        // 4. Compare EdgeLabel counts
        boolean edgeLabelCountMatch = baseline.edgeLabels.size() == partitioned.edgeLabels.size();
        System.out.printf("%-30s %s (Baseline: %d, Partitioned: %d)\n",
                "EdgeLabel Count",
                edgeLabelCountMatch ? "✓ MATCH" : "✗ MISMATCH",
                baseline.edgeLabels.size(),
                partitioned.edgeLabels.size());
        allPassed &= edgeLabelCountMatch;

        // 5. Deep content comparison
        Set<Edge> edgesOnlyInBaseline = new HashSet<>(baseline.edges);
        edgesOnlyInBaseline.removeAll(partitioned.edges);

        Set<Edge> edgesOnlyInPartitioned = new HashSet<>(partitioned.edges);
        edgesOnlyInPartitioned.removeAll(baseline.edges);

        boolean edgeContentMatch = edgesOnlyInBaseline.isEmpty() && edgesOnlyInPartitioned.isEmpty();
        System.out.printf("%-30s %s\n",
                "Edge Content Identical",
                edgeContentMatch ? "✓ MATCH" : "✗ MISMATCH");
        allPassed &= edgeContentMatch;

        Set<Junction> junctionsOnlyInBaseline = new HashSet<>(baseline.junctions);
        junctionsOnlyInBaseline.removeAll(partitioned.junctions);

        Set<Junction> junctionsOnlyInPartitioned = new HashSet<>(partitioned.junctions);
        junctionsOnlyInPartitioned.removeAll(baseline.junctions);

        boolean junctionContentMatch = junctionsOnlyInBaseline.isEmpty() && junctionsOnlyInPartitioned.isEmpty();
        System.out.printf("%-30s %s\n",
                "Junction Content Identical",
                junctionContentMatch ? "✓ MATCH" : "✗ MISMATCH");
        allPassed &= junctionContentMatch;

        // Print details if verbose and there are mismatches
        if (verbose && !edgeContentMatch) {
            System.out.println("\n--- Edges only in Baseline ---");
            edgesOnlyInBaseline.stream().limit(10).forEach(e ->
                    System.out.printf("  Edge[type=%s, p1=%d, p2=%d, joined=%b]\n",
                            e.getType(), e.getP1(), e.getP2(), e.isJoined()));
            if (edgesOnlyInBaseline.size() > 10) {
                System.out.println("  ... and " + (edgesOnlyInBaseline.size() - 10) + " more");
            }

            System.out.println("\n--- Edges only in Partitioned ---");
            edgesOnlyInPartitioned.stream().limit(10).forEach(e ->
                    System.out.printf("  Edge[type=%s, p1=%d, p2=%d, joined=%b]\n",
                            e.getType(), e.getP1(), e.getP2(), e.isJoined()));
            if (edgesOnlyInPartitioned.size() > 10) {
                System.out.println("  ... and " + (edgesOnlyInPartitioned.size() - 10) + " more");
            }
        }

        if (verbose && !junctionContentMatch) {
            System.out.println("\n--- Junctions only in Baseline ---");
            junctionsOnlyInBaseline.stream().limit(10).forEach(j ->
                    System.out.printf("  Junction[type=%s, basePoint=%d, p1=%d, p2=%d]\n",
                            j.getType(), j.getBasePoint(), j.getP1(), j.getP2()));

            System.out.println("\n--- Junctions only in Partitioned ---");
            junctionsOnlyInPartitioned.stream().limit(10).forEach(j ->
                    System.out.printf("  Junction[type=%s, basePoint=%d, p1=%d, p2=%d]\n",
                            j.getType(), j.getBasePoint(), j.getP1(), j.getP2()));
        }

        // Final verdict
        System.out.println("\n" + "=".repeat(60));
        if (allPassed) {
            System.out.println("OVERALL RESULT: ✓ ALL TESTS PASSED - Partitioning is CORRECT");
        } else {
            System.out.println("OVERALL RESULT: ✗ TESTS FAILED - Partitioning has CORRECTNESS ISSUES");
            System.out.println("\n[!] WARNING: The partitioned approach differs from baseline.");
            System.out.println("    This indicates potential issues with:");
            System.out.println("    - Bridge line handling (duplicate processing)");
            System.out.println("    - Rule dependencies across partitions");
            System.out.println("    - State merging after parallel execution");
        }
        System.out.println("=".repeat(60));
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
                    Line l = new Line(Integer.parseInt(m.group(1)),
                            Integer.parseInt(m.group(2)));
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
                    Label l = new Label(m.group(1),
                            m.group(2),
                            m.group(3),
                            m.group(4),
                            m.group(5),
                            m.group(7));
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
     * Main method for running the correctness verification.
     */
    public static void main(String[] args) throws Exception {
        String dataFile = "waltzdb16.dat";
        int[] threadCounts = {1, 2, 4, 8};
        boolean verbose = true;

        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║       WaltzDB Partitioned Approach - Correctness Check   ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println("Data file: " + dataFile);
        System.out.println("Testing thread counts: " + Arrays.toString(threadCounts));
        System.out.println();

        WaltzDbCorrectnessVerifier verifier = new WaltzDbCorrectnessVerifier(dataFile);

        // Run baseline once
        System.out.println(">>> Running BASELINE (non-partitioned)...");
        long startTime = System.currentTimeMillis();
        ExecutionResult baseline = verifier.runBaseline();
        long baselineTime = System.currentTimeMillis() - startTime;
        System.out.printf("    Completed in %d ms. Rules fired: %d\n",
                baselineTime, baseline.rulesFired);
        System.out.printf("    Facts: %d Edges, %d Junctions, %d EdgeLabels\n",
                baseline.edges.size(), baseline.junctions.size(), baseline.edgeLabels.size());

        // Test each thread count
        for (int threads : threadCounts) {
            System.out.println("\n>>> Running PARTITIONED with " + threads + " thread(s)...");
            startTime = System.currentTimeMillis();
            ExecutionResult partitioned = verifier.runPartitioned(threads);
            long partitionedTime = System.currentTimeMillis() - startTime;
            System.out.printf("    Completed in %d ms. Rules fired: %d\n",
                    partitionedTime, partitioned.rulesFired);

            // Compare results
            verifier.compareResults(baseline, partitioned, threads, verbose);
        }

        System.out.println("\n=== Verification Complete ===");
    }
}
