package org.metrics.defectlab.analysis.promise.bytecode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The single directed class-coupling graph behind CBO, Ca and Ce, so the three
 * metrics can never drift apart.
 *
 * <p>An edge is recorded for every channel CKJM treats as coupling: superclass,
 * implemented interfaces, field types, method parameter and return types,
 * declared and caught exceptions, method invocations, field accesses and
 * {@code checkcast}/{@code instanceof} operands.
 *
 * <p>Self-coupling is dropped, JDK classes are excluded by CKJM's default
 * policy, and primitives never appear. External non-JDK classes are kept: a
 * dependency the release genuinely uses is real coupling.
 */
public final class CouplingGraph {

    private final Map<String, Set<String>> outgoing;
    private final Map<String, Set<String>> incoming;

    private CouplingGraph(Map<String, Set<String>> outgoing, Map<String, Set<String>> incoming) {
        this.outgoing = outgoing;
        this.incoming = incoming;
    }

    static CouplingGraph build(BytecodeProjectModel model) {
        Map<String, Set<String>> outgoing = new LinkedHashMap<>();
        Map<String, Set<String>> incoming = new LinkedHashMap<>();

        for (BytecodeClassModel type : model.getClasses()) {
            String source = type.getFqn();
            Set<String> targets = outgoing
                    .computeIfAbsent(source, ignored -> new LinkedHashSet<>());
            incoming.computeIfAbsent(source, ignored -> new LinkedHashSet<>());

            for (String referenced : type.allReferencedTypes()) {
                if (!JdkClassPolicy.isCandidateForCoupling(referenced)
                        || referenced.equals(source)) {
                    continue;
                }
                targets.add(referenced);
                incoming.computeIfAbsent(referenced, ignored -> new LinkedHashSet<>())
                        .add(source);
            }
        }
        return new CouplingGraph(outgoing, incoming);
    }

    /** Ce: distinct classes this class depends upon. */
    public Set<String> outgoingFrom(String fqn) {
        return Collections.unmodifiableSet(
                outgoing.getOrDefault(fqn, Set.of()));
    }

    /** Ca: distinct classes that depend upon this class. */
    public Set<String> incomingTo(String fqn) {
        return Collections.unmodifiableSet(
                incoming.getOrDefault(fqn, Set.of()));
    }

    /** CBO: the direction-neutral union, each coupled class counted once. */
    public Set<String> coupledWith(String fqn) {
        Set<String> union = new LinkedHashSet<>(outgoingFrom(fqn));
        union.addAll(incomingTo(fqn));
        return union;
    }
}
