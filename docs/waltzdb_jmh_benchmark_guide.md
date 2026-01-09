# WaltzDB JMH Benchmark - Research-Grade Experimental Guide

> [!IMPORTANT]
> This guide provides research-grade benchmarking using JMH (Java Microbenchmark Harness) - the industry standard for Java performance measurement used in academic research and production systems.

---

## Table of Contents
1. [Overview](#overview)
2. [Why JMH?](#why-jmh)
3. [Quick Start](#quick-start)
4. [Full Experiment Workflow](#full-experiment-workflow)
5. [Understanding JMH Output](#understanding-jmh-output)
6. [Memory Profiling](#memory-profiling)
7. [Cross-Branch Comparison](#cross-branch-comparison)
8. [Troubleshooting](#troubleshooting)

---

## Overview

### What This Benchmark Measures
- **Execution time** for WaltzDB rule engine operations
- **Memory allocation** per operation (with GC profiler)
- **GC overhead** during execution

### Key Files

| File | Location | Purpose |
|------|----------|---------|
| `WaltzDbJmhBenchmark.java` | `drools-examples/src/test/java/org/drools/benchmark/waltzdb/` | Main JMH benchmark class |
| `BenchmarkRunner.java` | Same directory | Programmatic runner with CLI options |

### JMH Configuration

| Setting | Value | Rationale |
|---------|-------|-----------|
| `@Fork(5)` | 5 JVM processes | Fresh JVM state for each fork |
| `@Warmup(iterations = 10)` | 10 iterations discarded | JIT compilation completes before measurement |
| `@Measurement(iterations = 50)` | 50 iterations measured | Statistically significant sample |
| `@BenchmarkMode(Mode.SingleShotTime)` | Single execution time | Measures one execution per iteration |
| `@OutputTimeUnit(TimeUnit.MILLISECONDS)` | Milliseconds | Human-readable output |

---

## Why JMH?

### Problems with Manual Benchmarking

| Issue | Manual Approach | JMH Solution |
|-------|-----------------|--------------|
| **Timer precision** | `System.currentTimeMillis()` (~10ms resolution) | `System.nanoTime()` with drift compensation |
| **JIT Warmup** | First iterations include compilation | `@Warmup` discards warmup iterations |
| **JVM State** | Memory accumulates across iterations | `@Fork` runs in fresh JVM |
| **Dead Code** | JVM may optimize away code | `Blackhole.consume()` prevents elimination |
| **Statistics** | Manual calculation | Built-in mean, CI, percentiles |

### Verified Warmup Effect

Our test run showed:
```
Warmup Iteration 1: 525.072 ms/op  ← JIT not compiled
Warmup Iteration 2: 403.305 ms/op  ← JIT partially compiled
Measurement mean:   170.321 ms/op  ← JIT fully compiled (3x faster!)
```

---

## Quick Start

### Prerequisites

```bash
cd /home/maheshdila/mahesh/research/incubator-kie-drools

# Compile the benchmark
mvn clean compile test-compile -f drools-examples/pom.xml -DskipTests

# Build classpath
mvn dependency:build-classpath -f drools-examples/pom.xml \
    -DincludeScope=test -Dmdep.outputFile=/tmp/jmh_cp.txt -q
```

### Quick Test (5 minutes)

```bash
java -cp "drools-examples/target/test-classes:drools-examples/target/classes:$(cat /tmp/jmh_cp.txt)" \
    org.openjdk.jmh.Main WaltzDbJmhBenchmark \
    -f 1 -wi 2 -i 5 -bm ss -tu ms
```

### Full Research Run (30+ minutes)

```bash
java -cp "drools-examples/target/test-classes:drools-examples/target/classes:$(cat /tmp/jmh_cp.txt)" \
    org.openjdk.jmh.Main WaltzDbJmhBenchmark \
    -f 5 -wi 10 -i 50 -bm ss -tu ms \
    -rff results.csv -rf csv -prof gc
```

---

## Full Experiment Workflow

### Step 1: System Isolation (Optional but Recommended)

```bash
# Set CPU to performance mode
sudo cpupower frequency-set -g performance

# Disable swap
sudo swapoff -a

# Drop caches
sudo sync && echo 3 | sudo tee /proc/sys/vm/drop_caches
```

### Step 2: Compile on Target Branch

```bash
cd /home/maheshdila/mahesh/research/incubator-kie-drools

# Checkout your branch
git checkout your_branch_name

# Clean compile
mvn clean compile test-compile -f drools-examples/pom.xml -DskipTests -q

# Build classpath
mvn dependency:build-classpath -f drools-examples/pom.xml \
    -DincludeScope=test -Dmdep.outputFile=/tmp/jmh_cp.txt -q
```

### Step 3: Run Benchmark

```bash
# Create results directory
mkdir -p ~/jmh_results

# Run with GC profiler
java -Xms512m -Xmx2g \
    -cp "drools-examples/target/test-classes:drools-examples/target/classes:$(cat /tmp/jmh_cp.txt)" \
    org.openjdk.jmh.Main WaltzDbJmhBenchmark \
    -f 5 -wi 10 -i 50 -bm ss -tu ms \
    -rff ~/jmh_results/branch_name_results.csv -rf csv \
    -prof gc
```

### Step 4: Restore System

```bash
sudo cpupower frequency-set -g powersave
sudo swapon -a
```

---

## Understanding JMH Output

### Console Output

```
Benchmark                          (dataFile)  Mode  Cnt    Score     Error  Units
WaltzDbJmhBenchmark.runWaltzDb  waltzdb16.dat    ss  250  168.432 ± 12.567  ms/op
```

| Column | Meaning |
|--------|---------|
| `Benchmark` | Benchmark method name |
| `(dataFile)` | Parameter value |
| `Mode` | `ss` = SingleShotTime |
| `Cnt` | Total measurements (forks × iterations) |
| `Score` | Mean execution time |
| `Error` | 99.9% confidence interval |
| `Units` | Time unit (ms/op) |

### CSV Output Format

The CSV file contains detailed results:
```csv
"Benchmark","Mode","Threads","Samples","Score","Score Error (99.9%)","Unit"
"WaltzDbJmhBenchmark.runWaltzDb","ss",1,250,168.432,12.567,"ms/op"
```

### GC Profiler Output (with `-prof gc`)

```
WaltzDbJmhBenchmark.runWaltzDb:gc.alloc.rate.norm    423456.789 B/op
WaltzDbJmhBenchmark.runWaltzDb:gc.count              15 counts
WaltzDbJmhBenchmark.runWaltzDb:gc.time               234 ms
```

| Metric | Meaning |
|--------|---------|
| `gc.alloc.rate.norm` | Bytes allocated per operation |
| `gc.count` | Number of GC events |
| `gc.time` | Total time spent in GC |

---

## Memory Profiling

### Enable GC Profiler

```bash
java -cp "..." org.openjdk.jmh.Main WaltzDbJmhBenchmark -prof gc
```

### Key Memory Metrics

| Metric | What It Tells You |
|--------|-------------------|
| `gc.alloc.rate.norm` | Memory efficiency (lower is better) |
| `gc.count` | GC frequency (lower is better) |
| `gc.time` | GC overhead (lower is better) |

### Memory Analysis with JFR (Advanced)

```bash
java -cp "..." org.openjdk.jmh.Main WaltzDbJmhBenchmark \
    -prof "jfr:dir=jfr_output;settings=profile"
```

Then analyze with Java Mission Control:
```bash
jmc jfr_output/*.jfr
```

---

## Cross-Branch Comparison

### Automated Script for 4 Branches

```bash
#!/bin/bash
BRANCHES=("base_exp_no_print" "base_exp_with_print" "mod_exp_no_print" "mod_exp_with_print")
RESULTS_DIR=~/jmh_results
PROJECT=/home/maheshdila/mahesh/research/incubator-kie-drools

mkdir -p $RESULTS_DIR

for BRANCH in "${BRANCHES[@]}"; do
    echo "=== Running benchmark for: $BRANCH ==="
    
    cd $PROJECT
    git checkout $BRANCH
    mvn clean compile test-compile -f drools-examples/pom.xml -DskipTests -q
    mvn dependency:build-classpath -f drools-examples/pom.xml \
        -DincludeScope=test -Dmdep.outputFile=/tmp/jmh_cp.txt -q
    
    java -Xms512m -Xmx2g \
        -cp "drools-examples/target/test-classes:drools-examples/target/classes:$(cat /tmp/jmh_cp.txt)" \
        org.openjdk.jmh.Main WaltzDbJmhBenchmark \
        -f 5 -wi 10 -i 50 -bm ss -tu ms \
        -rff $RESULTS_DIR/${BRANCH}_results.csv -rf csv -prof gc
    
    echo "=== Completed: $BRANCH ==="
    sleep 30  # Cool-down
done

echo "All experiments complete! Results in: $RESULTS_DIR"
```

### Comparing Results

After running all branches, compare the CSV files:

```python
import pandas as pd

branches = ["base_exp_no_print", "base_exp_with_print", 
            "mod_exp_no_print", "mod_exp_with_print"]

for branch in branches:
    df = pd.read_csv(f"~/jmh_results/{branch}_results.csv")
    print(f"{branch}: {df['Score'].values[0]:.2f} ± {df['Score Error (99.9%)'].values[0]:.2f} ms")
```

---

## JMH Command Reference

| Flag | Description | Example |
|------|-------------|---------|
| `-f N` | Number of forks | `-f 5` |
| `-wi N` | Warmup iterations | `-wi 10` |
| `-i N` | Measurement iterations | `-i 50` |
| `-bm MODE` | Benchmark mode | `-bm ss` (SingleShot) |
| `-tu UNIT` | Time unit | `-tu ms` |
| `-rf FORMAT` | Result format | `-rf csv` |
| `-rff FILE` | Result file | `-rff results.csv` |
| `-prof NAME` | Profiler | `-prof gc` |
| `-p PARAM=VAL` | Parameter | `-p dataFile=waltzdb8.dat` |
| `-t N` | Threads | `-t 1` |

### Available Profilers

```bash
java -cp "..." org.openjdk.jmh.Main -lprof
```

Common profilers:
- `gc` - Garbage collection statistics
- `jfr` - Java Flight Recorder
- `stack` - Stack trace sampler
- `comp` - JIT compilation profiler

---

## Troubleshooting

### Build Errors

**Issue**: Maven compilation fails
```bash
mvn clean install -DskipTests  # Rebuild entire project first
```

**Issue**: JMH class not found
```bash
mvn test-compile -f drools-examples/pom.xml  # Ensure test classes are compiled
```

### Runtime Errors

**Issue**: OutOfMemoryError
```bash
java -Xms1g -Xmx4g -cp "..." org.openjdk.jmh.Main ...
```

**Issue**: GC profiler not available
```bash
java -cp "..." org.openjdk.jmh.Main -lprof  # List available profilers
```

### Low Confidence Results

**Issue**: High error margins
- Increase forks: `-f 10`
- Increase iterations: `-i 100`
- Ensure system isolation

---

## Statistical Interpretation

### Significance Testing

If two benchmarks have **non-overlapping confidence intervals**, they are statistically different at 99.9% confidence.

Example:
- Branch A: 150.5 ± 8.2 ms
- Branch B: 180.3 ± 7.5 ms
- Range A: [142.3, 158.7]
- Range B: [172.8, 187.8]
- **No overlap → Statistically significant difference**

### Effect Size

Calculate percentage difference:
```
% diff = (Branch_B - Branch_A) / Branch_A × 100
% diff = (180.3 - 150.5) / 150.5 × 100 = 19.8%
```

---

## References

- [JMH Official Documentation](https://github.com/openjdk/jmh)
- [JMH Samples](https://github.com/openjdk/jmh/tree/master/jmh-samples)
- [Oracle JMH Guide](https://www.oracle.com/technical-resources/articles/java/architect-benchmarking.html)

---

**End of Guide**
