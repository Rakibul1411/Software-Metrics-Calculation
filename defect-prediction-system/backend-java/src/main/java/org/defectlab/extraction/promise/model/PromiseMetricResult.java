package org.metrics.promise.model;

import java.util.HashSet;
import java.util.Set;

public class PromiseMetricResult {
    private String fullyQualifiedName;
    private int wmc;           // Weighted Methods per Class
    private int dit;           // Depth of Inheritance Tree
    private int noc;           // Number of Children (immediate subclasses)
    private int cbo;           // Coupling Between Objects
    private int rfc;           // Response For a Class
    private int ca;            // Afferent Coupling (classes that depend on this class)
    private int ce;            // Efferent Coupling (classes this class depends on)
    private int npm;           // Number of Public Methods
    private int loc;           // Lines of Code (excluding blanks and comments)
    private int moa;           // Measure of Aggregation (user-defined class fields)
    private int ic;            // Inheritance Coupling (parent classes coupled through method calls)
    private int cbm;           // Coupling Between Methods (method invocations to parent methods)
    private int maxCc;         // Maximum Cyclomatic Complexity
    private double avgCc;      // Average Cyclomatic Complexity
    private int lcom;          // Lack of Cohesion of Methods (Chidamber & Kemerer)
    private double lcom3;      // LCOM variant 3 (Henderson-Sellers)
    private double amc;        // Average Method Complexity (avg method LOC)
    private double dam;        // Data Access Metric (ratio of private/protected attributes)
    private double mfa;        // Measure of Functional Abstraction (ratio of inherited methods)
    private double cam;        // Cohesion Among Methods (parameter type cohesion)
    private String superclassName;  // Used for NOC and DIT calculation
    private boolean isInterface;    // Whether this type is an interface
    private Set<String> dependencies = new HashSet<>();  // For CBO calculation
    private Set<String> methodNames = new HashSet<>();   // Methods defined in this class
    private Set<String> invokedMethods = new HashSet<>(); // Methods invoked by this class
    private Set<String> inheritedMethodInvocations = new HashSet<>(); // Parent method invocations for CBM
    private java.util.List<String> fieldTypes = new java.util.ArrayList<>(); // Field types for MOA

    public PromiseMetricResult() {
    }

    public PromiseMetricResult(String fullyQualifiedName) {
        this.fullyQualifiedName = fullyQualifiedName;
    }

    public String getFullyQualifiedName() { return fullyQualifiedName; }
    public void setFullyQualifiedName(String fullyQualifiedName) { this.fullyQualifiedName = fullyQualifiedName; }

    public int getWmc() { return wmc; }
    public void setWmc(int wmc) { this.wmc = wmc; }

    public int getDit() { return dit; }
    public void setDit(int dit) { this.dit = dit; }

    public int getNoc() { return noc; }
    public void setNoc(int noc) { this.noc = noc; }

    public int getCbo() { return cbo; }
    public void setCbo(int cbo) { this.cbo = cbo; }

    public int getRfc() { return rfc; }
    public void setRfc(int rfc) { this.rfc = rfc; }

    public int getCa() { return ca; }
    public void setCa(int ca) { this.ca = ca; }

    public int getCe() { return ce; }
    public void setCe(int ce) { this.ce = ce; }

    public Set<String> getDependencies() { return dependencies; }
    public void setDependencies(Set<String> dependencies) { this.dependencies = dependencies; }

    public int getNpm() { return npm; }
    public void setNpm(int npm) { this.npm = npm; }

    public int getLoc() { return loc; }
    public void setLoc(int loc) { this.loc = loc; }

    public int getMoa() { return moa; }
    public void setMoa(int moa) { this.moa = moa; }

    public int getIc() { return ic; }
    public void setIc(int ic) { this.ic = ic; }

    public int getCbm() { return cbm; }
    public void setCbm(int cbm) { this.cbm = cbm; }

    public int getMaxCc() { return maxCc; }
    public void setMaxCc(int maxCc) { this.maxCc = maxCc; }

    public double getAvgCc() { return avgCc; }
    public void setAvgCc(double avgCc) { this.avgCc = avgCc; }

    public int getLcom() { return lcom; }
    public void setLcom(int lcom) { this.lcom = lcom; }

    public double getLcom3() { return lcom3; }
    public void setLcom3(double lcom3) { this.lcom3 = lcom3; }

    public double getAmc() { return amc; }
    public void setAmc(double amc) { this.amc = amc; }

    public double getDam() { return dam; }
    public void setDam(double dam) { this.dam = dam; }

    public double getMfa() { return mfa; }
    public void setMfa(double mfa) { this.mfa = mfa; }

    public double getCam() { return cam; }
    public void setCam(double cam) { this.cam = cam; }

    public Set<String> getMethodNames() { return methodNames; }
    public void setMethodNames(Set<String> methodNames) { this.methodNames = methodNames; }

    public Set<String> getInvokedMethods() { return invokedMethods; }
    public void setInvokedMethods(Set<String> invokedMethods) { this.invokedMethods = invokedMethods; }

    public Set<String> getInheritedMethodInvocations() { return inheritedMethodInvocations; }
    public void setInheritedMethodInvocations(Set<String> inheritedMethodInvocations) { this.inheritedMethodInvocations = inheritedMethodInvocations; }

    public java.util.List<String> getFieldTypes() { return fieldTypes; }
    public void setFieldTypes(java.util.List<String> fieldTypes) { this.fieldTypes = fieldTypes; }

    public String getSuperclassName() { return superclassName; }
    public void setSuperclassName(String superclassName) { this.superclassName = superclassName; }

    public boolean isInterface() { return isInterface; }
    public void setInterface(boolean isInterface) { this.isInterface = isInterface; }
}
