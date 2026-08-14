package org.metrics.defectlab.analysis.promise.bytecode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** One compiled class or interface. */
public final class BytecodeClassModel {

    private final String fqn;
    private final String packageName;
    private final String superclass;
    private final List<String> interfaces;
    private final boolean interfaceType;
    private final boolean nested;

    final List<BytecodeFieldModel> fields = new ArrayList<>();
    final List<BytecodeMethodModel> methods = new ArrayList<>();
    /** Types referenced by the class itself (extends/implements/field types). */
    final Set<String> referencedTypes = new LinkedHashSet<>();

    BytecodeClassModel(
            String fqn,
            String packageName,
            String superclass,
            List<String> interfaces,
            boolean interfaceType,
            boolean nested) {
        this.fqn = fqn;
        this.packageName = packageName;
        this.superclass = superclass;
        this.interfaces = List.copyOf(interfaces);
        this.interfaceType = interfaceType;
        this.nested = nested;
    }

    public String getFqn() {
        return fqn;
    }

    public String getPackageName() {
        return packageName;
    }

    /** Direct superclass, or {@code null} for {@code java.lang.Object}. */
    public String getSuperclass() {
        return superclass;
    }

    public List<String> getInterfaces() {
        return interfaces;
    }

    public boolean isInterfaceType() {
        return interfaceType;
    }

    /** True for inner, nested and anonymous classes ({@code Outer$Inner}). */
    public boolean isNested() {
        return nested;
    }

    public List<BytecodeFieldModel> getFields() {
        return fields;
    }

    /**
     * Every compiled method, including constructors and the static initializer.
     * This is the single method universe used by WMC, NPM, LCOM, LCOM3, CAM,
     * AMC, Max_CC and Avg_CC.
     */
    public List<BytecodeMethodModel> getMethods() {
        return methods;
    }

    public Set<String> getReferencedTypes() {
        return referencedTypes;
    }

    /** Total bytecode instructions across all methods; feeds LOC and AMC. */
    public int totalInstructionCount() {
        return methods.stream().mapToInt(BytecodeMethodModel::getInstructionCount).sum();
    }

    /** All types this class touches, from its own signature and its method bodies. */
    public Set<String> allReferencedTypes() {
        Set<String> all = new LinkedHashSet<>(referencedTypes);
        for (BytecodeMethodModel method : methods) {
            all.addAll(method.getReferencedTypes());
        }
        return all;
    }
}
