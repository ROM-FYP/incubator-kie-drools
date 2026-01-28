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
package org.drools.benchmark.waltzdb;

import org.drools.benchmark.waltzdb.parallel.WaltzDbPhasedParallelBenchmark;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmark for PPESB (Phased Parallel Execution with Synchronization
 * Barriers).
 * 
 * This benchmark measures the performance of the three-phase parallel WaltzDB
 * approach:
 * - Phase 1: DUPLICATE (parallel) - Lines to Edges conversion
 * - Phase 2: DETECT_JUNCTIONS (parallel) - Junction detection with basePoint
 * partitioning
 * - Phase 3: REMAINING STAGES (sequential) - Constraint propagation
 * 
 * Benchmark Parameters:
 * - dataFile: Input data file (default: waltzdb16.dat)
 * - numThreads: Thread count for parallel phases (1, 2, 4, 8)
 * 
 * Run with:
 * java -jar benchmarks.jar WaltzDbPpebsJmhBenchmark
 * 
 * Or with specific parameters:
 * java -jar benchmarks.jar WaltzDbPpebsJmhBenchmark -p numThreads=4 -p
 * dataFile=waltzdb16.dat
 */
@Fork(3)
@State(Scope.Thread)
@BenchmarkMode(Mode.SingleShotTime)
@Warmup(iterations = 5)
@Measurement(iterations = 20)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class WaltzDbPpebsJmhBenchmark {

    /**
     * Data file parameter - can be configured via -p dataFile=waltzdb8.dat
     */
    @Param({ "waltzdb16.dat" })
    private String dataFile;

    /**
     * Number of threads for parallel phases.
     * Tests scalability across different thread counts.
     */
    @Param({ "1", "2", "4", "8" })
    private int numThreads;

    /**
     * Reusable benchmark instance (data loading is expensive).
     * Created once per trial to avoid measuring data loading time.
     */
    private WaltzDbPhasedParallelBenchmark ppebsBenchmark;

    /**
     * Setup the PPESB benchmark instance once per trial.
     * This loads data files and prepares knowledge bases.
     */
    @Setup(Level.Trial)
    public void setup() {
        // Create benchmark instance with verbose=false for clean JMH output
        ppebsBenchmark = new WaltzDbPhasedParallelBenchmark(dataFile, numThreads, false);
    }

    /**
     * Main benchmark method - measures complete PPESB execution.
     * 
     * Includes:
     * - Phase 1 parallel execution (DUPLICATE)
     * - Barrier 1 synchronization
     * - Phase 2 parallel execution (DETECT_JUNCTIONS)
     * - Barrier 2 synchronization
     * - Phase 3 sequential execution (remaining stages)
     * 
     * @param bh Blackhole to prevent dead code elimination
     * @return BenchmarkResult containing timing breakdown
     */
    @Benchmark
    public WaltzDbPhasedParallelBenchmark.BenchmarkResult runPpesb(Blackhole bh) throws Exception {
        WaltzDbPhasedParallelBenchmark.BenchmarkResult result = ppebsBenchmark.execute();

        // Consume all relevant outputs to prevent dead code elimination
        bh.consume(result.totalTimeMs);
        bh.consume(result.phase1TimeMs);
        bh.consume(result.phase2TimeMs);
        bh.consume(result.phase3TimeMs);
        bh.consume(result.totalRulesFired);

        return result;
    }

    /**
     * Auxiliary benchmark to measure Phase 1 + Phase 2 (parallel portions only).
     * Useful for understanding the parallel scalability independent of Phase 3.
     * 
     * Note: This creates a new benchmark instance per invocation since we need
     * to capture intermediate state. Use with caution for statistical analysis.
     */
    @Benchmark
    public long runParallelPhasesOnly(Blackhole bh) throws Exception {
        WaltzDbPhasedParallelBenchmark.BenchmarkResult result = ppebsBenchmark.execute();

        long parallelTime = result.phase1TimeMs + result.phase2TimeMs;
        bh.consume(parallelTime);
        bh.consume(result.totalRulesFired);

        return parallelTime;
    }
}
