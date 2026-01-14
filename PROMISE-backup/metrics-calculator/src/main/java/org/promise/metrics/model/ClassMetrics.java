package org.promise.metrics.model;

/**
 * Data model to hold calculated metrics for a Java class.
 */
public class ClassMetrics {
    private String fullyQualifiedName;
    private int npm;           // Number of Public Methods
    private int loc;           // Lines of Code (excluding blanks and comments)

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


    @Override
    public String toString() {
        return String.format("ClassMetrics{name='%s', npm=%d, loc=%d}",
                fullyQualifiedName, npm, loc);
    }
}
