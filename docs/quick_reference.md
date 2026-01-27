# WaltzDB Benchmark Experiment - Quick Reference Card

> [!NOTE]
> This is a condensed version of the complete guide. See `waltzdb_benchmark_experimental_guide.md` for full details, troubleshooting, and explanations.

---

## 🚀 Quick Start (Copy & Paste)

### 1. One-Time Setup

```bash
# Install dependencies (Fedora 42)
sudo dnf update -y
sudo dnf install -y kernel-tools cpupowerutils python3-pip
pip3 install --user numpy scipy pandas matplotlib seaborn

# Create all scripts (copy each script section from the full guide)
# Scripts needed:
#   - ~/setup_benchmark_isolation.sh
#   - ~/restore_system.sh  
#   - ~/run_single_benchmark.sh
#   - ~/run_all_experiments.sh
#   - ~/analyze_results.py
```

### 2. Run Experiment

```bash
# Apply maximum isolation
sudo ~/setup_benchmark_isolation.sh

# Run all 4 branches (50 iterations each, ~15-30 minutes)
taskset -c 0-5 ~/run_all_experiments.sh

# Analyze results
python3 ~/analyze_results.py

# Restore system to normal
sudo ~/restore_system.sh
```

### 3. View Results

```bash
# View statistics summary
cat ~/waltzdb_experiment_results/analysis/statistics_summary.csv

# View pairwise comparisons
cat ~/waltzdb_experiment_results/analysis/pairwise_comparisons.csv

# View visualization
xdg-open ~/waltzdb_experiment_results/analysis/benchmark_visualizations.png
```

---

## 📋 Script Snippets

### Setup Isolation Script (`~/setup_benchmark_isolation.sh`)

```bash
#!/bin/bash
set -e
if [ "$EUID" -ne 0 ]; then echo "Run as root (sudo)"; exit 1; fi

# Set performance governor (Fedora)
cpupower frequency-set -g performance 2>/dev/null || {
    for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
        [ -f "$cpu" ] && echo "performance" > "$cpu"
    done
}

# Disable turbo boost
echo 1 > /sys/devices/system/cpu/intel_pstate/no_turbo 2>/dev/null || true

# Disable swap
swapoff -a

# Drop caches
sync && echo 3 > /proc/sys/vm/drop_caches

echo "✓ Isolation setup complete"
```

### Restore System Script (`~/restore_system.sh`)

```bash
#!/bin/bash
set -e
if [ "$EUID" -ne 0 ]; then echo "Run as root (sudo)"; exit 1; fi

# Restore powersave governor (Fedora)
cpupower frequency-set -g powersave 2>/dev/null || {
    for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
        [ -f "$cpu" ] && echo "powersave" > "$cpu"
    done
}

# Re-enable turbo boost
echo 0 > /sys/devices/system/cpu/intel_pstate/no_turbo 2>/dev/null || true

# Re-enable swap
swapon -a

echo "✓ System restored"
```

### Single Benchmark Runner (`~/run_single_benchmark.sh`)

```bash
#!/bin/bash
set -e

BRANCH=$1
ITERATIONS=$2
WARMUP=${3:-10}
PROJECT_DIR="/home/maheshdila/mahesh/research/incubator-kie-drools"
RESULTS_DIR="$HOME/waltzdb_experiment_results"
OUTPUT_FILE="$RESULTS_DIR/${BRANCH}_results.csv"
CP_FILE="/tmp/waltzdb_classpath_${BRANCH}.txt"

if [ -z "$BRANCH" ] || [ -z "$ITERATIONS" ]; then
    echo "Usage: $0 <branch_name> <num_iterations> [warmup]"
    exit 1
fi

cd "$PROJECT_DIR"
git checkout "$BRANCH"
mvn clean compile -DskipTests -q -f drools-examples/pom.xml

# Build classpath
mvn dependency:build-classpath \
    -f drools-examples/pom.xml \
    -DincludeScope=compile \
    -Dmdep.outputFile="$CP_FILE" -q

CLASSPATH="drools-examples/target/classes:$(cat $CP_FILE)"

mkdir -p "$RESULTS_DIR"
echo "iteration,execution_time_ms" > "$OUTPUT_FILE"

# Warmup
for i in $(seq 1 $WARMUP); do
    java -cp "$CLASSPATH" org.drools.benchmark.waltzdb.WaltzDbBenchmark > /dev/null 2>&1
done

# Actual runs
for i in $(seq 1 $ITERATIONS); do
    OUTPUT=$(java -cp "$CLASSPATH" org.drools.benchmark.waltzdb.WaltzDbBenchmark 2>&1)
    AVG_TIME=$(echo "$OUTPUT" | grep -oP 'Average Time:\s+\K[0-9.]+' || echo "0")
    echo "$i,$AVG_TIME" >> "$OUTPUT_FILE"
    sleep 2
done

rm -f "$CP_FILE"
echo "Results: $OUTPUT_FILE"
```

### Master Runner (`~/run_all_experiments.sh`)

```bash
#!/bin/bash
set -e

ITERATIONS=50
WARMUP=10
BRANCHES=("base_exp_no_print" "base_exp_with_print" "mod_exp_no_print" "mod_exp_with_print")

mkdir -p "$HOME/waltzdb_experiment_results"

for BRANCH in "${BRANCHES[@]}"; do
    echo "Running: $BRANCH"
    taskset -c 0-5 ~/run_single_benchmark.sh "$BRANCH" "$ITERATIONS" "$WARMUP"
    sleep 30  # Cool-down
done

echo "✓ All experiments complete"
```

