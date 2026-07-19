package org.metrics.aeeem.export;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.ToDoubleFunction;

import org.metrics.aeeem.model.AeeemMetricResult;

/** Authoritative AEEEM-56 predictor schema in LC.arff attribute order. */
public final class AeeemFeatureSchema {

    private static final List<Feature> FEATURES = Collections.unmodifiableList(Arrays.asList(
            feature("ck_oo_numberOfPrivateMethods", AeeemMetricResult::getCkOoNumberOfPrivateMethods),
            feature("LDHH_lcom", AeeemMetricResult::getLdhhLcom),
            feature("LDHH_fanIn", AeeemMetricResult::getLdhhFanIn),
            feature("WCHU_numberOfPublicAttributes", AeeemMetricResult::getWchuNumberOfPublicAttributes),
            feature("WCHU_numberOfAttributes", AeeemMetricResult::getWchuNumberOfAttributes),
            feature("CvsWEntropy", AeeemMetricResult::getCvsWEntropy),
            feature("LDHH_numberOfPublicMethods", AeeemMetricResult::getLdhhNumberOfPublicMethods),
            feature("WCHU_fanIn", AeeemMetricResult::getWchuFanIn),
            feature("LDHH_numberOfPrivateAttributes", AeeemMetricResult::getLdhhNumberOfPrivateAttributes),
            feature("CvsEntropy", AeeemMetricResult::getCvsEntropy),
            feature("LDHH_numberOfPublicAttributes", AeeemMetricResult::getLdhhNumberOfPublicAttributes),
            feature("WCHU_numberOfPrivateMethods", AeeemMetricResult::getWchuNumberOfPrivateMethods),
            feature("WCHU_numberOfMethods", AeeemMetricResult::getWchuNumberOfMethods),
            feature("ck_oo_numberOfPublicAttributes", AeeemMetricResult::getCkOoNumberOfPublicAttributes),
            feature("ck_oo_noc", AeeemMetricResult::getCkOoNoc),
            feature("ck_oo_wmc", AeeemMetricResult::getCkOoWmc),
            feature("LDHH_numberOfPrivateMethods", AeeemMetricResult::getLdhhNumberOfPrivateMethods),
            feature("WCHU_numberOfPrivateAttributes", AeeemMetricResult::getWchuNumberOfPrivateAttributes),
            feature("CvsLogEntropy", AeeemMetricResult::getCvsLogEntropy),
            feature("WCHU_noc", AeeemMetricResult::getWchuNoc),
            feature("LDHH_numberOfAttributesInherited", AeeemMetricResult::getLdhhNumberOfAttributesInherited),
            feature("WCHU_wmc", AeeemMetricResult::getWchuWmc),
            feature("ck_oo_fanOut", AeeemMetricResult::getCkOoFanOut),
            feature("ck_oo_numberOfLinesOfCode", AeeemMetricResult::getCkOoNumberOfLinesOfCode),
            feature("ck_oo_numberOfAttributesInherited", AeeemMetricResult::getCkOoNumberOfAttributesInherited),
            feature("ck_oo_numberOfMethods", AeeemMetricResult::getCkOoNumberOfMethods),
            feature("ck_oo_dit", AeeemMetricResult::getCkOoDit),
            feature("ck_oo_fanIn", AeeemMetricResult::getCkOoFanIn),
            feature("LDHH_noc", AeeemMetricResult::getLdhhNoc),
            feature("WCHU_dit", AeeemMetricResult::getWchuDit),
            feature("ck_oo_lcom", AeeemMetricResult::getCkOoLcom),
            feature("WCHU_numberOfAttributesInherited", AeeemMetricResult::getWchuNumberOfAttributesInherited),
            feature("ck_oo_rfc", AeeemMetricResult::getCkOoRfc),
            feature("LDHH_wmc", AeeemMetricResult::getLdhhWmc),
            feature("LDHH_numberOfAttributes", AeeemMetricResult::getLdhhNumberOfAttributes),
            feature("LDHH_numberOfLinesOfCode", AeeemMetricResult::getLdhhNumberOfLinesOfCode),
            feature("WCHU_fanOut", AeeemMetricResult::getWchuFanOut),
            feature("WCHU_lcom", AeeemMetricResult::getWchuLcom),
            feature("ck_oo_cbo", AeeemMetricResult::getCkOoCbo),
            feature("WCHU_rfc", AeeemMetricResult::getWchuRfc),
            feature("ck_oo_numberOfAttributes", AeeemMetricResult::getCkOoNumberOfAttributes),
            feature("ck_oo_numberOfPrivateAttributes", AeeemMetricResult::getCkOoNumberOfPrivateAttributes),
            feature("WCHU_numberOfPublicMethods", AeeemMetricResult::getWchuNumberOfPublicMethods),
            feature("LDHH_dit", AeeemMetricResult::getLdhhDit),
            feature("WCHU_cbo", AeeemMetricResult::getWchuCbo),
            feature("CvsLinEntropy", AeeemMetricResult::getCvsLinEntropy),
            feature("WCHU_numberOfMethodsInherited", AeeemMetricResult::getWchuNumberOfMethodsInherited),
            feature("LDHH_fanOut", AeeemMetricResult::getLdhhFanOut),
            feature("LDHH_numberOfMethodsInherited", AeeemMetricResult::getLdhhNumberOfMethodsInherited),
            feature("LDHH_rfc", AeeemMetricResult::getLdhhRfc),
            feature("ck_oo_numberOfMethodsInherited", AeeemMetricResult::getCkOoNumberOfMethodsInherited),
            feature("ck_oo_numberOfPublicMethods", AeeemMetricResult::getCkOoNumberOfPublicMethods),
            feature("LDHH_cbo", AeeemMetricResult::getLdhhCbo),
            feature("WCHU_numberOfLinesOfCode", AeeemMetricResult::getWchuNumberOfLinesOfCode),
            feature("CvsExpEntropy", AeeemMetricResult::getCvsExpEntropy),
            feature("LDHH_numberOfMethods", AeeemMetricResult::getLdhhNumberOfMethods)
    ));

    private AeeemFeatureSchema() {
    }

    public static List<String> columnsWithIdentifier() {
        List<String> columns = new ArrayList<>(FEATURES.size() + 1);
        columns.add("name");
        for (Feature feature : FEATURES) {
            columns.add(feature.name);
        }
        return Collections.unmodifiableList(columns);
    }

    public static List<Object> row(AeeemMetricResult metrics) {
        List<Object> values = new ArrayList<>(FEATURES.size() + 1);
        values.add(metrics.getFullyQualifiedName());
        for (Feature feature : FEATURES) {
            values.add(feature.value.applyAsDouble(metrics));
        }
        return values;
    }

    private static Feature feature(String name, ToDoubleFunction<AeeemMetricResult> value) {
        return new Feature(name, value);
    }

    private static final class Feature {
        private final String name;
        private final ToDoubleFunction<AeeemMetricResult> value;

        private Feature(String name, ToDoubleFunction<AeeemMetricResult> value) {
            this.name = name;
            this.value = value;
        }
    }
}
