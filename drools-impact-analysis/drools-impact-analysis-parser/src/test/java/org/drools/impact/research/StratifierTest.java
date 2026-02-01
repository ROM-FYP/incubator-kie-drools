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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Stratifier}.
 */
class StratifierTest {

    // =========================================================================
    // VERIFICATION TEST - WaltzDB-like scenario
    // =========================================================================

    @Test
    @DisplayName("VERIFICATION: WaltzDB-like rules - reverse_edges should NOT be in same phase as labeling")
    void testWaltzDbLikeScenario() {
        // Simulate WaltzDB rule dependencies:
        // Phase 1: reverse_edges (reads Line, outputs Edge)
        // Phase 2: make_3_junction, make_L (read Edge, output Junction+Edge)
        // Phase 3: labeling rules (read Junction/Edge, output Edge - cyclic among
        // themselves)

        RuleMeta reverseEdges = new RuleMeta("reverse_edges",
                Set.of("Line", "Stage"), Set.of("Edge"));

        RuleMeta reversingDone = new RuleMeta("reversing_done",
                Set.of("Stage"), Set.of("Stage"));

        RuleMeta make3Junction = new RuleMeta("make_3_junction",
                Set.of("Stage", "Edge"), Set.of("Junction", "Edge"));

        RuleMeta makeL = new RuleMeta("make_L",
                Set.of("Stage", "Edge"), Set.of("Junction", "Edge"));

        RuleMeta detectingDone = new RuleMeta("detecting_done",
                Set.of("Stage"), Set.of("Stage"));

        // Labeling rules - they read Edge/Junction and modify Edge
        // They form a cycle because they all read and write Edge
        RuleMeta labelL = new RuleMeta("label_L",
                Set.of("Stage", "Junction", "Edge"), Set.of("Edge"));

        RuleMeta matchEdge = new RuleMeta("match_edge",
                Set.of("Stage", "Edge"), Set.of("Edge"));

        RuleMeta labelFork1 = new RuleMeta("label_fork_1",
                Set.of("Stage", "Junction", "Edge"), Set.of("Edge"));

        List<RuleMeta> rules = Arrays.asList(
                reverseEdges, reversingDone, make3Junction, makeL,
                detectingDone, labelL, matchEdge, labelFork1);

        Stratifier stratifier = new Stratifier(rules);
        List<Set<String>> phases = stratifier.stratify();

        // Print for visual inspection
        System.out.println(stratifier);

        // VERIFICATION: reverse_edges should be in an earlier phase than labeling rules
        int reverseEdgesPhase = findPhase(phases, "reverse_edges");
        int labelLPhase = findPhase(phases, "label_L");
        int matchEdgePhase = findPhase(phases, "match_edge");

        assertTrue(reverseEdgesPhase < labelLPhase,
                "reverse_edges (Phase " + reverseEdgesPhase + ") should be before label_L (Phase " + labelLPhase + ")");
        assertTrue(reverseEdgesPhase < matchEdgePhase,
                "reverse_edges should be before match_edge");

        // Junction rules should be after reverse_edges
        int make3Phase = findPhase(phases, "make_3_junction");
        int makeLPhase = findPhase(phases, "make_L");

        assertTrue(reverseEdgesPhase < make3Phase || reverseEdgesPhase == make3Phase,
                "reverse_edges should be in same or earlier phase than make_3_junction");
        assertTrue(reverseEdgesPhase < makeLPhase || reverseEdgesPhase == makeLPhase,
                "reverse_edges should be in same or earlier phase than make_L");
    }

    private int findPhase(List<Set<String>> phases, String ruleName) {
        for (int i = 0; i < phases.size(); i++) {
            if (phases.get(i).contains(ruleName)) {
                return i;
            }
        }
        return -1;
    }

    // =========================================================================
    // Acyclic Graph Tests
    // =========================================================================

