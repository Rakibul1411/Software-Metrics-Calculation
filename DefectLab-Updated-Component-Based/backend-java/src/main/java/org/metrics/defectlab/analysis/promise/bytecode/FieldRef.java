package org.metrics.defectlab.analysis.promise.bytecode;

import java.util.Objects;

/** Identity of a compiled field: declaring class plus field name. */
public final class FieldRef {

    private final String owner;
    private final String name;

    public FieldRef(String owner, String name) {
        this.owner = owner;
        this.name = name;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FieldRef)) {
            return false;
        }
        FieldRef that = (FieldRef) other;
        return owner.equals(that.owner) && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, name);
    }

    @Override
    public String toString() {
        return owner + "." + name;
    }
}
