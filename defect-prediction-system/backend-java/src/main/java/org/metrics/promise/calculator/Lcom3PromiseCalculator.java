package org.metrics.promise.calculator;

import org.eclipse.jdt.core.dom.Modifier;
import org.metrics.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class Lcom3PromiseCalculator {
    private Lcom3PromiseCalculator() {
    }

    public static double calculate(TypeInfo type) {
        int attributes = (int) type.fields.stream()
                .filter(field -> !Modifier.isStatic(field.declaration.getModifiers()))
                .count();
        int methods = type.methods.size();
        if (attributes == 0 || methods <= 1) {
            return 0.0;
        }

        int accessSum = type.methods.stream()
                .mapToInt(method -> method.accessedInstanceFields.size())
                .sum();
        double value = (methods - ((double) accessSum / attributes)) / (methods - 1);
        return Math.max(0.0, value);
    }
}