    @Test
    @DisplayName("Linear chain A -> B -> C should produce 3 phases")
    void testLinearChain() {
        // A outputs X, B reads X outputs Y, C reads Y
        RuleMeta ruleA = new RuleMeta("RuleA", Collections.emptySet(), Set.of("X"));
        RuleMeta ruleB = new RuleMeta("RuleB", Set.of("X"), Set.of("Y"));
        RuleMeta ruleC = new RuleMeta("RuleC", Set.of("Y"), Collections.emptySet());

        Stratifier stratifier = new Stratifier(Arrays.asList(ruleA, ruleB, ruleC));
        List<Set<String>> phases = stratifier.stratify();

        System.out.println(stratifier);

        assertEquals(3, phases.size(), "Should have 3 phases for linear chain");
        assertTrue(phases.get(0).contains("RuleA"), "Phase 0 should contain RuleA");
        assertTrue(phases.get(1).contains("RuleB"), "Phase 1 should contain RuleB");
        assertTrue(phases.get(2).contains("RuleC"), "Phase 2 should contain RuleC");

        assertFalse(stratifier.hasCycles(), "Linear chain should have no cycles");
    }

    @Test
    @DisplayName("Diamond pattern should have correct layering")
    void testDiamondPattern() {
        // A -> B, A -> C, B -> D, C -> D
        RuleMeta ruleA = new RuleMeta("RuleA", Collections.emptySet(), Set.of("X"));
        RuleMeta ruleB = new RuleMeta("RuleB", Set.of("X"), Set.of("Y"));
        RuleMeta ruleC = new RuleMeta("RuleC", Set.of("X"), Set.of("Z"));
        RuleMeta ruleD = new RuleMeta("RuleD", Set.of("Y", "Z"), Collections.emptySet());

        Stratifier stratifier = new Stratifier(Arrays.asList(ruleA, ruleB, ruleC, ruleD));
        List<Set<String>> phases = stratifier.stratify();

        System.out.println(stratifier);

        assertEquals(3, phases.size(), "Diamond should have 3 phases");
        assertTrue(phases.get(0).contains("RuleA"), "Phase 0: RuleA");
        assertTrue(phases.get(1).contains("RuleB") && phases.get(1).contains("RuleC"),
                "Phase 1: RuleB and RuleC");
        assertTrue(phases.get(2).contains("RuleD"), "Phase 2: RuleD");

        assertFalse(stratifier.hasCycles());
    }

    @Test
    @DisplayName("Parallel rules with no dependencies should be in Phase 0")
    void testParallelRules() {
        RuleMeta ruleA = new RuleMeta("RuleA", Set.of("A"), Set.of("B"));
        RuleMeta ruleB = new RuleMeta("RuleB", Set.of("C"), Set.of("D"));
        RuleMeta ruleC = new RuleMeta("RuleC", Set.of("E"), Set.of("F"));

        Stratifier stratifier = new Stratifier(Arrays.asList(ruleA, ruleB, ruleC));
        List<Set<String>> phases = stratifier.stratify();

        System.out.println(stratifier);

        assertEquals(1, phases.size(), "All independent rules should be in one phase");
        assertEquals(3, phases.get(0).size(), "Phase 0 should have all 3 rules");
    }

    // =========================================================================
    // Cycle Detection Tests
    // =========================================================================

    @Test
    @DisplayName("Two rules in a cycle should be in the same phase")
    void testSimpleCycle() {
        // A outputs X reads Y, B outputs Y reads X -> cycle
        RuleMeta ruleA = new RuleMeta("RuleA", Set.of("Y"), Set.of("X"));
        RuleMeta ruleB = new RuleMeta("RuleB", Set.of("X"), Set.of("Y"));

        Stratifier stratifier = new Stratifier(Arrays.asList(ruleA, ruleB));
        List<Set<String>> phases = stratifier.stratify();

        System.out.println(stratifier);

        assertTrue(stratifier.hasCycles(), "Should detect cycle");
        assertEquals(1, stratifier.getCyclicComponents().size(), "Should have 1 cyclic component");

        // Both rules should be in the same phase
        assertEquals(1, phases.size(), "Cyclic rules should be in same phase");
        assertTrue(phases.get(0).contains("RuleA") && phases.get(0).contains("RuleB"),
                "Both cyclic rules should be in Phase 0");
    }

