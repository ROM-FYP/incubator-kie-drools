# DRL Rule Parser

A Java utility for parsing DRL (Drools Rule Language) files and extracting rule metadata, including object types from LHS patterns and RHS write operations.

## Overview

This module provides a simple API to parse DRL strings and extract:
- **Rule names** - The identifier of each rule
- **Input types** - Object types matched in the LHS patterns (e.g., `Person`, `Car`)
- **Output types** - Object types that are inserted/modified/deleted in the RHS

This is useful for:
- Static analysis of DRL rule files
- Building rule dependency graphs
- Impact analysis and refactoring tools
- Documentation generation

## Classes

### `RuleMeta`
A data class representing metadata for a single rule.

| Method | Description |
| ------ | ----------- |
| `getRuleName()` | Returns the rule name |
| `getInputs()` | Returns `Set<String>` of LHS pattern types |
| `getOutputs()` | Returns `Set<String>` of RHS write types |
| `addInput(String)` | Adds an input object type |
| `addOutput(String)` | Adds an output object type |

### `DrlRuleParser`
The main parser class that uses Drools' `DrlParser` to parse DRL content.

| Method | Description |
| ------ | ----------- |
| `DrlRuleParser()` | Creates parser with DRL6 language level (default) |
| `parse(String drl)` | Parses DRL and returns `List<RuleMeta>` |

## Usage Example

```java
import org.drools.impact.research.DrlRuleParser;
import org.drools.impact.research.RuleMeta;

public class Example {
    public static void main(String[] args) throws Exception {
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
        System.out.println("Rule: " + rule.getRuleName());
        System.out.println("Inputs: " + rule.getInputs());
        System.out.println("Outputs: " + rule.getOutputs());
        // Output:
        // Rule: test
        // Inputs: [Person]
        // Outputs: [Person, Log]
    }
}
```

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

## Running Tests

```bash
cd /home/maheshdila/mahesh/research/incubator-kie-drools

# Run all DrlRuleParser tests (20 tests)
mvn test -pl drools-impact-analysis/drools-impact-analysis-parser \
    -Dtest=org.drools.impact.research.DrlRuleParserTest
```

## Test Cases

### LHS Tests
| Test | Description |
| ---- | ----------- |
| `testParseRuleWithTwoPatterns` | Extracts Person and Car from two patterns |
| `testParseSinglePatternRule` | Single pattern extraction |
| `testParseMultipleRules` | Multiple rules in same DRL |
| `testParseNestedConditionalElements` | Handles `not`, `exists` |
| `testParseRuleWithConstraints` | Patterns with constraints |
| `testParseRuleWithBindings` | Patterns with `$var : Type()` |
| `testDeduplicateSamePatternType` | Deduplication |

### RHS Tests
| Test | Description |
| ---- | ----------- |
| `testVerificationModifyAndInsert` | 📌 **Main verification**: modify($p) + insert(new Log()) → [Person, Log] |
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

## Dependencies

- `drools-engine` - For DRL parsing capabilities
- `junit-jupiter` - For unit testing (test scope)

## License

Licensed under the Apache License, Version 2.0.

