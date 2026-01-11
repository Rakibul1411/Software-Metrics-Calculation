package org.promise.metrics.model;

/**
 * Data model to hold calculated metrics for a Java class.
 */
public class ClassMetrics {
    private String fullyQualifiedName;
    private int wmc;           // Weighted Methods per Class (sum of cyclomatic complexity)
    private int npm;           // Number of Public Methods
    private int loc;           // Lines of Code (excluding blanks and comments)
    private double amc;        // Average Method Complexity (WMC / number_of_methods)
    private int maxCC;         // Maximum Cyclomatic Complexity
    private double avgCC;      // Average Cyclomatic Complexity (same as AMC)
    private int numberOfMethods; // Total number of methods (for internal calculation)

    public ClassMetrics() {
    }

    public ClassMetrics(String fullyQualifiedName) {
        this.fullyQualifiedName = fullyQualifiedName;
    }

    // Getters and Setters
    public String getFullyQualifiedName() {
        return fullyQualifiedName;
    }

    public void setFullyQualifiedName(String fullyQualifiedName) {
        this.fullyQualifiedName = fullyQualifiedName;
    }

    public int getWmc() {
        return wmc;
    }

    public void setWmc(int wmc) {
        this.wmc = wmc;
    }

    public int getNpm() {
        return npm;
    }

    public void setNpm(int npm) {
        this.npm = npm;
    }

    public int getLoc() {
        return loc;
    }

    public void setLoc(int loc) {
        this.loc = loc;
    }

    public double getAmc() {
        return amc;
    }

    public void setAmc(double amc) {
        this.amc = amc;
    }

    public int getMaxCC() {
        return maxCC;
    }

    public void setMaxCC(int maxCC) {
        this.maxCC = maxCC;
    }

    public double getAvgCC() {
        return avgCC;
    }

    public void setAvgCC(double avgCC) {
        this.avgCC = avgCC;
    }

    public int getNumberOfMethods() {
        return numberOfMethods;
    }

    public void setNumberOfMethods(int numberOfMethods) {
        this.numberOfMethods = numberOfMethods;
    }

    /**
     * Calculate derived metrics after setting WMC, NPM, and method count.
     */
    public void calculateDerivedMetrics() {
        if (numberOfMethods > 0) {
            this.amc = (double) wmc / numberOfMethods;
            this.avgCC = (double) wmc / numberOfMethods;
        } else {
            this.amc = 0.0;
            this.avgCC = 0.0;
        }
    }

    @Override
    public String toString() {
        return String.format("ClassMetrics{name='%s', wmc=%d, npm=%d, loc=%d, amc=%.2f, max_cc=%d, avg_cc=%.4f}",
                fullyQualifiedName, wmc, npm, loc, amc, maxCC, avgCC);
    }
}
