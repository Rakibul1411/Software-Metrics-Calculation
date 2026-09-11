package org.metrics.defectlab.analysis.aeeem.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metrics.defectlab.analysis.aeeem.history.AeeemBenchmarkProfile;

class AeeemDatasetValidationTest {

    private static final Path WORKSPACE = Path.of(
            "/Users/md.rakibulislam/IIT/SPL-3/promise-dataset-source-code/DefectLab-Updated-Component-Based");
    private static final Path LUCENE_TAR = WORKSPACE.resolve(
            "Final dataset list/PROMISE-backup-copy/source code/lucene/lucene-solr-releases-lucene-2.4.0.tar.gz");

    @TempDir
    Path tempDir;

    @Test
    void validatesPdeProfileModuleScopeIncludesBothUiAndCore() {
        AeeemBenchmarkProfile profile = AeeemBenchmarkProfile.PDE;
        assertEquals("ui", profile.getDefaultModulePath(),
                "PDE default module path must be 'ui' to include both PDE UI and PDE Core plugins.");
        assertEquals(1497, profile.getReferenceRowCount(),
                "PDE reference row count must match predefined PDE.arff.");
    }

    @Test
    void validatesMylynProfileIncludesAllHistoricalComponents() {
        AeeemBenchmarkProfile profile = AeeemBenchmarkProfile.ML;
        List<AeeemBenchmarkProfile.HistoricalRepository> repos = profile.getHistoricalRepositories();
        assertEquals(6, repos.size(),
                "Mylyn must include releng, commons, context, tasks, docs, and incubator repositories.");

        Set<String> repoNames = repos.stream()
                .map(AeeemBenchmarkProfile.HistoricalRepository::getName)
                .collect(Collectors.toSet());
        assertTrue(repoNames.contains("Mylyn Docs"), "Must include Mylyn Docs (WikiText)");
        assertTrue(repoNames.contains("Mylyn Incubator"), "Must include Mylyn Incubator");
        assertEquals(1862, profile.getReferenceRowCount(),
                "Mylyn reference row count must match predefined ML.arff.");
    }

    @Test
    void validatesLuceneProductionSourceSelectorRetainsBenchmarkContrib() throws Exception {
        if (!Files.exists(LUCENE_TAR)) {
            return;
        }
        Path extracted = tempDir.resolve("lucene-2.4.0");
        extractTarGz(LUCENE_TAR, extracted);

        List<Path> productionFiles = ProductionSourceSelector.collectJavaFiles(extracted);

        // Verify that contrib/benchmark classes are NOT excluded
        boolean hasBenchmarkContrib = productionFiles.stream().anyMatch(
                p -> p.toString().contains("contrib/benchmark")
                        || p.toString().contains("contrib\\benchmark"));
        assertTrue(hasBenchmarkContrib,
                "ProductionSourceSelector must include Lucene's contrib/benchmark production classes.");

        System.out.println("Lucene 2.4.0 production java files: " + productionFiles.size());
        assertTrue(productionFiles.size() >= 691,
                "Lucene production file count (" + productionFiles.size()
                        + ") must be >= predefined benchmark count (691).");
    }

    @Test
    void validatesEquinoxAndJdtReferenceProperties() {
        assertEquals("bundles/org.eclipse.osgi", AeeemBenchmarkProfile.EQ.getDefaultModulePath());
        assertEquals(324, AeeemBenchmarkProfile.EQ.getReferenceRowCount());

        assertEquals("org.eclipse.jdt.core", AeeemBenchmarkProfile.JDT.getDefaultModulePath());
        assertEquals(997, AeeemBenchmarkProfile.JDT.getReferenceRowCount());
    }

    private static void extractTarGz(Path tarGzFile, Path destDir) throws Exception {
        try (InputStream fi = Files.newInputStream(tarGzFile);
             GzipCompressorInputStream gzi = new GzipCompressorInputStream(fi);
             TarArchiveInputStream ti = new TarArchiveInputStream(gzi)) {
            TarArchiveEntry entry;
            while ((entry = ti.getNextTarEntry()) != null) {
                Path destPath = destDir.resolve(entry.getName()).normalize();
                if (!destPath.startsWith(destDir)) {
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destPath);
                } else {
                    Files.createDirectories(destPath.getParent());
                    Files.copy(ti, destPath);
                }
            }
        }
    }
}
