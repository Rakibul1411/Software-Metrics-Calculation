package org.metrics.defectlab.analysis.promise;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metrics.defectlab.analysis.promise.analyzer.PromiseProjectAnalyzer;
import org.metrics.defectlab.analysis.promise.model.PromiseMetricResult;

class PromiseDatasetValidationTest {

    private static final Path WORKSPACE = Path.of(
            "/Users/md.rakibulislam/IIT/SPL-3/promise-dataset-source-code/DefectLab-Updated-Component-Based");
    private static final Path SOURCE_DIR = WORKSPACE.resolve("PROMISE-backup-copy/source code");
    private static final Path BUG_DIR = WORKSPACE.resolve("PROMISE-backup-copy/bug-data");

    @TempDir
    Path tempDir;

    @Test
    void validatesAnt13CalculatedGreaterThanOrEqualToPredefined() throws Exception {
        Path archive = SOURCE_DIR.resolve("ant/jakarta-ant-1.3-src.zip");
        Path gtFile = BUG_DIR.resolve("ant/ant-1.3.csv");
        if (!Files.exists(archive) || !Files.exists(gtFile)) {
            return;
        }
        Path extracted = tempDir.resolve("ant13");
        extractZip(archive, extracted);

        Set<String> predefClasses = readPredefinedClasses(gtFile);
        List<PromiseMetricResult> results =
                new PromiseProjectAnalyzer().analyze(List.of(extracted));

        Set<String> calculated = results.stream()
                .map(PromiseMetricResult::getFullyQualifiedName)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> matched = new LinkedHashSet<>(predefClasses);
        matched.retainAll(calculated);

        System.out.println("ANT 1.3 -> Predefined: " + predefClasses.size()
                + ", Calculated: " + calculated.size()
                + ", Matched: " + matched.size());

        assertTrue(calculated.size() >= predefClasses.size(),
                "Calculated (" + calculated.size() + ") should be >= predefined ("
                + predefClasses.size() + ")");
        assertTrue(matched.size() >= 120, "Expected at least 120 matches out of 125");
    }

    @Test
    void validatesSynapse10CalculatedGreaterThanOrEqualToPredefined() throws Exception {
        Path archive = SOURCE_DIR.resolve("synapse/synapse-1.0.tar.gz");
        Path gtFile = BUG_DIR.resolve("synapse/synapse-1.0.csv");
        if (!Files.exists(archive) || !Files.exists(gtFile)) {
            return;
        }
        Path extracted = tempDir.resolve("synapse10");
        extractTarGz(archive, extracted);

        Set<String> predefClasses = readPredefinedClasses(gtFile);
        List<PromiseMetricResult> results =
                new PromiseProjectAnalyzer().analyze(List.of(extracted));

        Set<String> calculated = results.stream()
                .map(PromiseMetricResult::getFullyQualifiedName)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> matched = new LinkedHashSet<>(predefClasses);
        matched.retainAll(calculated);

        System.out.println("SYNAPSE 1.0 -> Predefined: " + predefClasses.size()
                + ", Calculated: " + calculated.size()
                + ", Matched: " + matched.size());

        assertTrue(calculated.size() >= predefClasses.size(),
                "Calculated (" + calculated.size() + ") should be >= predefined ("
                + predefClasses.size() + ")");
        assertTrue(matched.size() >= 150, "Expected at least 150 matches out of 157");
    }

    @Test
    void validatesCamel10CalculatedGreaterThanOrEqualToPredefined() throws Exception {
        Path archive = SOURCE_DIR.resolve("camel/camel-camel-1.0.0.tar.gz");
        Path gtFile = BUG_DIR.resolve("camel/camel-1.0.csv");
        if (!Files.exists(archive) || !Files.exists(gtFile)) {
            return;
        }
        Path extracted = tempDir.resolve("camel10");
        extractTarGz(archive, extracted);

        Set<String> predefClasses = readPredefinedClasses(gtFile);
        List<PromiseMetricResult> results =
                new PromiseProjectAnalyzer().analyze(List.of(extracted));

        Set<String> calculated = results.stream()
                .map(PromiseMetricResult::getFullyQualifiedName)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> matched = new LinkedHashSet<>(predefClasses);
        matched.retainAll(calculated);

        System.out.println("CAMEL 1.0 -> Predefined: " + predefClasses.size()
                + ", Calculated: " + calculated.size()
                + ", Matched: " + matched.size());

        assertTrue(calculated.size() >= predefClasses.size(),
                "Calculated (" + calculated.size() + ") should be >= predefined ("
                + predefClasses.size() + ")");
    }

    private Set<String> readPredefinedClasses(Path csvPath) throws IOException {
        Set<String> classes = new LinkedHashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String line = reader.readLine(); // skip header
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    String[] cols = line.split(",");
                    if (cols.length > 0) {
                        classes.add(cols[0].trim());
                    }
                }
            }
        }
        return classes;
    }

    private void extractZip(Path zipFile, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path dest = targetDir.resolve(entry.getName()).normalize();
                if (entry.isDirectory()) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(zis, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private void extractTarGz(Path tarGzFile, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (InputStream fi = Files.newInputStream(tarGzFile);
             GzipCompressorInputStream gzi = new GzipCompressorInputStream(fi);
             TarArchiveInputStream tis = new TarArchiveInputStream(gzi)) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                Path dest = targetDir.resolve(entry.getName()).normalize();
                if (entry.isDirectory()) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(tis, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
