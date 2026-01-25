package org.promise.metrics.model;

import java.util.HashSet;
import java.util.Set;

/**
 * Data model to hold calculated metrics for a Java class.
 */
public class ClassMetrics {
    private String fullyQualifiedName;
    private int wmc;           // Weighted Methods per Class
    private int dit;           // Depth of Inheritance Tree
    private int noc;           // Number of Children (immediate subclasses)
    private int cbo;           // Coupling Between Objects
    private int ca;            // Afferent Coupling (classes that depend on this class)
    private int ce;            // Efferent Coupling (classes this class depends on)
    private int npm;           // Number of Public Methods
    private int loc;           // Lines of Code (excluding blanks and comments)
    private String superclassName;  // Used for NOC and DIT calculation
    private boolean isInterface;    // Whether this type is an interface
    private Set<String> dependencies = new HashSet<>();  // For CBO calculation

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

    public int getDit() {
        return dit;
    }

    public void setDit(int dit) {
        this.dit = dit;
    }

    public int getNoc() {
        return noc;
    }

    public void setNoc(int noc) {
        this.noc = noc;
    }

    public int getCbo() {
        return cbo;
    }

    public void setCbo(int cbo) {
        this.cbo = cbo;
    }

    public int getCa() {
        return ca;
    }

    public void setCa(int ca) {
        this.ca = ca;
    }

    public int getCe() {
        return ce;
    }

    public void setCe(int ce) {
        this.ce = ce;
    }

    public Set<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(Set<String> dependencies) {
        this.dependencies = dependencies;
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

    public String getSuperclassName() {
        return superclassName;
    }

    public void setSuperclassName(String superclassName) {
        this.superclassName = superclassName;
    }

    public boolean isInterface() {
        return isInterface;
    }

    public void setInterface(boolean isInterface) {
        this.isInterface = isInterface;
    }

    @Override
    public String toString() {
        return String.format("ClassMetrics{name='%s', wmc=%d, dit=%d, noc=%d, cbo=%d, npm=%d, loc=%d}",
                fullyQualifiedName, wmc, dit, noc, cbo, npm, loc);
    }
}
