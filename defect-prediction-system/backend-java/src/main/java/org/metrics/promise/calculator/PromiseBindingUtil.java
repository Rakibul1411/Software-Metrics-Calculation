package org.metrics.promise.calculator;

import java.util.Set;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Modifier;

final class PromiseBindingUtil {
    static final String OBJECT = "java.lang.Object";

    private PromiseBindingUtil() {
    }

    static ITypeBinding safeErasure(ITypeBinding binding) {
        if (binding == null) {
            return null;
        }
        try {
            return binding.getErasure();
        } catch (RuntimeException ignored) {
            return binding;
        }
    }

    static String typeKey(ITypeBinding binding) {
        if (binding == null) {
            return null;
        }
        ITypeBinding declaration = binding.getTypeDeclaration();
        if (declaration == null) {
            declaration = binding;
        }
        String key = declaration.getKey();
        if (key != null && !key.isEmpty()) {
            return key;
        }
        String name = typeName(declaration);
        return name == null || name.isEmpty() ? null : name;
    }

    static String typeName(ITypeBinding binding) {
        if (binding == null) {
            return null;
        }
        ITypeBinding declaration = binding.getTypeDeclaration();
        if (declaration == null) {
            declaration = binding;
        }
        String qualified = declaration.getQualifiedName();
        if (qualified != null && !qualified.isEmpty()) {
            return qualified;
        }
        return declaration.getName();
    }

    static String methodSubsignature(IMethodBinding binding) {
        if (binding == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(binding.isConstructor() ? "<init>" : binding.getName()).append('(');
        ITypeBinding[] parameters = binding.getParameterTypes();
        for (int index = 0; index < parameters.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            String key = typeKey(safeErasure(parameters[index]));
            builder.append(key == null ? "?" : key);
        }
        builder.append(')');
        return builder.toString();
    }

    static boolean isInheritable(IMethodBinding method) {
        int modifiers = method.getModifiers();
        return !method.isConstructor()
                && !Modifier.isPrivate(modifiers)
                && !Modifier.isStatic(modifiers);
    }

    static void collectReferencedTypeKeys(ITypeBinding binding, Set<String> target) {
        if (binding == null) {
            return;
        }
        for (ITypeBinding argument : binding.getTypeArguments()) {
            collectReferencedTypeKeys(argument, target);
        }

        ITypeBinding erasure = safeErasure(binding);
        if (erasure == null) {
            return;
        }
        if (erasure.isArray()) {
            collectReferencedTypeKeys(erasure.getElementType(), target);
            return;
        }
        if (erasure.isPrimitive() || erasure.isNullType()
                || erasure.isTypeVariable() || erasure.isWildcardType()) {
            return;
        }

        String key = typeKey(erasure);
        if (key != null) {
            target.add(key);
        }
    }
}
