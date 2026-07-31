package org.metrics.defectlab.analysis.promise.calculator;

import java.util.Map;

import org.metrics.defectlab.analysis.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class CaPromiseCalculator {
    private CaPromiseCalculator() {
    }

    public static int calculate(TypeInfo type, Map<String, TypeInfo> typesByKey) {
        return (int) type.incomingTypeKeys.stream().filter(typesByKey::containsKey).count();
    }
}
