package org.metrics.defectlab.analysis.promise.calculator;

import java.util.Map;

import org.metrics.defectlab.analysis.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class CePromiseCalculator {
    private CePromiseCalculator() {
    }

    public static int calculate(TypeInfo type, Map<String, TypeInfo> typesByKey) {
        return (int) type.outgoingTypeKeys.stream().filter(typesByKey::containsKey).count();
    }
}
