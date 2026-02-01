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
package org.drools.impact.research;

import java.util.*;
import java.util.stream.Collectors;

import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector;
import org.jgrapht.alg.interfaces.StrongConnectivityAlgorithm;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

/**
 * Stratifies rules into execution phases using topological sort and
 * Strongly Connected Component (SCC) analysis.
 * 
 * <p>
 * <b>Algorithm:</b>
 * </p>
 * <ol>
 * <li>Build dependency graph from rules</li>
 * <li>Detect SCCs using Kosaraju's algorithm - rules in an SCC form a cycle
 * and must execute in the same "sequential" phase</li>
 * <li>Build condensation graph treating each SCC as a "super node"</li>
 * <li>Perform topological sort on the condensation graph</li>
 * <li>Assign layers: Layer 0 = in-degree 0 nodes (Phase 1), etc.</li>
 * </ol>
 * 
 * <p>
 * <b>Research Insight:</b> All rules in an SCC must be in the same phase
 * because they can trigger each other in cycles.
 * </p>
 */
public class Stratifier {

    private final Graph<RuleMeta, DefaultEdge> ruleGraph;
    private final List<Set<RuleMeta>> stronglyConnectedComponents;
    private final boolean hasCycles;

    /**
     * Creates a Stratifier from a list of rules.
     *
     * @param rules the rules to stratify
     */
    public Stratifier(List<RuleMeta> rules) {
        DependencyGraphBuilder graphBuilder = new DependencyGraphBuilder(rules);
        this.ruleGraph = graphBuilder.getGraph();

        // Detect SCCs using Kosaraju's algorithm
        StrongConnectivityAlgorithm<RuleMeta, DefaultEdge> inspector = new KosarajuStrongConnectivityInspector<>(
                ruleGraph);
        this.stronglyConnectedComponents = inspector.stronglyConnectedSets();

        // A cycle exists if any SCC has more than one node
        this.hasCycles = stronglyConnectedComponents.stream()
                .anyMatch(scc -> scc.size() > 1);
    }

    /**
     * Creates a Stratifier from an existing dependency graph.
     *
     * @param graphBuilder the pre-built dependency graph
     */
    public Stratifier(DependencyGraphBuilder graphBuilder) {
        this.ruleGraph = graphBuilder.getGraph();

        StrongConnectivityAlgorithm<RuleMeta, DefaultEdge> inspector = new KosarajuStrongConnectivityInspector<>(
                ruleGraph);
        this.stronglyConnectedComponents = inspector.stronglyConnectedSets();

        this.hasCycles = stronglyConnectedComponents.stream()
                .anyMatch(scc -> scc.size() > 1);
    }

    /**
     * Stratifies rules into phases.
     * 
     * @return List of phases, where index 0 is Phase 1 (rules with no
     *         dependencies),
     *         index 1 is Phase 2 (rules depending only on Phase 1), etc.
     *         Each phase contains a set of rule names.
     */
    public List<Set<String>> stratify() {
        if (ruleGraph.vertexSet().isEmpty()) {
            return Collections.emptyList();
        }

        // Build condensation graph: each SCC becomes a super-node
        Graph<Set<RuleMeta>, DefaultEdge> condensationGraph = buildCondensationGraph();

        // Map each rule to its SCC
        Map<RuleMeta, Set<RuleMeta>> ruleToScc = new HashMap<>();
        for (Set<RuleMeta> scc : stronglyConnectedComponents) {
            for (RuleMeta rule : scc) {
                ruleToScc.put(rule, scc);
            }
        }

        // Perform layered topological sort on condensation graph
        List<Set<Set<RuleMeta>>> layers = computeLayers(condensationGraph);

        // Convert to List<Set<String>> - rule names per phase
        List<Set<String>> phases = new ArrayList<>();
        for (Set<Set<RuleMeta>> layer : layers) {
            Set<String> phaseRules = new LinkedHashSet<>();
            for (Set<RuleMeta> scc : layer) {
                for (RuleMeta rule : scc) {
                    phaseRules.add(rule.getRuleName());
                }
            }
            phases.add(phaseRules);
        }

        return phases;
    }

