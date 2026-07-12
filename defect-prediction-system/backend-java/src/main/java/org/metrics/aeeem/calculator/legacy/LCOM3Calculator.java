package org.metrics.aeeem.calculator.legacy;

import java.util.Map;
import java.util.Set;

/**
 * Calculator for LCOM3 (Lack of Cohesion of Methods - Henderson-Sellers variant).
 *
 * LCOM3 is a normalized measure of cohesion.
 *
 * Formula:
 *   LCOM3 = (m - (1/a) * sum(mA(i))) / (m - 1)
 *
 * Where:
 *   m = number of methods in the class
 *   a = number of instance variables (attributes/fields)
 *   mA(i) = number of methods that access attribute i
 *   sum(mA(i)) = sum over all attributes of the count of methods accessing each attribute
 *
 * Special cases:
 *   - If m <= 1, LCOM3 = 0  (0 or 1 methods means maximum cohesion)
 *   - If a == 0, LCOM3 = 0  (no attributes means no cohesion to measure)
 *
 * LCOM3 ranges from 0 to 2:
 *   - 0 = perfect cohesion
 *   - Values > 1 indicate lack of cohesion
 */
public class LCOM3Calculator {

    /**
     * Calculate LCOM3 for a type, reusing data already extracted by LCOMCalculator.
     *
     * @param instanceVariables  Set of instance variable names
     * @param methodFieldAccess  Map of method -> set of accessed field names
     * @param isInterface        Whether the type is an interface
     * @return LCOM3 value (Henderson-Sellers)
     */
    public static double calculateLCOM3(Set<String> instanceVariables,
                                         Map<String, Set<String>> methodFieldAccess,
                                         boolean isInterface) {
        // Special case: interfaces get LCOM3 = 2 as sentinel value (matching ckjm-extended)
        if (isInterface) {
            return 2.0;
        }

        int m = methodFieldAccess.size();  // number of methods
        int a = instanceVariables.size();  // number of attributes

        // Special cases
        if (a == 0) {
            // No attributes but has methods - return 2 as sentinel (matching ckjm for classes with no fields)
            return 2.0;
        }

        if (m <= 1) {
            return 0.0;
        }

        // For each attribute, count how many methods access it
        double sumMA = 0.0;
        for (String attribute : instanceVariables) {
            int methodsAccessingAttribute = 0;
            for (Set<String> accessedFields : methodFieldAccess.values()) {
                if (accessedFields.contains(attribute)) {
                    methodsAccessingAttribute++;
                }
            }
            sumMA += methodsAccessingAttribute;
        }

        // LCOM3 = (m - (1/a) * sum(mA)) / (m - 1)
        double lcom3 = (m - (sumMA / a)) / (m - 1);

        // Clamp to 0 minimum (shouldn't be negative but just in case)
        return Math.max(0.0, lcom3);
    }
}
