package org.metrics.defectlab.analysis.promise.bytecode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Superclass chains and immediate-subclass counts.
 *
 * <p>Only {@code extends} edges are recorded. A class implementing an interface
 * is deliberately not a child of that interface, which is what keeps NOC equal
 * to the number of immediate descendants.
 */
public final class InheritanceGraph {

    private final Map<String, Integer> immediateSubclassCount;
    private final Map<String, String> superclassByFqn;

    private InheritanceGraph(
            Map<String, Integer> immediateSubclassCount,
            Map<String, String> superclassByFqn) {
        this.immediateSubclassCount = immediateSubclassCount;
        this.superclassByFqn = superclassByFqn;
    }

    static InheritanceGraph build(BytecodeProjectModel model) {
        Map<String, Integer> children = new LinkedHashMap<>();
        Map<String, String> superclasses = new LinkedHashMap<>();

        for (BytecodeClassModel type : model.getClasses()) {
            String superclass = type.getSuperclass();
            if (superclass != null) {
                superclasses.put(type.getFqn(), superclass);
                children.merge(superclass, 1, Integer::sum);
            }
        }
        return new InheritanceGraph(children, superclasses);
    }

    /** NOC: immediate subclasses only, so a grandchild is not counted. */
    public int immediateSubclassesOf(String fqn) {
        return immediateSubclassCount.getOrDefault(fqn, 0);
    }

    /**
     * Superclass chain from the direct parent upwards, stopping before
     * {@code java.lang.Object} and guarding against a cyclic hierarchy.
     */
    public List<String> superclassChain(String fqn) {
        List<String> chain = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        String current = superclassByFqn.get(fqn);
        while (current != null
                && !"java.lang.Object".equals(current)
                && visited.add(current)) {
            chain.add(current);
            current = superclassByFqn.get(current);
        }
        return chain;
    }

    /**
     * DIT: inheritance levels below the top of the hierarchy. A class extending
     * {@code java.lang.Object} directly has depth 1.
     */
    public int depthOf(String fqn) {
        int depth = 0;
        Set<String> visited = new LinkedHashSet<>();
        String current = fqn;
        while (visited.add(current)) {
            String superclass = superclassByFqn.get(current);
            if (superclass == null) {
                // Either java.lang.Object itself, or the parent left the model.
                break;
            }
            depth++;
            if ("java.lang.Object".equals(superclass)) {
                break;
            }
            current = superclass;
        }
        return depth;
    }

    /** True when the parent is somewhere above the class in the extends chain. */
    public boolean isAncestor(String fqn, String candidate) {
        return superclassChain(fqn).contains(candidate);
    }
}
