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
 * Holds the results from executing a single phase partition.
 * Used to collect facts for consolidation across partitions.
 */
public class PhaseResult {

    private final int partitionId;
    private long rulesFired;

    private final Set<Edge> edges = new HashSet<>();
    private final Set<Junction> junctions = new HashSet<>();
    private final Set<Line> lines = new HashSet<>();
    private final Set<Label> labels = new HashSet<>();
    private final Set<EdgeLabel> edgeLabels = new HashSet<>();

    public PhaseResult(int partitionId) {
        this.partitionId = partitionId;
    }

    public int getPartitionId() {
        return partitionId;
    }

    public long getRulesFired() {
        return rulesFired;
    }

    public void setRulesFired(long rulesFired) {
        this.rulesFired = rulesFired;
    }

    // Edge methods
    public void addEdge(Edge edge) {
        edges.add(edge);
    }

    public Set<Edge> getEdges() {
        return edges;
    }

    // Junction methods
    public void addJunction(Junction junction) {
        junctions.add(junction);
    }

    public Set<Junction> getJunctions() {
        return junctions;
    }

    // Line methods
    public void addLine(Line line) {
        lines.add(line);
    }

    public Set<Line> getLines() {
        return lines;
    }

    // Label methods
    public void addLabel(Label label) {
        labels.add(label);
    }

    public Set<Label> getLabels() {
        return labels;
    }

    // EdgeLabel methods
    public void addEdgeLabel(EdgeLabel edgeLabel) {
        edgeLabels.add(edgeLabel);
    }

    public Set<EdgeLabel> getEdgeLabels() {
        return edgeLabels;
    }

    @Override
    public String toString() {
        return String.format("PhaseResult[partition=%d, rules=%d, edges=%d, junctions=%d]",
                partitionId, rulesFired, edges.size(), junctions.size());
    }
}
