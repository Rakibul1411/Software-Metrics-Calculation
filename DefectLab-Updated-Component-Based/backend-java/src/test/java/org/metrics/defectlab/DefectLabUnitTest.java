package org.metrics.defectlab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metrics.defectlab.shared.csv.CsvCells;
import org.metrics.defectlab.dataset.infrastructure.DatasetFileParser;
import org.metrics.defectlab.dataset.domain.DatasetQuality;
import org.metrics.defectlab.dataset.domain.DatasetTable;
import org.metrics.defectlab.dataset.domain.FeatureProfile;
import org.metrics.defectlab.dataset.domain.MetricDataset;
import org.metrics.defectlab.auth.domain.User;

class DefectLabUnitTest {

    @TempDir
    Path directory;

    private static final String PROMISE_HEADER =
            "name,wmc,dit,noc,cbo,rfc,lcom,ca,ce,npm,lcom3,loc,dam,moa,mfa,cam,ic,cbm,amc,max_cc,avg_cc,bug";

    @Test
    void normalizesEmailToLowercaseSoUniquenessIsCaseInsensitive() {
        assertEquals("rakib@iit.du.ac.bd", User.normalizeEmail("  Rakib@IIT.du.AC.bd "));
    }

    @Test
    void promiseProfileRegistersTwentyPredictorsAndFourScaleOnly() {
        FeatureProfile profile = FeatureProfile.promise();
        assertEquals(20, profile.getFeatures().size());
        assertEquals(16, profile.getLogFeatures().size());
        for (String scaleOnly : List.of("lcom3", "dam", "mfa", "cam")) {
            assertFalse(profile.getLogFeatures().contains(scaleOnly),
                    scaleOnly + " must be scale-only");
        }
    }

    @Test
    void aeeemProfileRegistersFiftySixFeaturesAndKeepsLdhhScaleOnly() {
        FeatureProfile profile = FeatureProfile.aeeem();
        assertEquals(56, profile.getFeatures().size());
        assertEquals(34, profile.getLogFeatures().size());
        assertTrue(profile.getLogFeatures().stream().noneMatch(name -> name.startsWith("ldhh_")));
    }

    @Test
    void detectsTheFamilyFromColumnNamesRegardlessOfOrder() {
        List<String> reversed = new java.util.ArrayList<>(FeatureProfile.promise().getFeatures());
        java.util.Collections.reverse(reversed);
        assertEquals(MetricDataset.Family.PROMISE,
                FeatureProfile.detect(reversed).orElseThrow().getFamily());
        assertTrue(FeatureProfile.detect(List.of("alpha", "beta")).isEmpty());
    }

    @Test
    void normalizesPublishedAeeemAndPromiseHeaderAliases() {
        assertEquals("ck_oo_numberofprivatemethods",
                DatasetFileParser.normalizeHeader("ckooPrivateMethod"));
        assertEquals("wchu_numberofattributes",
                DatasetFileParser.normalizeHeader("WCHUNumAttr"));
        assertEquals("ldhh_numberoflinesofcode",
                DatasetFileParser.normalizeHeader("LDHHLOC"));
        assertEquals("numberofhighprioritybugsfounduntil:",
                DatasetFileParser.normalizeHeader("NumHPBFU"));
        assertEquals("max_cc", DatasetFileParser.normalizeHeader("MAX_CC"));
    }

    @Test
    void excludesPriorDefectHistoryColumns() {
        assertTrue(FeatureProfile.isExcludedHistoryColumn("numberOfBugsFoundUntil:"));
        assertTrue(FeatureProfile.isExcludedHistoryColumn("numberOfCriticalBugsFoundUntil:"));
        assertFalse(FeatureProfile.isExcludedHistoryColumn("ck_oo_wmc"));
    }

    @Test
    void parsesCsvWithNormalizedHeaders() throws Exception {
        Path file = write("dataset.csv", PROMISE_HEADER.toUpperCase() + "\n" + promiseRow("demo.A", 1));
        DatasetTable table = DatasetFileParser.parse(file);
        assertEquals("name", table.getHeaders().get(0));
        assertTrue(table.getHeaders().contains("max_cc"));
        assertEquals(1, table.getRowCount());
    }

