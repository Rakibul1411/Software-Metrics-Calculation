package org.metrics.promise.calculator;

import org.metrics.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class Lcom3PromiseCalculator {
    private Lcom3PromiseCalculator() {
    }

    public static double calculate(TypeInfo type) {
        // CKJM/PROMISE uses all declared attributes here, including static
        // fields. Excluding static attributes turns constructor-only utility
        // and exception classes into an incorrect zero.
        int attributes = type.fields.size();
        int methods = type.methods.size();
        if (attributes == 0 || methods <= 1) {
            return 0.0;
        }

        int accessSum = type.methods.stream()
                .mapToInt(method -> method.accessedFields.size())
                .sum();
        double value = (methods - ((double) accessSum / attributes)) / (methods - 1);
        return Math.max(0.0, value);
    }
}
