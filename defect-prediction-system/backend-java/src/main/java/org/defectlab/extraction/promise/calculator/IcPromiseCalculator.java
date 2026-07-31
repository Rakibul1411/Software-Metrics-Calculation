package org.metrics.promise.calculator;

import java.util.HashSet;
import java.util.Set;

import org.metrics.promise.analyzer.PromiseProjectAnalyzer.MethodInfo;
import org.metrics.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class IcPromiseCalculator {
    private IcPromiseCalculator() {
    }

    public static int calculate(TypeInfo type) {
        Set<String> ancestors = new HashSet<>();
        for (MethodInfo method : type.methods) {
            ancestors.addAll(method.inheritedCallAncestors);
        }
        return ancestors.size();
    }
}
