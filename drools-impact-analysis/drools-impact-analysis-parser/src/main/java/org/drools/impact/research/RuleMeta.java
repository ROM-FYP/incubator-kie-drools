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

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents metadata extracted from a parsed DRL rule.
 * Contains the rule name, the set of object types found in the LHS patterns
 * (inputs),
 * and the set of object types that are created/modified/deleted in the RHS
 * (outputs).
 */
public class RuleMeta {

    private final String ruleName;
    private final Set<String> inputs;
    private final Set<String> outputs;

    /**
     * Creates a new RuleMeta with the given rule name and empty sets of
     * inputs/outputs.
     *
     * @param ruleName the name of the rule
     */
    public RuleMeta(String ruleName) {
        this.ruleName = ruleName;
        this.inputs = new HashSet<>();
        this.outputs = new HashSet<>();
    }

    /**
     * Creates a new RuleMeta with the given rule name, inputs, and outputs.
     *
     * @param ruleName the name of the rule
     * @param inputs   the set of object types found in the LHS patterns
     * @param outputs  the set of object types modified/inserted/deleted in the RHS
     */
    public RuleMeta(String ruleName, Set<String> inputs, Set<String> outputs) {
        this.ruleName = ruleName;
        this.inputs = inputs != null ? new HashSet<>(inputs) : new HashSet<>();
        this.outputs = outputs != null ? new HashSet<>(outputs) : new HashSet<>();
    }

    /**
     * Gets the rule name.
     *
     * @return the rule name
     */
    public String getRuleName() {
        return ruleName;
    }

    /**
     * Gets an unmodifiable view of the input object types (LHS patterns).
     *
     * @return an unmodifiable set of object type names found in the LHS patterns
     */
    public Set<String> getInputs() {
        return Collections.unmodifiableSet(inputs);
    }

    /**
     * Gets an unmodifiable view of the output object types (RHS writes).
     *
     * @return an unmodifiable set of object type names that are
     *         inserted/modified/deleted
     */
    public Set<String> getOutputs() {
        return Collections.unmodifiableSet(outputs);
    }

    /**
     * Adds an input object type to this rule's metadata.
     *
     * @param objectType the object type name to add
     */
    public void addInput(String objectType) {
        if (objectType != null && !objectType.isEmpty()) {
            inputs.add(objectType);
        }
    }

    /**
     * Adds an output object type to this rule's metadata.
     *
     * @param objectType the object type name to add (from insert/modify/delete)
     */
    public void addOutput(String objectType) {
        if (objectType != null && !objectType.isEmpty()) {
            outputs.add(objectType);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RuleMeta ruleMeta = (RuleMeta) o;
        return Objects.equals(ruleName, ruleMeta.ruleName) &&
                Objects.equals(inputs, ruleMeta.inputs) &&
                Objects.equals(outputs, ruleMeta.outputs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ruleName, inputs, outputs);
    }

    @Override
    public String toString() {
        return "RuleMeta{" +
                "ruleName='" + ruleName + '\'' +
                ", inputs=" + inputs +
                ", outputs=" + outputs +
                '}';
    }
}
