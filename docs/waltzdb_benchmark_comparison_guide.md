# WaltzDB Benchmark Comparison Guide

> [!IMPORTANT]
> This guide provides commands to compare **Partitioned vs Non-Partitioned** WaltzDB execution.

---

## Prerequisites

```bash
cd /home/maheshdila/mahesh/research/incubator-kie-drools

# Compile both main and test classes
mvn clean compile test-compile -f drools-examples/pom.xml -DskipTests -q

# Build classpath
mvn dependency:build-classpath -f drools-examples/pom.xml \
    -DincludeScope=test -Dmdep.outputFile=/tmp/cp.txt -q
```

---

## Option 1: Quick Standalone Tests (5-10 minutes each)

### Non-Partitioned (Baseline)

```bash
java -cp "drools-examples/target/classes:$(cat /tmp/cp.txt)" \
    org.drools.benchmark.waltzdb.WaltzDbBenchmark
```

### Partitioned (Parallel)

```bash
java -cp "drools-examples/target/classes:$(cat /tmp/cp.txt)" \
    org.drools.benchmark.waltzdb.partition.WaltzDbPartitionedBenchmark
```

---

## Option 2: JMH Benchmark (Research-Grade, 30+ mins each)

### Non-Partitioned JMH Benchmark

```bash
java -Xms512m -Xmx4g \
    -cp "drools-examples/target/test-classes:drools-examples/target/classes:$(cat /tmp/cp.txt)" \
    org.openjdk.jmh.Main WaltzDbJmhBenchmark \
    -f 3 -wi 5 -i 20 -bm ss -tu ms \
    -rff ~/waltzdb_baseline_results.csv -rf csv -prof gc
```

### Partitioned JMH Benchmark (All Thread Counts)

```bash
java -Xms512m -Xmx4g \
    -cp "drools-examples/target/test-classes:drools-examples/target/classes:$(cat /tmp/cp.txt)" \
    org.openjdk.jmh.Main WaltzDbPartitionedJmhBenchmark \
    -f 3 -wi 5 -i 20 -bm ss -tu ms \
    -rff ~/waltzdb_partitioned_results.csv -rf csv -prof gc
```

### Partitioned JMH (Specific Thread Count)

```bash
# 1 Thread (for fair comparison with baseline)
java -Xms512m -Xmx4g \
    -cp "drools-examples/target/test-classes:drools-examples/target/classes:$(cat /tmp/cp.txt)" \
    org.openjdk.jmh.Main WaltzDbPartitionedJmhBenchmark \
    -p numThreads=1 \
    -f 3 -wi 5 -i 20 -bm ss -tu ms

# 2 Threads
java -Xms512m -Xmx4g \
    -cp "drools-examples/target/test-classes:drools-examples/target/classes:$(cat /tmp/cp.txt)" \
    org.openjdk.jmh.Main WaltzDbPartitionedJmhBenchmark \
    -p numThreads=2 \
    -f 3 -wi 5 -i 20 -bm ss -tu ms

# 4 Threads
java -Xms512m -Xmx4g \
    -cp "drools-examples/target/test-classes:drools-examples/target/classes:$(cat /tmp/cp.txt)" \
    org.openjdk.jmh.Main WaltzDbPartitionedJmhBenchmark \
    -p numThreads=4 \
    -f 3 -wi 5 -i 20 -bm ss -tu ms

# 8 Threads
java -Xms512m -Xmx4g \
    -cp "drools-examples/target/test-classes:drools-examples/target/classes:$(cat /tmp/cp.txt)" \
    org.openjdk.jmh.Main WaltzDbPartitionedJmhBenchmark \
    -p numThreads=8 \
    -f 3 -wi 5 -i 20 -bm ss -tu ms
```

---

## Option 3: Automated Comparison Script

Save as `run_comparison.sh`:

