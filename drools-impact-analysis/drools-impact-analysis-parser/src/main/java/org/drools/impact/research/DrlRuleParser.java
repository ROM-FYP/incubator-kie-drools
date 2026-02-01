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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.drools.drl.ast.descr.BaseDescr;
import org.drools.drl.ast.descr.ConditionalElementDescr;
import org.drools.drl.ast.descr.PackageDescr;
import org.drools.drl.ast.descr.PatternDescr;
import org.drools.drl.ast.descr.RuleDescr;
import org.drools.drl.parser.DrlParser;
import org.drools.drl.parser.DroolsParserException;
import org.kie.internal.builder.conf.LanguageLevelOption;

/**
 * Parses DRL (Drools Rule Language) strings and extracts rule metadata.
 * Uses {@link org.drools.drl.parser.DrlParser} to parse the DRL content
 * and extracts information about each rule including:
 * <ul>
 * <li><b>inputs</b>: object types found in the LHS (Left Hand Side)
 * patterns</li>
 * <li><b>outputs</b>: object types that are inserted/modified/deleted in the
 * RHS (Right Hand Side)</li>
 * </ul>
 * 
 * RHS analysis uses regex heuristics to detect:
 * <ul>
 * <li>{@code insert(new Type(...))} - extracts Type</li>
 * <li>{@code modify($var){...}} - resolves $var to its LHS pattern type</li>
 * <li>{@code delete($var)} - resolves $var to its LHS pattern type</li>
 * </ul>
 */
public class DrlRuleParser {

    private final LanguageLevelOption languageLevel;

    // Regex patterns for RHS analysis
    // Matches: insert(new TypeName or insertLogical(new TypeName
    private static final Pattern INSERT_PATTERN = Pattern.compile("insert(?:Logical)?\\s*\\(\\s*new\\s+(\\w+)");

    // Matches: modify($varName) or modify( $varName )
    private static final Pattern MODIFY_PATTERN = Pattern.compile("modify\\s*\\(\\s*\\$(\\w+)");

    // Matches: delete($varName) or retract($varName)
    private static final Pattern DELETE_PATTERN = Pattern.compile("(?:delete|retract)\\s*\\(\\s*\\$(\\w+)");

    // Matches: update($varName)
    private static final Pattern UPDATE_PATTERN = Pattern.compile("update\\s*\\(\\s*\\$(\\w+)");

    /**
     * Creates a new DrlRuleParser with DRL6 language level (default).
     */
    public DrlRuleParser() {
        this(LanguageLevelOption.DRL6);
    }

    /**
     * Creates a new DrlRuleParser with the specified language level.
     *
     * @param languageLevel the DRL language level to use for parsing
     */
    public DrlRuleParser(LanguageLevelOption languageLevel) {
        this.languageLevel = languageLevel;
    }

    /**
     * Parses a DRL string and extracts metadata from all rules.
     *
     * @param drlContent the DRL content to parse
     * @return a list of RuleMeta objects, one for each rule in the DRL
     * @throws DroolsParserException if the DRL content cannot be parsed
     */
    public List<RuleMeta> parse(String drlContent) throws DroolsParserException {
        if (drlContent == null || drlContent.trim().isEmpty()) {
            return new ArrayList<>();
        }

        DrlParser parser = new DrlParser(languageLevel);
        PackageDescr packageDescr = parser.parse(new StringReader(drlContent));

        if (packageDescr == null) {
            throw new DroolsParserException("Failed to parse DRL content: parser returned null");
        }

        if (parser.hasErrors()) {
            throw new DroolsParserException("Failed to parse DRL content: " + parser.getErrors());
        }

        List<RuleMeta> ruleMetas = new ArrayList<>();

        for (RuleDescr ruleDescr : packageDescr.getRules()) {
            RuleMeta ruleMeta = new RuleMeta(ruleDescr.getName());

            // Build variable-to-type map from LHS pattern bindings
            Map<String, String> variableTypeMap = new HashMap<>();

            // Parse the LHS to extract pattern object types and bindings
            if (ruleDescr.getLhs() != null) {
                extractPatternsFromDescr(ruleDescr.getLhs(), ruleMeta, variableTypeMap);
            }

            // Parse the RHS to extract write set (insert/modify/delete)
            extractWrites(ruleDescr, variableTypeMap, ruleMeta);

            ruleMetas.add(ruleMeta);
        }

        return ruleMetas;
    }

