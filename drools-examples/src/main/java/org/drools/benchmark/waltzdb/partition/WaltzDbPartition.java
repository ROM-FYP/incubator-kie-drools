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

import java.util.List;
import java.util.Set;

/**
 * Represents a partition of the WaltzDB dataset.
 * Each partition contains a subset of Lines and Labels
 * that should be processed together in a separate KieSession.
 */
public class WaltzDbPartition {
    private final int partitionId;
    private final Set<Integer> junctionIds; // Junction points in this partition
    private final List<Line> lines; // Lines internal to this partition
    private final List<Line> bridgeLines; // Lines crossing to other partitions
    private final List<Label> labels; // Labels (same for all partitions)

    public WaltzDbPartition(int partitionId, Set<Integer> junctionIds,
            List<Line> lines, List<Line> bridgeLines,
            List<Label> labels) {
        this.partitionId = partitionId;
        this.junctionIds = junctionIds;
        this.lines = lines;
        this.bridgeLines = bridgeLines;
        this.labels = labels;
    }

    public int getPartitionId() {
        return partitionId;
    }

    public Set<Integer> getJunctionIds() {
        return junctionIds;
    }

    public List<Line> getLines() {
        return lines;
    }

    public List<Line> getBridgeLines() {
        return bridgeLines;
    }

    public List<Label> getLabels() {
        return labels;
    }

    public int getTotalLineCount() {
        return lines.size() + bridgeLines.size();
    }

    @Override
    public String toString() {
        return String.format("Partition[id=%d, junctions=%d, internal=%d, bridge=%d]",
                partitionId, junctionIds.size(), lines.size(), bridgeLines.size());
    }
}
