package org.metrics.defectlab.dataset.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Canonical feature lists for the two supported families.
 *
 * <p>A family is identified by the exact required feature set, never by column
 * order, and the registered order is what source and target are reordered to.</p>
 */
public final class FeatureProfile {

    public static final String IDENTIFIER = "name";
    public static final String PROMISE_LABEL = "bug";

    /** The 20 PROMISE predictors in registered order. */
    public static final List<String> PROMISE_FEATURES = List.of(
            "wmc", "dit", "noc", "cbo", "rfc", "lcom", "ca", "ce", "npm", "lcom3",
            "loc", "dam", "moa", "mfa", "cam", "ic", "cbm", "amc", "max_cc", "avg_cc");

    /** PROMISE columns that are non-negative counts; a negative value is invalid data. */
    public static final Set<String> PROMISE_NONNEGATIVE_FEATURES = new LinkedHashSet<>(Arrays.asList(
            "wmc", "dit", "noc", "cbo", "rfc", "lcom", "ca", "ce",
            "npm", "loc", "moa", "ic", "cbm", "amc", "max_cc", "avg_cc"));

    /** The 17 AEEEM base metric suffixes. */
    public static final List<String> AEEEM_BASE_METRICS = List.of(
            "wmc", "dit", "rfc", "noc", "cbo", "lcom", "fanin", "fanout",
            "numberofattributes", "numberofpublicattributes",
            "numberofprivateattributes", "numberofattributesinherited",
            "numberoflinesofcode", "numberofmethods", "numberofpublicmethods",
            "numberofprivatemethods", "numberofmethodsinherited");

    public static final List<String> AEEEM_ENTROPY_FEATURES = List.of(
            "cvsentropy", "cvswentropy", "cvslinentropy",
            "cvslogentropy", "cvsexpentropy");

    /** Prior-defect-history columns excluded from the model to avoid leakage. */
    public static final Set<String> AEEEM_EXCLUDED_PREFIXES = Set.of(
            "numberofbugsfounduntil", "numberofnontrivialbugsfounduntil",
            "numberofmajorbugsfounduntil", "numberofcriticalbugsfounduntil",
            "numberofhighprioritybugsfounduntil");

    private final MetricDataset.Family family;
    private final List<String> features;
    private final Set<String> nonNegativeFeatures;
    private final Set<String> unitRangeFeatures;
    private final String labelColumn;

    private FeatureProfile(MetricDataset.Family family, List<String> features,
                           Set<String> nonNegativeFeatures, Set<String> unitRangeFeatures,
                           String labelColumn) {
        this.family = family;
        this.features = Collections.unmodifiableList(features);
        this.nonNegativeFeatures = Collections.unmodifiableSet(nonNegativeFeatures);
        this.unitRangeFeatures = Collections.unmodifiableSet(unitRangeFeatures);
        this.labelColumn = labelColumn;
    }

    public static FeatureProfile promise() {
        return new FeatureProfile(MetricDataset.Family.PROMISE,
                new ArrayList<>(PROMISE_FEATURES),
                new LinkedHashSet<>(PROMISE_NONNEGATIVE_FEATURES),
                Set.of("dam", "mfa", "cam"), PROMISE_LABEL);
    }

    public static FeatureProfile aeeem() {
        List<String> features = new ArrayList<>();
        Set<String> nonNegativeFeatures = new LinkedHashSet<>();
        for (String prefix : List.of("ck_oo_", "wchu_", "ldhh_")) {
            for (String base : AEEEM_BASE_METRICS) {
                String column = prefix + base;
                features.add(column);
                // LDHH values are already deltas and can legitimately be negative.
                if (!"ldhh_".equals(prefix)) {
                    nonNegativeFeatures.add(column);
                }
            }
        }
        features.addAll(AEEEM_ENTROPY_FEATURES);
        return new FeatureProfile(
                MetricDataset.Family.AEEEM, features, nonNegativeFeatures, Set.of(), "class");
    }

    /**
     * Detects the family from the header set. Returns empty when neither family's
     * complete feature list is present, which the caller reports as a hard failure.
     */
    public static java.util.Optional<FeatureProfile> detect(List<String> headers) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String header : headers) {
            normalized.add(MetricHeaderNormalizer.normalize(header));
        }
        FeatureProfile promise = promise();
        if (normalized.containsAll(promise.getFeatures())) {
            return java.util.Optional.of(promise);
        }
        FeatureProfile aeeem = aeeem();
        if (normalized.containsAll(aeeem.getFeatures())) {
            return java.util.Optional.of(aeeem);
        }
        return java.util.Optional.empty();
    }

    public static boolean isExcludedHistoryColumn(String header) {
        String normalized = MetricHeaderNormalizer.normalize(header)
                .replace(":", "").replace("_", "").toLowerCase(Locale.ROOT);
        return AEEEM_EXCLUDED_PREFIXES.stream().anyMatch(normalized::startsWith);
    }

    public MetricDataset.Family getFamily() {
        return family;
    }

    public List<String> getFeatures() {
        return features;
    }

    public Set<String> getNonNegativeFeatures() {
        return nonNegativeFeatures;
    }

    public Set<String> getUnitRangeFeatures() {
        return unitRangeFeatures;
    }

    public String getLabelColumn() {
        return labelColumn;
    }

    /** Accepts either the family's own label column or the shared {@code bug} column. */
    public java.util.Optional<String> findLabelColumn(List<String> headers) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String header : headers) {
            normalized.add(MetricHeaderNormalizer.normalize(header));
        }
        if (normalized.contains(labelColumn)) {
            return java.util.Optional.of(labelColumn);
        }
        return normalized.contains(PROMISE_LABEL)
                ? java.util.Optional.of(PROMISE_LABEL) : java.util.Optional.empty();
    }
}
