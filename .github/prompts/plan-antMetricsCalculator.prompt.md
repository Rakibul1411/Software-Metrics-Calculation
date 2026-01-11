# Java Metrics Calculator for Ant Source Code

A Java-based metrics calculator using Eclipse JDT to parse ant-1.3 source code and generate CSV output matching the bug-data/ant/ant-1.3.csv format.

## Objective

Calculate the following 7 metrics from Java source code:

| Metric         | Formula                                         |
| -------------- | ----------------------------------------------- |
| **CC(method)** | `1 + (# if + for + while + case + catch + && + \|\| + ?:)` |
| **WMC**        | `Σ CC(method)`                                  |
| **NPM**        | `count(public methods)`                         |
| **LOC**        | `line(end) - line(start) - blank/comment lines` |
| **AMC**        | `WMC / number_of_methods`                       |
| **MAX_CC**     | `max(CC(method))`                               |
| **AVG_CC**     | `Σ CC(method) / number_of_methods`              |

## Source Code Information

### Ant-1.3 Structure
- **Location**: `/Users/md.rakibulislam/IIT/SPL-3/promise-dataset-source-code/PROMISE-backup/source code/ant/jakarta-ant-1.3/src/main`
- **Total Java Files**: ~120-130 files
- **Main Packages**:
  - `org.apache.tools.ant` - Core Ant classes (28 files)
  - `org.apache.tools.ant.taskdefs` - Task definitions (64+ files)
    - `taskdefs.compilers` - Compiler adapters (7 files)
    - `taskdefs.optional` - Optional tasks
  - `org.apache.tools.ant.types` - Type definitions (13 files)
  - `org.apache.tools.ant.util` - Utility classes (8 files)
    - `util.regexp` - Regular expression utilities
  - `org.apache.tools.tar` - TAR archive handling (6 files)
  - `org.apache.tools.mail` - Mail utilities (2 files)

### Expected Output Format

CSV file matching bug-data/ant/ant-1.3.csv with columns:
- `name` - Fully qualified class name (e.g., "org.apache.tools.ant.taskdefs.ExecuteOn")
- `wmc` - Weighted Methods per Class
- `npm` - Number of Public Methods
- `loc` - Lines of Code
- `amc` - Average Method Complexity
- `max_cc` - Maximum Cyclomatic Complexity
- `avg_cc` - Average Cyclomatic Complexity

Note: The full CSV has 22 columns, but we're implementing only the 7 required metrics initially.

## Proposed Project Structure

```
PROMISE-backup/
└── metrics-calculator/
    ├── pom.xml                           # Maven configuration
    ├── README.md                         # Documentation
    ├── src/
    │   └── main/
    │       └── java/
    │           └── org/
    │               └── promise/
    │                   └── metrics/
    │                       ├── MetricsCalculatorMain.java
    │                       ├── parser/
    │                       │   └── ASTParser.java
    │                       ├── calculator/
    │                       │   ├── ComplexityCalculator.java
    │                       │   ├── LOCCalculator.java
    │                       │   └── NPMCalculator.java
    │                       ├── model/
    │                       │   └── ClassMetrics.java
    │                       └── export/
    │                           └── CSVExporter.java
    └── output/                           # Generated CSV files
        └── ant-1.3-calculated.csv
```

## Implementation Steps

### 1. Setup Maven Project

Create `pom.xml` with dependencies:
- **Eclipse JDT Core** - For parsing Java source files and AST traversal
- **Apache Commons CSV** - For CSV file generation
- Java version: 8+ (compatible with older source code)

### 2. Implement ComplexityCalculator

**Purpose**: Calculate cyclomatic complexity (CC) for each method

**Algorithm**:
1. Use Eclipse JDT ASTVisitor to traverse method declarations
2. Count control flow nodes:
   - `if` statements
   - `for` loops
   - `while` loops
   - `do-while` loops
   - `case` statements in switch
   - `catch` blocks
   - `&&` operators
   - `||` operators
   - `?:` ternary operators
3. CC = 1 + count of these nodes
4. Aggregate for class-level metrics:
   - **WMC**: Sum of all method CC values
   - **MAX_CC**: Maximum CC across all methods
   - **AVG_CC**: WMC / number_of_methods
   - **AMC**: Same as AVG_CC (Average Method Complexity)

### 3. Implement LOCCalculator

**Purpose**: Calculate lines of code excluding blanks and comments

**Algorithm**:
1. Get CompilationUnit from JDT parser
2. Get line range: `line(end) - line(start)`
3. Use JDT's comment visitor to identify comment ranges
4. Count blank lines using regex
5. Subtract comments and blank lines from total

### 4. Implement NPMCalculator

**Purpose**: Count public methods

**Algorithm**:
1. Visit all method declarations using JDT ASTVisitor
2. Check method modifiers with `Modifier.isPublic()`
3. Count methods where public flag is true
4. Exclude constructors if needed (clarify requirement)

### 5. Create ClassMetrics Model

