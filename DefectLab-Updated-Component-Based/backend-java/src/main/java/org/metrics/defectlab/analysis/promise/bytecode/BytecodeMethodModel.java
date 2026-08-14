package org.metrics.defectlab.analysis.promise.bytecode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** One compiled method, with the facts every PROMISE metric needs. */
public final class BytecodeMethodModel {

    private final MethodRef ref;
    private final List<String> argumentTypes;
    private final String returnType;
    private final List<String> declaredExceptions;
    private final boolean publicMethod;
    private final boolean staticMethod;
    private final boolean abstractMethod;
    private final boolean nativeMethod;
    private final int instructionCount;
    private final int cyclomaticComplexity;

    final Set<MethodRef> invokedMethods = new LinkedHashSet<>();
    final Set<FieldRef> fieldReads = new LinkedHashSet<>();
    final Set<FieldRef> fieldWrites = new LinkedHashSet<>();
    /** Every type this method touches; feeds the coupling graph. */
    final Set<String> referencedTypes = new LinkedHashSet<>();

    BytecodeMethodModel(
            MethodRef ref,
            List<String> argumentTypes,
            String returnType,
            List<String> declaredExceptions,
            boolean publicMethod,
            boolean staticMethod,
            boolean abstractMethod,
            boolean nativeMethod,
            int instructionCount,
            int cyclomaticComplexity) {
        this.ref = ref;
        this.argumentTypes = List.copyOf(argumentTypes);
        this.returnType = returnType;
        this.declaredExceptions = List.copyOf(declaredExceptions);
        this.publicMethod = publicMethod;
        this.staticMethod = staticMethod;
        this.abstractMethod = abstractMethod;
        this.nativeMethod = nativeMethod;
        this.instructionCount = instructionCount;
        this.cyclomaticComplexity = cyclomaticComplexity;
    }

    public MethodRef getRef() {
        return ref;
    }

    public String getName() {
        return ref.getName();
    }

    public String getOwner() {
        return ref.getOwner();
    }

    public List<String> getArgumentTypes() {
        return argumentTypes;
    }

    public String getReturnType() {
        return returnType;
    }

    public List<String> getDeclaredExceptions() {
        return declaredExceptions;
    }

    public boolean isPublicMethod() {
        return publicMethod;
    }

    public boolean isStaticMethod() {
        return staticMethod;
    }

    public boolean isAbstractMethod() {
        return abstractMethod;
    }

    public boolean isNativeMethod() {
        return nativeMethod;
    }

    public boolean isConstructor() {
        return "<init>".equals(ref.getName());
    }

    public boolean isStaticInitializer() {
        return "<clinit>".equals(ref.getName());
    }

    public int getInstructionCount() {
        return instructionCount;
    }

    public int getCyclomaticComplexity() {
        return cyclomaticComplexity;
    }

    public Set<MethodRef> getInvokedMethods() {
        return invokedMethods;
    }

    public Set<FieldRef> getFieldReads() {
        return fieldReads;
    }

    public Set<FieldRef> getFieldWrites() {
        return fieldWrites;
    }

    /** Fields this method touches in either direction. */
    public Set<FieldRef> getAccessedFields() {
        Set<FieldRef> accessed = new LinkedHashSet<>(fieldReads);
        accessed.addAll(fieldWrites);
        return accessed;
    }

    public Set<String> getReferencedTypes() {
        return referencedTypes;
    }
}
