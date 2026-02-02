# DRL Rule Impact Analysis

A Java toolkit for parsing DRL (Drools Rule Language) files, building rule dependency graphs, and stratifying rules into execution phases.

## Overview

This module provides a comprehensive API for static analysis of DRL rule files:

- **Rule Parsing** - Extract rule metadata including inputs (LHS patterns) and outputs (RHS writes)
- **Dependency Graph** - Build directed graphs showing rule dependencies based on data flow
- **Phase Stratification** - Determine execution phases using topological sort and SCC analysis

### Use Cases

- Static analysis and impact analysis of DRL rule files
- Building rule dependency graphs for visualization
- Automatic phase detection for parallel rule execution
- Documentation generation
- Refactoring tools

---

## Classes

### `RuleMeta`
A data class representing metadata for a single rule.

| Method | Description |
| ------ | ----------- |
| `RuleMeta(String ruleName)` | Creates with empty inputs/outputs |
| `RuleMeta(String, Set<String>, Set<String>)` | Creates with specified inputs/outputs |
| `getRuleName()` | Returns the rule name |
| `getInputs()` | Returns `Set<String>` of LHS pattern types |
| `getOutputs()` | Returns `Set<String>` of RHS write types |
| `addInput(String)` | Adds an input object type |
| `addOutput(String)` | Adds an output object type |

---

### `DrlRuleParser`
The main parser class that uses Drools' `DrlParser` to parse DRL content.

| Method | Description |
| ------ | ----------- |
| `DrlRuleParser()` | Creates parser with DRL6 language level (default) |
| `parse(String drl)` | Parses DRL and returns `List<RuleMeta>` |

---

### `DependencyGraphBuilder`
Builds a directed graph of rule dependencies using JGraphT.

An edge from Rule A to Rule B is created if Rule A's outputs intersect with Rule B's inputs (i.e., Rule A may trigger Rule B).

| Method | Description |
| ------ | ----------- |
| `DependencyGraphBuilder(List<RuleMeta>)` | Constructs graph from rules |
| `getGraph()` | Returns the JGraphT `Graph<RuleMeta, DefaultEdge>` |
| `getRules()` | Returns the set of rules (vertices) |
| `getPredecessors(RuleMeta)` | Returns rules that may trigger the given rule |
| `getSuccessors(RuleMeta)` | Returns rules that may be triggered by the given rule |
| `hasEdge(RuleMeta, RuleMeta)` | Checks if direct edge exists |
| `isIsolated(RuleMeta)` | Checks if rule has no dependencies/dependents |
| `toString()` | Returns adjacency list representation |

---

### `Stratifier`
Stratifies rules into execution phases using topological sort and Strongly Connected Component (SCC) analysis.

**Algorithm:**
1. Build dependency graph from rules
2. Detect SCCs using Kosaraju's algorithm (rules in a cycle must be in the same phase)
3. Build condensation graph (each SCC becomes a single node)
4. Perform topological sort on the condensation graph
5. Assign layers: Layer 0 = in-degree 0 nodes (Phase 1), etc.

| Method | Description |
| ------ | ----------- |
| `Stratifier(List<RuleMeta>)` | Constructs stratifier from rules |
| `stratify()` | Returns `List<Set<String>>` of phases (index 0 = Phase 1) |
| `hasCycles()` | Returns true if cycles exist in the dependency graph |
| `getStronglyConnectedComponents()` | Returns all SCCs |
| `getCyclicComponents()` | Returns only SCCs with >1 rule (actual cycles) |
| `toString()` | Returns formatted stratification result |

---

## Usage Examples

### Example 1: Parse Rules and Extract Metadata

```java
import org.drools.impact.research.DrlRuleParser;
import org.drools.impact.research.RuleMeta;

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
System.out.println("Rule: " + rule.getRuleName());   // test
System.out.println("Inputs: " + rule.getInputs());   // [Person]
System.out.println("Outputs: " + rule.getOutputs()); // [Person, Log]
```

### Example 2: Build Dependency Graph

```java
import org.drools.impact.research.DependencyGraphBuilder;

// Rule A produces Edge, Rule B reads Edge and produces Junction
RuleMeta ruleA = new RuleMeta("RuleA", Collections.emptySet(), Set.of("Edge"));
RuleMeta ruleB = new RuleMeta("RuleB", Set.of("Edge"), Set.of("Junction"));
RuleMeta ruleC = new RuleMeta("RuleC", Set.of("Line"), Collections.emptySet());

DependencyGraphBuilder builder = new DependencyGraphBuilder(Arrays.asList(ruleA, ruleB, ruleC));

System.out.println(builder.hasEdge(ruleA, ruleB)); // true (A triggers B)
System.out.println(builder.isIsolated(ruleC));     // true (no dependencies)
System.out.println(builder);                        // Adjacency list output
```

### Example 3: Stratify Rules into Phases

```java
import org.drools.impact.research.Stratifier;

RuleMeta start = new RuleMeta("Start", Collections.emptySet(), Set.of("Init"));
RuleMeta processA = new RuleMeta("ProcessA", Set.of("Init"), Set.of("DataA"));
RuleMeta processB = new RuleMeta("ProcessB", Set.of("Init"), Set.of("DataB"));
RuleMeta combine = new RuleMeta("Combine", Set.of("DataA", "DataB"), Set.of("Result"));

Stratifier stratifier = new Stratifier(Arrays.asList(start, processA, processB, combine));
List<Set<String>> phases = stratifier.stratify();

// Phase 1: [Start]          - no dependencies
// Phase 2: [ProcessA, ProcessB] - depend on Phase 1
// Phase 3: [Combine]        - depends on Phase 2

System.out.println(stratifier);
```

