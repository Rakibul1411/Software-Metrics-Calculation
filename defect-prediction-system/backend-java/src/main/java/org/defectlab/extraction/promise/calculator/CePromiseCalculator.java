package org.metrics.promise.calculator;

import java.util.Map;

import org.metrics.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class CePromiseCalculator {
    private CePromiseCalculator() {
    }

    public static int calculate(TypeInfo type, Map<String, TypeInfo> typesByKey) {
        return (int) type.outgoingTypeKeys.stream().filter(typesByKey::containsKey).count();
    }
}
