#!/bin/bash
# WaltzDB Benchmark Runner
# Automatically detect the project directory relative to this script or current dir
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
# Assuming script is in artifacts dir, we try to locate drools-examples
# If running from drools-examples root, use current dir.
if [[ -d "src/main/java/org/drools/benchmark/waltzdb" ]]; then
    PROJECT_DIR=$(pwd)
elif [[ -d "$HOME/mahesh/research/incubator-kie-drools/drools-examples" ]]; then
    PROJECT_DIR="$HOME/mahesh/research/incubator-kie-drools/drools-examples"
else
    echo "Error: Could not locate drools-examples directory."
    exit 1
fi
echo "Using project directory: $PROJECT_DIR"
cd "$PROJECT_DIR" || exit 1
function show_help {
    echo "Usage: ./run_benchmarks.sh [base|partitioned|phased|compare|verify]"
    echo ""
    echo "  base        Run the standard sequential WaltzDB benchmark"
    echo "  partitioned Run the partitioned parallel benchmark (1-8 threads)"
    echo "  phased      Run the phased parallel benchmark (Recommended)"
    echo "  compare     Run head-to-head comparison (Baseline vs PPESB)"
    echo "  verify      Run correctness verification for PPESB"
    echo ""
}
if [ $# -eq 0 ]; then
    show_help
    exit 1
fi
MODE=$1
# Ensure we have compiled classes (skip tests for speed)
if [ ! -d "target/classes" ]; then
    echo "Compiling project..."
    mvn compile -Dmaven.test.skip=true > /dev/null
fi
echo "------------------------------------------------"
case "$MODE" in
    "base")
        echo "Running Base WaltzDB Benchmark..."
        mvn exec:exec \
            -Dexec.executable="java" \
            -Dexec.args="-classpath %classpath org.drools.benchmark.waltzdb.WaltzDbBenchmark" \
            -Dexec.classpathScope="test"
        ;;
    "partitioned")
        echo "Running Partitioned Parallel Benchmark..."
        mvn exec:exec \
            -Dexec.executable="java" \
            -Dexec.args="-classpath %classpath org.drools.benchmark.waltzdb.partition.WaltzDbPartitionedBenchmark" \
            -Dexec.classpathScope="test"
        ;;
    "phased")
        echo "Running Phased Parallel Benchmark..."
        mvn exec:exec \
            -Dexec.executable="java" \
            -Dexec.args="-classpath %classpath org.drools.benchmark.waltzdb.parallel.WaltzDbPhasedParallelBenchmark" \
            -Dexec.classpathScope="test"
        ;;
    "compare")
        echo "Running Baseline vs PPESB Comparison..."
        mvn exec:exec \
            -Dexec.executable="java" \
            -Dexec.args="-classpath %classpath org.drools.benchmark.waltzdb.parallel.BaselineVsPpebsComparison" \
            -Dexec.classpathScope="test"
        ;;
    "verify")
        echo "Running PPESB Correctness Verification..."
        mvn exec:exec \
            -Dexec.executable="java" \
            -Dexec.args="-classpath %classpath org.drools.benchmark.waltzdb.parallel.PpebsCorrectnessVerifier" \
            -Dexec.classpathScope="test"
        ;;
    *)
        show_help
        exit 1
        ;;
esac