---

## Supported DRL Features

### LHS (Input) Pattern Extraction
- ✅ Simple patterns: `Person()`
- ✅ Patterns with constraints: `Person(age > 18)`
- ✅ Patterns with bindings: `$p : Person()`
- ✅ Nested `not` / `exists` / `or` elements
- ✅ Deduplication of same type

### RHS (Output) Write Detection
Uses regex heuristics to detect:
- ✅ `insert(new Type())` - extracts `Type`
- ✅ `insertLogical(new Type())` - extracts `Type`
- ✅ `modify($var)` - resolves `$var` to LHS pattern type
- ✅ `update($var)` - resolves `$var` to LHS pattern type
- ✅ `delete($var)` - resolves `$var` to LHS pattern type
- ✅ `retract($var)` - resolves `$var` to LHS pattern type

---

## Running the Demo

```bash
cd /home/maheshdila/mahesh/research/incubator-kie-drools

# Compile and run the Main demo class
mvn compile exec:java -pl drools-impact-analysis/drools-impact-analysis-parser \
    -Dexec.mainClass="org.drools.impact.research.Main"
```

---

## Running Tests

```bash
cd /home/maheshdila/mahesh/research/incubator-kie-drools

# Run all research module tests
mvn test -pl drools-impact-analysis/drools-impact-analysis-parser \
    -Dtest="org.drools.impact.research.*Test"

# Run individual test classes
mvn test -pl drools-impact-analysis/drools-impact-analysis-parser \
    -Dtest=org.drools.impact.research.DrlRuleParserTest

mvn test -pl drools-impact-analysis/drools-impact-analysis-parser \
    -Dtest=org.drools.impact.research.DependencyGraphBuilderTest

mvn test -pl drools-impact-analysis/drools-impact-analysis-parser \
    -Dtest=org.drools.impact.research.StratifierTest
```

---

## Test Overview

### DrlRuleParserTest

| Test | Description |
| ---- | ----------- |
| `testParseRuleWithTwoPatterns` | Extracts Person and Car from two patterns |
| `testParseSinglePatternRule` | Single pattern extraction |
| `testParseMultipleRules` | Multiple rules in same DRL |
| `testParseNestedConditionalElements` | Handles `not`, `exists` |
| `testParseRuleWithConstraints` | Patterns with constraints |
| `testParseRuleWithBindings` | Patterns with `$var : Type()` |
| `testDeduplicateSamePatternType` | Deduplication |
| `testVerificationModifyAndInsert` | 📌 Main verification: modify($p) + insert → outputs |
| `testExtractInsert` | insert(new Type) extraction |
| `testExtractInsertLogical` | insertLogical(new Type) extraction |
| `testExtractModifyWithVariableResolution` | modify($var) → Type resolution |
| `testExtractDelete` | delete($var) type resolution |
| `testExtractRetract` | retract($var) type resolution |
| `testExtractUpdate` | update($var) type resolution |
| `testMultipleInserts` | Multiple insert statements |
| `testComplexRhs` | Complex RHS with mixed operations |
| `testNoWriteOperations` | Empty outputs for no writes |
| `testUnresolvedVariable` | Handles unbound variables gracefully |

### DependencyGraphBuilderTest

| Test | Description |
| ---- | ----------- |
| `testEdgeCreation` | Verifies edges are created based on output/input intersection |
| `testNoSelfLoops` | Ensures self-loops are not created |
| `testIsolatedRules` | Tests detection of isolated rules |
| `testPredecessorsAndSuccessors` | Validates predecessor/successor queries |
| `testEmptyGraph` | Handles empty rule list |

### StratifierTest

| Test | Description |
| ---- | ----------- |
| `testSimplePhases` | Linear chain: A → B → C produces 3 phases |
| `testParallelRules` | Rules with no deps are in same phase |
| `testCyclicRules` | Rules in cycle are grouped in same phase |
| `testWaltzDbPhases` | 📌 Validates WaltzDB rule stratification |
| `testComplexDag` | Complex DAG with multiple paths |

---

## Architecture

```
┌─────────────────┐     ┌──────────────────────┐     ┌─────────────┐
│  DRL String     │────▶│   DrlRuleParser      │────▶│ List<Rule   │
│  (input)        │     │   - extract LHS/RHS  │     │   Meta>     │
└─────────────────┘     └──────────────────────┘     └─────────────┘
                                                            │
                                                            ▼
┌─────────────────┐     ┌──────────────────────┐     ┌─────────────┐
│  List<Set<      │◀────│   Stratifier         │◀────│ Dependency  │
│    String>>     │     │   - SCC detection    │     │   Graph     │
│  (phases)       │     │   - topo sort        │     │   Builder   │
└─────────────────┘     └──────────────────────┘     └─────────────┘
```

---

## Dependencies

- `drools-engine` - For DRL parsing capabilities
- `jgrapht-core` - Graph algorithms (SCC, topological sort)
- `junit-jupiter` - For unit testing (test scope)

---

## License

Licensed under the Apache License, Version 2.0.
