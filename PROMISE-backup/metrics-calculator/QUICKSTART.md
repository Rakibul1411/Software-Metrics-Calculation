# Metrics Calculator - Quick Reference

## What Was Implemented

A complete Java-based metrics calculator that:
- Parses Java source code using Eclipse JDT
- Calculates 7 code metrics: WMC, NPM, LOC, AMC, MAX_CC, AVG_CC, and CC
- Exports results to CSV format matching the PROMISE dataset structure
- Handles nested classes, recursive directory scanning, and error recovery

## Project Structure

```
metrics-calculator/
├── pom.xml                          # Maven build configuration
├── README.md                        # Full documentation
├── run-ant-1.3.sh                   # Quick start script
├── .gitignore                       # Git ignore rules
├── src/main/java/org/promise/metrics/
│   ├── MetricsCalculatorMain.java  # Entry point
│   ├── model/
│   │   └── ClassMetrics.java       # Data model for metrics
│   ├── parser/
│   │   └── JavaSourceParser.java   # JDT-based Java parser
│   ├── calculator/
│   │   ├── ComplexityCalculator.java  # CC, WMC, MAX_CC, AVG_CC
│   │   ├── LOCCalculator.java         # Lines of Code
│   │   └── NPMCalculator.java         # Number of Public Methods
│   └── export/
│       └── CSVExporter.java        # CSV file generation
└── output/                          # Generated CSV files
```

## Quick Start

### 1. Build the Project

```bash
cd metrics-calculator
mvn clean package
```

### 2. Run Analysis

```bash
# Using the convenience script
./run-ant-1.3.sh

# Or manually
java -jar target/metrics-calculator-1.0.0.jar \
  "../source code/ant/jakarta-ant-1.3/src/main" \
  "output/ant-1.3-calculated.csv"

# Or with Maven
mvn exec:java -Dexec.args="../source\ code/ant/jakarta-ant-1.3/src/main output/ant-1.3.csv"
```

### 3. View Results

```bash
# View first 10 rows
head -n 11 output/ant-1.3-calculated.csv

# Count total classes
wc -l output/ant-1.3-calculated.csv

# Compare with original
diff <(head -n 5 output/ant-1.3-calculated.csv | cut -d, -f1,2,10,12,19,20,21) \
     <(head -n 5 ../bug-data/ant/ant-1.3.csv | cut -d, -f1,2,10,12,19,20,21)
```

## Metrics Calculated

| Metric | Formula | Description |
|--------|---------|-------------|
| **CC** | `1 + (# decision points)` | Cyclomatic Complexity per method |
| **WMC** | `Σ CC(method)` | Sum of all method complexities |
| **NPM** | `count(public methods)` | Number of public methods |
| **LOC** | `total - blanks - comments` | Lines of executable code |
| **AMC** | `WMC / method_count` | Average method complexity |
| **MAX_CC** | `max(CC)` | Highest complexity in class |
| **AVG_CC** | `WMC / method_count` | Average cyclomatic complexity |

### Decision Points (for CC calculation)
- `if` statements
- `for` / `while` / `do-while` loops
- `case` in switch statements
- `catch` blocks
- `&&` / `||` operators
- `?:` ternary operators

## Output Format

### Default (7 columns)
```csv
name,wmc,npm,loc,amc,max_cc,avg_cc
org.apache.tools.ant.Main,50,8,450,6.25,12,6.25
org.apache.tools.ant.Project,120,25,850,4.8,15,4.8
```

### Full Format (22 columns with placeholders)
Use `--full-format` flag to match original dataset structure with unimplemented metrics as 0.

## Command-Line Options

```bash
java -jar metrics-calculator.jar <source-dir> [output-file] [--full-format]
```

**Arguments:**
- `source-dir` (required): Path to Java source directory
- `output-file` (optional): CSV output path (default: `output/metrics.csv`)
- `--full-format` (optional): Export 22 columns instead of 7

## Examples

```bash
# Analyze Ant 1.3
java -jar target/metrics-calculator-1.0.0.jar \
  "../source code/ant/jakarta-ant-1.3/src/main" \
  "output/ant-1.3.csv"

# Analyze Ant 1.4
java -jar target/metrics-calculator-1.0.0.jar \
  "../source code/ant/jakarta-ant-1.4/src/main" \
  "output/ant-1.4.csv"

# Full format export
java -jar target/metrics-calculator-1.0.0.jar \
  "../source code/ant/jakarta-ant-1.3/src/main" \
  "output/ant-1.3-full.csv" \
  --full-format
```

## Validation

Expected results for Ant 1.3:
- **~126 classes** should be found
- Class names should match those in `../bug-data/ant/ant-1.3.csv`
- Metrics should be within ±5% of original values (due to algorithm differences)

## Troubleshooting

### Build fails with dependency errors
```bash
mvn clean install -U
```

### Out of memory for large projects
```bash
java -Xmx2g -jar target/metrics-calculator-1.0.0.jar <args>
```

### Parse errors in old Java code
The tool uses Java 1.4 compatibility mode but may still encounter issues with very old syntax. These files will be skipped with a warning.

## Next Steps

To extend the calculator:

1. **Add CK Metrics** (DIT, NOC, CBO, RFC, LCOM):
   - Integrate CKJM library or implement custom visitors
   - Update `ClassMetrics.java` model
   - Modify `JavaSourceParser.java` to call new calculators

2. **Improve LOC Accuracy**:
   - Fine-tune comment detection
   - Handle mixed comment/code lines better

3. **Add Coupling Metrics** (CA, CE, IC, CBM):
   - Implement dependency graph analysis
   - Track import statements and method calls

4. **Batch Processing**:
   - Create scripts to analyze all Ant versions
   - Generate comparison reports

## Files Created

✅ `pom.xml` - Maven configuration with JDT and Commons CSV
✅ `src/main/java/org/promise/metrics/MetricsCalculatorMain.java` - Main entry point
✅ `src/main/java/org/promise/metrics/model/ClassMetrics.java` - Data model
✅ `src/main/java/org/promise/metrics/parser/JavaSourceParser.java` - JDT parser
✅ `src/main/java/org/promise/metrics/calculator/ComplexityCalculator.java` - CC calculator
✅ `src/main/java/org/promise/metrics/calculator/LOCCalculator.java` - LOC calculator
✅ `src/main/java/org/promise/metrics/calculator/NPMCalculator.java` - NPM calculator
✅ `src/main/java/org/promise/metrics/export/CSVExporter.java` - CSV export
✅ `README.md` - Complete documentation
✅ `run-ant-1.3.sh` - Convenience script
✅ `.gitignore` - Git ignore rules
✅ `output/` - Output directory

## Status

✅ **Implementation Complete!**

All planned components have been implemented. The tool is ready to use.
