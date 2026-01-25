package org.promise.metrics.model;

/**
 * Data model to hold calculated metrics for a Java class.
 */
public class ClassMetrics {
    private String fullyQualifiedName;
    private int wmc;           // Weighted Methods per Class
    private int npm;           // Number of Public Methods
    private int loc;           // Lines of Code (excluding blanks and comments)
    private int noc;           // Number of Children (immediate subclasses)
    private int dit;           // Depth of Inheritance Tree
    private String superclassName;  // Used for NOC and DIT calculation
    private boolean isInterface;    // Whether this type is an interface

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

    public int getNoc() {
        return noc;
    }

    public void setNoc(int noc) {
        this.noc = noc;
    }

    public String getSuperclassName() {
        return superclassName;
    }

    public void setSuperclassName(String superclassName) {
        this.superclassName = superclassName;
    }

    public int getDit() {
        return dit;
    }

    public void setDit(int dit) {
        this.dit = dit;
    }

    public boolean isInterface() {
        return isInterface;
    }

    public void setInterface(boolean isInterface) {
        this.isInterface = isInterface;
    }

    @Override
    public String toString() {
        return String.format("ClassMetrics{name='%s', wmc=%d, npm=%d, loc=%d, noc=%d, dit=%d}",
                fullyQualifiedName, wmc, npm, loc, noc, dit);
    }
}
