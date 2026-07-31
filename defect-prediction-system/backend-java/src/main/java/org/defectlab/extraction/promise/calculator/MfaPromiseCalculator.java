package org.metrics.promise.calculator;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.metrics.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class MfaPromiseCalculator {
    private MfaPromiseCalculator() {
    }

    public static double calculate(TypeInfo type) {
        Set<String> declared = type.methods.stream()
                .filter(method -> !method.constructor)
                .map(method -> method.binding == null
                        ? method.signature
                        : PromiseBindingUtil.methodSubsignature(method.binding))
                .collect(Collectors.toSet());

        if (type.binding == null) {
            return 0.0;
        }
        Set<String> inherited = inheritedMethodSubsignatures(type.binding);
        inherited.removeAll(declared);
        int total = declared.size() + inherited.size();
        return total == 0 ? 0.0 : (double) inherited.size() / total;
    }

    private static Set<String> inheritedMethodSubsignatures(ITypeBinding binding) {
        Set<String> inherited = new HashSet<>();
        Set<String> overridden = new HashSet<>();
        ITypeBinding current = binding.getSuperclass();
        while (current != null && !PromiseBindingUtil.OBJECT.equals(PromiseBindingUtil.typeName(current))) {
            for (IMethodBinding method : current.getDeclaredMethods()) {
                if (PromiseBindingUtil.isInheritable(method)) {
                    String signature = PromiseBindingUtil.methodSubsignature(method);
                    if (!overridden.contains(signature)) {
                        inherited.add(signature);
                    }
                }
            }
            for (IMethodBinding method : current.getDeclaredMethods()) {
                overridden.add(PromiseBindingUtil.methodSubsignature(method));
            }
            current = current.getSuperclass();
        }
        return inherited;
    }
}
