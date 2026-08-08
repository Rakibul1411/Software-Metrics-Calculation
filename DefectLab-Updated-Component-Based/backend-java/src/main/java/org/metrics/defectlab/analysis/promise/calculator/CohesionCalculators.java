package org.metrics.defectlab.analysis.promise.calculator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.metrics.defectlab.analysis.promise.bytecode.BytecodeClassModel;
import org.metrics.defectlab.analysis.promise.bytecode.BytecodeMethodModel;
import org.metrics.defectlab.analysis.promise.bytecode.FieldRef;
import org.metrics.defectlab.analysis.promise.bytecode.MethodRef;

/** LCOM, LCOM3 and CAM. */
public final class CohesionCalculators {

    /** CKJM reports this for a class that cannot express cohesion. */
    private static final double LCOM3_DEGENERATE = 2.0;

    private CohesionCalculators() {
    }

    /**
     * LCOM, the original Chidamber-Kemerer pairwise form: pairs of methods that
     * share no field of this class, minus pairs that do, floored at zero.
     */
    public static int lcom(BytecodeClassModel type) {
        List<BytecodeMethodModel> methods = type.getMethods();
        List<Set<String>> accessed = new ArrayList<>();
        for (BytecodeMethodModel method : methods) {
            accessed.add(ownFieldsOf(type, method));
        }

        int disjoint = 0;
        int shared = 0;
        for (int first = 0; first < methods.size(); first++) {
            for (int second = first + 1; second < methods.size(); second++) {
                if (intersects(accessed.get(first), accessed.get(second))) {
                    shared++;
                } else {
                    disjoint++;
                }
            }
        }
        return Math.max(disjoint - shared, 0);
    }

    /**
     * LCOM3 (Henderson-Sellers):
     * {@code ((1/a) * sum(mu(Aj)) - m) / (1 - m)}.
     *
     * <p>Following CKJM, a method that calls another method of the same class is
     * treated as accessing the fields that callee reaches, so a delegating
     * accessor does not look uncohesive. Degenerate classes with no field or at
     * most one method report {@value #LCOM3_DEGENERATE} rather than dividing by
     * zero.
     */
    public static double lcom3(BytecodeClassModel type) {
        int methodCount = type.getMethods().size();
        int fieldCount = type.getFields().size();
        if (fieldCount == 0 || methodCount <= 1) {
            return LCOM3_DEGENERATE;
        }

        Map<MethodRef, Set<String>> effective = effectiveFieldAccess(type);
        int accessSum = 0;
        for (var field : type.getFields()) {
            for (BytecodeMethodModel method : type.getMethods()) {
                if (effective.getOrDefault(method.getRef(), Set.of())
                        .contains(field.getName())) {
                    accessSum++;
                }
            }
        }

        double average = (double) accessSum / fieldCount;
        return (average - methodCount) / (1 - methodCount);
    }

    /**
     * CAM: cohesion from the parameter lists.
     *
     * <p>A non-static method contributes an implicit {@code this} type, a static
     * method does not, and the static initializer is ignored entirely.
     */
    public static double cam(BytecodeClassModel type) {
        List<BytecodeMethodModel> methods = type.getMethods().stream()
                .filter(method -> !method.isStaticInitializer())
                .toList();
        if (methods.isEmpty()) {
            return 0.0;
        }

        Set<String> globalTypes = new LinkedHashSet<>();
        int numerator = 0;
        for (BytecodeMethodModel method : methods) {
            Set<String> methodTypes = parameterTypesOf(method);
            globalTypes.addAll(methodTypes);
            numerator += methodTypes.size();
        }

        int denominator = globalTypes.size() * methods.size();
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private static Set<String> parameterTypesOf(BytecodeMethodModel method) {
        Set<String> types = new LinkedHashSet<>(method.getArgumentTypes());
        if (!method.isStaticMethod()) {
            types.add("this");
        }
        return types;
    }

    /**
     * Field names of this class reached by each method, following calls to other
     * methods of the same class transitively.
     */
    private static Map<MethodRef, Set<String>> effectiveFieldAccess(BytecodeClassModel type) {
        Map<MethodRef, BytecodeMethodModel> byRef = new LinkedHashMap<>();
        type.getMethods().forEach(method -> byRef.put(method.getRef(), method));

        Map<MethodRef, Set<String>> effective = new LinkedHashMap<>();
        for (BytecodeMethodModel method : type.getMethods()) {
            Set<String> reached = new LinkedHashSet<>();
            Set<MethodRef> visited = new LinkedHashSet<>();
            Deque<BytecodeMethodModel> pending = new ArrayDeque<>();
            pending.push(method);
            visited.add(method.getRef());

            while (!pending.isEmpty()) {
                BytecodeMethodModel current = pending.pop();
                reached.addAll(ownFieldsOf(type, current));
                for (MethodRef invoked : current.getInvokedMethods()) {
                    BytecodeMethodModel sameClass = byRef.get(invoked);
                    if (sameClass != null && visited.add(invoked)) {
                        pending.push(sameClass);
                    }
                }
            }
            effective.put(method.getRef(), reached);
        }
        return effective;
    }

    /** Names of fields declared by this class that the method touches. */
    private static Set<String> ownFieldsOf(
            BytecodeClassModel type, BytecodeMethodModel method) {
        Set<String> names = new LinkedHashSet<>();
        for (FieldRef accessed : method.getAccessedFields()) {
            if (type.getFqn().equals(accessed.getOwner())) {
                names.add(accessed.getName());
            }
        }
        return names;
    }

    private static boolean intersects(Set<String> first, Set<String> second) {
        Set<String> smaller = first.size() <= second.size() ? first : second;
        Set<String> larger = smaller == first ? second : first;
        for (String value : smaller) {
            if (larger.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
