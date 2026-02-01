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

/**
 * Example main class demonstrating DrlRuleParser and DependencyGraphBuilder
 * usage.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        // Example 1: Parse a single rule and extract inputs/outputs
        String drl = """
                package org.example

                rule "test"
                when
                    $p: Person()
                then
                    modify($p){ setAge(30) };
                    insert(new Log());
                end
                """;

        DrlRuleParser parser = new DrlRuleParser();
        List<RuleMeta> rules = parser.parse(drl);

        RuleMeta rule = rules.get(0);
        System.out.println("=== Single Rule Analysis ===");
        System.out.println("Rule: " + rule.getRuleName());
        System.out.println("Inputs: " + rule.getInputs());
        System.out.println("Outputs: " + rule.getOutputs());

        // Example 2: Build a dependency graph
        System.out.println("\n=== Dependency Graph Demo ===");

        // Create sample rules to demonstrate graph building
        // Rule A: produces Edge
        // Rule B: reads Edge, produces Junction
        // Rule C: reads Line (isolated)
        RuleMeta ruleA = new RuleMeta("RuleA_ProducesEdge", Collections.emptySet(), Set.of("Edge"));
        RuleMeta ruleB = new RuleMeta("RuleB_ReadsEdge", Set.of("Edge"), Set.of("Junction"));
        RuleMeta ruleC = new RuleMeta("RuleC_ReadsLine", Set.of("Line"), Collections.emptySet());

        List<RuleMeta> sampleRules = Arrays.asList(ruleA, ruleB, ruleC);
        DependencyGraphBuilder graphBuilder = new DependencyGraphBuilder(sampleRules);

        System.out.println(graphBuilder);
        System.out.println("Edge A->B exists: " + graphBuilder.hasEdge(ruleA, ruleB));
        System.out.println("Rule C is isolated: " + graphBuilder.isIsolated(ruleC));
    }
}
