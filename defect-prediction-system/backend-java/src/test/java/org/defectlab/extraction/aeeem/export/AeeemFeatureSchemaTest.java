package org.metrics.aeeem.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metrics.aeeem.model.AeeemMetricResult;

class AeeemFeatureSchemaTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void usesLcArffPredictorOrderWithoutDefectHistoryOrLabel() {
        List<String> expected = Arrays.asList(
                "name", "ck_oo_numberOfPrivateMethods", "LDHH_lcom", "LDHH_fanIn",
                "WCHU_numberOfPublicAttributes", "WCHU_numberOfAttributes", "CvsWEntropy",
                "LDHH_numberOfPublicMethods", "WCHU_fanIn", "LDHH_numberOfPrivateAttributes",
                "CvsEntropy", "LDHH_numberOfPublicAttributes", "WCHU_numberOfPrivateMethods",
                "WCHU_numberOfMethods", "ck_oo_numberOfPublicAttributes", "ck_oo_noc", "ck_oo_wmc",
                "LDHH_numberOfPrivateMethods", "WCHU_numberOfPrivateAttributes", "CvsLogEntropy",
                "WCHU_noc", "LDHH_numberOfAttributesInherited", "WCHU_wmc", "ck_oo_fanOut",
                "ck_oo_numberOfLinesOfCode", "ck_oo_numberOfAttributesInherited", "ck_oo_numberOfMethods",
                "ck_oo_dit", "ck_oo_fanIn", "LDHH_noc", "WCHU_dit", "ck_oo_lcom",
                "WCHU_numberOfAttributesInherited", "ck_oo_rfc", "LDHH_wmc", "LDHH_numberOfAttributes",
                "LDHH_numberOfLinesOfCode", "WCHU_fanOut", "WCHU_lcom", "ck_oo_cbo", "WCHU_rfc",
                "ck_oo_numberOfAttributes", "ck_oo_numberOfPrivateAttributes", "WCHU_numberOfPublicMethods",
                "LDHH_dit", "WCHU_cbo", "CvsLinEntropy", "WCHU_numberOfMethodsInherited",
                "LDHH_fanOut", "LDHH_numberOfMethodsInherited", "LDHH_rfc",
                "ck_oo_numberOfMethodsInherited", "ck_oo_numberOfPublicMethods", "LDHH_cbo",
                "WCHU_numberOfLinesOfCode", "CvsExpEntropy", "LDHH_numberOfMethods"
        );

        List<String> actual = AeeemFeatureSchema.columnsWithIdentifier();

        assertEquals(expected, actual);
        assertEquals(57, actual.size());
        assertFalse(actual.contains("class"));
        assertFalse(actual.stream().anyMatch(column -> column.startsWith("numberOf") && column.endsWith("BugsFoundUntil:")));
    }

    @Test
    void exporterWritesEntropyValuesUnderTheirSchemaColumns() throws Exception {
        AeeemMetricResult metrics = new AeeemMetricResult("example.PaymentService");
        metrics.setCvsWEntropy(1.1);
        metrics.setCvsEntropy(2.2);
        metrics.setCvsLogEntropy(3.3);
        metrics.setCvsLinEntropy(4.4);
        metrics.setCvsExpEntropy(5.5);
        Path output = temporaryDirectory.resolve("aeeem.csv");

        AeeemCsvExporter.exportAeeemToCSV(Collections.singletonList(metrics), output);

        try (java.io.Reader reader = Files.newBufferedReader(output)) {
            List<CSVRecord> records = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                    .build().parse(reader).getRecords();
            assertEquals(1, records.size());
            CSVRecord row = records.get(0);
            assertEquals("example.PaymentService", row.get("name"));
            assertEquals("1.1", row.get("CvsWEntropy"));
            assertEquals("2.2", row.get("CvsEntropy"));
            assertEquals("3.3", row.get("CvsLogEntropy"));
            assertEquals("4.4", row.get("CvsLinEntropy"));
            assertEquals("5.5", row.get("CvsExpEntropy"));
        }
    }

    @Test
    void exporterUsesReferenceDatasetPrecisionWithoutFloatingPointTails() throws Exception {
        AeeemMetricResult metrics = new AeeemMetricResult("example.DecimalService");
        metrics.setCvsEntropy(0.1d + 0.2d);
        metrics.setCvsWEntropy(0.123456789d);
        metrics.setCvsLogEntropy(-0.0d);
        Path output = temporaryDirectory.resolve("aeeem-precision.csv");

        AeeemCsvExporter.exportAeeemToCSV(Collections.singletonList(metrics), output);

        try (java.io.Reader reader = Files.newBufferedReader(output)) {
            CSVRecord row = CSVFormat.DEFAULT.builder().setHeader()
                    .setSkipHeaderRecord(true).build().parse(reader)
                    .getRecords().get(0);
            assertEquals("0.3", row.get("CvsEntropy"));
            assertEquals("0.123457", row.get("CvsWEntropy"));
            assertEquals("0", row.get("CvsLogEntropy"));
        }
    }
}
