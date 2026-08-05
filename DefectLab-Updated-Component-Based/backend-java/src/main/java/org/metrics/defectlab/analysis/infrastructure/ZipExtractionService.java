package org.metrics.defectlab.analysis.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.stereotype.Service;

@Service
public class ZipExtractionService {

    private static final int MAX_ENTRIES = 20_000;
    private static final long MAX_UNCOMPRESSED_BYTES = 250L * 1024L * 1024L;
    private static final int BUFFER_SIZE = 8192;

    private final Path extractLocation = Paths.get("storage/extracted-projects");

    public ZipExtractionService() throws IOException {
        Files.createDirectories(extractLocation);
    }

    public Path extractArchiveFile(Path archivePath) throws IOException {
        String filename = archivePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".zip")) {
            return extractZipFile(archivePath);
        }
        if (filename.endsWith(".tar.gz") || filename.endsWith(".tgz")
                || filename.endsWith(".gz")) {
            return extractTarFile(archivePath, true);
        }
        if (filename.endsWith(".tar")) {
            return extractTarFile(archivePath, false);
        }
        throw new IOException(
                "Unsupported project archive. Use .zip, .tar, .tar.gz, .tgz, or .gz.");
    }

    private Path extractZipFile(Path zipFilePath) throws IOException {
        Path targetPath = extractLocation.resolve("zip_" + UUID.randomUUID()).toAbsolutePath().normalize();
        Files.createDirectories(targetPath);

        int entryCount = 0;
        long totalBytes = 0;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (InputStream input = Files.newInputStream(zipFilePath);
             ZipInputStream zipInput = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new IOException("The ZIP contains too many files.");
                }

                Path destination = safeDestination(targetPath, entry.getName(), "ZIP");

                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else {
                    Path parent = destination.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    try (java.io.OutputStream output = Files.newOutputStream(
                            destination, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                        int read;
                        while ((read = zipInput.read(buffer)) != -1) {
                            totalBytes += read;
                            if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                                throw new IOException("The uncompressed ZIP is larger than 250 MB.");
                            }
                            output.write(buffer, 0, read);
                        }
                    }
                }
                zipInput.closeEntry();
            }
        } catch (IOException exception) {
            FileStorageService.deleteRecursively(targetPath);
            throw exception;
        }

        if (entryCount == 0) {
            FileStorageService.deleteRecursively(targetPath);
            throw new IOException("The uploaded ZIP is empty or invalid.");
        }
        return targetPath;
    }

    private Path extractTarFile(Path tarFilePath, boolean gzipCompressed) throws IOException {
        String archiveType = gzipCompressed ? "TAR.GZ" : "TAR";
        Path targetPath = extractLocation.resolve("tar_" + UUID.randomUUID()).toAbsolutePath().normalize();
        Files.createDirectories(targetPath);

        int entryCount = 0;
        long totalBytes = 0;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (InputStream input = Files.newInputStream(tarFilePath);
             InputStream archiveInput = gzipCompressed
                     ? new GzipCompressorInputStream(input) : input;
             TarArchiveInputStream tarInput = new TarArchiveInputStream(archiveInput)) {
            TarArchiveEntry entry;
            while ((entry = tarInput.getNextTarEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new IOException("The " + archiveType + " contains too many files.");
                }
                if (entry.isSymbolicLink() || entry.isLink()) {
                    throw new IOException(
                            "The " + archiveType + " contains links, which are not supported.");
                }

                Path destination = safeDestination(targetPath, entry.getName(), archiveType);
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else if (entry.isFile()) {
                    Path parent = destination.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    try (java.io.OutputStream output = Files.newOutputStream(
                            destination, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                        int read;
                        while ((read = tarInput.read(buffer)) != -1) {
                            totalBytes += read;
                            if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                                throw new IOException(
                                        "The uncompressed " + archiveType + " is larger than 250 MB.");
                            }
                            output.write(buffer, 0, read);
                        }
                    }
                }
            }
        } catch (IOException exception) {
            FileStorageService.deleteRecursively(targetPath);
            throw exception;
        }

        if (entryCount == 0) {
            FileStorageService.deleteRecursively(targetPath);
            throw new IOException("The uploaded " + archiveType + " is empty or invalid.");
        }
        return targetPath;
    }

    private Path safeDestination(Path targetPath, String entryName, String archiveType) throws IOException {
        if (entryName == null || entryName.trim().isEmpty()) {
            throw new IOException("The " + archiveType + " contains an empty file path.");
        }
        Path destination = targetPath.resolve(entryName.replace('\\', '/')).normalize();
        if (!destination.startsWith(targetPath)) {
            throw new IOException("The " + archiveType + " contains an unsafe file path.");
        }
        return destination;
    }
}
