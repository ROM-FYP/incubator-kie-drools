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
import org.drools.core.impl.RuleBaseFactory;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.definition.KiePackage;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;

import java.util.*;
import java.util.concurrent.Callable;

/**
 * Executes a single phase of the WaltzDB algorithm.
 * Each phase uses a phase-specific DRL file and returns collected facts.
 */
public class PhaseExecutor implements Callable<PhaseResult> {

    private final int partitionId;
    private final String drlResource;
    private final List<?> inputFacts;
    private final InternalKnowledgeBase kbase;

    public PhaseExecutor(int partitionId, String drlResource, List<?> inputFacts) {
        this.partitionId = partitionId;
        this.drlResource = drlResource;
        this.inputFacts = inputFacts;
        this.kbase = buildKnowledgeBase();
    }

    private InternalKnowledgeBase buildKnowledgeBase() {
        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(ResourceFactory.newClassPathResource(drlResource,
                PhaseExecutor.class), ResourceType.DRL);

        if (kbuilder.hasErrors()) {
            throw new RuntimeException("DRL compilation errors in " + drlResource + ": " + kbuilder.getErrors());
        }

        Collection<KiePackage> pkgs = kbuilder.getKnowledgePackages();

        KieBaseConfiguration kbaseConfiguration = RuleBaseFactory.newKnowledgeBaseConfiguration();
        kbaseConfiguration.setProperty("drools.removeIdentities", "true");

        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase(kbaseConfiguration);
        kbase.addPackages(pkgs);
        return kbase;
    }

    @Override
    public PhaseResult call() {
        KieSession ksession = kbase.newKieSession();
        PhaseResult result = new PhaseResult(partitionId);

        try {
            // Insert all input facts
            for (Object fact : inputFacts) {
                ksession.insert(fact);
            }

            // Fire all rules until completion
            long rulesFired = ksession.fireAllRules();
            result.setRulesFired(rulesFired);

            // Collect output facts by type
            for (Object fact : ksession.getObjects()) {
                if (fact instanceof Edge) {
                    result.addEdge((Edge) fact);
                } else if (fact instanceof Junction) {
                    result.addJunction((Junction) fact);
                } else if (fact instanceof Line) {
                    result.addLine((Line) fact);
                } else if (fact instanceof Label) {
                    result.addLabel((Label) fact);
                } else if (fact instanceof EdgeLabel) {
                    result.addEdgeLabel((EdgeLabel) fact);
                }
            }

            return result;
        } finally {
            ksession.dispose();
        }
    }
}