    @Test
    @DisplayName("Three rules in a cycle (A -> B -> C -> A) should be in same phase")
    void testTriangleCycle() {
        RuleMeta ruleA = new RuleMeta("RuleA", Set.of("Z"), Set.of("X"));
        RuleMeta ruleB = new RuleMeta("RuleB", Set.of("X"), Set.of("Y"));
        RuleMeta ruleC = new RuleMeta("RuleC", Set.of("Y"), Set.of("Z"));

        Stratifier stratifier = new Stratifier(Arrays.asList(ruleA, ruleB, ruleC));
        List<Set<String>> phases = stratifier.stratify();

        System.out.println(stratifier);

        assertTrue(stratifier.hasCycles());
        assertEquals(1, phases.size(), "All 3 cyclic rules should be in same phase");
        assertEquals(3, phases.get(0).size());
    }

    @Test
    @DisplayName("Mixed: acyclic rules leading into a cycle")
    void testAcyclicLeadingToCycle() {
        // Start -> A -> [B <-> C cycle]
        RuleMeta start = new RuleMeta("Start", Collections.emptySet(), Set.of("Init"));
        RuleMeta ruleA = new RuleMeta("RuleA", Set.of("Init"), Set.of("X"));
        RuleMeta ruleB = new RuleMeta("RuleB", Set.of("X", "Y"), Set.of("XX"));
        RuleMeta ruleC = new RuleMeta("RuleC", Set.of("XX"), Set.of("Y"));

        Stratifier stratifier = new Stratifier(Arrays.asList(start, ruleA, ruleB, ruleC));
        List<Set<String>> phases = stratifier.stratify();

        System.out.println(stratifier);

        assertTrue(stratifier.hasCycles());

        // Start should be Phase 0
        assertTrue(phases.get(0).contains("Start"));

        // RuleA should be after Start
        int startPhase = findPhase(phases, "Start");
        int ruleAPhase = findPhase(phases, "RuleA");
        assertTrue(startPhase < ruleAPhase, "Start should come before RuleA");

        // B and C should be in same phase (cyclic)
        int ruleBPhase = findPhase(phases, "RuleB");
        int ruleCPhase = findPhase(phases, "RuleC");
        assertEquals(ruleBPhase, ruleCPhase, "Cyclic rules B and C should be in same phase");
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Test
    @DisplayName("Empty rule list should return empty phases")
    void testEmptyRules() {
        Stratifier stratifier = new Stratifier(Collections.emptyList());
        List<Set<String>> phases = stratifier.stratify();

        assertTrue(phases.isEmpty());
        assertFalse(stratifier.hasCycles());
    }

    @Test
    @DisplayName("Single rule should be in Phase 0")
    void testSingleRule() {
        RuleMeta rule = new RuleMeta("OnlyRule", Set.of("A"), Set.of("B"));

        Stratifier stratifier = new Stratifier(Collections.singletonList(rule));
        List<Set<String>> phases = stratifier.stratify();

        assertEquals(1, phases.size());
        assertTrue(phases.get(0).contains("OnlyRule"));
        assertFalse(stratifier.hasCycles());
    }

    @Test
    @DisplayName("toString should produce readable output")
    void testToString() {
        RuleMeta ruleA = new RuleMeta("RuleA", Collections.emptySet(), Set.of("X"));
        RuleMeta ruleB = new RuleMeta("RuleB", Set.of("X"), Collections.emptySet());

        Stratifier stratifier = new Stratifier(Arrays.asList(ruleA, ruleB));
        String output = stratifier.toString();

        assertNotNull(output);
        assertTrue(output.contains("Phase 1"));
        assertTrue(output.contains("Phase 2"));
        assertTrue(output.contains("RuleA"));
        assertTrue(output.contains("RuleB"));
    }
}
