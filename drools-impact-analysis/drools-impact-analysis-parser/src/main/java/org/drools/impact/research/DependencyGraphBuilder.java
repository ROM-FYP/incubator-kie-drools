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

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

/**
 * Builds a Directed Acyclic Graph (DAG) of rule dependencies using JGraphT.
 * 
 * <p>
 * Vertices in the graph are {@link RuleMeta} objects. An edge from Rule A to
 * Rule B is created if Rule A's outputs intersect with Rule B's inputs, meaning
 * Rule A may trigger Rule B.
 * </p>
 * 
 * <p>
 * Self-loops (a rule triggering itself) are ignored unless explicitly needed
 * for detecting infinite recursion.
 * </p>
 */
public class DependencyGraphBuilder {

    private final Graph<RuleMeta, DefaultEdge> graph;

    /**
     * Constructs a dependency graph from the given list of rules.
     * 
     * <p>
     * Edge Logic: An edge from ruleA to ruleB is added if:
     * {@code !Collections.disjoint(ruleA.getOutputs(), ruleB.getInputs())}
     * </p>
     *
     * @param rules the list of RuleMeta objects to build the graph from
     */
    public DependencyGraphBuilder(List<RuleMeta> rules) {
        this.graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        buildGraph(rules);
    }

    /**
     * Builds the graph by adding vertices and edges based on input/output
     * intersections.
     */
    private void buildGraph(List<RuleMeta> rules) {
        if (rules == null || rules.isEmpty()) {
            return;
        }

        // Add all rules as vertices
        for (RuleMeta rule : rules) {
            graph.addVertex(rule);
        }

        // Add edges based on output/input intersection
        for (RuleMeta ruleA : rules) {
            for (RuleMeta ruleB : rules) {
                // Skip self-loops
                if (ruleA == ruleB) {
                    continue;
                }

                // Create edge if ruleA's outputs intersect with ruleB's inputs
                if (!Collections.disjoint(ruleA.getOutputs(), ruleB.getInputs())) {
                    graph.addEdge(ruleA, ruleB);
                }
            }
        }
    }

    /**
     * Returns the built dependency graph.
     *
     * @return the directed graph of rule dependencies
     */
    public Graph<RuleMeta, DefaultEdge> getGraph() {
        return graph;
    }

    /**
     * Returns the set of rules (vertices) in the graph.
     *
     * @return set of RuleMeta vertices
     */
    public Set<RuleMeta> getRules() {
        return graph.vertexSet();
    }

    /**
     * Returns the rules that the given rule depends on (predecessors).
     *
     * @param rule the rule to find predecessors for
     * @return set of rules that may trigger the given rule
     */
    public Set<RuleMeta> getPredecessors(RuleMeta rule) {
        return graph.incomingEdgesOf(rule).stream()
                .map(graph::getEdgeSource)
                .collect(Collectors.toSet());
    }

    /**
     * Returns the rules that depend on the given rule (successors).
     *
     * @param rule the rule to find successors for
     * @return set of rules that may be triggered by the given rule
     */
    public Set<RuleMeta> getSuccessors(RuleMeta rule) {
        return graph.outgoingEdgesOf(rule).stream()
                .map(graph::getEdgeTarget)
                .collect(Collectors.toSet());
    }

    /**
     * Checks if there is a direct edge from ruleA to ruleB.
     *
     * @param ruleA the source rule
     * @param ruleB the target rule
     * @return true if ruleA directly triggers ruleB
     */
    public boolean hasEdge(RuleMeta ruleA, RuleMeta ruleB) {
        return graph.containsEdge(ruleA, ruleB);
    }

    /**
     * Checks if the given rule is isolated (has no incoming or outgoing edges).
     *
     * @param rule the rule to check
     * @return true if the rule has no dependencies and no dependents
     */
    public boolean isIsolated(RuleMeta rule) {
        return graph.inDegreeOf(rule) == 0 && graph.outDegreeOf(rule) == 0;
    }

    /**
     * Returns a string representation of the graph as an adjacency list.
     *
     * @return adjacency list representation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Dependency Graph (Adjacency List):\n");
        sb.append("=================================\n");

        for (RuleMeta rule : graph.vertexSet()) {
            sb.append(rule.getRuleName()).append(" -> ");
            Set<RuleMeta> successors = getSuccessors(rule);
            if (successors.isEmpty()) {
                sb.append("(no outgoing edges)");
            } else {
                sb.append(successors.stream()
                        .map(RuleMeta::getRuleName)
                        .collect(Collectors.joining(", ")));
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
