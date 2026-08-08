package org.metrics.defectlab.analysis.promise.bytecode;

/**
 * Decides which types count as JDK classes.
 *
 * <p>CKJM excludes Java SDK classes from coupling by default, so CBO, Ca and Ce
 * ignore them. RFC is deliberately unaffected: a call to a JDK method still
 * belongs to the response set of the class.
 */
public final class JdkClassPolicy {

    private JdkClassPolicy() {
    }

    public static boolean isJdkClass(String fqn) {
        return fqn != null && (fqn.startsWith("java.") || fqn.startsWith("javax."));
    }

    /** Primitives and {@code void} never become coupled classes. */
    public static boolean isPrimitive(String type) {
        switch (type) {
            case "boolean":
            case "byte":
            case "char":
            case "short":
            case "int":
            case "long":
            case "float":
            case "double":
            case "void":
                return true;
            default:
                return false;
        }
    }

    /** True when the type may participate in coupling at all. */
    public static boolean isCandidateForCoupling(String type) {
        return type != null && !type.isEmpty() && !isPrimitive(type) && !isJdkClass(type);
    }
}
