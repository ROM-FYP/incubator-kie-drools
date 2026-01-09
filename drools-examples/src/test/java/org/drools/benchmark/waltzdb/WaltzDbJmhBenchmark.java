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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Collection;

/**
 * JMH Benchmark for WaltzDB - Research-grade execution time measurement.
 * 
 * This benchmark uses proper JMH methodology:
 * - Fork isolation: Each fork runs in a separate JVM
 * - Warmup iterations: JIT compilation completes before measurement
 * - Blackhole: Prevents dead code elimination
 * - SingleShotTime mode: Measures single execution time (not throughput)
 * 
 * Run with: java -jar benchmarks.jar WaltzDbJmhBenchmark
 * Or programmatically via BenchmarkRunner
 */
@Fork(5)
@State(Scope.Thread)
@BenchmarkMode(Mode.SingleShotTime)
@Warmup(iterations = 10)
@Measurement(iterations = 50)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class WaltzDbJmhBenchmark {

    /**
     * Data file parameter - can be configured via -p dataFile=waltzdb8.dat
     */
    @Param({ "waltzdb16.dat" })
    private String dataFile;

    private InternalKnowledgeBase kbase;
    private List<Line> lines;
    private List<Label> labels;
    private KieSession ksession;

    /**
     * Setup the KnowledgeBase once per fork (Trial level).
     * This is expensive and should not be measured.
     */
    @Setup(Level.Trial)
    public void setupKnowledgeBase() {
        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(ResourceFactory.newClassPathResource("waltzdb.drl",
                WaltzDbBenchmark.class),
                ResourceType.DRL);

        if (kbuilder.hasErrors()) {
            throw new RuntimeException("DRL compilation errors: " + kbuilder.getErrors());
        }

        Collection<KiePackage> pkgs = kbuilder.getKnowledgePackages();

        KieBaseConfiguration kbaseConfiguration = RuleBaseFactory.newKnowledgeBaseConfiguration();
        kbaseConfiguration.setProperty("drools.removeIdentities", "true");

        kbase = KnowledgeBaseFactory.newKnowledgeBase(kbaseConfiguration);
        kbase.addPackages(pkgs);

        // Load data once per fork
        lines = loadLines(dataFile);
        labels = loadLabels(dataFile);
    }

    /**
     * Setup before each benchmark invocation.
     * Creates a fresh KieSession for each measurement.
     */
    @Setup(Level.Invocation)
    public void setupSession() {
        ksession = kbase.newKieSession();
    }

    /**
     * Cleanup after each benchmark invocation.
     * Disposes the session to prevent memory leaks.
     */
    @TearDown(Level.Invocation)
    public void tearDownSession() {
        if (ksession != null) {
            ksession.dispose();
            ksession = null;
        }
    }

    /**
     * The main benchmark method.
     * Measures the time to insert facts and fire all rules.
     * 
     * @param bh Blackhole to prevent dead code elimination
     * @return Number of rules fired (consumed by blackhole)
     */
    @Benchmark
    public long runWaltzDb(Blackhole bh) {
        // Insert Line facts
        for (Line line : lines) {
            ksession.insert(line);
        }

        // Insert Label facts
        for (Label label : labels) {
            ksession.insert(label);
        }

        // Insert initial Stage
        Stage stage = new Stage(Stage.DUPLICATE);
        ksession.insert(stage);

        // Fire all rules and measure
        long rulesFired = ksession.fireAllRules();

        // Consume result to prevent dead code elimination
        bh.consume(rulesFired);

        return rulesFired;
    }

    /**
     * Load Line objects from data file.
     */
    private List<Line> loadLines(String filename) {
        List<Line> result = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(WaltzDbBenchmark.class.getResourceAsStream("data/" + filename),
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
            throw new IllegalArgumentException("Could not read file: " + filename, e);
        }
        return result;
    }

    /**
     * Load Label objects from data file.
     */
    private List<Label> loadLabels(String filename) {
        List<Label> result = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(WaltzDbBenchmark.class.getResourceAsStream("data/" + filename),
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
                            m.group(6));
                    result.add(l);
                }
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read file: " + filename, e);
        }
        return result;
    }
}
