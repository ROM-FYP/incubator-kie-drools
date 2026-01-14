# WaltzDb Trace Mining Logger Documentation

This document explains how to use the optional `MiningTraceLogger` included in the WaltzDb benchmark.

## Overview

The `MiningTraceLogger` captures granular rule execution traces (Rule Name, Timestamp, etc.) and writes them to a CSV file (`waltzdb_trace.csv`). This logging is **optional** and disabled by default to avoid performance overhead during standard benchmark runs.

## Working Directory
**Crucial:** All commands listed below must be executed from the **root directory** of the project (e.g., `~/.../incubator-kie-drools/`).
- Do **NOT** run these commands from inside `drools-examples` or `waltzdb`.
- You verify you are in the right directory if you see the parent `pom.xml` and modules like `drools-examples`.

## Building the Project

Ensure you have Maven installed.

```bash
mvn clean install -DskipTests -pl drools-examples -am
```

## Running the Benchmark

We recommend running the benchmark manually using `java` to avoid conflicts with the existing Maven configuration in `drools-examples`.

### 1. Build the Classpath
First, generate the classpath file. This only needs to be done once (or when dependencies change).

```bash
mvn dependency:build-classpath -pl drools-examples -Dmdep.outputFile=cp.txt
```

### 2. Run with Logging Enabled
To enable the logger, pass the system property `-Dmining.trace=true`.

```bash
java -cp $(cat drools-examples/cp.txt):drools-examples/target/classes -Dmining.trace=true org.drools.benchmark.waltzdb.WaltzDbBenchmark
```

### 3. Run with Logging Disabled (Default)
To run without logging, simply omit the system property.

```bash
java -cp $(cat drools-examples/cp.txt):drools-examples/target/classes org.drools.benchmark.waltzdb.WaltzDbBenchmark
```

**Output:**
If enabled, a file named `waltzdb_trace.csv` will be created in the execution directory.

**CSV Format:**
```csv
CaseID,SequenceNr,Activity,Timestamp
1,1,reverse_edges,1768418320292
...
```

