package org.metrics.promise.calculator;

import java.util.HashSet;
import java.util.Set;

import org.metrics.promise.analyzer.PromiseProjectAnalyzer.MethodInfo;
import org.metrics.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class RfcPromiseCalculator {
    private RfcPromiseCalculator() {
    }

    public static int calculate(TypeInfo type) {
        Set<String> responseSet = new HashSet<>();
        for (MethodInfo method : type.methods) {
            if (method.signature != null) {
                responseSet.add(method.signature);
            }
            responseSet.addAll(method.invokedMethodKeys);
        }
        return responseSet.size();
    }
}
