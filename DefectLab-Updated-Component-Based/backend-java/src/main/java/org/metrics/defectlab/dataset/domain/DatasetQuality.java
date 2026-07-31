package org.metrics.defectlab.dataset.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Column-level quality report.
 *
 * <p>Reports problems instead of repairing them: the SRS forbids guessing whether
 * a negative value is valid, so unexpected values are surfaced as blocking issues.</p>
 */
public final class DatasetQuality {

    private final List<String> blockingIssues = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, ColumnQuality> columns = new LinkedHashMap<>();

    public static final class ColumnQuality {
        private final String name;
        private int missing;
        private int nonNumeric;
        private int negative;
        private int nonFinite;
        private int missingMarkers;
        private Double minimum;
        private Double maximum;

        ColumnQuality(String name) {
            this.name = name;
        }

        void observe(String raw) {
            if (raw == null || raw.isEmpty() || "?".equals(raw)
                    || "na".equalsIgnoreCase(raw) || "nan".equalsIgnoreCase(raw)) {
                missing++;
                return;
            }
            double value;
            try {
                value = Double.parseDouble(raw);
            } catch (NumberFormatException exception) {
                nonNumeric++;
                return;
            }
            if (!Double.isFinite(value)) {
                nonFinite++;
                return;
            }
            if (value == -1.0 || value == -999.0) {
                missing++;
                missingMarkers++;
                return;
            }
            if (value < 0) {
                negative++;
            }
            minimum = minimum == null ? value : Math.min(minimum, value);
            maximum = maximum == null ? value : Math.max(maximum, value);
        }

        public String getName() {
            return name;
        }

        public int getMissing() {
            return missing;
        }

        public int getNonNumeric() {
            return nonNumeric;
        }

        public int getNegative() {
            return negative;
        }

        public int getNonFinite() {
            return nonFinite;
        }

        public int getMissingMarkers() {
            return missingMarkers;
        }

        public Double getMinimum() {
            return minimum;
        }

        public Double getMaximum() {
            return maximum;
        }
    }

    public static DatasetQuality inspect(DatasetTable table, FeatureProfile profile) {
        DatasetQuality quality = new DatasetQuality();

        List<String> duplicates = duplicateHeaders(table.getHeaders());
        if (!duplicates.isEmpty()) {
            quality.blockingIssues.add("Duplicate columns: " + String.join(", ", duplicates));
        }

        List<String> missingColumns = new ArrayList<>();
        for (String feature : profile.getFeatures()) {
            if (table.indexOf(feature) < 0) {
                missingColumns.add(feature);
            }
        }
        if (!missingColumns.isEmpty()) {
            quality.blockingIssues.add(
                    "Missing required columns: " + String.join(", ", missingColumns));
        }

        for (String feature : profile.getFeatures()) {
            if (table.indexOf(feature) < 0) {
                continue;
            }
            ColumnQuality column = new ColumnQuality(feature);
            for (String raw : table.column(feature)) {
                column.observe(raw);
            }
            quality.columns.put(feature, column);

            if (column.nonNumeric > 0) {
                quality.blockingIssues.add(
                        column.nonNumeric + " non-numeric value(s) in column '" + feature + "'");
            }
            if (column.nonFinite > 0) {
                quality.blockingIssues.add(
                        column.nonFinite + " non-finite value(s) in column '" + feature + "'");
            }
            // A negative raw count is either a missing marker or an extraction bug;
            // the run must stop rather than silently clip it.
            if (column.negative > 0 && profile.getLogFeatures().contains(feature)) {
                quality.blockingIssues.add(column.negative
                        + " negative value(s) in non-negative column '" + feature + "'");
            }
            if (profile.getUnitRangeFeatures().contains(feature)
                    && ((column.minimum != null && column.minimum < 0)
                    || (column.maximum != null && column.maximum > 1))) {
                quality.blockingIssues.add(
                        "Value(s) outside [0,1] in ratio column '" + feature + "'");
            }
            int ordinaryMissing = column.missing - column.missingMarkers;
            if (ordinaryMissing > 0) {
                quality.warnings.add(ordinaryMissing
                        + " missing value(s) in column '" + feature
                        + "' will use source-median imputation");
            }
            if (column.missingMarkers > 0) {
                quality.warnings.add(column.missingMarkers
                        + " configured missing marker(s) in column '" + feature
                        + "' will be treated as missing");
            }
        }

        if (table.getRowCount() == 0) {
            quality.blockingIssues.add("The dataset contains no data rows.");
        }
        return quality;
    }

    private static List<String> duplicateHeaders(List<String> headers) {
        List<String> duplicates = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String header : headers) {
            counts.merge(header, 1, Integer::sum);
        }
        counts.forEach((header, count) -> {
            if (count > 1) {
                duplicates.add(header);
            }
        });
        return duplicates;
    }

    public boolean isUsable() {
        return blockingIssues.isEmpty();
    }

    public List<String> getBlockingIssues() {
        return blockingIssues;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public Map<String, ColumnQuality> getColumns() {
        return columns;
    }
}
