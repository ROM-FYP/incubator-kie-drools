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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * WaltzDB DRL Analyzer - The "Golden" Integration Test
 * 
 * <p>
 * This analyzer reads a DRL file, parses all rules, builds the dependency
 * graph,
 * and stratifies rules into execution phases using topological sort and SCC
 * analysis.
 * </p>
 * 
 * <p>
 * <b>Usage:</b>
 * </p>
 * 
 * <pre>
 * # Default: WaltzDB DRL
 * java WaltzDbAnalyzer
 * 
 * # Custom DRL file
 * java WaltzDbAnalyzer /path/to/your.drl
 * </pre>
 */
public class WaltzDbAnalyzer {

        // ═══════════════════════════════════════════════════════════════════════════
        // CONFIGURATION - Change this path to analyze different DRL files
        // ═══════════════════════════════════════════════════════════════════════════
        private static final String DEFAULT_DRL_PATH = "/home/maheshdila/mahesh/research/incubator-kie-drools/drools-examples/src/main/resources/org/drools/benchmark/waltzdb/waltzdb.drl";

        // ═══════════════════════════════════════════════════════════════════════════
        // ANSI Color Codes for Beautiful Console Output
        // ═══════════════════════════════════════════════════════════════════════════
        private static final String RESET = "\u001B[0m";
        private static final String BOLD = "\u001B[1m";
        private static final String DIM = "\u001B[2m";

        private static final String RED = "\u001B[31m";
        private static final String GREEN = "\u001B[32m";
        private static final String YELLOW = "\u001B[33m";
        private static final String BLUE = "\u001B[34m";
        private static final String MAGENTA = "\u001B[35m";
        private static final String CYAN = "\u001B[36m";
        private static final String WHITE = "\u001B[37m";

        private static final String BG_BLUE = "\u001B[44m";
        private static final String BG_GREEN = "\u001B[42m";
        private static final String BG_YELLOW = "\u001B[43m";
        private static final String BG_RED = "\u001B[41m";

        private boolean verbose = false;

        public static void main(String[] args) {
                String drlPath = DEFAULT_DRL_PATH;
                boolean verbose = false;

                // Parse arguments
                for (String arg : args) {
                        if (arg.equals("--verbose") || arg.equals("-v")) {
                                verbose = true;
                        } else if (arg.equals("--help") || arg.equals("-h")) {
                                printHelp();
                                return;
                        } else if (!arg.startsWith("-")) {
                                drlPath = arg;
                        }
                }

                try {
                        WaltzDbAnalyzer analyzer = new WaltzDbAnalyzer();
                        analyzer.setVerbose(verbose);
                        analyzer.analyze(drlPath);
                } catch (Exception e) {
                        printError("Analysis failed: " + e.getMessage());
                        e.printStackTrace();
                        System.exit(1);
                }
        }

        private static void printHelp() {
                System.out.println();
                System.out.println(CYAN + BOLD + "DRL Analyzer - Rule Dependency Graph & Phase Stratification Tool"
                                + RESET);
                System.out.println();
                System.out.println(WHITE + "Usage:" + RESET);
                System.out.println("  java WaltzDbAnalyzer [options] [drl-file-path]");
                System.out.println();
                System.out.println(WHITE + "Options:" + RESET);
                System.out.println("  -v, --verbose    Show all rules in detailed output (default: first 10)");
                System.out.println("  -h, --help       Show this help message");
                System.out.println();
                System.out.println(WHITE + "Examples:" + RESET);
                System.out.println("  # Analyze default WaltzDB DRL");
                System.out.println("  java WaltzDbAnalyzer");
                System.out.println();
                System.out.println("  # Analyze with verbose output");
                System.out.println("  java WaltzDbAnalyzer --verbose");
                System.out.println();
                System.out.println("  # Analyze custom DRL file");
                System.out.println("  java WaltzDbAnalyzer /path/to/your.drl");
                System.out.println();
                System.out.println("  # Analyze custom DRL with verbose output");
                System.out.println("  java WaltzDbAnalyzer --verbose /path/to/your.drl");
                System.out.println();
        }

        public void setVerbose(boolean verbose) {
                this.verbose = verbose;
        }

