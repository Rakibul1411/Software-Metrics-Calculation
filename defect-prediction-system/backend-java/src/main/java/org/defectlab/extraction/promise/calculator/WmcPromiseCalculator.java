package org.metrics.promise.calculator;

import org.metrics.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class WmcPromiseCalculator {
    private WmcPromiseCalculator() {
    }

    public static int calculate(TypeInfo type) {
        return type.methods.size();
    }
}
