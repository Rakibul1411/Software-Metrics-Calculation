package org.metrics.defectlab.analysis.promise.calculator;

import org.metrics.defectlab.analysis.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class AvgCcPromiseCalculator {
    private AvgCcPromiseCalculator() {
    }

    public static double calculate(TypeInfo type) {
        return type.methods.isEmpty()
                ? 0.0
                : type.methods.stream().mapToInt(method -> method.complexity).average().orElse(0.0);
    }
}
