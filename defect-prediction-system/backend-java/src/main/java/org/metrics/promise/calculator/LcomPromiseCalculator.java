package org.metrics.promise.calculator;

import org.metrics.promise.analyzer.PromiseProjectAnalyzer.MethodInfo;
import org.metrics.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class LcomPromiseCalculator {
    private LcomPromiseCalculator() {
    }

    public static int calculate(TypeInfo type) {
        if (type.methods.size() < 2) {
            return 0;
        }

        int disjointPairs = 0;
        int sharedPairs = 0;
        for (int i = 0; i < type.methods.size(); i++) {
            for (int j = i + 1; j < type.methods.size(); j++) {
                if (sharesAnyField(type.methods.get(i), type.methods.get(j))) {
                    sharedPairs++;
                } else {
                    disjointPairs++;
                }
            }
        }
        return Math.max(disjointPairs - sharedPairs, 0);
    }

    private static boolean sharesAnyField(MethodInfo first, MethodInfo second) {
        for (String field : first.accessedFields) {
            if (second.accessedFields.contains(field)) {
                return true;
            }
        }
        return false;
    }
}
