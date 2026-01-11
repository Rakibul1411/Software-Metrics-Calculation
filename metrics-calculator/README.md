# Java Metrics Calculator

A Java-based tool to calculate code metrics from Java source code using Eclipse JDT parser. This tool was designed to analyze the PROMISE dataset (Apache Ant versions) but can be used for any Java project.

## Calculated Metrics

The tool calculates the following 7 metrics:

| Metric | Description | Formula |
|--------|-------------|---------|
| **WMC** | Weighted Methods per Class | Sum of cyclomatic complexity of all methods |
| **NPM** | Number of Public Methods | Count of public methods in the class |
| **LOC** | Lines of Code | Total lines - blank lines - comment lines |
| **AMC** | Average Method Complexity | WMC / number_of_methods |
| **MAX_CC** | Maximum Cyclomatic Complexity | Maximum CC among all methods |
| **AVG_CC** | Average Cyclomatic Complexity | Same as AMC (WMC / number_of_methods) |

### Cyclomatic Complexity (CC)

CC is calculated for each method using the formula:
```
CC = 1 + (# if + for + while + do-while + case + catch + && + || + ?:)
```

## Requirements

- Java 8 or higher
- Maven 3.x

## Building the Project

```bash
cd metrics-calculator
mvn clean package
```

This will create an executable JAR file at `target/metrics-calculator-1.0.0.jar`.

## Usage

### Option 1: Using Maven (Development)

```bash
mvn exec:java -Dexec.args="<source-directory> [output-file]"
```

**Example:**
```bash
mvn exec:java -Dexec.args="../source code/ant/jakarta-ant-1.3/src/main output/ant-1.3.csv"
```

### Option 2: Using JAR (Production)

```bash
java -jar target/metrics-calculator-1.0.0.jar <source-directory> [output-file] [--full-format]
```

**Arguments:**
- `source-directory` (required): Path to the Java source code directory
- `output-file` (optional): Path to output CSV file (default: `output/metrics.csv`)
- `--full-format` (optional): Export with all 22 columns (unimplemented metrics filled with 0)

**Examples:**

```bash
# Basic usage with default output
java -jar target/metrics-calculator-1.0.0.jar "../source code/ant/jakarta-ant-1.3/src/main"

# Specify custom output file
java -jar target/metrics-calculator-1.0.0.jar \
  "../source code/ant/jakarta-ant-1.3/src/main" \
  output/ant-1.3-calculated.csv

# Export with full 22-column format
java -jar target/metrics-calculator-1.0.0.jar \
  "../source code/ant/jakarta-ant-1.3/src/main" \
  output/ant-1.3-full.csv \
  --full-format
```

## Output Format

### Standard Format (7 columns)

```csv
name,wmc,npm,loc,amc,max_cc,avg_cc
org.apache.tools.ant.Main,50,8,450,6.25,12,6.25
org.apache.tools.ant.Project,120,25,850,4.8,15,4.8
```

### Full Format (22 columns)

When using `--full-format`, the output includes all 22 columns matching the original PROMISE dataset format. Unimplemented metrics are filled with `0`:

```csv
name,wmc,dit,noc,cbo,rfc,lcom,ca,ce,npm,lcom3,loc,dam,moa,mfa,cam,ic,cbm,amc,max_cc,avg_cc,bug
org.apache.tools.ant.Main,50,0,0,0,0,0,0,0,8,0,450,0,0,0,0,0,0,6.25,12,6.25,0
```

## Project Structure

```
metrics-calculator/
├── pom.xml                                 # Maven configuration
├── README.md                               # This file
├── src/
│   └── main/
│       └── java/
│           └── org/
│               └── promise/
│                   └── metrics/
│                       ├── MetricsCalculatorMain.java    # Entry point
│                       ├── parser/
│                       │   └── JavaSourceParser.java     # JDT-based parser
│                       ├── calculator/
│                       │   ├── ComplexityCalculator.java # CC, WMC calculations
│                       │   ├── LOCCalculator.java        # LOC calculation
│                       │   └── NPMCalculator.java        # NPM calculation
│                       ├── model/
│                       │   └── ClassMetrics.java         # Data model
│                       └── export/
│                           └── CSVExporter.java          # CSV generation
└── output/                                  # Generated CSV files
```

## Dependencies

