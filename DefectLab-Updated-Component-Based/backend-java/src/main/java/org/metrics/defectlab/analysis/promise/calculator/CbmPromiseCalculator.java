package org.metrics.defectlab.analysis.promise.calculator;

import java.util.HashSet;
import java.util.Set;

import org.metrics.defectlab.analysis.promise.analyzer.PromiseProjectAnalyzer.MethodInfo;
import org.metrics.defectlab.analysis.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class CbmPromiseCalculator {
    private CbmPromiseCalculator() {
    }

    public static int calculate(TypeInfo type) {
        Set<String> pairs = new HashSet<>();
        for (MethodInfo method : type.methods) {
            pairs.addAll(method.inheritedCallPairs);
        }
        return pairs.size();
    }
}
