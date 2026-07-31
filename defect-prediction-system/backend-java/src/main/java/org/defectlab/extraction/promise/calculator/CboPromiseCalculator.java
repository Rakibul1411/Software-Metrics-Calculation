package org.metrics.promise.calculator;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.metrics.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class CboPromiseCalculator {
    private CboPromiseCalculator() {
    }

    public static int calculate(TypeInfo type, Map<String, TypeInfo> typesByKey) {
        // CKJM/PROMISE CBO is direction-neutral. Ca and Ce retain the incoming
        // and outgoing views, while CBO counts the union so a class coupled in
        // both directions is counted once.
        Set<String> coupledClasses = new HashSet<>();
        type.incomingTypeKeys.stream()
                .filter(typesByKey::containsKey)
                .filter(key -> !key.equals(type.key))
                .forEach(coupledClasses::add);
        type.outgoingTypeKeys.stream()
                .filter(typesByKey::containsKey)
                .filter(key -> !key.equals(type.key))
                .forEach(coupledClasses::add);
        return coupledClasses.size();
    }
}
