package org.metrics.aeeem.model;

import java.util.HashSet;
import java.util.Set;

/**
 * Data model to hold calculated metrics for a Java class (AEEEM format).
 * Holds AEEEM-compatible static metrics plus internal hierarchy data.
 */
public class AeeemMetricResult {
    private String fullyQualifiedName;
    private String sourcePath;
    private String superclassName;
    private boolean isInterface;
    private Set<String> dependencies = new HashSet<>();
    private Set<String> methodNames = new HashSet<>();
    private int declaredAttributeCount;

    // ck_oo metrics
    private double ckOoNumberOfPrivateMethods;
    private double ckOoNumberOfPublicAttributes;
    private double ckOoNoc;
    private double ckOoWmc;
    private double ckOoFanOut;
    private double ckOoNumberOfLinesOfCode;
    private double ckOoNumberOfAttributesInherited;
    private double ckOoNumberOfMethods;
    private double ckOoDit;
    private double ckOoFanIn;
    private double ckOoLcom;
    private double ckOoRfc;
    private double ckOoCbo;
    private double ckOoNumberOfAttributes;
    private double ckOoNumberOfPrivateAttributes;
    private double ckOoNumberOfMethodsInherited;
    private double ckOoNumberOfPublicMethods;

    // LDHH metrics
    private double ldhhLcom;
    private double ldhhFanIn;
    private double ldhhNumberOfPublicMethods;
    private double ldhhNumberOfPrivateAttributes;
    private double ldhhNumberOfPublicAttributes;
    private double ldhhNumberOfPrivateMethods;
    private double ldhhNumberOfAttributesInherited;
    private double ldhhNoc;
    private double ldhhWmc;
    private double ldhhNumberOfAttributes;
    private double ldhhNumberOfLinesOfCode;
    private double ldhhDit;
    private double ldhhFanOut;
    private double ldhhNumberOfMethodsInherited;
    private double ldhhRfc;
    private double ldhhCbo;
    private double ldhhNumberOfMethods;

    // WCHU metrics
    private double wchuNumberOfPublicAttributes;
    private double wchuNumberOfAttributes;
    private double wchuFanIn;
    private double wchuNumberOfPrivateMethods;
    private double wchuNumberOfMethods;
    private double wchuNumberOfPrivateAttributes;
    private double wchuNoc;
    private double wchuWmc;
    private double wchuDit;
    private double wchuNumberOfAttributesInherited;
    private double wchuFanOut;
    private double wchuLcom;
    private double wchuRfc;
    private double wchuNumberOfPublicMethods;
    private double wchuCbo;
    private double wchuNumberOfMethodsInherited;
    private double wchuNumberOfLinesOfCode;

    // Entropy metrics (Cvs)
    private double cvsWEntropy;
    private double cvsEntropy;
    private double cvsLogEntropy;
    private double cvsLinEntropy;
    private double cvsExpEntropy;

    public AeeemMetricResult() {
    }

    public AeeemMetricResult(String fullyQualifiedName) {
        this.fullyQualifiedName = fullyQualifiedName;
    }

    public String getFullyQualifiedName() {
        return fullyQualifiedName;
    }

