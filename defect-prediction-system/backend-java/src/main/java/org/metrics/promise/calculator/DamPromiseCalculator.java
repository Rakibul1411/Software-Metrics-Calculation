package org.metrics.promise.calculator;

import org.eclipse.jdt.core.dom.Modifier;
import org.metrics.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class DamPromiseCalculator {
    private DamPromiseCalculator() {
    }

    public static double calculate(TypeInfo type) {
        if (type.fields.isEmpty()) {
            return 0.0;
        }
        long encapsulated = type.fields.stream()
                .filter(field -> Modifier.isPrivate(field.declaration.getModifiers())
                        || Modifier.isProtected(field.declaration.getModifiers()))
                .count();
        return (double) encapsulated / type.fields.size();
    }
}
