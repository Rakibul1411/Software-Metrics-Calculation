#!/bin/bash

# Quick start script to analyze Ant 1.3 source code
# Usage: ./run-ant-1.3.sh

echo "Building metrics-calculator..."
mvn clean package -q

if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

echo "Running metrics calculation for Ant 1.3..."
java -jar target/metrics-calculator-1.0.0.jar \
    "../source code/ant/jakarta-ant-1.3/src/main" \
    "output/ant-1.3-calculated.csv"

if [ $? -eq 0 ]; then
    echo ""
    echo "Success! Results saved to: output/ant-1.3-calculated.csv"
    echo ""
    echo "First 10 rows:"
    head -n 11 output/ant-1.3-calculated.csv | column -t -s,
fi
