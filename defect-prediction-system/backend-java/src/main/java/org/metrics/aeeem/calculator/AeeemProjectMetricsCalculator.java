package org.metrics.aeeem.calculator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.metrics.aeeem.calculator.legacy.CBOCalculator;
import org.metrics.aeeem.calculator.legacy.NOCCalculator;
import org.metrics.aeeem.model.AeeemMetricResult;

public final class AeeemProjectMetricsCalculator {

    private AeeemProjectMetricsCalculator() {
    }

    public static void apply(List<AeeemMetricResult> allMetrics) {
        NOCCalculator nocCalculator = new NOCCalculator();
        CBOCalculator cboCalculator = new CBOCalculator();
        Map<String, AeeemMetricResult> classesByName = new HashMap<>();
        for (AeeemMetricResult metrics : allMetrics) {
            String name = metrics.getFullyQualifiedName();
            nocCalculator.registerClass(name, metrics.getSuperclassName());
            cboCalculator.registerClass(name, metrics.getSuperclassName(), metrics.getDependencies());
            classesByName.put(name, metrics);
            classesByName.putIfAbsent(simpleName(name), metrics);
        }
        cboCalculator.postProcessDependencies();

        for (AeeemMetricResult metrics : allMetrics) {
            String name = metrics.getFullyQualifiedName();
            int inheritedAttributes = 0;
            Set<String> inheritedMethods = new HashSet<>();
            Set<String> visited = new HashSet<>();
            AeeemMetricResult parent = find(classesByName, metrics.getSuperclassName());
            while (parent != null && visited.add(parent.getFullyQualifiedName())) {
                inheritedAttributes += parent.getDeclaredAttributeCount();
                inheritedMethods.addAll(parent.getMethodNames());
                parent = find(classesByName, parent.getSuperclassName());
            }
            metrics.setCkOoNoc(nocCalculator.calculateNOC(name));
            metrics.setCkOoFanIn(cboCalculator.calculateCA(name));
            metrics.setCkOoFanOut(cboCalculator.calculateCE(name));
            metrics.setCkOoCbo(cboCalculator.calculateCBO(name));
            metrics.setCkOoNumberOfAttributesInherited(inheritedAttributes);
            metrics.setCkOoNumberOfMethodsInherited(inheritedMethods.size());
        }
    }

    private static AeeemMetricResult find(Map<String, AeeemMetricResult> values, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        AeeemMetricResult exact = values.get(name);
        return exact == null ? values.get(simpleName(name)) : exact;
    }

    private static String simpleName(String value) {
        int separator = value == null ? -1 : value.lastIndexOf('.');
        return separator < 0 ? value : value.substring(separator + 1);
    }
}