    @Test
    void parsesArffAndSkipsCommentLinesInTheDataSection() throws Exception {
        StringBuilder arff = new StringBuilder("@relation demo\n\n");
        for (String column : PROMISE_HEADER.split(",")) {
            arff.append("@attribute '").append(column).append("' numeric\n");
        }
        // The published AEEEM files carry a '###' legend line inside @data.
        arff.append("\n@data\n### legend row that must be skipped\n")
            .append(promiseRow("demo.A", 0)).append('\n')
            .append(promiseRow("demo.B", 1)).append('\n');

        DatasetTable table = DatasetFileParser.parse(write("dataset.arff", arff.toString()));
        assertEquals(2, table.getRowCount());
        assertEquals("demo.A", table.getRows().get(0).get(0));
    }

    @Test
    void qualityReportsUnexpectedNegativeInANonNegativeColumn() throws Exception {
        Path file = write("negative.csv", PROMISE_HEADER + "\n"
                + promiseRow("demo.A", 0).replace(",5,", ",-5,"));
        DatasetQuality quality = DatasetQuality.inspect(
                DatasetFileParser.parse(file), FeatureProfile.promise());
        assertFalse(quality.isUsable());
        assertTrue(quality.getBlockingIssues().stream()
                .anyMatch(issue -> issue.contains("negative")));
    }

    @Test
    void qualityAcceptsACleanPromiseFile() throws Exception {
        Path file = write("clean.csv", PROMISE_HEADER + "\n"
                + promiseRow("demo.A", 0) + "\n" + promiseRow("demo.B", 1));
        DatasetQuality quality = DatasetQuality.inspect(
                DatasetFileParser.parse(file), FeatureProfile.promise());
        assertTrue(quality.isUsable(), String.join("; ", quality.getBlockingIssues()));
    }

    @Test
    void qualityAcceptsConfiguredMissingMarkersForSourceMedianImputation() throws Exception {
        Path file = write("missing-marker.csv", PROMISE_HEADER + "\n"
                + promiseRow("demo.A", 0).replaceFirst(",5,", ",-1,") + "\n"
                + promiseRow("demo.B", 1));
        DatasetQuality quality = DatasetQuality.inspect(
                DatasetFileParser.parse(file), FeatureProfile.promise());
        assertTrue(quality.isUsable(), String.join("; ", quality.getBlockingIssues()));
        assertTrue(quality.getWarnings().stream()
                .anyMatch(warning -> warning.contains("configured missing marker")));
    }

    @Test
    void qualityRejectsPromiseRatioOutsideUnitRange() throws Exception {
        Path file = write("ratio.csv", PROMISE_HEADER + "\n"
                + promiseRow("demo.A", 0).replace(",0.5,1,0.4,0.6,", ",1.5,1,0.4,0.6,"));
        DatasetQuality quality = DatasetQuality.inspect(
                DatasetFileParser.parse(file), FeatureProfile.promise());
        assertFalse(quality.isUsable());
        assertTrue(quality.getBlockingIssues().stream()
                .anyMatch(issue -> issue.contains("outside [0,1]")));
    }

    @Test
    void workflowEnumsMatchTheDatabaseValues() {
        assertEquals("PROMISE", MetricDataset.Family.PROMISE.name());
        assertEquals("MANUAL", MetricDataset.Type.MANUAL.name());
        assertEquals("PREDEFINED", MetricDataset.Type.PREDEFINED.name());
    }

    @Test
    void csvExportNeutralisesSpreadsheetFormulas() {
        assertEquals("'=cmd|' /c calc'!A1", CsvCells.escape("=cmd|' /c calc'!A1"));
        assertEquals("'+SUM(A1)", CsvCells.escape("+SUM(A1)"));
        assertEquals("'-1", CsvCells.escape("-1"));
        assertEquals("'@import", CsvCells.escape("@import"));
        assertEquals("org.apache.Foo", CsvCells.escape("org.apache.Foo"));
        assertEquals("\"a,b\"", CsvCells.escape("a,b"));
    }

    /** 20 predictors plus name and bug, with every ratio inside [0,1]. */
    private static String promiseRow(String name, int bug) {
        return name + ",5,1,0,4,9,3,2,3,4,0.5,120,0.5,1,0.4,0.6,0,0,12.5,3,1.5," + bug;
    }

    private Path write(String filename, String content) throws Exception {
        Path file = directory.resolve(filename);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