```bash
#!/bin/bash

PROJECT=/home/maheshdila/mahesh/research/incubator-kie-drools
RESULTS_DIR=~/waltzdb_comparison_results
mkdir -p $RESULTS_DIR

cd $PROJECT

# Build classpath
mvn dependency:build-classpath -f drools-examples/pom.xml \
    -DincludeScope=test -Dmdep.outputFile=/tmp/cp.txt -q

CP="drools-examples/target/test-classes:drools-examples/target/classes:$(cat /tmp/cp.txt)"

echo "=========================================="
echo "  WaltzDB Benchmark Comparison"
echo "  Started: $(date)"
echo "=========================================="

# 1. Run Non-Partitioned Baseline
echo ""
echo ">>> Running NON-PARTITIONED (Baseline)..."
java -Xms512m -Xmx4g -cp "$CP" \
    org.openjdk.jmh.Main WaltzDbJmhBenchmark \
    -f 3 -wi 5 -i 20 -bm ss -tu ms \
    -rff $RESULTS_DIR/baseline.csv -rf csv -prof gc

sleep 30  # Cool-down

# 2. Run Partitioned with 1 Thread
echo ""
echo ">>> Running PARTITIONED with 1 thread..."
java -Xms512m -Xmx4g -cp "$CP" \
    org.openjdk.jmh.Main WaltzDbPartitionedJmhBenchmark \
    -p numThreads=1 \
    -f 3 -wi 5 -i 20 -bm ss -tu ms \
    -rff $RESULTS_DIR/partitioned_1t.csv -rf csv -prof gc

sleep 30

# 3. Run Partitioned with 2 Threads
echo ""
echo ">>> Running PARTITIONED with 2 threads..."
java -Xms512m -Xmx4g -cp "$CP" \
    org.openjdk.jmh.Main WaltzDbPartitionedJmhBenchmark \
    -p numThreads=2 \
    -f 3 -wi 5 -i 20 -bm ss -tu ms \
    -rff $RESULTS_DIR/partitioned_2t.csv -rf csv -prof gc

sleep 30

# 4. Run Partitioned with 4 Threads
echo ""
echo ">>> Running PARTITIONED with 4 threads..."
java -Xms512m -Xmx4g -cp "$CP" \
    org.openjdk.jmh.Main WaltzDbPartitionedJmhBenchmark \
    -p numThreads=4 \
    -f 3 -wi 5 -i 20 -bm ss -tu ms \
    -rff $RESULTS_DIR/partitioned_4t.csv -rf csv -prof gc

sleep 30

# 5. Run Partitioned with 8 Threads
echo ""
echo ">>> Running PARTITIONED with 8 threads..."
java -Xms512m -Xmx4g -cp "$CP" \
    org.openjdk.jmh.Main WaltzDbPartitionedJmhBenchmark \
    -p numThreads=8 \
    -f 3 -wi 5 -i 20 -bm ss -tu ms \
    -rff $RESULTS_DIR/partitioned_8t.csv -rf csv -prof gc

echo ""
echo "=========================================="
echo "  Comparison Complete!"
echo "  Results in: $RESULTS_DIR"
echo "  Finished: $(date)"
echo "=========================================="
```

Run the script:
```bash
chmod +x run_comparison.sh
./run_comparison.sh
```

---

## Analyzing Results

### View CSV Results

```bash
# View all results
cat ~/waltzdb_comparison_results/baseline.csv
cat ~/waltzdb_comparison_results/partitioned_*.csv
```

### Python Comparison Script

```python
import pandas as pd

results_dir = "~/waltzdb_comparison_results"

# Load results
baseline = pd.read_csv(f"{results_dir}/baseline.csv")
p1 = pd.read_csv(f"{results_dir}/partitioned_1t.csv")
p2 = pd.read_csv(f"{results_dir}/partitioned_2t.csv")
p4 = pd.read_csv(f"{results_dir}/partitioned_4t.csv")
p8 = pd.read_csv(f"{results_dir}/partitioned_8t.csv")

# Extract scores
base_score = baseline['Score'].values[0]
scores = {
    'Baseline': base_score,
    '1 Thread': p1['Score'].values[0],
    '2 Threads': p2['Score'].values[0],
    '4 Threads': p4['Score'].values[0],
    '8 Threads': p8['Score'].values[0],
}

print("=== WaltzDB Benchmark Comparison ===")
print(f"{'Configuration':<15} {'Time (ms)':<15} {'Speedup':<10}")
print("-" * 40)
for name, score in scores.items():
    speedup = base_score / score
    print(f"{name:<15} {score:<15.2f} {speedup:<10.2f}x")
```

---

## Expected Output Format

```
=== WaltzDB Benchmark Comparison ===
Configuration   Time (ms)       Speedup   
----------------------------------------
Baseline        180.43          1.00x
1 Thread        185.21          0.97x
2 Threads       98.45           1.83x
4 Threads       55.32           3.26x
8 Threads       38.91           4.64x
```

---

## Key Metrics to Compare

| Metric | Meaning |
|--------|---------|
| **Score** | Average execution time per operation (lower = better) |
| **Error** | 99.9% confidence interval |
| **gc.alloc.rate.norm** | Memory allocated per operation (lower = better) |
| **gc.count** | GC frequency during benchmark |
| **Speedup** | Baseline Time / Partitioned Time |

---

## JMH Flags Reference

| Flag | Description |
|------|-------------|
| `-f N` | Number of forks (JVM instances) |
| `-wi N` | Warmup iterations |
| `-i N` | Measurement iterations |
| `-bm ss` | SingleShotTime mode |
| `-tu ms` | Time unit = milliseconds |
| `-rff FILE` | Result file path |
| `-rf csv` | Result format = CSV |
| `-prof gc` | Enable GC profiler |
| `-p param=val` | Set benchmark parameter |

---

**End of Guide**
