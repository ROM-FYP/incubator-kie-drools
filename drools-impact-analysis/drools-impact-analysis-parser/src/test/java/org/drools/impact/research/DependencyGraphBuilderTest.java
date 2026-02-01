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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DependencyGraphBuilder}.
 */
class DependencyGraphBuilderTest {

    // =========================================================================
    // VERIFICATION TEST - Main specification test case
    // =========================================================================

    @Test
    @DisplayName("VERIFICATION: Rule A produces Edge, Rule B reads Edge, Rule C reads Line - Edge A->B exists, C is isolated")
    void testVerificationScenario() {
        // Given:
        // Rule A: outputs {Edge}, inputs {}
        // Rule B: outputs {}, inputs {Edge}
        // Rule C: outputs {}, inputs {Line}
        RuleMeta ruleA = new RuleMeta("RuleA", Collections.emptySet(), Set.of("Edge"));
        RuleMeta ruleB = new RuleMeta("RuleB", Set.of("Edge"), Collections.emptySet());
        RuleMeta ruleC = new RuleMeta("RuleC", Set.of("Line"), Collections.emptySet());

        List<RuleMeta> rules = Arrays.asList(ruleA, ruleB, ruleC);

        // When: Build the dependency graph
        DependencyGraphBuilder builder = new DependencyGraphBuilder(rules);

        // Then:
        // 1. Edge A -> B should exist (A outputs Edge, B inputs Edge)
        assertTrue(builder.hasEdge(ruleA, ruleB),
                "Edge A->B should exist because A outputs 'Edge' which B inputs");

        // 2. No edge from A to C (A outputs Edge, C inputs Line - no intersection)
        assertFalse(builder.hasEdge(ruleA, ruleC),
                "No edge A->C because A outputs 'Edge' but C inputs 'Line'");

        // 3. No edge from B to C
        assertFalse(builder.hasEdge(ruleB, ruleC),
                "No edge B->C because B has no outputs");

        // 4. Rule C should be isolated (no incoming or outgoing edges)
        assertTrue(builder.isIsolated(ruleC),
                "Rule C should be isolated (connected to nothing)");

        // 5. Rule A should not be isolated (has outgoing edge to B)
        assertFalse(builder.isIsolated(ruleA),
                "Rule A should not be isolated (has edge to B)");

        // 6. Rule B should not be isolated (has incoming edge from A)
        assertFalse(builder.isIsolated(ruleB),
                "Rule B should not be isolated (has edge from A)");

        // Print the graph for visual verification
        System.out.println(builder);
    }

    // =========================================================================
    // Additional Edge Cases
    // =========================================================================

    @Test
    @DisplayName("Should handle empty rule list")
    void testEmptyRuleList() {
        DependencyGraphBuilder builder = new DependencyGraphBuilder(Collections.emptyList());

        assertEquals(0, builder.getRules().size(), "Graph should have no vertices");
    }

    @Test
    @DisplayName("Should handle null rule list")
    void testNullRuleList() {
        DependencyGraphBuilder builder = new DependencyGraphBuilder(null);

        assertEquals(0, builder.getRules().size(), "Graph should have no vertices");
    }

    @Test
    @DisplayName("Should handle single rule with no edges")
    void testSingleRule() {
        RuleMeta rule = new RuleMeta("SingleRule", Set.of("Input"), Set.of("Output"));

        DependencyGraphBuilder builder = new DependencyGraphBuilder(Collections.singletonList(rule));

        assertEquals(1, builder.getRules().size(), "Graph should have one vertex");
        assertTrue(builder.isIsolated(rule), "Single rule should be isolated");
    }

    @Test
    @DisplayName("Should prevent self-loops (rule triggering itself)")
    void testSelfLoopPrevention() {
        // Rule with matching input and output - could trigger itself
        RuleMeta rule = new RuleMeta("SelfTrigger", Set.of("Type"), Set.of("Type"));

        DependencyGraphBuilder builder = new DependencyGraphBuilder(Collections.singletonList(rule));

        // Should NOT have a self-loop
        assertFalse(builder.hasEdge(rule, rule),
                "Self-loops should be prevented");
        assertTrue(builder.isIsolated(rule),
                "Rule should be isolated when self-loop is prevented");
    }

