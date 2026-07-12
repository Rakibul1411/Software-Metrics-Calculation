package org.metrics.promise.calculator;

import org.metrics.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class NpmPromiseCalculator {
    private NpmPromiseCalculator() {
    }

    public static int calculate(TypeInfo type) {
        return (int) type.methods.stream().filter(method -> method.publicMethod).count();
    }
}
