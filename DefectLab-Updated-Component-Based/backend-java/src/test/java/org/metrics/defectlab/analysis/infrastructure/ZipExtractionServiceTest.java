package org.metrics.defectlab.analysis.infrastructure;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipExtractionServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void extractsAJavaProjectZip() throws Exception {
        Path zip = createZip("src/main/java/demo/Example.java", "package demo; class Example {}");
        Path extracted = new ZipExtractionService().extractArchiveFile(zip);
        try {
            assertTrue(Files.exists(extracted.resolve("src/main/java/demo/Example.java")));
        } finally {
            FileStorageService.deleteRecursively(extracted);
        }
    }

    @Test
    void extractsAJavaProjectTarGz() throws Exception {
        Path tarGz = createTarGz("lucene/src/java/demo/Example.java", "package demo; class Example {}");
        Path extracted = new ZipExtractionService().extractArchiveFile(tarGz);
        try {
            assertTrue(Files.exists(extracted.resolve("lucene/src/java/demo/Example.java")));
        } finally {
            FileStorageService.deleteRecursively(extracted);
        }
    }

    @Test
    void extractsAJavaProjectTar() throws Exception {
        Path tar = createTar("src/main/java/demo/Example.java", "package demo; class Example {}");
        Path extracted = new ZipExtractionService().extractArchiveFile(tar);
        try {
            assertTrue(Files.exists(extracted.resolve("src/main/java/demo/Example.java")));
        } finally {
            FileStorageService.deleteRecursively(extracted);
        }
    }

    @Test
    void rejectsZipSlipEntries() throws Exception {
        Path zip = createZip("../../outside.java", "class Outside {}");
        assertThrows(IOException.class, () -> new ZipExtractionService().extractArchiveFile(zip));
    }

    @Test
    void rejectsTarGzSlipEntries() throws Exception {
        Path tarGz = createTarGz("../../outside.java", "class Outside {}");
        assertThrows(IOException.class, () -> new ZipExtractionService().extractArchiveFile(tarGz));
    }

    private Path createZip(String entryName, String content) throws IOException {
        Path zip = tempDirectory.resolve("project.zip");
        try (OutputStream output = Files.newOutputStream(zip);
             ZipOutputStream zipOutput = new ZipOutputStream(output)) {
            zipOutput.putNextEntry(new ZipEntry(entryName));
            zipOutput.write(content.getBytes(StandardCharsets.UTF_8));
            zipOutput.closeEntry();
        }
        return zip;
    }

    private Path createTarGz(String entryName, String content) throws IOException {
        Path tarGz = tempDirectory.resolve("project.tar.gz");
        try (OutputStream output = Files.newOutputStream(tarGz);
             GzipCompressorOutputStream gzipOutput = new GzipCompressorOutputStream(output)) {
            writeTar(gzipOutput, entryName, content);
        }
        return tarGz;
    }

    private Path createTar(String entryName, String content) throws IOException {
        Path tar = tempDirectory.resolve("project.tar");
        try (OutputStream output = Files.newOutputStream(tar)) {
            writeTar(output, entryName, content);
        }
        return tar;
    }

    private void writeTar(OutputStream output, String entryName, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(output)) {
            TarArchiveEntry entry = new TarArchiveEntry(entryName);
            entry.setSize(bytes.length);
            tarOutput.putArchiveEntry(entry);
            tarOutput.write(bytes);
            tarOutput.closeArchiveEntry();
        }
    }
}
