package org.metrics.defectlab.analysis.promise.bytecode;

/** One compiled field declared by a class. */
public final class BytecodeFieldModel {

    private final FieldRef ref;
    private final String type;
    private final boolean privateField;
    private final boolean protectedField;
    private final boolean staticField;

    BytecodeFieldModel(
            FieldRef ref,
            String type,
            boolean privateField,
            boolean protectedField,
            boolean staticField) {
        this.ref = ref;
        this.type = type;
        this.privateField = privateField;
        this.protectedField = protectedField;
        this.staticField = staticField;
    }

    public FieldRef getRef() {
        return ref;
    }

    public String getName() {
        return ref.getName();
    }

    /** Erased type as written in the descriptor, e.g. {@code p.Node} or {@code int}. */
    public String getType() {
        return type;
    }

    public boolean isPrivateField() {
        return privateField;
    }

    public boolean isProtectedField() {
        return protectedField;
    }

    public boolean isStaticField() {
        return staticField;
    }
}
