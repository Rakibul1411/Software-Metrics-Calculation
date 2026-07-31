package org.metrics.defectlab.analysis.promise.calculator;

import org.metrics.defectlab.analysis.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class MaxCcPromiseCalculator {
    private MaxCcPromiseCalculator() {
    }

    public static int calculate(TypeInfo type) {
        return type.methods.stream().mapToInt(method -> method.complexity).max().orElse(0);
    }
}