    /**
     * Recursively extracts PatternDescr object types and variable bindings from a
     * descriptor tree.
     * This handles nested conditional elements (AND, OR, NOT, EXISTS, etc.).
     *
     * @param descr           the descriptor to process
     * @param ruleMeta        the RuleMeta to add extracted inputs to
     * @param variableTypeMap map to populate with variable bindings ($var -> Type)
     */
    private void extractPatternsFromDescr(BaseDescr descr, RuleMeta ruleMeta,
            Map<String, String> variableTypeMap) {
        if (descr instanceof PatternDescr) {
            PatternDescr patternDescr = (PatternDescr) descr;
            String objectType = patternDescr.getObjectType();
            String identifier = patternDescr.getIdentifier();

            if (objectType != null && !objectType.isEmpty()) {
                ruleMeta.addInput(objectType);

                // Store variable binding (e.g., $p -> Person)
                if (identifier != null && !identifier.isEmpty()) {
                    // Remove $ prefix if present for consistent lookup
                    String varName = identifier.startsWith("$") ? identifier.substring(1) : identifier;
                    variableTypeMap.put(varName, objectType);
                }
            }
        } else if (descr instanceof ConditionalElementDescr) {
            ConditionalElementDescr condDescr = (ConditionalElementDescr) descr;
            for (BaseDescr childDescr : condDescr.getDescrs()) {
                extractPatternsFromDescr(childDescr, ruleMeta, variableTypeMap);
            }
        }
    }

    /**
     * Extracts write set from the RHS consequence using regex heuristics.
     * Detects insert(new Type), modify($var), delete($var), update($var), and
     * retract($var).
     *
     * @param ruleDescr       the rule descriptor containing the consequence
     * @param variableTypeMap map of variable names to their types from LHS
     * @param ruleMeta        the RuleMeta to add extracted outputs to
     */
    private void extractWrites(RuleDescr ruleDescr, Map<String, String> variableTypeMap,
            RuleMeta ruleMeta) {
        Object consequence = ruleDescr.getConsequence();
        if (consequence == null) {
            return;
        }

        String rhsContent = consequence.toString();
        Set<String> outputs = new HashSet<>();

        // Extract insert(new Type) patterns
        Matcher insertMatcher = INSERT_PATTERN.matcher(rhsContent);
        while (insertMatcher.find()) {
            String typeName = insertMatcher.group(1);
            if (typeName != null && !typeName.isEmpty()) {
                outputs.add(typeName);
            }
        }

        // Extract modify($var) patterns and resolve variable types
        Matcher modifyMatcher = MODIFY_PATTERN.matcher(rhsContent);
        while (modifyMatcher.find()) {
            String varName = modifyMatcher.group(1);
            String resolvedType = variableTypeMap.get(varName);
            if (resolvedType != null) {
                outputs.add(resolvedType);
            }
        }

        // Extract delete($var) and retract($var) patterns
        Matcher deleteMatcher = DELETE_PATTERN.matcher(rhsContent);
        while (deleteMatcher.find()) {
            String varName = deleteMatcher.group(1);
            String resolvedType = variableTypeMap.get(varName);
            if (resolvedType != null) {
                outputs.add(resolvedType);
            }
        }

        // Extract update($var) patterns
        Matcher updateMatcher = UPDATE_PATTERN.matcher(rhsContent);
        while (updateMatcher.find()) {
            String varName = updateMatcher.group(1);
            String resolvedType = variableTypeMap.get(varName);
            if (resolvedType != null) {
                outputs.add(resolvedType);
            }
        }

        // Add all outputs to ruleMeta
        for (String output : outputs) {
            ruleMeta.addOutput(output);
        }
    }
}
