package org.metrics.promise.calculator;

import org.metrics.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class MaxCcPromiseCalculator {
    private MaxCcPromiseCalculator() {
    }

    public static int calculate(TypeInfo type) {
        return type.methods.stream().mapToInt(method -> method.complexity).max().orElse(0);
    }
}
