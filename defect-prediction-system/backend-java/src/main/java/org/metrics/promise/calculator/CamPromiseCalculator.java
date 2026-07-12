package org.metrics.promise.calculator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.metrics.promise.analyzer.PromiseProjectAnalyzer.MethodInfo;
import org.metrics.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class CamPromiseCalculator {
    private CamPromiseCalculator() {
    }

    public static double calculate(TypeInfo type) {
        List<MethodInfo> methods = type.methods.stream()
                .filter(method -> !method.constructor)
                .collect(Collectors.toList());
        if (methods.isEmpty()) {
            return 0.0;
        }

        Set<String> allParameterTypes = new HashSet<>();
        for (MethodInfo method : methods) {
            allParameterTypes.addAll(method.parameterTypes);
        }
        if (allParameterTypes.isEmpty()) {
            return 0.0;
        }

        int sum = 0;
        for (MethodInfo method : methods) {
            sum += new HashSet<>(method.parameterTypes).size();
        }
        return (double) sum / (methods.size() * allParameterTypes.size());
    }
}