    /**
     * Builds the condensation graph where each SCC is a single node.
     * An edge exists from SCC_A to SCC_B if any rule in SCC_A has an edge
     * to any rule in SCC_B (and SCC_A != SCC_B).
     */
    private Graph<Set<RuleMeta>, DefaultEdge> buildCondensationGraph() {
        Graph<Set<RuleMeta>, DefaultEdge> condensation = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Add all SCCs as vertices
        for (Set<RuleMeta> scc : stronglyConnectedComponents) {
            condensation.addVertex(scc);
        }

        // Map each rule to its SCC for quick lookup
        Map<RuleMeta, Set<RuleMeta>> ruleToScc = new HashMap<>();
        for (Set<RuleMeta> scc : stronglyConnectedComponents) {
            for (RuleMeta rule : scc) {
                ruleToScc.put(rule, scc);
            }
        }

        // Add edges between SCCs
        for (DefaultEdge edge : ruleGraph.edgeSet()) {
            RuleMeta source = ruleGraph.getEdgeSource(edge);
            RuleMeta target = ruleGraph.getEdgeTarget(edge);

            Set<RuleMeta> sourceScc = ruleToScc.get(source);
            Set<RuleMeta> targetScc = ruleToScc.get(target);

            // Only add edge if different SCCs (avoid self-loops in condensation)
            if (sourceScc != targetScc && !condensation.containsEdge(sourceScc, targetScc)) {
                condensation.addEdge(sourceScc, targetScc);
            }
        }

        return condensation;
    }

    /**
     * Computes layers using Kahn's algorithm for topological sort.
     * Layer 0 = nodes with in-degree 0
     * Layer 1 = nodes whose dependencies are all in Layer 0
     * etc.
     */
    private <V> List<Set<V>> computeLayers(Graph<V, DefaultEdge> graph) {
        List<Set<V>> layers = new ArrayList<>();

        if (graph.vertexSet().isEmpty()) {
            return layers;
        }

        // Track in-degrees
        Map<V, Integer> inDegree = new HashMap<>();
        for (V vertex : graph.vertexSet()) {
            inDegree.put(vertex, graph.inDegreeOf(vertex));
        }

        // Track which nodes are assigned to layers
        Set<V> assigned = new HashSet<>();

        while (assigned.size() < graph.vertexSet().size()) {
            // Find all nodes with in-degree 0 that haven't been assigned
            Set<V> currentLayer = new LinkedHashSet<>();
            for (V vertex : graph.vertexSet()) {
                if (!assigned.contains(vertex) && inDegree.get(vertex) == 0) {
                    currentLayer.add(vertex);
                }
            }

            if (currentLayer.isEmpty()) {
                // This shouldn't happen if the condensation graph is acyclic
                // But handle it gracefully - add remaining nodes as final layer
                Set<V> remaining = new LinkedHashSet<>();
                for (V vertex : graph.vertexSet()) {
                    if (!assigned.contains(vertex)) {
                        remaining.add(vertex);
                    }
                }
                if (!remaining.isEmpty()) {
                    layers.add(remaining);
                }
                break;
            }

            layers.add(currentLayer);
            assigned.addAll(currentLayer);

            // Reduce in-degree of successors
            for (V vertex : currentLayer) {
                for (DefaultEdge edge : graph.outgoingEdgesOf(vertex)) {
                    V successor = graph.getEdgeTarget(edge);
                    inDegree.put(successor, inDegree.get(successor) - 1);
                }
            }
        }

        return layers;
    }

    /**
     * Returns whether the rule dependency graph contains cycles.
     *
     * @return true if cycles exist
     */
    public boolean hasCycles() {
        return hasCycles;
    }

    /**
     * Returns the strongly connected components.
     * Each SCC contains rules that can trigger each other in a cycle.
     *
     * @return list of SCCs
     */
    public List<Set<RuleMeta>> getStronglyConnectedComponents() {
        return Collections.unmodifiableList(stronglyConnectedComponents);
    }

    /**
     * Returns SCCs that have more than one rule (i.e., actual cycles).
     *
     * @return list of cyclic SCCs
     */
    public List<Set<RuleMeta>> getCyclicComponents() {
        return stronglyConnectedComponents.stream()
                .filter(scc -> scc.size() > 1)
                .collect(Collectors.toList());
    }

    /**
     * Returns a formatted string showing the stratification result.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Rule Stratification\n");
        sb.append("===================\n");
        sb.append("Has Cycles: ").append(hasCycles).append("\n\n");

        List<Set<String>> phases = stratify();
        for (int i = 0; i < phases.size(); i++) {
            sb.append("Phase ").append(i + 1).append(": ");
            sb.append(phases.get(i).stream().collect(Collectors.joining(", ")));
            sb.append("\n");
        }

        if (hasCycles) {
            sb.append("\nCyclic Components (rules that must run sequentially together):\n");
            for (Set<RuleMeta> scc : getCyclicComponents()) {
                sb.append("  - ");
                sb.append(scc.stream()
                        .map(RuleMeta::getRuleName)
                        .collect(Collectors.joining(", ")));
                sb.append("\n");
            }
        }

        return sb.toString();
    }
}
