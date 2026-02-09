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
package org.drools.benchmark.waltzdb;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Utility to track rule execution timing for WaltzDB benchmark.
 */
public class RuleTimingTracker {

    private static RuleTimingTracker instance;
    private PrintWriter writer;
    private boolean initialized = false;
    private Long reverseEdgesStartTime = null;
    private Long doneReversingEndTime = null;

    // Output directory name
    private String outputDir = "timing_logs";

    private RuleTimingTracker() {
    }

    public static synchronized RuleTimingTracker getInstance() {
        if (instance == null) {
            instance = new RuleTimingTracker();
        }
        return instance;
    }

    /**
     * Initialize a new timing file for this benchmark run (called automatically).
     */
    private synchronized void initIfNeeded() {
        if (!initialized) {
            try {
                // Get the resource directory path based on class location
                String resourcePath = getResourceDirectoryPath();
                File dir = new File(resourcePath, outputDir);
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                // Create unique filename with timestamp
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS");
                String timestamp = dateFormat.format(new Date());
                String filename = dir.getAbsolutePath() + File.separator + "waltzdb_timing_" + timestamp + ".txt";

                writer = new PrintWriter(new BufferedWriter(new FileWriter(filename, true)));
                initialized = true;

                System.out.println("Timing log file created: " + filename);
            } catch (IOException e) {
                System.err.println("Failed to create timing log file: " + e.getMessage());
            }
        }
    }

    /**
     * Get the resource directory path for the waltzdb package.
     */
    private String getResourceDirectoryPath() {
        // Try to locate the source directory
        String classPath = RuleTimingTracker.class.getProtectionDomain().getCodeSource().getLocation().getPath();

        // Decode URL encoding (spaces, etc.)
        try {
            classPath = java.net.URLDecoder.decode(classPath, "UTF-8");
        } catch (Exception e) {
            // Ignore decoding errors
        }

        // If running from target/classes, go back to src/main/resources
        if (classPath.contains("target/classes") || classPath.contains("target\\classes")) {
            String basePath = classPath.replace("target/classes/", "").replace("target\\classes\\", "")
                    .replace("target/classes", "").replace("target\\classes", "");
            // Remove leading slash on Windows if present
            if (basePath.startsWith("/") && basePath.length() > 2 && basePath.charAt(2) == ':') {
                basePath = basePath.substring(1);
            }
            return basePath + "src/main/resources/org/drools/benchmark/waltzdb/timing_logs/";
        }

        // Fallback to current directory
        return "timing_logs";
    }

    /**
     * Check if start time has already been recorded.
     */
    public synchronized Boolean hasStarted() {
        return reverseEdgesStartTime != null;
    }

    /**
     * Check if end time has already been recorded.
     */
    public synchronized Boolean hasEnded() {
        return doneReversingEndTime != null;
    }

    public synchronized void recordReverseEdgesStart() {
        if (reverseEdgesStartTime == null) {
            initIfNeeded();
            reverseEdgesStartTime = System.currentTimeMillis();
        }
    }

    public synchronized void recordDoneReversingEnd() {
        if (reverseEdgesStartTime != null && doneReversingEndTime == null) {
            doneReversingEndTime = System.currentTimeMillis();
            writeTuple();
            close();
        }
    }

    private void writeTuple() {
        if (writer != null && reverseEdgesStartTime != null && doneReversingEndTime != null) {
            writer.println("(" + reverseEdgesStartTime + ", " + doneReversingEndTime + ")");
            writer.flush();
        }
    }

    public synchronized void close() {
        if (writer != null) {
            writer.close();
            writer = null;
        }
    }
}
