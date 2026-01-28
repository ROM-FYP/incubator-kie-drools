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

import java.util.*;

/**
 * Consolidates facts from multiple parallel phase executions.
 * Handles deduplication and merging of facts across partitions.
 */
public class FactConsolidator {

    private final Set<Edge> consolidatedEdges = new HashSet<>();
    private final Set<Junction> consolidatedJunctions = new HashSet<>();
    private final Set<Line> consolidatedLines = new HashSet<>();
    private final Set<Label> consolidatedLabels = new HashSet<>();
    private final Set<EdgeLabel> consolidatedEdgeLabels = new HashSet<>();

    private long totalRulesFired = 0;
    private int partitionsProcessed = 0;

    /**
     * Merge results from a single partition into the consolidated set.
     */
    public void mergeResult(PhaseResult result) {
        consolidatedEdges.addAll(result.getEdges());
        consolidatedJunctions.addAll(result.getJunctions());
        consolidatedLines.addAll(result.getLines());
        consolidatedLabels.addAll(result.getLabels());
        consolidatedEdgeLabels.addAll(result.getEdgeLabels());

        totalRulesFired += result.getRulesFired();
        partitionsProcessed++;
    }

    /**
     * Merge results from multiple partitions.
     */
    public void mergeResults(List<PhaseResult> results) {
        for (PhaseResult result : results) {
            mergeResult(result);
        }
    }

    // Getters for consolidated facts
    public Set<Edge> getEdges() {
        return consolidatedEdges;
    }

    public Set<Junction> getJunctions() {
        return consolidatedJunctions;
    }

    public Set<Line> getLines() {
        return consolidatedLines;
    }

    public Set<Label> getLabels() {
        return consolidatedLabels;
    }

    public Set<EdgeLabel> getEdgeLabels() {
        return consolidatedEdgeLabels;
    }

    public long getTotalRulesFired() {
        return totalRulesFired;
    }

    public int getPartitionsProcessed() {
        return partitionsProcessed;
    }

    /**
     * Get all consolidated facts as a single list for insertion into next phase.
     */
    public List<Object> getAllFacts() {
        List<Object> allFacts = new ArrayList<>();
        allFacts.addAll(consolidatedEdges);
        allFacts.addAll(consolidatedJunctions);
        allFacts.addAll(consolidatedLines);
        allFacts.addAll(consolidatedLabels);
        allFacts.addAll(consolidatedEdgeLabels);
        return allFacts;
    }

    /**
     * Clear all consolidated facts for reuse.
     */
    public void clear() {
        consolidatedEdges.clear();
        consolidatedJunctions.clear();
        consolidatedLines.clear();
        consolidatedLabels.clear();
        consolidatedEdgeLabels.clear();
        totalRulesFired = 0;
        partitionsProcessed = 0;
    }

    /**
     * Print consolidation statistics.
     */
    public void printStats(String phaseName) {
        System.out.println("\n=== " + phaseName + " Consolidation Stats ===");
        System.out.println("Partitions merged: " + partitionsProcessed);
        System.out.println("Total rules fired: " + totalRulesFired);
        System.out.println("Consolidated Edges: " + consolidatedEdges.size());
        System.out.println("Consolidated Junctions: " + consolidatedJunctions.size());
        System.out.println("Consolidated EdgeLabels: " + consolidatedEdgeLabels.size());
    }
}
