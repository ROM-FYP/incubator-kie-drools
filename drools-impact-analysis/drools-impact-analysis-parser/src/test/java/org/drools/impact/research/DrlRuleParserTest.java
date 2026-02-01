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

import java.util.List;
import java.util.Set;

import org.drools.drl.parser.DroolsParserException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DrlRuleParser}.
 */
class DrlRuleParserTest {

    private DrlRuleParser parser;

    @BeforeEach
    void setUp() {
        parser = new DrlRuleParser();
    }

    // =========================================================================
    // LHS (Input) Analysis Tests
    // =========================================================================

    @Test
    @DisplayName("Should extract Person and Car inputs from rule with two patterns")
    void testParseRuleWithTwoPatterns() throws DroolsParserException {
        // Given: A DRL with rule "test" containing Person() and Car() patterns
        String drl = """
                package org.example

                rule "test"
                when
                    Person()
                    Car()
                then
                    // do something
                end
                """;

        // When: Parse the DRL
        List<RuleMeta> rules = parser.parse(drl);

        // Then: Should return exactly one rule with inputs containing both Person and
        // Car
        assertNotNull(rules, "Rules list should not be null");
        assertEquals(1, rules.size(), "Should have exactly one rule");

        RuleMeta ruleMeta = rules.get(0);
        assertNotNull(ruleMeta, "RuleMeta should not be null");
        assertEquals("test", ruleMeta.getRuleName(), "Rule name should be 'test'");

        Set<String> inputs = ruleMeta.getInputs();
        assertNotNull(inputs, "Inputs should not be null");
        assertEquals(2, inputs.size(), "Should have exactly 2 inputs");
        assertTrue(inputs.contains("Person"), "Inputs should contain 'Person'");
        assertTrue(inputs.contains("Car"), "Inputs should contain 'Car'");
    }

    @Test
    @DisplayName("Should handle single pattern rule")
    void testParseSinglePatternRule() throws DroolsParserException {
        String drl = """
                package org.example

                rule "single"
                when
                    Order()
                then
                end
                """;

        List<RuleMeta> rules = parser.parse(drl);

        assertNotNull(rules);
        assertEquals(1, rules.size());
        assertEquals("single", rules.get(0).getRuleName());
        assertEquals(Set.of("Order"), rules.get(0).getInputs());
    }

    @Test
    @DisplayName("Should handle multiple rules in same DRL")
    void testParseMultipleRules() throws DroolsParserException {
        String drl = """
                package org.example

                rule "rule1"
                when
                    Person()
                then
                end

                rule "rule2"
                when
                    Car()
                    Engine()
                then
                end
                """;

        List<RuleMeta> rules = parser.parse(drl);

        assertNotNull(rules);
        assertEquals(2, rules.size());

        RuleMeta rule1 = rules.get(0);
        assertEquals("rule1", rule1.getRuleName());
        assertEquals(Set.of("Person"), rule1.getInputs());

        RuleMeta rule2 = rules.get(1);
        assertEquals("rule2", rule2.getRuleName());
        assertEquals(Set.of("Car", "Engine"), rule2.getInputs());
    }

    @Test
    @DisplayName("Should handle nested conditional elements (not, exists, or)")
    void testParseNestedConditionalElements() throws DroolsParserException {
        String drl = """
                package org.example

                rule "nested"
                when
                    Person()
                    not Car()
                    exists Order()
                then
                end
                """;

        List<RuleMeta> rules = parser.parse(drl);

        assertNotNull(rules);
        assertEquals(1, rules.size());

        Set<String> inputs = rules.get(0).getInputs();
        assertEquals(3, inputs.size());
        assertTrue(inputs.contains("Person"));
        assertTrue(inputs.contains("Car"));
        assertTrue(inputs.contains("Order"));
    }

    @Test
    @DisplayName("Should handle empty DRL string")
    void testParseEmptyDrl() throws DroolsParserException {
        List<RuleMeta> rules = parser.parse("");
        assertNotNull(rules);
        assertTrue(rules.isEmpty());
    }

    @Test
    @DisplayName("Should handle null DRL string")
    void testParseNullDrl() throws DroolsParserException {
        List<RuleMeta> rules = parser.parse(null);
        assertNotNull(rules);
        assertTrue(rules.isEmpty());
    }

    @Test
    @DisplayName("Should handle rule with constraints")
    void testParseRuleWithConstraints() throws DroolsParserException {
        String drl = """
                package org.example

                rule "constrained"
                when
                    Person(age > 18, name == "John")
                    Car(brand == "Toyota")
                then
                end
                """;

        List<RuleMeta> rules = parser.parse(drl);

        assertNotNull(rules);
        assertEquals(1, rules.size());

        Set<String> inputs = rules.get(0).getInputs();
        assertEquals(2, inputs.size());
        assertTrue(inputs.contains("Person"));
        assertTrue(inputs.contains("Car"));
    }