- **Eclipse JDT Core 3.32.0** - Java parser and AST
- **Apache Commons CSV 1.10.0** - CSV file generation
- **Apache Commons IO 2.11.0** - File utilities

## Features

- ✅ Parses Java source code without compilation
- ✅ Supports Java 1.4+ syntax (compatible with older codebases)
- ✅ Handles nested classes (exports as `OuterClass$InnerClass`)
- ✅ Recursive directory scanning
- ✅ Detailed progress reporting
- ✅ Error handling for parse issues
- ✅ Sorted output (alphabetically by fully qualified name)

## Limitations

The following metrics from the full PROMISE dataset are **not yet implemented**:

- DIT (Depth of Inheritance Tree)
- NOC (Number of Children)
- CBO (Coupling Between Objects)
- RFC (Response For a Class)
- LCOM/LCOM3 (Lack of Cohesion in Methods)
- CA (Afferent Couplings)
- CE (Efferent Couplings)
- DAM (Data Access Metric)
- MOA (Measure of Aggregation)
- MFA (Measure of Functional Abstraction)
- CAM (Cohesion Among Methods)
- IC (Inheritance Coupling)
- CBM (Coupling Between Methods)
- Bug (requires external defect data)

To implement these metrics, consider using:
- **CKJM** (Chidamber & Kemerer Java Metrics) library
- Custom JDT visitors for coupling metrics
- External bug tracking data for defect counts

## Example: Analyzing Ant 1.3

```bash
# Navigate to metrics-calculator directory
cd metrics-calculator

# Build the project
mvn clean package

# Run analysis on Ant 1.3 source code
java -jar target/metrics-calculator-1.0.0.jar \
  "../source code/ant/jakarta-ant-1.3/src/main" \
  output/ant-1.3-calculated.csv

# View results
head output/ant-1.3-calculated.csv
```

**Expected output:**
```
Java Metrics Calculator
======================
Source directory: ../source code/ant/jakarta-ant-1.3/src/main
Output file: output/ant-1.3-calculated.csv

Scanning for Java files...
Processing: .../org/apache/tools/ant/Main.java
  - org.apache.tools.ant.Main
Processing: .../org/apache/tools/ant/Project.java
  - org.apache.tools.ant.Project
...

Total classes found: 126

Exported 126 class metrics to: output/ant-1.3-calculated.csv

=== Metrics Summary ===
Total classes analyzed: 126
Average WMC: 15.32
Average NPM: 8.45
Average LOC: 245.67
Maximum CC found: 35
Total LOC: 30954

Metrics calculation completed successfully!
```

## Validation

To validate the results against the original PROMISE dataset:

1. Generate metrics for Ant 1.3:
   ```bash
   java -jar target/metrics-calculator-1.0.0.jar \
     "../source code/ant/jakarta-ant-1.3/src/main" \
     output/ant-1.3-calculated.csv
   ```

2. Compare with original:
   ```bash
   # View original metrics
   head ../bug-data/ant/ant-1.3.csv
   
   # View calculated metrics
   head output/ant-1.3-calculated.csv
   ```

3. Acceptable tolerance: ±5% due to differences in:
   - Comment line counting algorithms
   - Blank line definitions
   - Inner class handling

## Troubleshooting

### Parse Errors

If you see warnings like "Parse problems in X.java", this usually means:
- Old Java syntax not fully supported
- File encoding issues
- Incomplete source files

The tool will continue processing and skip problematic files.

### Memory Issues

For very large codebases, increase heap size:
```bash
java -Xmx2g -jar target/metrics-calculator-1.0.0.jar <source-directory>
```

### Maven Build Issues

If Maven cannot resolve dependencies:
```bash
mvn clean install -U
```

## Contributing

To extend this tool with additional metrics:

1. Create a new calculator in `org.promise.metrics.calculator`
2. Update `ClassMetrics.java` to include new fields
3. Modify `JavaSourceParser.java` to call your calculator
4. Update `CSVExporter.java` to export new columns

## License

This project is part of the PROMISE dataset analysis toolkit. See the parent repository for license information.

## References

- Eclipse JDT Documentation: https://www.eclipse.org/jdt/core/
- Cyclomatic Complexity: McCabe, T.J. (1976)
- Chidamber & Kemerer Metrics: https://www.spinellis.gr/sw/ckjm/
- PROMISE Repository: http://promise.site.uottawa.ca/SERepository/
