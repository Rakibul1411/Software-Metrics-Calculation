package org.metrics.defectlab.analysis.promise.calculator;

import org.metrics.defectlab.analysis.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class WmcPromiseCalculator {
    private WmcPromiseCalculator() {
    }

    public static int calculate(TypeInfo type) {
        return type.methods.size();
    }
}
