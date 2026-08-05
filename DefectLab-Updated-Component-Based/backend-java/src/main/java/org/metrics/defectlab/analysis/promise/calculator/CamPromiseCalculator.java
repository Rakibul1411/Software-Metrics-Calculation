package org.metrics.defectlab.analysis.promise.calculator;

import java.util.HashSet;
import java.util.Set;

import org.metrics.defectlab.analysis.promise.analyzer.PromiseProjectAnalyzer.MethodInfo;
import org.metrics.defectlab.analysis.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class CamPromiseCalculator {
    private CamPromiseCalculator() {
    }

    public static double calculate(TypeInfo type) {
        if (type.methods.isEmpty()) {
            return 0.0;
        }

        // CAMC treats the owning class as an implicit parameter type for every
        // method. This makes a parameterless cohesive class equal to 1 and is
        // required to reproduce the supplied PROMISE rows.
        String selfType = type.key == null ? type.name : type.key;
        Set<String> allParameterTypes = new HashSet<>();
        allParameterTypes.add(selfType);
        for (MethodInfo method : type.methods) {
            allParameterTypes.addAll(method.parameterTypes);
        }

        int sum = 0;
        for (MethodInfo method : type.methods) {
            Set<String> methodTypes = new HashSet<>(method.parameterTypes);
            methodTypes.add(selfType);
            sum += methodTypes.size();
        }
        return (double) sum / (type.methods.size() * allParameterTypes.size());
    }
}
