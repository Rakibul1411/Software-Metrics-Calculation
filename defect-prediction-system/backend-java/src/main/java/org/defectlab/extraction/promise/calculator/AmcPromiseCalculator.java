package org.metrics.promise.calculator;

import org.metrics.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class AmcPromiseCalculator {
    private AmcPromiseCalculator() {
    }

    public static double calculate(TypeInfo type) {
        return type.methods.isEmpty()
                ? 0.0
                : type.methods.stream().mapToInt(method -> method.loc).average().orElse(0.0);
    }
}