### Analysis Script (First 50 lines of `~/analyze_results.py`)

```python
#!/usr/bin/env python3
import numpy as np
import pandas as pd
from scipy import stats
import matplotlib.pyplot as plt
import seaborn as sns
from pathlib import Path

RESULTS_DIR = Path.home() / "waltzdb_experiment_results"
OUTPUT_DIR = RESULTS_DIR / "analysis"
CONFIDENCE_LEVEL = 0.95
ALPHA = 0.05
BRANCHES = ["base_exp_no_print", "base_exp_with_print", 
            "mod_exp_no_print", "mod_exp_with_print"]

def load_data(branch_name):
    csv_file = RESULTS_DIR / f"{branch_name}_results.csv"
    df = pd.read_csv(csv_file)
    return df['execution_time_ms'].values

def calculate_statistics(data):
    n = len(data)
    mean = np.mean(data)
    std = np.std(data, ddof=1)
    se = std / np.sqrt(n)
    t_critical = stats.t.ppf((1 + CONFIDENCE_LEVEL) / 2, df=n-1)
    ci_lower = mean - t_critical * se
    ci_upper = mean + t_critical * se
    
    return {
        'n': n, 'mean': mean, 'std': std, 'se': se,
        'cv': (std/mean)*100, 'median': np.median(data),
        'ci_lower': ci_lower, 'ci_upper': ci_upper,
        'margin_error': t_critical * se
    }

# ... (see full guide for complete script)
```

> [!TIP]
> See the complete `waltzdb_benchmark_experimental_guide.md` for the full analysis script with visualizations and detailed statistical comparisons.

---

## 🧪 Test Run (Before Full Experiment)

```bash
# Quick verification with 5 iterations
sudo ~/setup_benchmark_isolation.sh
taskset -c 0-5 ~/run_single_benchmark.sh base_exp_no_print 5 2
cat ~/waltzdb_experiment_results/base_exp_no_print_results.csv
sudo ~/restore_system.sh
```

---

## 📊 Interpreting Results

### Key Metrics

- **Mean**: Average execution time
- **95% CI**: Confidence interval (if CIs don't overlap, difference is likely significant)
- **p-value < 0.05**: Statistically significant difference
- **Cohen's d**: Effect size (small: <0.2, medium: 0.2-0.8, large: >0.8)

### Key Comparisons

1. **Effect of printing in baseline**: `base_exp_no_print` vs `base_exp_with_print`
2. **Effect of printing in modified**: `mod_exp_no_print` vs `mod_exp_with_print` 
3. **Effect of modification (no print)**: `base_exp_no_print` vs `mod_exp_no_print`
4. **Effect of modification (with print)**: `base_exp_with_print` vs `mod_exp_with_print`

---

## ⚙️ Configuration

### Adjust Sample Size

Edit `~/run_all_experiments.sh`:
```bash
ITERATIONS=100  # Change from 50 to 100
```

### Adjust CPU Affinity

```bash
taskset -c 0-7 ~/run_all_experiments.sh  # Use cores 0-7 instead of 0-5
```

### Change Data File (in benchmark code)

Default is `waltzdb16.dat`. Other options: `waltzdb12.dat`, `waltzdb8.dat`, `waltzdb4.dat`

---

## ❗ Common Issues

**Issue**: Maven compilation fails  
**Fix**: `cd /home/maheshdila/mahesh/research/incubator-kie-drools && mvn clean install -DskipTests`

**Issue**: Cannot extract execution time  
**Fix**: Check output format matches `Average Time: XXX`. Adjust grep pattern if needed.

**Issue**: Python packages missing  
**Fix**: `pip3 install --user numpy scipy pandas matplotlib seaborn`  
**Alt**: `sudo dnf install -y python3-numpy python3-scipy python3-pandas python3-matplotlib python3-seaborn`

---

## 📁 Output Structure

```
~/waltzdb_experiment_results/
├── base_exp_no_print_results.csv
├── base_exp_with_print_results.csv
├── mod_exp_no_print_results.csv
├── mod_exp_with_print_results.csv
├── experiment_log.txt
└── analysis/
    ├── statistics_summary.csv
    ├── pairwise_comparisons.csv
    └── benchmark_visualizations.png
```

---

## 🎯 Complete Workflow

```bash
# 1. Setup (one-time, Fedora 42)
sudo dnf install -y kernel-tools cpupowerutils python3-pip
pip3 install --user numpy scipy pandas matplotlib seaborn
# Create all scripts (see full guide)

# 2. Run experiment
sudo ~/setup_benchmark_isolation.sh
taskset -c 0-5 ~/run_all_experiments.sh

# 3. Analyze
python3 ~/analyze_results.py

# 4. Cleanup
sudo ~/restore_system.sh

# 5. Review
cat ~/waltzdb_experiment_results/analysis/statistics_summary.csv
xdg-open ~/waltzdb_experiment_results/analysis/benchmark_visualizations.png
```

---

**For complete details, see: `waltzdb_benchmark_experimental_guide.md`**