    @Test
    @DisplayName("Should handle rule with binding variables")
    void testParseRuleWithBindings() throws DroolsParserException {
        String drl = """
                package org.example

                rule "bindings"
                when
                    $p : Person()
                    $c : Car()
                then
                end
                """;

        List<RuleMeta> rules = parser.parse(drl);

        assertNotNull(rules);
        assertEquals(1, rules.size());

        Set<String> inputs = rules.get(0).getInputs();
        assertEquals(2, inputs.size());
        assertTrue(inputs.contains("Person"));
        assertTrue(inputs.contains("Car"));
    }

    @Test
    @DisplayName("Should deduplicate same pattern type appearing multiple times")
    void testDeduplicateSamePatternType() throws DroolsParserException {
        String drl = """
                package org.example

                rule "duplicate"
                when
                    Person(age > 18)
                    Person(name == "John")
                then
                end
                """;

        List<RuleMeta> rules = parser.parse(drl);

        assertNotNull(rules);
        assertEquals(1, rules.size());

        Set<String> inputs = rules.get(0).getInputs();
        assertEquals(1, inputs.size(), "Should deduplicate 'Person'");
        assertTrue(inputs.contains("Person"));
    }

    // =========================================================================
    // RHS (Output) Analysis Tests - Phase 2
    // =========================================================================

    @Nested
    @DisplayName("RHS Analysis Tests")
    class RhsAnalysisTests {

        @Test
        @DisplayName("VERIFICATION: Should extract Person and Log from modify($p) and insert(new Log())")
        void testVerificationModifyAndInsert() throws DroolsParserException {
            // This is the main verification test case from the spec
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

            List<RuleMeta> rules = parser.parse(drl);

            assertNotNull(rules, "Rules list should not be null");
            assertEquals(1, rules.size(), "Should have exactly one rule");

            RuleMeta ruleMeta = rules.get(0);
            Set<String> outputs = ruleMeta.getOutputs();

            assertNotNull(outputs, "Outputs should not be null");
            assertEquals(2, outputs.size(), "Should have exactly 2 outputs");
            assertTrue(outputs.contains("Person"), "Outputs should contain 'Person' (from modify($p))");
            assertTrue(outputs.contains("Log"), "Outputs should contain 'Log' (from insert(new Log()))");
        }

        @Test
        @DisplayName("Should extract type from insert(new Type())")
        void testExtractInsert() throws DroolsParserException {
            String drl = """
                    package org.example

                    rule "insert_test"
                    when
                        Person()
                    then
                        insert(new Alert("message"));
                    end
                    """;

            List<RuleMeta> rules = parser.parse(drl);

            assertNotNull(rules);
            assertEquals(1, rules.size());

            Set<String> outputs = rules.get(0).getOutputs();
            assertEquals(1, outputs.size());
            assertTrue(outputs.contains("Alert"));
        }

        @Test
        @DisplayName("Should extract type from insertLogical(new Type())")
        void testExtractInsertLogical() throws DroolsParserException {
            String drl = """
                    package org.example

                    rule "insert_logical_test"
                    when
                        Person()
                    then
                        insertLogical(new Inference());
                    end
                    """;

            List<RuleMeta> rules = parser.parse(drl);

            assertNotNull(rules);
            assertEquals(1, rules.size());

            Set<String> outputs = rules.get(0).getOutputs();
            assertEquals(1, outputs.size());
            assertTrue(outputs.contains("Inference"));
        }

        @Test
        @DisplayName("Should resolve modify($var) to its LHS pattern type")
        void testExtractModifyWithVariableResolution() throws DroolsParserException {
            String drl = """
                    package org.example

                    rule "modify_test"
                    when
                        $order : Order(status == "pending")
                    then
                        modify($order){ setStatus("completed") };
                    end
                    """;

            List<RuleMeta> rules = parser.parse(drl);

            assertNotNull(rules);
            assertEquals(1, rules.size());

            Set<String> outputs = rules.get(0).getOutputs();
            assertEquals(1, outputs.size());
            assertTrue(outputs.contains("Order"), "Should resolve $order to Order type");
        }