    @Test
    @DisplayName("Should handle transitive chain A -> B -> C")
    void testTransitiveChain() {
        // A outputs X, B inputs X and outputs Y, C inputs Y
        RuleMeta ruleA = new RuleMeta("RuleA", Collections.emptySet(), Set.of("X"));
        RuleMeta ruleB = new RuleMeta("RuleB", Set.of("X"), Set.of("Y"));
        RuleMeta ruleC = new RuleMeta("RuleC", Set.of("Y"), Collections.emptySet());

        List<RuleMeta> rules = Arrays.asList(ruleA, ruleB, ruleC);
        DependencyGraphBuilder builder = new DependencyGraphBuilder(rules);

        // A -> B
        assertTrue(builder.hasEdge(ruleA, ruleB), "Edge A->B should exist");
        // B -> C
        assertTrue(builder.hasEdge(ruleB, ruleC), "Edge B->C should exist");
        // A should NOT directly connect to C (no transitive edge)
        assertFalse(builder.hasEdge(ruleA, ruleC),
                "No direct edge A->C (graph is not transitively closed)");
    }

    @Test
    @DisplayName("Should handle diamond dependency pattern")
    void testDiamondDependency() {
        // Diamond: A -> B, A -> C, B -> D, C -> D
        RuleMeta ruleA = new RuleMeta("RuleA", Collections.emptySet(), Set.of("X"));
        RuleMeta ruleB = new RuleMeta("RuleB", Set.of("X"), Set.of("Y"));
        RuleMeta ruleC = new RuleMeta("RuleC", Set.of("X"), Set.of("Z"));
        RuleMeta ruleD = new RuleMeta("RuleD", Set.of("Y", "Z"), Collections.emptySet());

        List<RuleMeta> rules = Arrays.asList(ruleA, ruleB, ruleC, ruleD);
        DependencyGraphBuilder builder = new DependencyGraphBuilder(rules);

        // A -> B, A -> C
        assertTrue(builder.hasEdge(ruleA, ruleB));
        assertTrue(builder.hasEdge(ruleA, ruleC));
        // B -> D, C -> D
        assertTrue(builder.hasEdge(ruleB, ruleD));
        assertTrue(builder.hasEdge(ruleC, ruleD));

        // Verify successors of A
        Set<RuleMeta> aSuccessors = builder.getSuccessors(ruleA);
        assertEquals(2, aSuccessors.size());
        assertTrue(aSuccessors.contains(ruleB));
        assertTrue(aSuccessors.contains(ruleC));

        // Verify predecessors of D
        Set<RuleMeta> dPredecessors = builder.getPredecessors(ruleD);
        assertEquals(2, dPredecessors.size());
        assertTrue(dPredecessors.contains(ruleB));
        assertTrue(dPredecessors.contains(ruleC));
    }

    @Test
    @DisplayName("Should handle multiple outputs matching multiple inputs")
    void testMultipleIntersections() {
        // Rule A outputs {X, Y}, Rule B inputs {Y, Z}
        // Intersection is {Y}, so edge should exist
        RuleMeta ruleA = new RuleMeta("RuleA", Collections.emptySet(), Set.of("X", "Y"));
        RuleMeta ruleB = new RuleMeta("RuleB", Set.of("Y", "Z"), Collections.emptySet());

        List<RuleMeta> rules = Arrays.asList(ruleA, ruleB);
        DependencyGraphBuilder builder = new DependencyGraphBuilder(rules);

        assertTrue(builder.hasEdge(ruleA, ruleB),
                "Edge should exist due to intersection {Y}");
    }

    @Test
    @DisplayName("Should handle disjoint rules (no edges)")
    void testDisjointRules() {
        RuleMeta ruleA = new RuleMeta("RuleA", Set.of("A"), Set.of("B"));
        RuleMeta ruleB = new RuleMeta("RuleB", Set.of("C"), Set.of("D"));

        List<RuleMeta> rules = Arrays.asList(ruleA, ruleB);
        DependencyGraphBuilder builder = new DependencyGraphBuilder(rules);

        assertFalse(builder.hasEdge(ruleA, ruleB), "No edge A->B (disjoint)");
        assertFalse(builder.hasEdge(ruleB, ruleA), "No edge B->A (disjoint)");
        assertTrue(builder.isIsolated(ruleA), "Rule A should be isolated");
        assertTrue(builder.isIsolated(ruleB), "Rule B should be isolated");
    }

    @Test
    @DisplayName("Should correctly print adjacency list")
    void testToString() {
        RuleMeta ruleA = new RuleMeta("RuleA", Collections.emptySet(), Set.of("X"));
        RuleMeta ruleB = new RuleMeta("RuleB", Set.of("X"), Collections.emptySet());

        List<RuleMeta> rules = Arrays.asList(ruleA, ruleB);
        DependencyGraphBuilder builder = new DependencyGraphBuilder(rules);

        String output = builder.toString();

        assertNotNull(output);
        assertTrue(output.contains("RuleA"), "Output should contain RuleA");
        assertTrue(output.contains("RuleB"), "Output should contain RuleB");
        assertTrue(output.contains("->"), "Output should contain arrow notation");
    }
}
