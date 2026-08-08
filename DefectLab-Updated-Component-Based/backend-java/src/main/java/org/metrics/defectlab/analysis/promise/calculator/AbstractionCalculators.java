package org.metrics.defectlab.analysis.promise.calculator;

import java.util.LinkedHashSet;
import java.util.Set;

import org.metrics.defectlab.analysis.promise.bytecode.BytecodeClassModel;
import org.metrics.defectlab.analysis.promise.bytecode.BytecodeFieldModel;
import org.metrics.defectlab.analysis.promise.bytecode.BytecodeMethodModel;
import org.metrics.defectlab.analysis.promise.bytecode.BytecodeProjectModel;
import org.metrics.defectlab.analysis.promise.bytecode.JdkClassPolicy;

/** DIT, NOC, DAM, MOA and MFA. */
public final class AbstractionCalculators {

    private AbstractionCalculators() {
    }

    /** DIT: inheritance levels; a direct subclass of Object has depth 1. */
    public static int dit(BytecodeClassModel type, BytecodeProjectModel project) {
        return project.getInheritanceGraph().depthOf(type.getFqn());
    }

    /** NOC: immediate subclasses. Implementing an interface is not inheritance here. */
    public static int noc(BytecodeClassModel type, BytecodeProjectModel project) {
        return project.getInheritanceGraph().immediateSubclassesOf(type.getFqn());
    }

    /** DAM: share of declared fields that are private or protected. */
    public static double dam(BytecodeClassModel type) {
        int total = type.getFields().size();
        if (total == 0) {
            return 0.0;
        }
        long encapsulated = type.getFields().stream()
                .filter(field -> field.isPrivateField() || field.isProtectedField())
                .count();
        return (double) encapsulated / total;
    }

    /**
     * MOA: declared fields whose type is a class of the analysed project.
     *
     * <p>A field typed as its own declaring class counts, so a linked structure
     * such as {@code class Node { Node next; }} reports 1. The erased field type
     * is used, so {@code List<Node>} is a JDK field rather than a Node field.
     */
    public static int moa(BytecodeClassModel type, BytecodeProjectModel project) {
        int count = 0;
        for (BytecodeFieldModel field : type.getFields()) {
            String fieldType = field.getType();
            // isProjectClass, not the broader isAnalyzedClass: the model also
            // holds externally-resolved JDK/dependency ancestors (see
            // BytecodeProjectAnalyzer#resolveExternalAncestors), and a field
            // typed as one of those is not an aggregation of the project's own
            // classes.
            if (!JdkClassPolicy.isPrimitive(fieldType)
                    && project.isProjectClass(fieldType)) {
                count++;
            }
        }
        return count;
    }

    /**
     * MFA: inherited methods over all methods reachable by the class.
     *
     * <p>Constructors, static initializers and anything declared by
     * {@code java.lang.Object} are ignored. The superclass chain is walked as
     * compiled, without adding accessibility or override rules of our own.
     */
    public static double mfa(BytecodeClassModel type, BytecodeProjectModel project) {
        int own = mfaMethodCount(type);

        // CKJM sums raw per-ancestor method COUNTS; it never deduplicates by
        // signature and never subtracts overrides, so a method the subclass
        // overrides is counted once in the parent and again in the child.
        // java.lang.Object is explicitly skipped (superclassChain already stops
        // before it), and an ancestor we cannot resolve simply contributes 0.
        int inherited = 0;
        for (String ancestorName : project.getInheritanceGraph().superclassChain(type.getFqn())) {
            BytecodeClassModel ancestor = project.getClass(ancestorName);
            if (ancestor != null) {
                inherited += mfaMethodCount(ancestor);
            }
        }

        int total = own + inherited;
        return total == 0 ? 0.0 : (double) inherited / total;
    }

    /** Compiled methods excluding constructors and static initializers. */
    private static int mfaMethodCount(BytecodeClassModel type) {
        int count = 0;
        for (BytecodeMethodModel method : type.getMethods()) {
            if (countsForMfa(method)) {
                count++;
            }
        }
        return count;
    }

    private static boolean countsForMfa(BytecodeMethodModel method) {
        return !method.isConstructor() && !method.isStaticInitializer();
    }
}
