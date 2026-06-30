package org.metrics.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipExtractionServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void extractsAJavaProjectZip() throws Exception {
        Path zip = createZip("src/main/java/demo/Example.java", "package demo; class Example {}");
        Path extracted = new ZipExtractionService().extractZipFile(zip);
        try {
            assertTrue(Files.exists(extracted.resolve("src/main/java/demo/Example.java")));
        } finally {
            FileStorageService.deleteRecursively(extracted);
        }
    }

    @Test
    void rejectsZipSlipEntries() throws Exception {
        Path zip = createZip("../../outside.java", "class Outside {}");
        assertThrows(IOException.class, () -> new ZipExtractionService().extractZipFile(zip));
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
}
