# WaltzDB Partition Experiment - Complete Implementation Guide

> [!IMPORTANT]
> This guide provides step-by-step instructions to implement and run the **WaltzDB Partitioned Parallel Execution Experiment** using graph partitioning with JGraphT and parallel Drools KieSessions.

---

## Table of Contents
1. [Experiment Overview](#1-experiment-overview)
2. [Technology Stack](#2-technology-stack)
3. [Project Structure](#3-project-structure)
4. [Step 1: Create the Partition Experiment Module](#step-1-create-the-partition-experiment-module)
5. [Step 2: Maven Configuration](#step-2-maven-configuration)
6. [Step 3: POJO Classes](#step-3-pojo-classes)
7. [Step 4: Graph Partitioner Implementation](#step-4-graph-partitioner-implementation)
8. [Step 5: Parallel Benchmark Runner](#step-5-parallel-benchmark-runner)
9. [Step 6: JMH Benchmark Class](#step-6-jmh-benchmark-class)
10. [How to Run](#how-to-run)
11. [Expected Results](#expected-results)
12. [Troubleshooting](#troubleshooting)

---

## 1. Experiment Overview

### Purpose
This experiment evaluates the **parallelization potential** of the WaltzDB benchmark by:
1. **Partitioning** the WaltzDB graph data using Label Propagation Clustering
2. **Binning** micro-clusters into larger partitions (balanced workloads)
3. **Running** separate KieSessions on each partition with 1, 2, 4, and 8 threads
4. **Measuring** execution time speedup

### Key Concepts

| Concept | Description |
|---------|-------------|
| **Graph Partitioning** | Using JGraphT's Label Propagation to detect communities in the junction graph |
| **Micro-Clusters** | Initial small partitions from algorithm (may need binning) |
| **Binning** | Merging micro-clusters to create balanced partitions |
| **Parallel Sessions** | Each thread runs a separate KieSession with a partition's subset of facts |
| **Bridge Lines** | Lines crossing partitions (handled via ghost facts if needed) |

### Experiment Matrix

| Threads | Description |
|---------|-------------|
| **1** | Baseline (sequential execution) |
| **2** | 2 partitions, 2 parallel sessions |
| **4** | 4 partitions, 4 parallel sessions |
| **8** | 8 partitions, 8 parallel sessions |

---

## 2. Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Java | 17+ |
| Build Tool | Maven | 3.6+ |
| Rule Engine | Drools | (existing project version) |
| Graph Library | JGraphT Core | 1.5.2 |
| Graph Algorithms | JGraphT Opt | 1.5.2 |
| Benchmarking | JMH | 1.37 |

---

## 3. Project Structure

Create the following structure within the Drools project:

```
drools-examples/
├── src/main/java/org/drools/benchmark/waltzdb/
│   ├── ... (existing files)
│   └── partition/                           # NEW DIRECTORY
│       ├── WaltzDbGraphPartitioner.java     # Graph partitioning logic
│       ├── WaltzDbPartition.java            # Partition data holder
│       └── WaltzDbPartitionedBenchmark.java # Main partitioned runner
├── src/test/java/org/drools/benchmark/waltzdb/
│   └── WaltzDbPartitionedJmhBenchmark.java  # JMH benchmark
└── src/main/resources/org/drools/benchmark/waltzdb/
    └── ... (existing files)
```

---

## Step 1: Create the Partition Experiment Module

### 1.1 Create Directory Structure

```bash
cd /home/maheshdila/mahesh/research/incubator-kie-drools/drools-examples

# Create partition directory
mkdir -p src/main/java/org/drools/benchmark/waltzdb/partition
```

---

## Step 2: Maven Configuration

### 2.1 Add JGraphT Dependencies

Open `drools-examples/pom.xml` and add the JGraphT dependencies:

```xml
<!-- Add to <dependencies> section -->
<dependency>
    <groupId>org.jgrapht</groupId>
    <artifactId>jgrapht-core</artifactId>
    <version>1.5.2</version>
</dependency>
<dependency>
    <groupId>org.jgrapht</groupId>
    <artifactId>jgrapht-opt</artifactId>
    <version>1.5.2</version>
</dependency>
```

---

## Step 3: POJO Classes

### 3.1 Partition Data Holder

Create `WaltzDbPartition.java`:

```java
package org.drools.benchmark.waltzdb.partition;

import org.drools.benchmark.waltzdb.Line;
import org.drools.benchmark.waltzdb.Label;

import java.util.List;
import java.util.Set;

/**
 * Represents a partition of the WaltzDB dataset.
 * Each partition contains a subset of Lines and Labels
 * that should be processed together.
 */
public class WaltzDbPartition {
    private final int partitionId;
    private final Set<Integer> junctionIds;  // Junction points in this partition
    private final List<Line> lines;          // Lines internal to this partition
    private final List<Line> bridgeLines;    // Lines crossing to other partitions
    private final List<Label> labels;        // Labels (same for all partitions)

    public WaltzDbPartition(int partitionId, Set<Integer> junctionIds,
                            List<Line> lines, List<Line> bridgeLines,
                            List<Label> labels) {
        this.partitionId = partitionId;
        this.junctionIds = junctionIds;
        this.lines = lines;
        this.bridgeLines = bridgeLines;
        this.labels = labels;
    }

    public int getPartitionId() { return partitionId; }
    public Set<Integer> getJunctionIds() { return junctionIds; }
    public List<Line> getLines() { return lines; }
    public List<Line> getBridgeLines() { return bridgeLines; }
    public List<Label> getLabels() { return labels; }

    public int getTotalLineCount() {
        return lines.size() + bridgeLines.size();
    }

    @Override
    public String toString() {
        return String.format("Partition[id=%d, junctions=%d, internal=%d, bridge=%d]",
            partitionId, junctionIds.size(), lines.size(), bridgeLines.size());
    }
}
```

---

## Step 4: Graph Partitioner Implementation

### 4.1 Create the Partitioner

Create `WaltzDbGraphPartitioner.java`:

```java
package org.drools.benchmark.waltzdb.partition;

import org.drools.benchmark.waltzdb.Line;
import org.drools.benchmark.waltzdb.Label;
import org.drools.benchmark.waltzdb.WaltzDbBenchmark;
import org.drools.util.IoUtils;
import org.jgrapht.Graph;
import org.jgrapht.alg.clustering.LabelPropagationClustering;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Partitions WaltzDB data using graph community detection.
 * Uses Label Propagation Clustering from JGraphT.
 */
public class WaltzDbGraphPartitioner {

    private final String dataFile;
    private List<Line> allLines;
    private List<Label> allLabels;

    public WaltzDbGraphPartitioner(String dataFile) {
        this.dataFile = dataFile;
    }

    /**
     * Load and partition the data into the specified number of partitions.
     *
     * @param numPartitions Target number of partitions (may vary due to algorithm)
     * @return List of partitions ready for parallel processing
     */
    public List<WaltzDbPartition> partition(int numPartitions) {
        // 1. Load all data
        allLines = loadLines();
        allLabels = loadLabels();

        // 2. Build graph from lines
        Graph<Integer, DefaultEdge> graph = buildGraph();
        System.out.println("Graph built: " + graph.vertexSet().size() + " vertices, " 
            + graph.edgeSet().size() + " edges");

        // 3. Run Label Propagation Clustering
        LabelPropagationClustering<Integer, DefaultEdge> clustering = 
            new LabelPropagationClustering<>(graph);
        var clusters = clustering.getClustering();

        System.out.println("Initial clusters found: " + clusters.getNumberClusters());

        // 4. Build node-to-partition mapping
        Map<Integer, Integer> nodeToPartition = new HashMap<>();
        List<Set<Integer>> clusterList = new ArrayList<>();
        int clusterId = 0;
        for (Set<Integer> cluster : clusters.getClusters()) {
            clusterList.add(cluster);
            for (Integer node : cluster) {
                nodeToPartition.put(node, clusterId);
            }
            clusterId++;
        }

        // 5. Bin micro-clusters into target number of partitions
        List<Set<Integer>> binnedPartitions = binClusters(clusterList, numPartitions);

        // Update mapping for binned partitions
        nodeToPartition.clear();
        for (int i = 0; i < binnedPartitions.size(); i++) {
            for (Integer node : binnedPartitions.get(i)) {
                nodeToPartition.put(node, i);
            }
        }

        // 6. Distribute lines to partitions
        return createPartitions(binnedPartitions, nodeToPartition);
    }

    /**
     * Bin small micro-clusters into larger balanced partitions.
     */
    private List<Set<Integer>> binClusters(List<Set<Integer>> clusters, int targetPartitions) {
        // Sort clusters by size (largest first)
        clusters.sort((a, b) -> Integer.compare(b.size(), a.size()));

        // Initialize bins
        List<Set<Integer>> bins = new ArrayList<>();
        int[] binSizes = new int[targetPartitions];
        for (int i = 0; i < targetPartitions; i++) {
            bins.add(new HashSet<>());
        }

        // Greedy bin-packing: assign each cluster to smallest bin
        for (Set<Integer> cluster : clusters) {
            // Find bin with smallest current size
            int minIdx = 0;
            for (int i = 1; i < targetPartitions; i++) {
                if (binSizes[i] < binSizes[minIdx]) {
                    minIdx = i;
                }
            }
            bins.get(minIdx).addAll(cluster);
            binSizes[minIdx] += cluster.size();
        }

        return bins;
    }

    /**
     * Create partition objects with internal and bridge lines.
     */
    private List<WaltzDbPartition> createPartitions(List<Set<Integer>> partitionNodes,
                                                     Map<Integer, Integer> nodeToPartition) {
        int numPartitions = partitionNodes.size();

        // Initialize line lists for each partition
        List<List<Line>> internalLines = new ArrayList<>();
        List<List<Line>> bridgeLines = new ArrayList<>();
        for (int i = 0; i < numPartitions; i++) {
            internalLines.add(new ArrayList<>());
            bridgeLines.add(new ArrayList<>());
        }

        // Classify each line
        for (Line line : allLines) {
            Integer p1Part = nodeToPartition.get(line.getP1());
            Integer p2Part = nodeToPartition.get(line.getP2());

            if (p1Part == null || p2Part == null) {
                // Node not in any partition (shouldn't happen)
                continue;
            }

            if (p1Part.equals(p2Part)) {
                // Internal line - add to that partition
                internalLines.get(p1Part).add(line);
            } else {
                // Bridge line - add to both partitions
                bridgeLines.get(p1Part).add(line);
                bridgeLines.get(p2Part).add(line);
            }
        }

        // Create partition objects
        List<WaltzDbPartition> partitions = new ArrayList<>();
        for (int i = 0; i < numPartitions; i++) {
            partitions.add(new WaltzDbPartition(
                i,
                partitionNodes.get(i),
                internalLines.get(i),
                bridgeLines.get(i),
                allLabels  // Labels are shared across all partitions
            ));
        }

        return partitions;
    }

    private Graph<Integer, DefaultEdge> buildGraph() {
        Graph<Integer, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        for (Line line : allLines) {
            graph.addVertex(line.getP1());
            graph.addVertex(line.getP2());
            graph.addEdge(line.getP1(), line.getP2());
        }
        return graph;
    }

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
                        m.group(6));
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
     * Print partition statistics for analysis.
     */
    public static void printPartitionStats(List<WaltzDbPartition> partitions) {
        int totalInternal = 0;
        int totalBridge = 0;

        System.out.println("\n=== Partition Statistics ===");
        for (WaltzDbPartition p : partitions) {
            System.out.println(p);
            totalInternal += p.getLines().size();
            totalBridge += p.getBridgeLines().size();
        }

        System.out.println("---");
        System.out.println("Total internal lines: " + totalInternal);
        System.out.println("Total bridge lines: " + (totalBridge / 2));  // Divided by 2 since counted twice
        double bridgeRatio = (totalBridge / 2.0) / (totalInternal + totalBridge / 2.0) * 100;
        System.out.printf("Bridge line ratio: %.2f%%\n", bridgeRatio);

        if (bridgeRatio < 10) {
            System.out.println("VERDICT: EXCELLENT - Very suitable for partitioning");
        } else if (bridgeRatio < 30) {
            System.out.println("VERDICT: GOOD - Parallelism benefits should outweigh overhead");
        } else {
            System.out.println("VERDICT: POOR - High inter-partition dependencies");
        }
    }
}
```

---

## Step 5: Parallel Benchmark Runner

### 5.1 Create the Partitioned Benchmark

Create `WaltzDbPartitionedBenchmark.java`:

```java
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

            System.out.printf("Iteration %d: %.2f ms, Rules fired: %d\n",
                iter + 1, elapsedMs, totalRulesFired);
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        return totalTime / iterations;
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

            // Insert bridge lines (for completeness - may need ghost fact handling)
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
        int[] threadCounts = {1, 2, 4, 8};

        System.out.println("=== WaltzDB Partitioned Benchmark ===");
        System.out.println("Data file: " + dataFile);
        System.out.println("Iterations: " + iterations);
        System.out.println();

        WaltzDbPartitionedBenchmark benchmark = new WaltzDbPartitionedBenchmark(dataFile);

        for (int threads : threadCounts) {
            System.out.println("\n========================================");
            System.out.println("Running with " + threads + " thread(s)...");
            System.out.println("========================================");

            double avgTime = benchmark.runPartitioned(threads, iterations);

            System.out.printf("\n>>> Average time with %d thread(s): %.2f ms\n",
                threads, avgTime);
        }

        System.out.println("\n=== Experiment Complete ===");
    }
}
```

---

## Step 6: JMH Benchmark Class

### 6.1 Create the JMH Benchmark

Create `WaltzDbPartitionedJmhBenchmark.java` in `src/test/java/org/drools/benchmark/waltzdb/`:

```java
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
```

---

## How to Run

### Prerequisites

```bash
cd /home/maheshdila/mahesh/research/incubator-kie-drools
```

### Step 1: Compile the Project

```bash
mvn clean compile test-compile -f drools-examples/pom.xml -DskipTests
```

### Step 2: Build Classpath

```bash
mvn dependency:build-classpath -f drools-examples/pom.xml \
    -DincludeScope=test -Dmdep.outputFile=/tmp/partition_cp.txt -q
```

### Step 3: Run Standalone Benchmark (Quick Test)

```bash
java -cp "drools-examples/target/classes:$(cat /tmp/partition_cp.txt)" \
    org.drools.benchmark.waltzdb.partition.WaltzDbPartitionedBenchmark
```

### Step 4: Run JMH Benchmark (Full Research Run)

```bash
java -Xms512m -Xmx4g \
    -cp "drools-examples/target/test-classes:drools-examples/target/classes:$(cat /tmp/partition_cp.txt)" \
    org.openjdk.jmh.Main WaltzDbPartitionedJmhBenchmark \
    -f 3 -wi 5 -i 20 -bm ss -tu ms \
    -rff ~/partition_results.csv -rf csv -prof gc
```

### Step 5: Compare Thread Configurations

Run with specific thread count:

```bash
# 1 thread (baseline)
java -cp "..." org.openjdk.jmh.Main WaltzDbPartitionedJmhBenchmark -p numThreads=1

# 2 threads
java -cp "..." org.openjdk.jmh.Main WaltzDbPartitionedJmhBenchmark -p numThreads=2

# 4 threads
java -cp "..." org.openjdk.jmh.Main WaltzDbPartitionedJmhBenchmark -p numThreads=4

# 8 threads
java -cp "..." org.openjdk.jmh.Main WaltzDbPartitionedJmhBenchmark -p numThreads=8
```

---

## Expected Results

### Console Output Example

```
=== Partition Statistics ===
Partition[id=0, junctions=320, internal=310, bridge=45]
Partition[id=1, junctions=290, internal=280, bridge=52]
Partition[id=2, junctions=300, internal=290, bridge=48]
Partition[id=3, junctions=310, internal=300, bridge=50]
---
Total internal lines: 1180
Total bridge lines: 97
Bridge line ratio: 7.60%
VERDICT: EXCELLENT - Very suitable for partitioning
```

### JMH Output Example

```
Benchmark                                     (dataFile)  (numThreads)  Mode  Cnt    Score    Error  Units
WaltzDbPartitionedJmhBenchmark.runPartitionedWaltzDb  waltzdb16.dat           1    ss   60  180.432 ± 12.567  ms/op
WaltzDbPartitionedJmhBenchmark.runPartitionedWaltzDb  waltzdb16.dat           2    ss   60  105.321 ± 8.234   ms/op
WaltzDbPartitionedJmhBenchmark.runPartitionedWaltzDb  waltzdb16.dat           4    ss   60   62.145 ± 5.678   ms/op
WaltzDbPartitionedJmhBenchmark.runPartitionedWaltzDb  waltzdb16.dat           8    ss   60   45.890 ± 4.123   ms/op
```

### Speedup Analysis

| Threads | Time (ms) | Speedup |
|---------|-----------|---------|
| 1       | 180.43    | 1.00x   |
| 2       | 105.32    | 1.71x   |
| 4       | 62.15     | 2.90x   |
| 8       | 45.89     | 3.93x   |

---

## Troubleshooting

### Issue: JGraphT Not Found

```bash
# Ensure dependencies are downloaded
mvn dependency:resolve -f drools-examples/pom.xml
```

### Issue: Partition Imbalance

If partitions are highly imbalanced, you can:
1. Increase the number of initial micro-clusters
2. Adjust the binning algorithm to use weighted balancing

### Issue: High Bridge Line Ratio

A high bridge ratio (>30%) indicates the graph is highly interconnected. Options:
- Use a larger dataset (`waltzdb16.dat` instead of `waltzdb4.dat`)
- Accept the overhead and measure actual parallel speedup

### Issue: OutOfMemoryError

```bash
java -Xms1g -Xmx8g -cp "..." ...
```

---

## References

- [JGraphT Documentation](https://jgrapht.org/guide/UserOverview)
- [JMH Official Guide](https://github.com/openjdk/jmh)
- [Drools Documentation](https://docs.drools.org/)

---

**End of Guide**