        @Test
        @DisplayName("Should resolve delete($var) to its LHS pattern type")
        void testExtractDelete() throws DroolsParserException {
            String drl = """
                    package org.example

                    rule "delete_test"
                    when
                        $temp : TemporaryFact()
                    then
                        delete($temp);
                    end
                    """;

            List<RuleMeta> rules = parser.parse(drl);

            assertNotNull(rules);
            assertEquals(1, rules.size());

            Set<String> outputs = rules.get(0).getOutputs();
            assertEquals(1, outputs.size());
            assertTrue(outputs.contains("TemporaryFact"), "Should resolve $temp to TemporaryFact type");
        }

        @Test
        @DisplayName("Should resolve retract($var) to its LHS pattern type")
        void testExtractRetract() throws DroolsParserException {
            String drl = """
                    package org.example

                    rule "retract_test"
                    when
                        $old : OldData()
                    then
                        retract($old);
                    end
                    """;

            List<RuleMeta> rules = parser.parse(drl);

            assertNotNull(rules);
            assertEquals(1, rules.size());

            Set<String> outputs = rules.get(0).getOutputs();
            assertEquals(1, outputs.size());
            assertTrue(outputs.contains("OldData"), "Should resolve $old to OldData type");
        }

        @Test
        @DisplayName("Should resolve update($var) to its LHS pattern type")
        void testExtractUpdate() throws DroolsParserException {
            String drl = """
                    package org.example

                    rule "update_test"
                    when
                        $item : Item()
                    then
                        $item.setValue(100);
                        update($item);
                    end
                    """;

            List<RuleMeta> rules = parser.parse(drl);

            assertNotNull(rules);
            assertEquals(1, rules.size());

            Set<String> outputs = rules.get(0).getOutputs();
            assertEquals(1, outputs.size());
            assertTrue(outputs.contains("Item"), "Should resolve $item to Item type");
        }

        @Test
        @DisplayName("Should handle multiple inserts of different types")
        void testMultipleInserts() throws DroolsParserException {
            String drl = """
                    package org.example

                    rule "multi_insert"
                    when
                        Person()
                    then
                        insert(new Log());
                        insert(new Audit());
                        insert(new Notification());
                    end
                    """;

            List<RuleMeta> rules = parser.parse(drl);

            assertNotNull(rules);
            assertEquals(1, rules.size());

            Set<String> outputs = rules.get(0).getOutputs();
            assertEquals(3, outputs.size());
            assertTrue(outputs.contains("Log"));
            assertTrue(outputs.contains("Audit"));
            assertTrue(outputs.contains("Notification"));
        }

        @Test
        @DisplayName("Should handle complex RHS with multiple operations")
        void testComplexRhs() throws DroolsParserException {
            String drl = """
                    package org.example

                    rule "complex"
                    when
                        $p : Person()
                        $o : Order(customerId == $p.id)
                    then
                        modify($p){ setLastOrderDate(new Date()) };
                        modify($o){ setStatus("processed") };
                        insert(new OrderHistory($o));
                        delete($p);
                    end
                    """;

            List<RuleMeta> rules = parser.parse(drl);

            assertNotNull(rules);
            assertEquals(1, rules.size());

            Set<String> outputs = rules.get(0).getOutputs();
            assertEquals(3, outputs.size());
            assertTrue(outputs.contains("Person"), "Should have Person from modify and delete");
            assertTrue(outputs.contains("Order"), "Should have Order from modify");
            assertTrue(outputs.contains("OrderHistory"), "Should have OrderHistory from insert");
        }

        @Test
        @DisplayName("Should return empty outputs when RHS has no write operations")
        void testNoWriteOperations() throws DroolsParserException {
            String drl = """
                    package org.example

                    rule "no_writes"
                    when
                        Person()
                    then
                        System.out.println("Hello");
                    end
                    """;

            List<RuleMeta> rules = parser.parse(drl);

            assertNotNull(rules);
            assertEquals(1, rules.size());

            Set<String> outputs = rules.get(0).getOutputs();
            assertNotNull(outputs);
            assertTrue(outputs.isEmpty(), "Should have no outputs when RHS has no write operations");
        }

        @Test
        @DisplayName("Should not fail when variable not found in LHS")
        void testUnresolvedVariable() throws DroolsParserException {
            String drl = """
                    package org.example

                    rule "unresolved"
                    when
                        Person()
                    then
                        modify($unknown){ setValue(1) };
                    end
                    """;

            List<RuleMeta> rules = parser.parse(drl);

            assertNotNull(rules);
            assertEquals(1, rules.size());

            Set<String> outputs = rules.get(0).getOutputs();
            assertNotNull(outputs);
            // Should not contain anything since $unknown is not defined in LHS
            assertTrue(outputs.isEmpty(), "Should not resolve unbound variable");
        }
    }
}
