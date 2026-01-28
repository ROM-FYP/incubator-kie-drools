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

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.TimeUnit;

/**
 * Runner class for the PPESB (Phased Parallel Execution with Sync Barriers) JMH
 * Benchmark.
 * 
 * This runner provides a convenient way to execute the PPESB benchmark with
 * research-grade
 * configuration for statistical analysis.
 * 
 * Usage:
 * java -cp <classpath> org.drools.benchmark.waltzdb.PpebsBenchmarkRunner
 * 
 * Or with custom options:
 * java -cp <classpath> org.drools.benchmark.waltzdb.PpebsBenchmarkRunner
 * [forks] [warmup] [iterations] [outputFile]
 * 
 * Example:
 * java -cp <classpath> org.drools.benchmark.waltzdb.PpebsBenchmarkRunner 3 5 20
 * ppesb_results.csv
 */
public class PpebsBenchmarkRunner {

    public static void main(String[] args) throws RunnerException {
        // Default configuration for research-grade benchmarking
        int forks = 3;
        int warmupIterations = 5;
        int measurementIterations = 20;
        String outputFile = "ppesb_jmh_results.csv";
        boolean enableGcProfiler = false;

        // Parse command-line arguments if provided
        if (args.length >= 1) {
            forks = Integer.parseInt(args[0]);
        }
        if (args.length >= 2) {
            warmupIterations = Integer.parseInt(args[1]);
        }
        if (args.length >= 3) {
            measurementIterations = Integer.parseInt(args[2]);
        }
        if (args.length >= 4) {
            outputFile = args[3];
        }
        if (args.length >= 5) {
            enableGcProfiler = Boolean.parseBoolean(args[4]);
        }

        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║   PPESB JMH Benchmark - Research Grade                           ║");
        System.out.println("║   Phased Parallel Execution with Synchronization Barriers        ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  Forks:                  " + forks);
        System.out.println("  Warmup iterations:      " + warmupIterations);
        System.out.println("  Measurement iterations: " + measurementIterations);
        System.out.println("  Output file:            " + outputFile);
        System.out.println("  GC Profiler:            " + (enableGcProfiler ? "ENABLED" : "DISABLED"));
        System.out.println("  Thread counts:          1, 2, 4, 8");
        System.out.println();
        System.out.println("Benchmark Phases:");
        System.out.println("  Phase 1: DUPLICATE      (Parallel - Line → Edge conversion)");
        System.out.println("  Phase 2: DETECT_JUNCT   (Parallel - Junction detection)");
        System.out.println("  Phase 3: REMAINING      (Sequential - Constraint propagation)");
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════════");
        System.out.println();

        Options opt;
        if (enableGcProfiler) {
            opt = new OptionsBuilder()
                    .include(WaltzDbPpebsJmhBenchmark.class.getSimpleName())
                    .forks(forks)
                    .warmupIterations(warmupIterations)
                    .measurementIterations(measurementIterations)
                    .warmupTime(TimeValue.NONE)
                    .measurementTime(TimeValue.NONE)
                    .timeUnit(TimeUnit.MILLISECONDS)
                    .resultFormat(ResultFormatType.CSV)
                    .result(outputFile)
                    .jvmArgs("-Xms512m", "-Xmx4g")
                    .addProfiler("gc")
                    .build();
        } else {
            opt = new OptionsBuilder()
                    .include(WaltzDbPpebsJmhBenchmark.class.getSimpleName())
                    .forks(forks)
                    .warmupIterations(warmupIterations)
                    .measurementIterations(measurementIterations)
                    .warmupTime(TimeValue.NONE)
                    .measurementTime(TimeValue.NONE)
                    .timeUnit(TimeUnit.MILLISECONDS)
                    .resultFormat(ResultFormatType.CSV)
                    .result(outputFile)
                    .jvmArgs("-Xms512m", "-Xmx4g")
                    .build();
        }

        System.out.println("Starting PPESB benchmark...");
        System.out.println();

        new Runner(opt).run();

        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════════");
        System.out.println("PPESB Benchmark complete!");
        System.out.println("Results saved to: " + outputFile);
        System.out.println();
        System.out.println("Analyze with:");
        System.out.println("  - Import CSV into R/Python for statistical analysis");
        System.out.println("  - Use JMH Visualizer: https://jmh.morethan.io/");
        System.out.println("═══════════════════════════════════════════════════════════════════");
    }
}
