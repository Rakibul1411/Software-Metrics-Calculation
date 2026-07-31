package org.metrics.aeeem.calculator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.metrics.aeeem.model.AeeemMetricResult;

/** Completes metrics that require knowledge of every class in one snapshot. */
public final class AeeemProjectMetricsCalculator {

    private AeeemProjectMetricsCalculator() {
    }

    public static void apply(List<AeeemMetricResult> allMetrics) {
        Map<String, AeeemMetricResult> exact = new LinkedHashMap<>();
        Map<String, List<AeeemMetricResult>> bySimpleName = new HashMap<>();
        for (AeeemMetricResult metrics : allMetrics) {
            exact.put(metrics.getFullyQualifiedName(), metrics);
            bySimpleName.computeIfAbsent(simpleName(metrics.getFullyQualifiedName()),
                    ignored -> new ArrayList<>()).add(metrics);
        }

        Map<String, Set<String>> outgoing = new LinkedHashMap<>();
        Map<String, Set<String>> incoming = new LinkedHashMap<>();
        Map<String, String> parentByClass = new LinkedHashMap<>();
        for (AeeemMetricResult metrics : allMetrics) {
            String className = metrics.getFullyQualifiedName();
            Set<String> resolved = new LinkedHashSet<>();
            for (String dependency : metrics.getDependencies()) {
                AeeemMetricResult target = resolve(exact, bySimpleName, dependency);
                if (target != null && !className.equals(target.getFullyQualifiedName())) {
                    resolved.add(target.getFullyQualifiedName());
                }
            }
            outgoing.put(className, resolved);
            for (String dependency : resolved) {
                incoming.computeIfAbsent(dependency, ignored -> new LinkedHashSet<>())
                        .add(className);
            }
            AeeemMetricResult parent = resolve(exact, bySimpleName, metrics.getSuperclassName());
            if (parent != null && !className.equals(parent.getFullyQualifiedName())) {
                parentByClass.put(className, parent.getFullyQualifiedName());
            }
        }

        Map<String, Integer> children = new HashMap<>();
        for (String parent : parentByClass.values()) {
            children.put(parent, children.getOrDefault(parent, 0) + 1);
        }

        for (AeeemMetricResult metrics : allMetrics) {
            String className = metrics.getFullyQualifiedName();
            Set<String> outgoingClasses = outgoing.getOrDefault(className, Collections.emptySet());
            Set<String> incomingClasses = incoming.getOrDefault(className, Collections.emptySet());
            Set<String> coupled = new HashSet<>(outgoingClasses);
            coupled.addAll(incomingClasses);

            metrics.setCkOoNoc(children.getOrDefault(className, 0));
            metrics.setCkOoFanIn(incomingClasses.size());
            metrics.setCkOoFanOut(outgoingClasses.size());
            metrics.setCkOoCbo(coupled.size());
            applyInheritedMetrics(metrics, exact, parentByClass);
        }
    }

    private static void applyInheritedMetrics(
            AeeemMetricResult metrics,
            Map<String, AeeemMetricResult> exact,
            Map<String, String> parentByClass) {
        int inheritedAttributes = 0;
        int inheritedMethods = 0;
        Set<String> visibleMethods = new HashSet<>(metrics.getDeclaredMethodSignatures());
        Set<String> visited = new HashSet<>();
        String parentName = parentByClass.get(metrics.getFullyQualifiedName());
        while (parentName != null && visited.add(parentName)) {
            AeeemMetricResult parent = exact.get(parentName);
            if (parent == null) {
                break;
            }
            inheritedAttributes += parent.getInheritableDeclaredAttributeCount();
            for (String signature : parent.getInheritableMethodSignatures()) {
                if (visibleMethods.add(signature)) {
                    inheritedMethods++;
                }
            }
            parentName = parentByClass.get(parentName);
        }
        metrics.setCkOoNumberOfAttributesInherited(inheritedAttributes);
        metrics.setCkOoNumberOfMethodsInherited(inheritedMethods);
    }

    private static AeeemMetricResult resolve(
            Map<String, AeeemMetricResult> exact,
            Map<String, List<AeeemMetricResult>> bySimpleName,
            String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String normalized = stripTypeArguments(name.trim()).replace('$', '.');
        AeeemMetricResult exactMatch = exact.get(normalized);
        if (exactMatch != null) {
            return exactMatch;
        }
        List<AeeemMetricResult> candidates = bySimpleName.get(simpleName(normalized));
        return candidates != null && candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static String stripTypeArguments(String value) {
        int generic = value.indexOf('<');
        return generic < 0 ? value : value.substring(0, generic);
    }

    private static String simpleName(String value) {
        if (value == null) {
            return "";
        }
        int separator = Math.max(value.lastIndexOf('.'), value.lastIndexOf('$'));
        return separator < 0 ? value : value.substring(separator + 1);
    }
}
