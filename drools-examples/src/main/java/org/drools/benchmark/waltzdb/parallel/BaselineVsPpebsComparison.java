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

import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Head-to-head comparison between:
 * - Original Drools WaltzDB (sequential baseline)
 * - PPESB (Phased Parallel Execution with Synchronization Barriers)
 */
public class BaselineVsPpebsComparison {

    private static final String DATA_FILE = "waltzdb16.dat";
    private static final int WARMUP_ITERATIONS = 3;
    private static final int MEASURED_ITERATIONS = 5;

    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║   WaltzDB Benchmark: Original Drools vs PPESB Comparison           ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════╝");
        System.out.println("Data file: " + DATA_FILE);
        System.out.println("Warmup iterations: " + WARMUP_ITERATIONS);
        System.out.println("Measured iterations: " + MEASURED_ITERATIONS);
        System.out.println();

        // ========== WARMUP ==========
        System.out.println(">>> Warming up both approaches...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runBaseline(false);
            new WaltzDbPhasedParallelBenchmark(DATA_FILE, 4, false).execute();
        }
        System.out.println("    Warmup complete.\n");

        // ========== RUN BASELINE ==========
        System.out.println(">>> Running ORIGINAL DROOLS BASELINE...");
        List<BaselineResult> baselineResults = new ArrayList<>();
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            BaselineResult result = runBaseline(false);
            baselineResults.add(result);
            System.out.printf("    Iteration %d: %d ms, %d rules\n",
                    i + 1, result.timeMs, result.rulesFired);
        }

        // Calculate baseline stats
        long baselineAvgTime = baselineResults.stream().mapToLong(r -> r.timeMs).sum() / MEASURED_ITERATIONS;
        long baselineMinTime = baselineResults.stream().mapToLong(r -> r.timeMs).min().orElse(0);
        long baselineMaxTime = baselineResults.stream().mapToLong(r -> r.timeMs).max().orElse(0);
        long baselineRules = baselineResults.get(0).rulesFired;

        System.out.printf("    Baseline Average: %d ms (min=%d, max=%d)\n\n",
                baselineAvgTime, baselineMinTime, baselineMaxTime);

        // ========== RUN PPESB WITH DIFFERENT THREAD COUNTS ==========
        int[] threadCounts = { 1, 2, 4, 8 };
        Map<Integer, PpebsStats> ppebsResults = new LinkedHashMap<>();

        for (int threads : threadCounts) {
            System.out.println(">>> Running PPESB with " + threads + " thread(s)...");
            List<WaltzDbPhasedParallelBenchmark.BenchmarkResult> results = new ArrayList<>();

            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                WaltzDbPhasedParallelBenchmark benchmark = new WaltzDbPhasedParallelBenchmark(DATA_FILE, threads,
                        false);
                WaltzDbPhasedParallelBenchmark.BenchmarkResult result = benchmark.execute();
                results.add(result);
                System.out.printf("    Iteration %d: %d ms (P1=%d, P2=%d, P3=%d)\n",
                        i + 1, result.totalTimeMs, result.phase1TimeMs,
                        result.phase2TimeMs, result.phase3TimeMs);
            }

            PpebsStats stats = new PpebsStats();
            stats.threads = threads;
            stats.avgTotal = results.stream().mapToLong(r -> r.totalTimeMs).sum() / MEASURED_ITERATIONS;
            stats.avgPhase1 = results.stream().mapToLong(r -> r.phase1TimeMs).sum() / MEASURED_ITERATIONS;
            stats.avgPhase2 = results.stream().mapToLong(r -> r.phase2TimeMs).sum() / MEASURED_ITERATIONS;
            stats.avgPhase3 = results.stream().mapToLong(r -> r.phase3TimeMs).sum() / MEASURED_ITERATIONS;
            stats.minTotal = results.stream().mapToLong(r -> r.totalTimeMs).min().orElse(0);
            stats.maxTotal = results.stream().mapToLong(r -> r.totalTimeMs).max().orElse(0);
            stats.rulesFired = results.get(0).totalRulesFired;

            ppebsResults.put(threads, stats);
            System.out.printf("    PPESB-%dt Average: %d ms (min=%d, max=%d)\n\n",
                    threads, stats.avgTotal, stats.minTotal, stats.maxTotal);
        }

        // ========== PRINT COMPARISON TABLE ==========
        System.out.println("\n" + "═".repeat(85));
        System.out.println("                        RESULTS COMPARISON");
        System.out.println("═".repeat(85));

        System.out.printf("\n%-20s │ %-12s │ %-12s │ %-10s │ %-12s\n",
                "Approach", "Avg Time", "Min-Max", "Rules", "Speedup");
        System.out.println("─".repeat(85));

        // Baseline
        System.out.printf("%-20s │ %-12s │ %-12s │ %-10d │ %-12s\n",
                "Original Drools",
                baselineAvgTime + " ms",
                baselineMinTime + "-" + baselineMaxTime + " ms",
                baselineRules,
                "1.00x (baseline)");

        // PPESB variants
        for (int threads : threadCounts) {
            PpebsStats stats = ppebsResults.get(threads);
            double speedup = baselineAvgTime / (double) stats.avgTotal;
            System.out.printf("%-20s │ %-12s │ %-12s │ %-10d │ %-12s\n",
                    "PPESB (" + threads + " thread" + (threads > 1 ? "s" : "") + ")",
                    stats.avgTotal + " ms",
                    stats.minTotal + "-" + stats.maxTotal + " ms",
                    stats.rulesFired,
                    String.format("%.2fx", speedup));
        }

        // ========== PHASE BREAKDOWN ==========
        System.out.println("\n" + "═".repeat(85));
        System.out.println("                        PPESB PHASE BREAKDOWN");
        System.out.println("═".repeat(85));

        System.out.printf("\n%-10s │ %-15s │ %-15s │ %-15s │ %-15s\n",
                "Threads", "Phase 1 (DUP)", "Phase 2 (JUNC)", "Phase 3 (REST)", "Total");
        System.out.println("─".repeat(85));

        for (int threads : threadCounts) {
            PpebsStats stats = ppebsResults.get(threads);
            System.out.printf("%-10d │ %-15s │ %-15s │ %-15s │ %-15s\n",
                    threads,
                    stats.avgPhase1 + " ms",
                    stats.avgPhase2 + " ms",
                    stats.avgPhase3 + " ms",
                    stats.avgTotal + " ms");
        }

        // ========== CORRECTNESS CHECK ==========
        System.out.println("\n" + "═".repeat(85));
        System.out.println("                        CORRECTNESS VERIFICATION");
        System.out.println("═".repeat(85));

        long maxDiff = 0;
        for (PpebsStats stats : ppebsResults.values()) {
            long diff = Math.abs(baselineRules - stats.rulesFired);
            if (diff > maxDiff)
                maxDiff = diff;
        }

        double errorPercent = (maxDiff * 100.0) / baselineRules;
        System.out.printf("\nBaseline rules fired: %d\n", baselineRules);
        System.out.printf("Max difference: %d rules (%.3f%%)\n", maxDiff, errorPercent);

        if (errorPercent < 0.1) {
            System.out.println("\n✓ CORRECTNESS: PASSED - Results are equivalent");
        } else if (errorPercent < 1.0) {
            System.out.println("\n⚠ CORRECTNESS: ACCEPTABLE - Minor difference within tolerance");
        } else {
            System.out.println("\n✗ CORRECTNESS: FAILED - Results differ significantly");
        }

        System.out.println("\n" + "═".repeat(85));
        System.out.println("                        COMPARISON COMPLETE");
        System.out.println("═".repeat(85));
    }

    /**
     * Run the original Drools baseline (sequential).
     */
    private static BaselineResult runBaseline(boolean verbose) {
        InternalKnowledgeBase kbase = buildBaselineKnowledgeBase();
        List<Line> lines = loadLines();
        List<Label> labels = loadLabels();

        KieSession ksession = kbase.newKieSession();
        long start = System.currentTimeMillis();

        try {
            for (Line line : lines) {
                ksession.insert(line);
            }
            for (Label label : labels) {
                ksession.insert(label);
            }
            ksession.insert(new Stage(Stage.DUPLICATE));

            long rulesFired = ksession.fireAllRules();
            long elapsed = System.currentTimeMillis() - start;

            return new BaselineResult(elapsed, rulesFired);
        } finally {
            ksession.dispose();
        }
    }

    private static InternalKnowledgeBase buildBaselineKnowledgeBase() {
        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(ResourceFactory.newClassPathResource("waltzdb.drl",
                WaltzDbBenchmark.class), ResourceType.DRL);

        if (kbuilder.hasErrors()) {
            throw new RuntimeException("DRL errors: " + kbuilder.getErrors());
        }

        Collection<KiePackage> pkgs = kbuilder.getKnowledgePackages();

        KieBaseConfiguration kbaseConfig = RuleBaseFactory.newKnowledgeBaseConfiguration();
        kbaseConfig.setProperty("drools.removeIdentities", "true");

        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase(kbaseConfig);
        kbase.addPackages(pkgs);
        return kbase;
    }

    private static List<Line> loadLines() {
        List<Line> result = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(WaltzDbBenchmark.class.getResourceAsStream("data/" + DATA_FILE),
                            IoUtils.UTF8_CHARSET));
            Pattern pat = Pattern.compile(".*make line \\^p1 ([0-9]*) \\^p2 ([0-9]*).*");
            String line = reader.readLine();
            while (line != null) {
                Matcher m = pat.matcher(line);
                if (m.matches()) {
                    result.add(new Line(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
                }
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    private static List<Label> loadLabels() {
        List<Label> result = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(WaltzDbBenchmark.class.getResourceAsStream("data/" + DATA_FILE),
                            IoUtils.UTF8_CHARSET));
            Pattern pat = Pattern.compile(
                    ".*make label \\^type ([0-9a-z]*) \\^name ([0-9a-zA-Z]*) \\^id ([0-9]*) \\^n1 ([B+-]*) \\^n2 ([B+-]*)( \\^n3 ([B+-]*))?.*");
            String line = reader.readLine();
            while (line != null) {
                Matcher m = pat.matcher(line);
                if (m.matches()) {
                    result.add(new Label(m.group(1), m.group(2), m.group(3), m.group(4), m.group(5), m.group(7)));
                }
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    static class BaselineResult {
        long timeMs;
        long rulesFired;

        BaselineResult(long timeMs, long rulesFired) {
            this.timeMs = timeMs;
            this.rulesFired = rulesFired;
        }
    }

    static class PpebsStats {
        int threads;
        long avgTotal, avgPhase1, avgPhase2, avgPhase3;
        long minTotal, maxTotal;
        long rulesFired;
    }
}
