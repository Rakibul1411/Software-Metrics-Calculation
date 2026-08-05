package org.metrics.defectlab.dataset.domain;

import java.util.Locale;
import java.util.Map;

/**
 * Canonicalizes PROMISE and AEEEM column names without depending on file I/O.
 *
 * <p>This belongs to the dataset domain because both schema detection and the
 * CSV/ARFF adapter need the same naming rules.</p>
 */
public final class MetricHeaderNormalizer {

    private static final Map<String, String> AEEEM_PREFIXES = Map.of(
            "ckoo", "ck_oo_",
            "wchu", "wchu_",
            "ldhh", "ldhh_");

    private static final Map<String, String> AEEEM_SUFFIX_ALIASES = Map.ofEntries(
            Map.entry("wmc", "wmc"),
            Map.entry("dit", "dit"),
            Map.entry("rfc", "rfc"),
            Map.entry("noc", "noc"),
            Map.entry("cbo", "cbo"),
            Map.entry("lcom", "lcom"),
            Map.entry("fanin", "fanin"),
            Map.entry("fanout", "fanout"),
            Map.entry("attr", "numberofattributes"),
            Map.entry("numattr", "numberofattributes"),
            Map.entry("numberofattributes", "numberofattributes"),
            Map.entry("publicattr", "numberofpublicattributes"),
            Map.entry("numberofpublicattributes", "numberofpublicattributes"),
            Map.entry("privateattr", "numberofprivateattributes"),
            Map.entry("numberofprivateattributes", "numberofprivateattributes"),
            Map.entry("attrinherited", "numberofattributesinherited"),
            Map.entry("numberofattributesinherited", "numberofattributesinherited"),
            Map.entry("loc", "numberoflinesofcode"),
            Map.entry("numberoflinesofcode", "numberoflinesofcode"),
            Map.entry("method", "numberofmethods"),
            Map.entry("methods", "numberofmethods"),
            Map.entry("numberofmethods", "numberofmethods"),
            Map.entry("publicmethod", "numberofpublicmethods"),
            Map.entry("numberofpublicmethods", "numberofpublicmethods"),
            Map.entry("privatemethod", "numberofprivatemethods"),
            Map.entry("numberofprivatemethods", "numberofprivatemethods"),
            Map.entry("methodinherited", "numberofmethodsinherited"),
            Map.entry("numberofmethodsinherited", "numberofmethodsinherited"));

    private static final Map<String, String> SIMPLE_ALIASES = Map.ofEntries(
            Map.entry("maxcc", "max_cc"),
            Map.entry("avgcc", "avg_cc"),
            Map.entry("lcom3", "lcom3"),
            Map.entry("numbfu", "numberofbugsfounduntil:"),
            Map.entry("nntbfu", "numberofnontrivialbugsfounduntil:"),
            Map.entry("nummbfu", "numberofmajorbugsfounduntil:"),
            Map.entry("ncbfu", "numberofcriticalbugsfounduntil:"),
            Map.entry("numhpbfu", "numberofhighprioritybugsfounduntil:"),
            Map.entry("cvsentropy", "cvsentropy"),
            Map.entry("cvswentropy", "cvswentropy"),
            Map.entry("cvslinentropy", "cvslinentropy"),
            Map.entry("cvslogentropy", "cvslogentropy"),
            Map.entry("cvsexpentropy", "cvsexpentropy"));

    private MetricHeaderNormalizer() {
    }

    public static String normalize(String header) {
        if (header == null) {
            return "";
        }
        String normalized = header.trim().toLowerCase(Locale.ROOT);
        String compact = normalized.replaceAll("[^a-z0-9]", "");
        String simple = SIMPLE_ALIASES.get(compact);
        if (simple != null) {
            return simple;
        }
        for (Map.Entry<String, String> prefix : AEEEM_PREFIXES.entrySet()) {
            if (!compact.startsWith(prefix.getKey())) {
                continue;
            }
            String suffix = compact.substring(prefix.getKey().length());
            String canonicalSuffix = AEEEM_SUFFIX_ALIASES.get(suffix);
            if (canonicalSuffix != null) {
                return prefix.getValue() + canonicalSuffix;
            }
        }
        return normalized;
    }
}
