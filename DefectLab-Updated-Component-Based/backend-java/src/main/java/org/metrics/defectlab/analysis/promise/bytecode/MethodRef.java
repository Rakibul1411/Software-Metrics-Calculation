package org.metrics.defectlab.analysis.promise.bytecode;

import java.util.List;
import java.util.Objects;

/**
 * Identity of a compiled method. The argument list is part of the identity so
 * that overloads stay distinct, which RFC and the inheritance-coupling metrics
 * both rely on.
 */
public final class MethodRef {

    private final String owner;
    private final String name;
    private final String argumentSignature;

    public MethodRef(String owner, String name, List<String> argumentTypes) {
        this.owner = owner;
        this.name = name;
        this.argumentSignature = String.join(",", argumentTypes);
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    /** Name plus arguments, without the owner: the override-matching key. */
    public String subsignature() {
        return name + "(" + argumentSignature + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MethodRef)) {
            return false;
        }
        MethodRef that = (MethodRef) other;
        return owner.equals(that.owner)
                && name.equals(that.name)
                && argumentSignature.equals(that.argumentSignature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, name, argumentSignature);
    }

    @Override
    public String toString() {
        return owner + "#" + subsignature();
    }
}
