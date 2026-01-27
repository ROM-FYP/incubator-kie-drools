# WaltzDB Benchmark: Complete Experimental Comparison Guide
## 95% Confidence Interval with Maximum Isolation

> [!IMPORTANT]
> This guide provides a complete, copy-paste ready workflow for comparing 4 experimental branches of the WaltzDB benchmark with statistical rigor (95% CI) and maximum system isolation.

---

## Table of Contents
1. [Overview & Assumptions](#overview--assumptions)
2. [Prerequisites & System Setup](#prerequisites--system-setup)
3. [Maximum Isolation Configuration](#maximum-isolation-configuration)
4. [Experiment Workflow](#experiment-workflow)
5. [Data Collection Scripts](#data-collection-scripts)
6. [Statistical Analysis](#statistical-analysis)
7. [Results Interpretation](#results-interpretation)

---

## Overview & Assumptions

### Experimental Branches
- **`base_exp_no_print`**: Baseline experiment without print statements
- **`base_exp_with_print`**: Baseline experiment with print statements
- **`mod_exp_no_print`**: Modified experiment without print statements
- **`mod_exp_with_print`**: Modified experiment with print statements

### Key Assumptions

1. **System Configuration**:
   - CPU: Intel Core i7-10750H @ 2.60GHz (12 logical cores)
   - Java: 17.0.12 LTS (HotSpot VM)
   - OS: Linux
   - Maven project structure

2. **Statistical Requirements**:
   - **Confidence Level**: 95% (α = 0.05)
   - **Minimum Sample Size**: 30 iterations per branch (for normality via Central Limit Theorem)
   - **Recommended Sample Size**: 50-100 iterations for robust statistics
   - **Warmup Runs**: 10 iterations (discarded, not analyzed)
   - **Statistical Test**: Welch's t-test (does not assume equal variances)

3. **Operating System**:
   - Distribution: Fedora 42
   - Package Manager: DNF
   - Kernel: Linux (with performance tuning support)

4. **Benchmark Characteristics**:
   - Benchmark class: `org.drools.benchmark.waltzdb.WaltzDbBenchmark`
   - Input data: `waltzdb16.dat` (can be changed to waltzdb12.dat, waltzdb8.dat, waltzdb4.dat)
   - Metrics: Execution time per iteration (milliseconds)

5. **Isolation Requirements**:
   - Dedicated CPU cores
   - CPU frequency scaling disabled (performance governor)
   - Minimal background processes
   - Swapping disabled
   - Network and I/O activity minimized
   - JVM optimizations for deterministic behavior

---

## Prerequisites & System Setup

### 1. Install Required Tools

```bash
# Update system (Fedora 42)
sudo dnf update -y

# Install system performance tools
sudo dnf install -y kernel-tools cpupowerutils stress-ng htop perf

# Install Python for statistical analysis
sudo dnf install -y python3 python3-pip
pip3 install --user numpy scipy pandas matplotlib seaborn
```

### 2. Check Current System State

```bash
# Check CPU information
lscpu

# Check current CPU governor
cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor

# Check swap status
swapon --show

# Check active processes
ps aux | wc -l
```

---

## Maximum Isolation Configuration

### Step 1: Create Isolation Setup Script

Create a script to configure maximum isolation:

```bash
cat > ~/setup_benchmark_isolation.sh << 'EOF'
#!/bin/bash
# WaltzDB Benchmark - Maximum Isolation Setup Script

set -e

echo "=========================================="
echo "Setting up maximum isolation for benchmarking"
echo "=========================================="

# Require sudo
if [ "$EUID" -ne 0 ]; then 
    echo "Please run as root (sudo)"
    exit 1
fi

# 1. Set CPU governor to performance mode (Fedora)
echo "1. Setting CPU governor to performance mode..."
# Use cpupower on Fedora
cpupower frequency-set -g performance &>/dev/null || {
    # Fallback to manual setting
    for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
        [ -f "$cpu" ] && echo "performance" > "$cpu"
    done
}
echo "   ✓ CPU governor set to performance"

# 2. Disable CPU frequency scaling (turbo boost)
echo "2. Disabling Turbo Boost..."
echo 1 > /sys/devices/system/cpu/intel_pstate/no_turbo 2>/dev/null || \
    echo 0 > /sys/devices/system/cpu/cpufreq/boost 2>/dev/null || \
    echo "   ⚠ Could not disable turbo boost (may not be available)"

# 3. Disable swap
echo "3. Disabling swap..."
swapoff -a
echo "   ✓ Swap disabled"

# 4. Drop caches
echo "4. Dropping filesystem caches..."
sync
echo 3 > /proc/sys/vm/drop_caches
echo "   ✓ Caches dropped"

# 5. Set CPU affinity for isolcpus (optional - requires kernel parameter)
# This would require reboot with isolcpus=2,3,4,5 in GRUB
# For now, we'll just recommend using taskset

echo ""
echo "=========================================="
echo "Isolation setup complete!"
echo "=========================================="
echo ""
echo "Additional recommendations:"
echo "1. Close all unnecessary applications"
echo "2. Disable network (if not needed): sudo nmcli networking off"
echo "3. Stop unnecessary services: sudo systemctl stop bluetooth cups"
echo "4. Use 'taskset' to pin benchmark to specific cores"
echo ""
echo "To restore normal system:"
echo "  sudo ~/restore_system.sh"
echo ""
EOF

chmod +x ~/setup_benchmark_isolation.sh
```

### Step 2: Create System Restoration Script

```bash
cat > ~/restore_system.sh << 'EOF'
#!/bin/bash
# WaltzDB Benchmark - Restore System Script

set -e

echo "=========================================="
echo "Restoring system to normal state"
echo "=========================================="

# Require sudo
if [ "$EUID" -ne 0 ]; then 
    echo "Please run as root (sudo)"
    exit 1
fi

# 1. Restore CPU governor to powersave (Fedora)
echo "1. Restoring CPU governor to powersave mode..."
# Use cpupower on Fedora
cpupower frequency-set -g powersave &>/dev/null || {
    # Fallback to manual setting
    for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
        [ -f "$cpu" ] && echo "powersave" > "$cpu"
    done
}
echo "   ✓ CPU governor restored"

# 2. Re-enable Turbo Boost
echo "2. Re-enabling Turbo Boost..."
echo 0 > /sys/devices/system/cpu/intel_pstate/no_turbo 2>/dev/null || \
    echo 1 > /sys/devices/system/cpu/cpufreq/boost 2>/dev/null || \
    echo "   ⚠ Could not re-enable turbo boost"

# 3. Re-enable swap
echo "3. Re-enabling swap..."
swapon -a
echo "   ✓ Swap re-enabled"

echo ""
echo "=========================================="
echo "System restored to normal state"
echo "=========================================="
EOF

chmod +x ~/restore_system.sh
```

### Step 3: Apply Isolation

```bash
# Apply maximum isolation (requires sudo)
sudo ~/setup_benchmark_isolation.sh

# Verify isolation
cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor | sort -u  # Should show "performance"
swapon --show  # Should show nothing
```

---

## Experiment Workflow

### Step 1: Prepare Workspace

```bash
# Navigate to project directory
cd /home/maheshdila/mahesh/research/incubator-kie-drools

# Create results directory
mkdir -p ~/waltzdb_experiment_results
cd ~/waltzdb_experiment_results

# Create experiment log
echo "WaltzDB Benchmark Comparison Experiment" > experiment_log.txt
echo "Date: $(date)" >> experiment_log.txt
echo "System: $(uname -a)" >> experiment_log.txt
echo "CPU: $(cat /proc/cpuinfo | grep 'model name' | head -n 1 | cut -d':' -f2)" >> experiment_log.txt
echo "Java: $(java -version 2>&1 | head -n 1)" >> experiment_log.txt
echo "=====================================" >> experiment_log.txt
```

### Step 2: Create Benchmark Runner Script

Create a script to run the benchmark for each branch:

```bash
cat > ~/run_single_benchmark.sh << 'EOF'
#!/bin/bash
# WaltzDB Benchmark - Single Branch Runner
# Usage: ./run_single_benchmark.sh <branch_name> <num_iterations> <warmup_iterations>

set -e

BRANCH=$1
ITERATIONS=$2
WARMUP=${3:-10}
PROJECT_DIR="/home/maheshdila/mahesh/research/incubator-kie-drools"
RESULTS_DIR="$HOME/waltzdb_experiment_results"
OUTPUT_FILE="$RESULTS_DIR/${BRANCH}_results.csv"
CP_FILE="/tmp/waltzdb_classpath_${BRANCH}.txt"

if [ -z "$BRANCH" ] || [ -z "$ITERATIONS" ]; then
    echo "Usage: $0 <branch_name> <num_iterations> [warmup_iterations]"
    exit 1
fi

echo "=========================================="
echo "Running benchmark for branch: $BRANCH"
echo "Iterations: $ITERATIONS (+ $WARMUP warmup)"
echo "=========================================="

# Navigate to project
cd "$PROJECT_DIR"

# Checkout branch
echo "Checking out branch: $BRANCH"
git checkout "$BRANCH"

# Clean and compile
echo "Cleaning and compiling..."
mvn clean compile -DskipTests -q -f drools-examples/pom.xml

# Build classpath once
echo "Building classpath..."
mvn dependency:build-classpath \
    -f drools-examples/pom.xml \
    -DincludeScope=compile \
    -Dmdep.outputFile="$CP_FILE" -q

# Read classpath
CLASSPATH="drools-examples/target/classes:$(cat $CP_FILE)"

# Create CSV header
mkdir -p "$RESULTS_DIR"
echo "iteration,execution_time_ms" > "$OUTPUT_FILE"

# Run warmup iterations
echo "Running $WARMUP warmup iterations..."
for i in $(seq 1 $WARMUP); do
    echo -n "  Warmup $i/$WARMUP... "
    java -cp "$CLASSPATH" org.drools.benchmark.waltzdb.WaltzDbBenchmark > /dev/null 2>&1
    echo "done"
done

# Run actual benchmark iterations
echo "Running $ITERATIONS benchmark iterations..."
for i in $(seq 1 $ITERATIONS); do
    echo -n "  Iteration $i/$ITERATIONS... "
    
    # Run benchmark and capture output
    OUTPUT=$(java -cp "$CLASSPATH" org.drools.benchmark.waltzdb.WaltzDbBenchmark 2>&1)
    
    # Extract average time from output
    # Output format: "Average Time: XXX.XX"
    AVG_TIME=$(echo "$OUTPUT" | grep -oP 'Average Time:\s+\K[0-9.]+' || echo "0")
    
    # Write to CSV
    echo "$i,$AVG_TIME" >> "$OUTPUT_FILE"
    
    echo "done (${AVG_TIME}ms)"
    
    # Small delay between iterations
    sleep 2
done

# Cleanup
rm -f "$CP_FILE"

echo ""
echo "=========================================="
echo "Benchmark complete for $BRANCH"
echo "Results saved to: $OUTPUT_FILE"
echo "=========================================="
EOF

chmod +x ~/run_single_benchmark.sh
```

### Step 3: Create Master Experiment Runner

```bash
cat > ~/run_all_experiments.sh << 'EOF'
#!/bin/bash
# WaltzDB Benchmark - Master Experiment Runner
# Runs all 4 branches sequentially with maximum isolation

set -e

# Configuration
ITERATIONS=50  # Number of iterations per branch
WARMUP=10      # Warmup iterations (discarded)
RESULTS_DIR="$HOME/waltzdb_experiment_results"

# Branches to test
BRANCHES=(
    "base_exp_no_print"
    "base_exp_with_print"
    "mod_exp_no_print"
    "mod_exp_with_print"
)

echo "=========================================="
echo "WaltzDB Benchmark - Full Experimental Run"
echo "=========================================="
echo "Iterations per branch: $ITERATIONS"
echo "Warmup iterations: $WARMUP"
echo "Total branches: ${#BRANCHES[@]}"
echo "Estimated time: ~$((${#BRANCHES[@]} * ITERATIONS * 2 / 60)) minutes"
echo "=========================================="
echo ""

# Check if isolation is active
GOVERNOR=$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor)
if [ "$GOVERNOR" != "performance" ]; then
    echo "⚠ WARNING: CPU governor is not set to 'performance'"
    echo "   Current: $GOVERNOR"
    echo "   Run: sudo ~/setup_benchmark_isolation.sh"
    read -p "Continue anyway? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Create results directory
mkdir -p "$RESULTS_DIR"

# Run benchmark for each branch
for BRANCH in "${BRANCHES[@]}"; do
    echo ""
    echo "================================================"
    echo "Starting benchmark for: $BRANCH"
    echo "================================================"
    
    # Run with CPU pinning (cores 0-5 for benchmark)
    taskset -c 0-5 ~/run_single_benchmark.sh "$BRANCH" "$ITERATIONS" "$WARMUP"
    
    # Cool-down between branches
    echo "Cooling down for 30 seconds..."
    sleep 30
done

echo ""
echo "=========================================="
echo "All experiments complete!"
echo "=========================================="
echo "Results saved in: $RESULTS_DIR"
echo ""
echo "Next steps:"
echo "1. Run statistical analysis: python3 ~/analyze_results.py"
echo "2. Restore system: sudo ~/restore_system.sh"
echo ""
EOF

chmod +x ~/run_all_experiments.sh
```

---

## Data Collection Scripts

### Quick Run (Single Branch Test)

```bash
# Test a single branch (e.g., 10 iterations for quick verification)
taskset -c 0-5 ~/run_single_benchmark.sh base_exp_no_print 10 5
```

### Full Experimental Run

```bash
# Ensure isolation is active
sudo ~/setup_benchmark_isolation.sh

# Run all 4 branches (50 iterations each)
# This will take approximately 15-30 minutes
taskset -c 0-5 ~/run_all_experiments.sh

# Results will be in ~/waltzdb_experiment_results/
```

---

## Statistical Analysis

### Step 1: Create Statistical Analysis Script

```bash
cat > ~/analyze_results.py << 'EOF'
#!/usr/bin/env python3
"""
WaltzDB Benchmark - Statistical Analysis Script
Analyzes results from 4 experimental branches with 95% CI
"""

import numpy as np
import pandas as pd
from scipy import stats
import matplotlib.pyplot as plt
import seaborn as sns
import os
from pathlib import Path

# Configuration
RESULTS_DIR = Path.home() / "waltzdb_experiment_results"
OUTPUT_DIR = RESULTS_DIR / "analysis"
CONFIDENCE_LEVEL = 0.95
ALPHA = 1 - CONFIDENCE_LEVEL

BRANCHES = [
    "base_exp_no_print",
    "base_exp_with_print",
    "mod_exp_no_print",
    "mod_exp_with_print"
]

def load_data(branch_name):
    """Load benchmark results for a given branch."""
    csv_file = RESULTS_DIR / f"{branch_name}_results.csv"
    if not csv_file.exists():
        raise FileNotFoundError(f"Results file not found: {csv_file}")
    
    df = pd.read_csv(csv_file)
    return df['execution_time_ms'].values

def calculate_statistics(data):
    """Calculate descriptive statistics and confidence intervals."""
    n = len(data)
    mean = np.mean(data)
    std = np.std(data, ddof=1)  # Sample standard deviation
    se = std / np.sqrt(n)  # Standard error
    
    # 95% confidence interval using t-distribution
    t_critical = stats.t.ppf((1 + CONFIDENCE_LEVEL) / 2, df=n-1)
    ci_lower = mean - t_critical * se
    ci_upper = mean + t_critical * se
    margin_error = t_critical * se
    
    # Coefficient of variation
    cv = (std / mean) * 100
    
    return {
        'n': n,
        'mean': mean,
        'std': std,
        'se': se,
        'cv': cv,
        'median': np.median(data),
        'min': np.min(data),
        'max': np.max(data),
        'q25': np.percentile(data, 25),
        'q75': np.percentile(data, 75),
        'ci_lower': ci_lower,
        'ci_upper': ci_upper,
        'margin_error': margin_error
    }

def perform_comparison(data1, data2, name1, name2):
    """Perform Welch's t-test to compare two branches."""
    # Welch's t-test (does not assume equal variances)
    t_stat, p_value = stats.ttest_ind(data1, data2, equal_var=False)
    
    # Effect size (Cohen's d)
    pooled_std = np.sqrt((np.var(data1, ddof=1) + np.var(data2, ddof=1)) / 2)
    cohens_d = (np.mean(data1) - np.mean(data2)) / pooled_std
    
    # Determine significance
    is_significant = p_value < ALPHA
    
    return {
        'comparison': f"{name1} vs {name2}",
        't_statistic': t_stat,
        'p_value': p_value,
        'is_significant': is_significant,
        'cohens_d': cohens_d,
        'mean_diff': np.mean(data1) - np.mean(data2),
        'percent_diff': ((np.mean(data1) - np.mean(data2)) / np.mean(data2)) * 100
    }

def create_visualizations(all_data, stats_dict):
    """Create visualizations for the results."""
    OUTPUT_DIR.mkdir(exist_ok=True)
    
    # Set style
    sns.set_style("whitegrid")
    plt.rcParams['figure.figsize'] = (14, 10)
    
    # 1. Box plot with confidence intervals
    fig, axes = plt.subplots(2, 2, figsize=(16, 12))
    
    # Box plot
    ax1 = axes[0, 0]
    positions = range(len(BRANCHES))
    bp = ax1.boxplot([all_data[b] for b in BRANCHES], 
                      labels=BRANCHES, 
                      patch_artist=True,
                      showmeans=True)
    
    for patch in bp['boxes']:
        patch.set_facecolor('lightblue')
    
    ax1.set_ylabel('Execution Time (ms)', fontsize=12)
    ax1.set_title('WaltzDB Benchmark Results - Box Plot', fontsize=14, fontweight='bold')
    ax1.grid(True, alpha=0.3)
    plt.setp(ax1.get_xticklabels(), rotation=15, ha='right')
    
    # Violin plot
    ax2 = axes[0, 1]
    parts = ax2.violinplot([all_data[b] for b in BRANCHES], 
                           positions=positions, 
                           showmeans=True, 
                           showmedians=True)
    ax2.set_xticks(positions)
    ax2.set_xticklabels(BRANCHES, rotation=15, ha='right')
    ax2.set_ylabel('Execution Time (ms)', fontsize=12)
    ax2.set_title('WaltzDB Benchmark Results - Violin Plot', fontsize=14, fontweight='bold')
    ax2.grid(True, alpha=0.3)
    
    # Mean with 95% CI error bars
    ax3 = axes[1, 0]
    means = [stats_dict[b]['mean'] for b in BRANCHES]
    errors = [stats_dict[b]['margin_error'] for b in BRANCHES]
    
    ax3.bar(positions, means, yerr=errors, capsize=10, alpha=0.7, 
            color=['#1f77b4', '#ff7f0e', '#2ca02c', '#d62728'])
    ax3.set_xticks(positions)
    ax3.set_xticklabels(BRANCHES, rotation=15, ha='right')
    ax3.set_ylabel('Execution Time (ms)', fontsize=12)
    ax3.set_title('Mean Execution Time with 95% CI', fontsize=14, fontweight='bold')
    ax3.grid(True, alpha=0.3, axis='y')
    
    # Distribution histograms
    ax4 = axes[1, 1]
    for i, branch in enumerate(BRANCHES):
        ax4.hist(all_data[branch], bins=20, alpha=0.5, label=branch)
    ax4.set_xlabel('Execution Time (ms)', fontsize=12)
    ax4.set_ylabel('Frequency', fontsize=12)
    ax4.set_title('Distribution of Execution Times', fontsize=14, fontweight='bold')
    ax4.legend()
    ax4.grid(True, alpha=0.3)
    
    plt.tight_layout()
    plt.savefig(OUTPUT_DIR / 'benchmark_visualizations.png', dpi=300, bbox_inches='tight')
    print(f"  ✓ Saved visualization: {OUTPUT_DIR / 'benchmark_visualizations.png'}")
    plt.close()

def main():
    print("=" * 60)
    print("WaltzDB Benchmark - Statistical Analysis")
    print("=" * 60)
    print()
    
    # Load all data
    print("Loading data...")
    all_data = {}
    for branch in BRANCHES:
        try:
            all_data[branch] = load_data(branch)
            print(f"  ✓ Loaded {len(all_data[branch])} samples from {branch}")
        except FileNotFoundError as e:
            print(f"  ✗ Error: {e}")
            return
    
    print()
    
    # Calculate statistics for each branch
    print("Calculating statistics...")
    stats_dict = {}
    for branch in BRANCHES:
        stats_dict[branch] = calculate_statistics(all_data[branch])
        print(f"  ✓ Computed statistics for {branch}")
    
    print()
    
    # Print summary statistics
    print("=" * 60)
    print("SUMMARY STATISTICS (95% Confidence Interval)")
    print("=" * 60)
    print()
    
    for branch in BRANCHES:
        s = stats_dict[branch]
        print(f"{branch}:")
        print(f"  n                 = {s['n']}")
        print(f"  Mean              = {s['mean']:.3f} ms")
        print(f"  95% CI            = [{s['ci_lower']:.3f}, {s['ci_upper']:.3f}]")
        print(f"  Margin of Error   = ±{s['margin_error']:.3f} ms")
        print(f"  Std Dev           = {s['std']:.3f} ms")
        print(f"  Coeff. of Var.    = {s['cv']:.2f}%")
        print(f"  Median            = {s['median']:.3f} ms")
        print(f"  Range             = [{s['min']:.3f}, {s['max']:.3f}]")
        print(f"  IQR               = [{s['q25']:.3f}, {s['q75']:.3f}]")
        print()
    
    # Pairwise comparisons
    print("=" * 60)
    print("PAIRWISE COMPARISONS (Welch's t-test, α=0.05)")
    print("=" * 60)
    print()
    
    comparisons = []
    
    # Key comparisons
    comparison_pairs = [
        ("base_exp_no_print", "base_exp_with_print"),   # Effect of print in baseline
        ("mod_exp_no_print", "mod_exp_with_print"),     # Effect of print in modified
        ("base_exp_no_print", "mod_exp_no_print"),      # Effect of modification (no print)
        ("base_exp_with_print", "mod_exp_with_print"),  # Effect of modification (with print)
    ]
    
    for name1, name2 in comparison_pairs:
        comp = perform_comparison(all_data[name1], all_data[name2], name1, name2)
        comparisons.append(comp)
        
        print(f"{comp['comparison']}:")
        print(f"  Mean Difference   = {comp['mean_diff']:.3f} ms ({comp['percent_diff']:+.2f}%)")
        print(f"  t-statistic       = {comp['t_statistic']:.4f}")
        print(f"  p-value           = {comp['p_value']:.6f}")
        print(f"  Significant?      = {'YES' if comp['is_significant'] else 'NO'} (α=0.05)")
        print(f"  Effect Size (d)   = {comp['cohens_d']:.4f}")
        print()
    
    # Save results to CSV
    print("=" * 60)
    print("SAVING RESULTS")
    print("=" * 60)
    
    OUTPUT_DIR.mkdir(exist_ok=True)
    
    # Statistics summary
    stats_df = pd.DataFrame(stats_dict).T
    stats_file = OUTPUT_DIR / "statistics_summary.csv"
    stats_df.to_csv(stats_file)
    print(f"  ✓ Saved statistics: {stats_file}")
    
    # Comparisons
    comp_df = pd.DataFrame(comparisons)
    comp_file = OUTPUT_DIR / "pairwise_comparisons.csv"
    comp_df.to_csv(comp_file, index=False)
    print(f"  ✓ Saved comparisons: {comp_file}")
    
    # Create visualizations
    print()
    print("Creating visualizations...")
    create_visualizations(all_data, stats_dict)
    
    print()
    print("=" * 60)
    print("ANALYSIS COMPLETE!")
    print("=" * 60)
    print(f"Results saved in: {OUTPUT_DIR}")
    print()

if __name__ == "__main__":
    main()
EOF

chmod +x ~/analyze_results.py
```

### Step 2: Run Statistical Analysis

```bash
# After collecting all benchmark data, run analysis
python3 ~/analyze_results.py

# View results
ls -lh ~/waltzdb_experiment_results/analysis/
cat ~/waltzdb_experiment_results/analysis/statistics_summary.csv
cat ~/waltzdb_experiment_results/analysis/pairwise_comparisons.csv

# View visualization
xdg-open ~/waltzdb_experiment_results/analysis/benchmark_visualizations.png
```

---

## Results Interpretation

### Understanding Statistical Significance

**p-value < 0.05**: The difference between branches is statistically significant at 95% confidence level

**Effect Size (Cohen's d)**:
- **|d| < 0.2**: Small effect
- **0.2 ≤ |d| < 0.8**: Medium effect  
- **|d| ≥ 0.8**: Large effect

### Key Questions Answered

1. **Impact of Print Statements (Baseline)**:
   - Compare: `base_exp_no_print` vs `base_exp_with_print`
   - Metric: Mean difference and p-value

2. **Impact of Print Statements (Modified)**:
   - Compare: `mod_exp_no_print` vs `mod_exp_with_print`
   - Metric: Mean difference and p-value

3. **Impact of Modification (Without Print)**:
   - Compare: `base_exp_no_print` vs `mod_exp_no_print`
   - Metric: Mean difference and p-value

4. **Impact of Modification (With Print)**:
   - Compare: `base_exp_with_print` vs `mod_exp_with_print`
   - Metric: Mean difference and p-value

---

## Complete Workflow Summary

### One-Time Setup (First Run)

```bash
# 1. Create all scripts (copy-paste from above)
# Setup scripts already provided above

# 2. Install dependencies (Fedora 42)
sudo dnf update -y
sudo dnf install -y kernel-tools cpupowerutils python3-pip
pip3 install --user numpy scipy pandas matplotlib seaborn

# 3. Make scripts executable (if not already)
chmod +x ~/setup_benchmark_isolation.sh
chmod +x ~/restore_system.sh
chmod +x ~/run_single_benchmark.sh
chmod +x ~/run_all_experiments.sh
chmod +x ~/analyze_results.py
```

### Running the Experiment

```bash
# Step 1: Setup isolation
sudo ~/setup_benchmark_isolation.sh

# Step 2: Run all experiments (takes 15-30 minutes)
taskset -c 0-5 ~/run_all_experiments.sh

# Step 3: Analyze results
python3 ~/analyze_results.py

# Step 4: Restore system
sudo ~/restore_system.sh

# Step 5: View results
ls -lh ~/waltzdb_experiment_results/analysis/
cat ~/waltzdb_experiment_results/analysis/statistics_summary.csv
```

### Quick Verification (Test Before Full Run)

```bash
# Test with just 5 iterations to verify setup
sudo ~/setup_benchmark_isolation.sh
taskset -c 0-5 ~/run_single_benchmark.sh base_exp_no_print 5 2
sudo ~/restore_system.sh
```

---

> [!NOTE]
> **Why Direct Java Execution?**
> 
> The benchmark scripts use direct Java execution (`java -cp`) instead of `mvn exec:java` because the exec-maven-plugin in the POM has configuration that conflicts with command-line execution. Using direct Java with a Maven-built classpath provides:
> - Faster execution (no Maven overhead per iteration)
> - More reliable execution (no plugin configuration conflicts)
> - Better output capture for parsing results

---

## Troubleshooting

### Issue: Maven compilation fails

```bash
# Clean and rebuild the entire project
cd /home/maheshdila/mahesh/research/incubator-kie-drools
mvn clean install -DskipTests
```

### Issue: Cannot extract execution time from output

Check the `WaltzDbBenchmark.java` output format. The script expects:
```
Average Time: XXX.XXX
```

If different, modify the grep pattern in `run_single_benchmark.sh`:
```bash
AVG_TIME=$(echo "$OUTPUT" | grep -oP 'Average Time:\s+\K[0-9.]+')
```

### Issue: Permission denied for isolation scripts

```bash
# Ensure scripts have execute permissions
chmod +x ~/setup_benchmark_isolation.sh ~/restore_system.sh
```

### Issue: Python packages not found

```bash
# Install in user directory (recommended for Fedora)
pip3 install --user numpy scipy pandas matplotlib seaborn

# Or use system packages (Fedora)
sudo dnf install -y python3-numpy python3-scipy python3-pandas python3-matplotlib python3-seaborn
```

---

## Additional Notes

### Adjusting Sample Size

To change the number of iterations, edit `~/run_all_experiments.sh`:
```bash
ITERATIONS=100  # Change from 50 to 100
```

### Testing Different Data Files

To use different WaltzDB data files (waltzdb12.dat, waltzdb8.dat, waltzdb4.dat), modify the benchmark source code or pass as parameter.

### CPU Affinity Tuning

The default uses cores 0-5. To adjust:
```bash
taskset -c 0-7 ~/run_all_experiments.sh  # Use cores 0-7
```

### Recommended Reading

- **Statistical Power**: With n=50, you can detect medium effects (d≈0.4) with 80% power
- **Normality**: With n≥30, Central Limit Theorem ensures approximate normality
- **Multiple Comparisons**: Bonferroni correction for α: use α=0.0125 for 4 comparisons

---

## References

- Welch's t-test: Does not assume equal variances, robust for benchmark comparisons
- Cohen's d: Standardized measure of effect size
- 95% CI: Confidence interval computed using t-distribution
- CPU Isolation: Linux performance governor and CPU pinning via taskset

---

**End of Guide**
