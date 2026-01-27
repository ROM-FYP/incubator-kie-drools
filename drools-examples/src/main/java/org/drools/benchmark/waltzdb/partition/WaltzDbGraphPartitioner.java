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
 * Uses Label Propagation Clustering from JGraphT to identify communities
 * of junctions, then bins them into target number of partitions.
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
     * @param numPartitions Target number of partitions
     * @return List of partitions ready for parallel processing
     */
    public List<WaltzDbPartition> partition(int numPartitions) {
        // 1. Load all data
        allLines = loadLines();
        allLabels = loadLabels();

        System.out.println("Loaded " + allLines.size() + " lines and " + allLabels.size() + " labels");

        // 2. Build graph from lines
        Graph<Integer, DefaultEdge> graph = buildGraph();
        System.out.println("Graph built: " + graph.vertexSet().size() + " vertices, "
                + graph.edgeSet().size() + " edges");

        // 3. Run Label Propagation Clustering
        LabelPropagationClustering<Integer, DefaultEdge> clustering = new LabelPropagationClustering<>(graph);
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

        System.out.println("After binning: " + binnedPartitions.size() + " partitions");

        // 6. Distribute lines to partitions
        return createPartitions(binnedPartitions, nodeToPartition);
    }

    /**
     * Bin small micro-clusters into larger balanced partitions using greedy
     * bin-packing.
     */
    private List<Set<Integer>> binClusters(List<Set<Integer>> clusters, int targetPartitions) {
        // Sort clusters by size (largest first)
        List<Set<Integer>> sortedClusters = new ArrayList<>(clusters);
        sortedClusters.sort((a, b) -> Integer.compare(b.size(), a.size()));

        // Initialize bins
        List<Set<Integer>> bins = new ArrayList<>();
        int[] binSizes = new int[targetPartitions];
        for (int i = 0; i < targetPartitions; i++) {
            bins.add(new HashSet<>());
        }

        // Greedy bin-packing: assign each cluster to smallest bin
        for (Set<Integer> cluster : sortedClusters) {
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

        // Print bin sizes for debugging
        for (int i = 0; i < targetPartitions; i++) {
            System.out.println("  Bin " + i + ": " + binSizes[i] + " junctions");
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
                System.err.println("Warning: Node not found in partition mapping: "
                        + line.getP1() + " or " + line.getP2());
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
                    allLabels // Labels are shared across all partitions
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
                            m.group(7)); // Note: group(7) is the actual n3 value
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
     * Get all lines loaded from the data file.
     */
    public List<Line> getAllLines() {
        return allLines;
    }

    /**
     * Get all labels loaded from the data file.
     */
    public List<Label> getAllLabels() {
        return allLabels;
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
        // Bridge lines are counted twice (once per partition endpoint)
        int uniqueBridgeLines = totalBridge / 2;
        System.out.println("Total bridge lines: " + uniqueBridgeLines);
        double bridgeRatio = (double) uniqueBridgeLines / (totalInternal + uniqueBridgeLines) * 100;
        System.out.printf("Bridge line ratio: %.2f%%\n", bridgeRatio);

        if (bridgeRatio < 10) {
            System.out.println("VERDICT: EXCELLENT - Very suitable for partitioning");
        } else if (bridgeRatio < 30) {
            System.out.println("VERDICT: GOOD - Parallelism benefits should outweigh overhead");
        } else {
            System.out.println("VERDICT: POOR - High inter-partition dependencies");
        }
        System.out.println();
    }

    /**
     * Main method for standalone testing of partitioning.
     */
    public static void main(String[] args) {
        String dataFile = "waltzdb16.dat";
        int[] threadCounts = { 1, 2, 4, 8 };

        System.out.println("=== WaltzDB Graph Partitioner Test ===\n");

        for (int threads : threadCounts) {
            System.out.println("--- Testing with " + threads + " partition(s) ---");
            WaltzDbGraphPartitioner partitioner = new WaltzDbGraphPartitioner(dataFile);
            List<WaltzDbPartition> partitions = partitioner.partition(threads);
            printPartitionStats(partitions);
        }
    }
}
