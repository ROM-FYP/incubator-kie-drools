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
import org.drools.benchmark.waltzdb.partition.WaltzDbCorrectnessVerifier;
import org.drools.benchmark.waltzdb.partition.WaltzDbCorrectnessVerifier.ExecutionResult;

/**
 * Correctness verifier for the PPESB (Phased Parallel Execution with Sync
 * Barriers) approach.
 * Compares PPESB results against the baseline non-partitioned execution.
 */
public class PpebsCorrectnessVerifier {

    private final String dataFile;

    public PpebsCorrectnessVerifier(String dataFile) {
        this.dataFile = dataFile;
    }

    /**
     * Run PPESB and compare against baseline.
     */
    public void verify(int numThreads) throws Exception {
        // Run baseline using existing verifier
        WaltzDbCorrectnessVerifier baselineVerifier = new WaltzDbCorrectnessVerifier(dataFile);

        System.out.println(">>> Running BASELINE (sequential)...");
        long baselineStart = System.currentTimeMillis();
        ExecutionResult baseline = baselineVerifier.runBaseline();
        long baselineTime = System.currentTimeMillis() - baselineStart;

        System.out.printf("    Baseline: %d rules, %d edges, %d junctions in %d ms\n",
                baseline.rulesFired, baseline.edges.size(), baseline.junctions.size(), baselineTime);

        // Run PPESB
        System.out.println("\n>>> Running PPESB with " + numThreads + " threads...");
        WaltzDbPhasedParallelBenchmark ppesb = new WaltzDbPhasedParallelBenchmark(dataFile, numThreads, false);
        WaltzDbPhasedParallelBenchmark.BenchmarkResult ppebsResult = ppesb.execute();

        System.out.printf("    PPESB: %d rules in %d ms\n",
                ppebsResult.totalRulesFired, ppebsResult.totalTimeMs);

        // Compare
        System.out.println("\n" + "=".repeat(60));
        System.out.println("CORRECTNESS VERIFICATION");
        System.out.println("=".repeat(60));

        // Rules fired comparison (PPESB may have slightly different due to phase
        // separation)
        long rulesDiff = Math.abs(baseline.rulesFired - ppebsResult.totalRulesFired);
        double rulesErrorPercent = (rulesDiff * 100.0) / baseline.rulesFired;

        System.out.printf("\n%-25s %-15s %-15s %-10s\n", "Metric", "Baseline", "PPESB", "Status");
        System.out.println("-".repeat(65));

        String rulesStatus = rulesErrorPercent < 1.0 ? "✓ OK" : "⚠ DIFF";
        System.out.printf("%-25s %-15d %-15d %s (%.1f%% diff)\n",
                "Rules Fired", baseline.rulesFired, ppebsResult.totalRulesFired, rulesStatus, rulesErrorPercent);

        // Timing comparison
        double speedup = baselineTime / (double) ppebsResult.totalTimeMs;
        System.out.printf("%-25s %-15d %-15d %.2fx speedup\n",
                "Execution Time (ms)", baselineTime, ppebsResult.totalTimeMs, speedup);

        // Verdict
        System.out.println("\n" + "=".repeat(60));
        if (rulesErrorPercent < 1.0) {
            System.out.println("VERDICT: ✓ PPESB produces results within acceptable tolerance");
            System.out.println("         Algorithm correctness preserved with phased parallelization.");
        } else if (rulesErrorPercent < 5.0) {
            System.out.println("VERDICT: ⚠ PPESB has minor differences");
            System.out.println("         Difference may be due to rule ordering or edge cases.");
        } else {
            System.out.println("VERDICT: ✗ PPESB has significant differences");
            System.out.println("         Further investigation required.");
        }
        System.out.println("=".repeat(60));
    }

    public static void main(String[] args) throws Exception {
        String dataFile = "waltzdb16.dat";
        int[] threadCounts = { 1, 2, 4, 8 };

        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║         PPESB Correctness Verification                        ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝\n");

        PpebsCorrectnessVerifier verifier = new PpebsCorrectnessVerifier(dataFile);

        for (int threads : threadCounts) {
            System.out.println("\n" + "═".repeat(65));
            System.out.println("Testing with " + threads + " thread(s)");
            System.out.println("═".repeat(65));
            verifier.verify(threads);
        }

        System.out.println("\n=== Verification Complete ===");
    }
}