        public void analyze(String drlPath) throws Exception {
                printBanner();

                // ─────────────────────────────────────────────────────────────────────
                // Step 1: Load and validate DRL file
                // ─────────────────────────────────────────────────────────────────────
                printSectionHeader("📂 LOADING DRL FILE");
                String drlContent = loadDrlFile(drlPath);

                // ─────────────────────────────────────────────────────────────────────
                // Step 2: Parse rules
                // ─────────────────────────────────────────────────────────────────────
                printSectionHeader("🔍 PARSING RULES");
                DrlRuleParser parser = new DrlRuleParser();
                List<RuleMeta> rules = parser.parse(drlContent);
                printRuleSummary(rules);

                // ─────────────────────────────────────────────────────────────────────
                // Step 3: Build dependency graph
                // ─────────────────────────────────────────────────────────────────────
                printSectionHeader("🔗 BUILDING DEPENDENCY GRAPH");
                DependencyGraphBuilder graphBuilder = new DependencyGraphBuilder(rules);
                printGraphSummary(graphBuilder, rules);

                // ─────────────────────────────────────────────────────────────────────
                // Step 4: Stratify into phases
                // ─────────────────────────────────────────────────────────────────────
                printSectionHeader("📊 STRATIFYING RULES INTO PHASES");
                Stratifier stratifier = new Stratifier(rules);
                printStratificationResult(stratifier);

                // ─────────────────────────────────────────────────────────────────────
                // Step 5: Print detailed rule information
                // ─────────────────────────────────────────────────────────────────────
                printSectionHeader("📋 DETAILED RULE METADATA");
                printDetailedRules(rules);

                // ─────────────────────────────────────────────────────────────────────
                // Summary
                // ─────────────────────────────────────────────────────────────────────
                printFinalSummary(rules, graphBuilder, stratifier);
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // FILE LOADING
        // ═══════════════════════════════════════════════════════════════════════════

        private String loadDrlFile(String drlPath) throws IOException {
                Path path = Paths.get(drlPath);

                if (!Files.exists(path)) {
                        throw new IOException("DRL file not found: " + drlPath);
                }

                String content = Files.readString(path);
                long lineCount = content.lines().count();
                long byteSize = Files.size(path);

                System.out.println();
                printKeyValue("File Path", drlPath);
                printKeyValue("File Name", path.getFileName().toString());
                printKeyValue("Size", formatBytes(byteSize));
                printKeyValue("Lines", String.valueOf(lineCount));
                printSuccess("File loaded successfully!");

                return content;
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // OUTPUT FORMATTERS
        // ═══════════════════════════════════════════════════════════════════════════

        private void printBanner() {
                System.out.println();
                System.out.println(CYAN + BOLD
                                + "╔══════════════════════════════════════════════════════════════════════════════╗"
                                + RESET);
                System.out.println(CYAN + BOLD
                                + "║                                                                              ║"
                                + RESET);
                System.out.println(CYAN + BOLD + "║   " + WHITE
                                + "██████╗ ██████╗ ██╗         █████╗ ███╗   ██╗ █████╗ ██╗  ██╗   ██╗███████╗" + CYAN
                                + " ║" + RESET);
                System.out.println(CYAN + BOLD + "║   " + WHITE
                                + "██╔══██╗██╔══██╗██║        ██╔══██╗████╗  ██║██╔══██╗██║  ╚██╗ ██╔╝╚══███╔╝" + CYAN
                                + " ║" + RESET);
                System.out.println(CYAN + BOLD + "║   " + WHITE
                                + "██║  ██║██████╔╝██║        ███████║██╔██╗ ██║███████║██║   ╚████╔╝   ███╔╝ " + CYAN
                                + " ║" + RESET);
                System.out.println(CYAN + BOLD + "║   " + WHITE
                                + "██║  ██║██╔══██╗██║        ██╔══██║██║╚██╗██║██╔══██║██║    ╚██╔╝   ███╔╝  " + CYAN
                                + " ║" + RESET);
                System.out.println(CYAN + BOLD + "║   " + WHITE
                                + "██████╔╝██║  ██║███████╗   ██║  ██║██║ ╚████║██║  ██║███████╗██║   ███████╗" + CYAN
                                + " ║" + RESET);
                System.out.println(CYAN + BOLD + "║   " + WHITE
                                + "╚═════╝ ╚═╝  ╚═╝╚══════╝   ╚═╝  ╚═╝╚═╝  ╚═══╝╚═╝  ╚═╝╚══════╝╚═╝   ╚══════╝" + CYAN
                                + " ║" + RESET);
                System.out.println(CYAN + BOLD
                                + "║                                                                              ║"
                                + RESET);
                System.out.println(CYAN + BOLD + "║   " + YELLOW + "Rule Dependency Graph & Phase Stratification Tool"
                                + CYAN
                                + "                         ║" + RESET);
                System.out.println(CYAN + BOLD + "║   " + DIM + "Static analysis for Drools Rule Language (DRL) files"
                                + CYAN
                                + "                      ║" + RESET);
                System.out.println(CYAN + BOLD
                                + "║                                                                              ║"
                                + RESET);
                System.out.println(CYAN + BOLD
                                + "╚══════════════════════════════════════════════════════════════════════════════╝"
                                + RESET);
                System.out.println();
        }

        private void printSectionHeader(String title) {
                System.out.println();
                System.out.println(BLUE + BOLD
                                + "┌──────────────────────────────────────────────────────────────────────────────┐"
                                + RESET);
                System.out
                                .println(BLUE + BOLD + "│  " + WHITE + title + padRight("", 74 - title.length()) + BLUE
                                                + "│" + RESET);
                System.out.println(BLUE + BOLD
                                + "└──────────────────────────────────────────────────────────────────────────────┘"
                                + RESET);
        }

        private void printRuleSummary(List<RuleMeta> rules) {
                System.out.println();
                printSuccess("Parsed " + rules.size() + " rules successfully!");
                System.out.println();

                // Collect unique types
                Set<String> allInputTypes = rules.stream()
                                .flatMap(r -> r.getInputs().stream())
                                .collect(Collectors.toSet());
                Set<String> allOutputTypes = rules.stream()
                                .flatMap(r -> r.getOutputs().stream())
                                .collect(Collectors.toSet());

                printKeyValue("Total Rules", String.valueOf(rules.size()));
                printKeyValue("Unique Input Types", String.valueOf(allInputTypes.size()));
                printKeyValue("Unique Output Types", String.valueOf(allOutputTypes.size()));

                System.out.println();
                System.out.println(DIM + "  Input Types:  " + RESET + CYAN + String.join(", ", allInputTypes) + RESET);
                System.out.println(
                                DIM + "  Output Types: " + RESET + MAGENTA + String.join(", ", allOutputTypes) + RESET);
        }

        private void printGraphSummary(DependencyGraphBuilder graphBuilder, List<RuleMeta> rules) {
                System.out.println();

                int edgeCount = graphBuilder.getGraph().edgeSet().size();
                int vertexCount = graphBuilder.getGraph().vertexSet().size();
                long isolatedCount = rules.stream().filter(graphBuilder::isIsolated).count();

                printKeyValue("Vertices (Rules)", String.valueOf(vertexCount));
                printKeyValue("Edges (Dependencies)", String.valueOf(edgeCount));
                printKeyValue("Isolated Rules", String.valueOf(isolatedCount));

                if (isolatedCount > 0) {
                        System.out.println();
                        System.out.println(YELLOW + "  ⚠ Isolated rules (no dependencies):" + RESET);
                        rules.stream()
                                        .filter(graphBuilder::isIsolated)
                                        .forEach(r -> System.out.println(DIM + "    • " + RESET + r.getRuleName()));
                }
        }

        private void printStratificationResult(Stratifier stratifier) {
                System.out.println();

                List<Set<String>> phases = stratifier.stratify();

                if (stratifier.hasCycles()) {
                        System.out.println(YELLOW + "  ⚠ Cycles detected in dependency graph!" + RESET);
                        System.out.println(DIM + "    Rules in cycles are grouped in the same phase." + RESET);
                        System.out.println();
                }

                printKeyValue("Total Phases", String.valueOf(phases.size()));
                System.out.println();

                // Print each phase with a nice box
                String[] phaseColors = { GREEN, CYAN, MAGENTA, YELLOW, BLUE, RED };

                for (int i = 0; i < phases.size(); i++) {
                        Set<String> phase = phases.get(i);
                        String color = phaseColors[i % phaseColors.length];

                        System.out.println(color + BOLD
                                        + "  ╭─────────────────────────────────────────────────────────────────────────╮"
                                        + RESET);
                        System.out.println(color + BOLD + "  │  PHASE " + (i + 1) + " (" + phase.size() + " rules)"
                                        + padRight("", 60 - String.valueOf(i + 1).length()
                                                        - String.valueOf(phase.size()).length())
                                        + "│"
                                        + RESET);
                        System.out.println(color + BOLD
                                        + "  ├─────────────────────────────────────────────────────────────────────────┤"
                                        + RESET);

                        for (String ruleName : phase) {
                                String displayName = ruleName.length() > 68 ? ruleName.substring(0, 65) + "..."
                                                : ruleName;
                                System.out.println(color + "  │  " + RESET + "• " + displayName
                                                + padRight("", 70 - displayName.length()) + color + "│" + RESET);
                        }

                        System.out.println(color + BOLD
                                        + "  ╰─────────────────────────────────────────────────────────────────────────╯"
                                        + RESET);
                        System.out.println();
                }

                // Print phase dependency info
                System.out.println(
                                DIM + "  ℹ Phase Dependency: Phase N can only execute after Phases 1..(N-1) complete."
                                                + RESET);
                System.out.println(
                                DIM + "  ℹ Rules within the same phase can potentially execute in parallel." + RESET);
        }

        private void printDetailedRules(List<RuleMeta> rules) {
                System.out.println();

                int limit = verbose ? rules.size() : Math.min(10, rules.size());

                if (verbose) {
                        System.out.println(
                                        GREEN + "  📋 Showing ALL " + rules.size() + " rules (verbose mode):" + RESET);
                } else {
                        System.out.println(DIM + "  Showing first 10 rules (use --verbose for all):" + RESET);
                }
                System.out.println();

                for (int i = 0; i < limit; i++) {
                        RuleMeta rule = rules.get(i);
                        System.out.println(WHITE + BOLD + "  " + (i + 1) + ". " + rule.getRuleName() + RESET);
                        System.out.println(GREEN + "     Inputs:  " + RESET
                                        + (rule.getInputs().isEmpty() ? DIM + "(none)" + RESET
                                                        : String.join(", ", rule.getInputs())));
                        System.out.println(MAGENTA + "     Outputs: " + RESET
                                        + (rule.getOutputs().isEmpty() ? DIM + "(none)" + RESET
                                                        : String.join(", ", rule.getOutputs())));
                        System.out.println();
                }

                if (!verbose && rules.size() > 10) {
                        System.out.println(DIM + "  ... and " + (rules.size() - 10) + " more rules" + RESET);
                }
        }

        private void printFinalSummary(List<RuleMeta> rules, DependencyGraphBuilder graphBuilder,
                        Stratifier stratifier) {
                System.out.println();
                System.out.println(GREEN + BOLD
                                + "╔══════════════════════════════════════════════════════════════════════════════╗"
                                + RESET);
                System.out.println(GREEN + BOLD
                                + "║                              ANALYSIS COMPLETE                               ║"
                                + RESET);
                System.out.println(GREEN + BOLD
                                + "╚══════════════════════════════════════════════════════════════════════════════╝"
                                + RESET);
                System.out.println();

                List<Set<String>> phases = stratifier.stratify();

                System.out.println("  " + CYAN + "📊 Summary:" + RESET);
                System.out.println("     • Total rules parsed:      " + BOLD + rules.size() + RESET);
                System.out
                                .println("     • Dependency edges:        " + BOLD
                                                + graphBuilder.getGraph().edgeSet().size() + RESET);
                System.out.println("     • Execution phases:        " + BOLD + phases.size() + RESET);
                System.out.println("     • Contains cycles:         " + BOLD
                                + (stratifier.hasCycles() ? YELLOW + "Yes" : GREEN + "No") + RESET);
                System.out.println();

                // Phase breakdown
                System.out.println("  " + CYAN + "📈 Phase Breakdown:" + RESET);
                for (int i = 0; i < phases.size(); i++) {
                        String bar = "█".repeat(Math.min(phases.get(i).size() * 2, 40));
                        System.out.println(
                                        "     Phase " + (i + 1) + ": " + GREEN + bar + RESET + " "
                                                        + phases.get(i).size() + " rules");
                }
                System.out.println();

                // Potential parallelism
                int maxParallel = phases.stream().mapToInt(Set::size).max().orElse(0);
                System.out.println("  " + CYAN + "⚡ Parallelization Potential:" + RESET);
                System.out.println("     • Max rules per phase:     " + BOLD + maxParallel + RESET);
                System.out.println("     • Sequential phases:       " + BOLD + phases.size() + RESET);
                System.out.println();
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // UTILITY METHODS
        // ═══════════════════════════════════════════════════════════════════════════

        private void printKeyValue(String key, String value) {
                System.out.println("  " + DIM + key + ": " + RESET + BOLD + value + RESET);
        }

        private void printSuccess(String message) {
                System.out.println("  " + GREEN + "✓ " + message + RESET);
        }

        private static void printError(String message) {
                System.out.println("  " + RED + "✗ " + message + RESET);
        }

        private String padRight(String s, int n) {
                if (n <= 0)
                        return "";
                return String.format("%-" + n + "s", s);
        }

        private String formatBytes(long bytes) {
                if (bytes < 1024)
                        return bytes + " B";
                if (bytes < 1024 * 1024)
                        return String.format("%.1f KB", bytes / 1024.0);
                return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
}
