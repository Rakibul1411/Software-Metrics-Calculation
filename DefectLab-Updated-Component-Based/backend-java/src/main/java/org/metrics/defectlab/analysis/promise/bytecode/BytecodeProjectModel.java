package org.metrics.defectlab.analysis.promise.bytecode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every compiled class of one analysed release, plus the graphs derived from
 * them. Built once per extraction; never shared between projects.
 */
public final class BytecodeProjectModel {

    private final Map<String, BytecodeClassModel> classesByFqn = new LinkedHashMap<>();
    private final Set<String> projectClassNames;

    private CouplingGraph couplingGraph;
    private InheritanceGraph inheritanceGraph;

    BytecodeProjectModel(Set<String> projectClassNames) {
        this.projectClassNames = Set.copyOf(projectClassNames);
    }

    void add(BytecodeClassModel model) {
        classesByFqn.putIfAbsent(model.getFqn(), model);
    }

    void freeze() {
        this.inheritanceGraph = InheritanceGraph.build(this);
        canonicaliseFieldOwners();
        this.couplingGraph = CouplingGraph.build(this);
    }

    /**
     * Rewrites every field access to name the class that actually declares the
     * field.
     *
     * <p>The compiler records a field reference against the class named at the
     * use site, so a subclass writing an inherited field emits
     * {@code Child.state} while the parent reading it emits {@code Parent.state}.
     * Left alone, those two would look like different fields, which would hide
     * the attribute dependency behind IC/CBM and would let LCOM credit a
     * subclass with a field it does not declare.
     */
    private void canonicaliseFieldOwners() {
        for (BytecodeClassModel type : classesByFqn.values()) {
            for (BytecodeMethodModel method : type.getMethods()) {
                rewrite(method.fieldReads);
                rewrite(method.fieldWrites);
            }
        }
    }

    private void rewrite(Set<FieldRef> accesses) {
        List<FieldRef> resolved = new ArrayList<>(accesses.size());
        for (FieldRef access : accesses) {
            resolved.add(declaringField(access));
        }
        accesses.clear();
        accesses.addAll(resolved);
    }

    private FieldRef declaringField(FieldRef access) {
        String owner = access.getOwner();
        while (owner != null) {
            BytecodeClassModel type = classesByFqn.get(owner);
            if (type == null) {
                return access;
            }
            boolean declaresIt = type.getFields().stream()
                    .anyMatch(field -> field.getName().equals(access.getName()));
            if (declaresIt) {
                return owner.equals(access.getOwner())
                        ? access : new FieldRef(owner, access.getName());
            }
            owner = type.getSuperclass();
        }
        return access;
    }

    public Collection<BytecodeClassModel> getClasses() {
        return classesByFqn.values();
    }

    public BytecodeClassModel getClass(String fqn) {
        return classesByFqn.get(fqn);
    }

    public boolean isAnalyzedClass(String fqn) {
        return classesByFqn.containsKey(fqn);
    }

    /**
     * Classes that may become CSV rows. Nested and synthetic classes stay in the
     * model for hierarchy and coupling resolution even when they never produce a
     * row, matching the published PROMISE datasets which list top-level types only.
     */
    public boolean isProjectClass(String fqn) {
        return projectClassNames.contains(fqn);
    }

    public List<BytecodeClassModel> getRowClasses() {
        List<BytecodeClassModel> rows = new ArrayList<>();
        for (BytecodeClassModel model : classesByFqn.values()) {
            if (isProjectClass(model.getFqn())) {
                rows.add(model);
            }
        }
        return rows;
    }

    public CouplingGraph getCouplingGraph() {
        return couplingGraph;
    }

    public InheritanceGraph getInheritanceGraph() {
        return inheritanceGraph;
    }

    /** Fields declared by a class, by name; used to resolve field accesses. */
    public Set<String> declaredFieldNames(String fqn) {
        BytecodeClassModel model = classesByFqn.get(fqn);
        Set<String> names = new LinkedHashSet<>();
        if (model != null) {
            model.getFields().forEach(field -> names.add(field.getName()));
        }
        return names;
    }
}