    public void setFullyQualifiedName(String fullyQualifiedName) {
        this.fullyQualifiedName = fullyQualifiedName;
    }

    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }

    public String getSuperclassName() { return superclassName; }
    public void setSuperclassName(String superclassName) { this.superclassName = superclassName; }
    public boolean isInterface() { return isInterface; }
    public void setInterface(boolean anInterface) { isInterface = anInterface; }
    public Set<String> getDependencies() { return dependencies; }
    public void setDependencies(Set<String> dependencies) { this.dependencies = dependencies; }
    public Set<String> getMethodNames() { return methodNames; }
    public void setMethodNames(Set<String> methodNames) { this.methodNames = methodNames; }
    public int getDeclaredAttributeCount() { return declaredAttributeCount; }
    public void setDeclaredAttributeCount(int count) { this.declaredAttributeCount = count; }

    // Getters and Setters for ck_oo
    public double getCkOoNumberOfPrivateMethods() { return ckOoNumberOfPrivateMethods; }
    public void setCkOoNumberOfPrivateMethods(double val) { this.ckOoNumberOfPrivateMethods = val; }

    public double getCkOoNumberOfPublicAttributes() { return ckOoNumberOfPublicAttributes; }
    public void setCkOoNumberOfPublicAttributes(double val) { this.ckOoNumberOfPublicAttributes = val; }

    public double getCkOoNoc() { return ckOoNoc; }
    public void setCkOoNoc(double val) { this.ckOoNoc = val; }

    public double getCkOoWmc() { return ckOoWmc; }
    public void setCkOoWmc(double val) { this.ckOoWmc = val; }

    public double getCkOoFanOut() { return ckOoFanOut; }
    public void setCkOoFanOut(double val) { this.ckOoFanOut = val; }

    public double getCkOoNumberOfLinesOfCode() { return ckOoNumberOfLinesOfCode; }
    public void setCkOoNumberOfLinesOfCode(double val) { this.ckOoNumberOfLinesOfCode = val; }

    public double getCkOoNumberOfAttributesInherited() { return ckOoNumberOfAttributesInherited; }
    public void setCkOoNumberOfAttributesInherited(double val) { this.ckOoNumberOfAttributesInherited = val; }

    public double getCkOoNumberOfMethods() { return ckOoNumberOfMethods; }
    public void setCkOoNumberOfMethods(double val) { this.ckOoNumberOfMethods = val; }

    public double getCkOoDit() { return ckOoDit; }
    public void setCkOoDit(double val) { this.ckOoDit = val; }

    public double getCkOoFanIn() { return ckOoFanIn; }
    public void setCkOoFanIn(double val) { this.ckOoFanIn = val; }

    public double getCkOoLcom() { return ckOoLcom; }
    public void setCkOoLcom(double val) { this.ckOoLcom = val; }

    public double getCkOoRfc() { return ckOoRfc; }
    public void setCkOoRfc(double val) { this.ckOoRfc = val; }

    public double getCkOoCbo() { return ckOoCbo; }
    public void setCkOoCbo(double val) { this.ckOoCbo = val; }

    public double getCkOoNumberOfAttributes() { return ckOoNumberOfAttributes; }
    public void setCkOoNumberOfAttributes(double val) { this.ckOoNumberOfAttributes = val; }

    public double getCkOoNumberOfPrivateAttributes() { return ckOoNumberOfPrivateAttributes; }
    public void setCkOoNumberOfPrivateAttributes(double val) { this.ckOoNumberOfPrivateAttributes = val; }

    public double getCkOoNumberOfMethodsInherited() { return ckOoNumberOfMethodsInherited; }
    public void setCkOoNumberOfMethodsInherited(double val) { this.ckOoNumberOfMethodsInherited = val; }

    public double getCkOoNumberOfPublicMethods() { return ckOoNumberOfPublicMethods; }
    public void setCkOoNumberOfPublicMethods(double val) { this.ckOoNumberOfPublicMethods = val; }

    // Getters and Setters for LDHH
    public double getLdhhLcom() { return ldhhLcom; }
    public void setLdhhLcom(double val) { this.ldhhLcom = val; }

    public double getLdhhFanIn() { return ldhhFanIn; }
    public void setLdhhFanIn(double val) { this.ldhhFanIn = val; }

    public double getLdhhNumberOfPublicMethods() { return ldhhNumberOfPublicMethods; }
    public void setLdhhNumberOfPublicMethods(double val) { this.ldhhNumberOfPublicMethods = val; }

    public double getLdhhNumberOfPrivateAttributes() { return ldhhNumberOfPrivateAttributes; }
    public void setLdhhNumberOfPrivateAttributes(double val) { this.ldhhNumberOfPrivateAttributes = val; }

    public double getLdhhNumberOfPublicAttributes() { return ldhhNumberOfPublicAttributes; }
    public void setLdhhNumberOfPublicAttributes(double val) { this.ldhhNumberOfPublicAttributes = val; }

    public double getLdhhNumberOfPrivateMethods() { return ldhhNumberOfPrivateMethods; }
    public void setLdhhNumberOfPrivateMethods(double val) { this.ldhhNumberOfPrivateMethods = val; }

    public double getLdhhNumberOfAttributesInherited() { return ldhhNumberOfAttributesInherited; }
    public void setLdhhNumberOfAttributesInherited(double val) { this.ldhhNumberOfAttributesInherited = val; }

    public double getLdhhNoc() { return ldhhNoc; }
    public void setLdhhNoc(double val) { this.ldhhNoc = val; }

    public double getLdhhWmc() { return ldhhWmc; }
    public void setLdhhWmc(double val) { this.ldhhWmc = val; }

    public double getLdhhNumberOfAttributes() { return ldhhNumberOfAttributes; }
    public void setLdhhNumberOfAttributes(double val) { this.ldhhNumberOfAttributes = val; }

    public double getLdhhNumberOfLinesOfCode() { return ldhhNumberOfLinesOfCode; }
    public void setLdhhNumberOfLinesOfCode(double val) { this.ldhhNumberOfLinesOfCode = val; }

    public double getLdhhDit() { return ldhhDit; }
    public void setLdhhDit(double val) { this.ldhhDit = val; }

    public double getLdhhFanOut() { return ldhhFanOut; }
    public void setLdhhFanOut(double val) { this.ldhhFanOut = val; }

    public double getLdhhNumberOfMethodsInherited() { return ldhhNumberOfMethodsInherited; }
    public void setLdhhNumberOfMethodsInherited(double val) { this.ldhhNumberOfMethodsInherited = val; }

    public double getLdhhRfc() { return ldhhRfc; }
    public void setLdhhRfc(double val) { this.ldhhRfc = val; }

    public double getLdhhCbo() { return ldhhCbo; }
    public void setLdhhCbo(double val) { this.ldhhCbo = val; }

    public double getLdhhNumberOfMethods() { return ldhhNumberOfMethods; }
    public void setLdhhNumberOfMethods(double val) { this.ldhhNumberOfMethods = val; }

    // Getters and Setters for WCHU
    public double getWchuNumberOfPublicAttributes() { return wchuNumberOfPublicAttributes; }
    public void setWchuNumberOfPublicAttributes(double val) { this.wchuNumberOfPublicAttributes = val; }

    public double getWchuNumberOfAttributes() { return wchuNumberOfAttributes; }
    public void setWchuNumberOfAttributes(double val) { this.wchuNumberOfAttributes = val; }

    public double getWchuFanIn() { return wchuFanIn; }
    public void setWchuFanIn(double val) { this.wchuFanIn = val; }

    public double getWchuNumberOfPrivateMethods() { return wchuNumberOfPrivateMethods; }
    public void setWchuNumberOfPrivateMethods(double val) { this.wchuNumberOfPrivateMethods = val; }

    public double getWchuNumberOfMethods() { return wchuNumberOfMethods; }
    public void setWchuNumberOfMethods(double val) { this.wchuNumberOfMethods = val; }

    public double getWchuNumberOfPrivateAttributes() { return wchuNumberOfPrivateAttributes; }
    public void setWchuNumberOfPrivateAttributes(double val) { this.wchuNumberOfPrivateAttributes = val; }

    public double getWchuNoc() { return wchuNoc; }
    public void setWchuNoc(double val) { this.wchuNoc = val; }

    public double getWchuWmc() { return wchuWmc; }
    public void setWchuWmc(double val) { this.wchuWmc = val; }

    public double getWchuDit() { return wchuDit; }
    public void setWchuDit(double val) { this.wchuDit = val; }

    public double getWchuNumberOfAttributesInherited() { return wchuNumberOfAttributesInherited; }
    public void setWchuNumberOfAttributesInherited(double val) { this.wchuNumberOfAttributesInherited = val; }

    public double getWchuFanOut() { return wchuFanOut; }
    public void setWchuFanOut(double val) { this.wchuFanOut = val; }

    public double getWchuLcom() { return wchuLcom; }
    public void setWchuLcom(double val) { this.wchuLcom = val; }

    public double getWchuRfc() { return wchuRfc; }
    public void setWchuRfc(double val) { this.wchuRfc = val; }

    public double getWchuNumberOfPublicMethods() { return wchuNumberOfPublicMethods; }
    public void setWchuNumberOfPublicMethods(double val) { this.wchuNumberOfPublicMethods = val; }

    public double getWchuCbo() { return wchuCbo; }
    public void setWchuCbo(double val) { this.wchuCbo = val; }

    public double getWchuNumberOfMethodsInherited() { return wchuNumberOfMethodsInherited; }
    public void setWchuNumberOfMethodsInherited(double val) { this.wchuNumberOfMethodsInherited = val; }

    public double getWchuNumberOfLinesOfCode() { return wchuNumberOfLinesOfCode; }
    public void setWchuNumberOfLinesOfCode(double val) { this.wchuNumberOfLinesOfCode = val; }

    // Getters and Setters for Entropy
    public double getCvsWEntropy() { return cvsWEntropy; }
    public void setCvsWEntropy(double val) { this.cvsWEntropy = val; }

    public double getCvsEntropy() { return cvsEntropy; }
    public void setCvsEntropy(double val) { this.cvsEntropy = val; }

    public double getCvsLogEntropy() { return cvsLogEntropy; }
    public void setCvsLogEntropy(double val) { this.cvsLogEntropy = val; }

    public double getCvsLinEntropy() { return cvsLinEntropy; }
    public void setCvsLinEntropy(double val) { this.cvsLinEntropy = val; }

    public double getCvsExpEntropy() { return cvsExpEntropy; }
    public void setCvsExpEntropy(double val) { this.cvsExpEntropy = val; }

}
