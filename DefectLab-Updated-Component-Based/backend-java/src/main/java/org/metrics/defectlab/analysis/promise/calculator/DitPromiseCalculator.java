package org.metrics.defectlab.analysis.promise.calculator;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

import org.eclipse.jdt.core.dom.ITypeBinding;
import org.metrics.defectlab.analysis.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class DitPromiseCalculator {
    private DitPromiseCalculator() {
    }

    public static int calculate(TypeInfo type, List<String> diagnostics) {
        if (type.isInterface()) {
            return 0;
        }

        int depth = 0;
        Set<String> visited = new HashSet<>();
        ITypeBinding current = type.binding;
        while (current != null) {
            ITypeBinding parent = current.getSuperclass();
            if (parent == null) {
                break;
            }
            String parentName = PromiseBindingUtil.typeName(parent);
            if (parentName == null || PromiseBindingUtil.OBJECT.equals(parentName)) {
                depth++;
                break;
            }
            String key = PromiseBindingUtil.typeKey(parent);
            if (key != null && !visited.add(key)) {
                diagnostics.add("Inheritance cycle detected while calculating DIT for " + type.name);
                break;
            }
            depth++;
            current = parent;
        }
        return depth;
    }
}
