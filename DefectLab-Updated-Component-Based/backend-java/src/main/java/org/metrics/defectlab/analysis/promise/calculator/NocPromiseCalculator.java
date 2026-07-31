package org.metrics.defectlab.analysis.promise.calculator;

import java.util.Map;

import org.metrics.defectlab.analysis.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class NocPromiseCalculator {
    private NocPromiseCalculator() {
    }

    public static int calculate(TypeInfo parent, Map<String, TypeInfo> typesByKey) {
        int count = 0;
        for (TypeInfo possibleChild : typesByKey.values()) {
            if (parent.key.equals(possibleChild.superclassKey)
                    || possibleChild.interfaceKeys.contains(parent.key)) {
                count++;
            }
        }
        return count;
    }
}