**Data structure** to hold:
```java
public class ClassMetrics {
    private String fullyQualifiedName;
    private int wmc;
    private int npm;
    private int loc;
    private double amc;
    private int maxCC;
    private double avgCC;
    // Additional fields for other metrics if needed
}
```

### 6. Implement MetricsCalculatorMain

**Workflow**:
1. Accept command-line argument for source directory
2. Recursively scan for `.java` files
3. For each file:
   - Parse using Eclipse JDT
   - Extract class declarations
   - Calculate all 7 metrics
   - Store in ClassMetrics object
4. Collect all ClassMetrics
5. Pass to CSVExporter

### 7. Implement CSVExporter

**Output format**:
- Header: `name,wmc,npm,loc,amc,max_cc,avg_cc`
- Data rows: One per class with comma-separated values
- Use Apache Commons CSV for proper escaping
- Sort by fully qualified class name (alphabetically)

## Technical Decisions

### Parsing Library: Eclipse JDT Core

**Why JDT?**
- Most robust for older Java versions (ant-1.3 is from ~2000-2001)
- Better handling of incomplete/legacy code
- No compilation required (works on source only)
- Industry standard for Java static analysis

**Alternatives considered**:
- JavaParser: Modern but may struggle with old syntax
- Spoon: Requires compilation
- ANTLR: Too low-level for this task

### Handling Inner Classes

**Question**: Should inner/nested classes get separate CSV rows?

**Options**:
1. **Separate rows** with naming like `OuterClass$InnerClass`
2. **Roll up** inner class metrics into parent
3. **Exclude** inner classes entirely

**Recommendation**: Option 1 (separate rows) - matches typical CKJM output and provides granular metrics

### Build Tool: Maven

**Why Maven?**
- Standard dependency management
- Easy to reproduce builds
- Well-supported by IDEs
- Simple to add additional libraries later

## Open Questions

### 1. Full Metrics vs. Subset

The existing CSV has 22 columns:
- name, wmc, dit, noc, cbo, rfc, lcom, ca, ce, npm, lcom3, loc, dam, moa, mfa, cam, ic, cbm, amc, max_cc, avg_cc, bug

We're implementing only 7: wmc, npm, loc, amc, max_cc, avg_cc, (+ name)

**Question**: Should we:
- A) Implement only the 7 required metrics?
- B) Implement all 22 to match existing format?
- C) Use external library (CKJM) for CK metrics suite?

**Recommendation**: Start with 7, add others if validation requires full comparison.

### 2. Bug Column

The `bug` column contains defect counts (0-3 in sample).

**Question**: How to handle this?
- A) Omit entirely (not calculable from source)
- B) Default all to 0
- C) Merge with existing CSV data for validation

**Recommendation**: Option A (omit) - bug data requires external issue tracking.

### 3. Method Count Definition

**Question**: Does "number_of_methods" include:
- Constructors? 
- Static initializers?
- Private methods?

**Recommendation**: Include all methods (public, protected, private) and constructors, exclude static initializers.

### 4. CC Edge Cases

**Question**: How to count:
- Short-circuit evaluation: `a && b && c` (count as 2 or 3?)
- Nested ternary: `a ? b : c ? d : e` (count as 1 or 2?)

**Recommendation**: Each operator counts as +1, so `a && b && c` = +2, nested ternary = +2.

## Validation Strategy

1. **Generate metrics** for ant-1.3 source
2. **Compare** with existing bug-data/ant/ant-1.3.csv
3. **Focus on** the 7 implemented metrics
4. **Acceptable tolerance**: ±5% due to differences in:
   - Comment line counting
   - Blank line definitions
   - Inner class handling

## Next Steps

1. Create project structure in `metrics-calculator/` directory
2. Set up Maven `pom.xml` with dependencies
3. Implement core calculators (Complexity, LOC, NPM)
4. Test on sample Java file
5. Run on full ant-1.3 source
6. Validate against existing CSV
7. Document discrepancies and refine algorithms
8. Generalize to work with other Ant versions (1.4, 1.5, etc.)

## Usage Example

```bash
cd metrics-calculator
mvn clean compile
mvn exec:java -Dexec.mainClass="org.promise.metrics.MetricsCalculatorMain" \
  -Dexec.args="../source code/ant/jakarta-ant-1.3/src/main"
# Output: output/ant-1.3-calculated.csv
```

## Expected Timeline

- **Setup & Dependencies**: 30 minutes
- **Complexity Calculator**: 2-3 hours
- **LOC Calculator**: 1-2 hours  
- **NPM Calculator**: 30 minutes
- **CSV Export**: 1 hour
- **Testing & Validation**: 2-3 hours
- **Total**: ~8-10 hours

## References

- Eclipse JDT Documentation: https://www.eclipse.org/jdt/core/
- Cyclomatic Complexity: McCabe (1976)
- CKJM Tool: https://www.spinellis.gr/sw/ckjm/
- Apache Commons CSV: https://commons.apache.org/proper/commons-csv/
