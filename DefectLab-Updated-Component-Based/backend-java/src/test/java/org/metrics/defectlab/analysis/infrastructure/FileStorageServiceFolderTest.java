package org.metrics.defectlab.analysis.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class FileStorageServiceFolderTest {

    @Test
    void rebuildsThePackageLayoutOfASelectedFolder() throws Exception {
        MultipartFile[] files = {
                javaFile("Core.java", "package demo; public class Core { }"),
                javaFile("Helper.java", "package demo.util; public class Helper { }")
        };
        List<String> paths = List.of(
                "apache-ant-1.7.0/src/main/demo/Core.java",
                "apache-ant-1.7.0/src/main/demo/util/Helper.java");

        Path root = new FileStorageService().storeProjectFolder(files, paths);
        try {
            assertTrue(Files.isRegularFile(
                    root.resolve("apache-ant-1.7.0/src/main/demo/Core.java")));
            assertEquals("package demo.util; public class Helper { }",
                    Files.readString(root.resolve(
                            "apache-ant-1.7.0/src/main/demo/util/Helper.java")));
        } finally {
            FileStorageService.deleteRecursively(root);
        }
    }

    @Test
    void skipsFilesThatAreNotJavaSources() throws Exception {
        MultipartFile[] files = {
                javaFile("Core.java", "package demo; public class Core { }"),
                javaFile("build.xml", "<project/>")
        };
        List<String> paths = List.of("release/demo/Core.java", "release/build.xml");

        Path root = new FileStorageService().storeProjectFolder(files, paths);
        try {
            assertTrue(Files.isRegularFile(root.resolve("release/demo/Core.java")));
            assertFalse(Files.exists(root.resolve("release/build.xml")));
        } finally {
            FileStorageService.deleteRecursively(root);
        }
    }

    @Test
    void containsPathsThatTryToEscapeTheUploadRoot() {
        MultipartFile[] files = {javaFile("Evil.java", "package x; public class Evil { }")};

        assertThrows(IOException.class, () -> new FileStorageService().storeProjectFolder(
                files, List.of("../../../../escaped/Evil.java")));
    }

    @Test
    void rejectsFolderUploadsThatCarryNoUsableSource() {
        FileStorageService service = newService();
        MultipartFile[] files = {javaFile("notes.txt", "no java here")};

        assertThrows(IllegalArgumentException.class,
                () -> service.storeProjectFolder(files, List.of("release/notes.txt")));
        assertThrows(IllegalArgumentException.class,
                () -> service.storeProjectFolder(new MultipartFile[0], List.of()));
    }

    @Test
    void rejectsAPathListThatDoesNotMatchTheFileList() {
        MultipartFile[] files = {
                javaFile("Core.java", "package demo; public class Core { }"),
                javaFile("Helper.java", "package demo; public class Helper { }")
        };

        assertThrows(IllegalArgumentException.class, () -> new FileStorageService()
                .storeProjectFolder(files, List.of("release/demo/Core.java")));
        assertThrows(IllegalArgumentException.class, () -> new FileStorageService()
                .storeProjectFolder(files, null));
    }

    private static FileStorageService newService() {
        try {
            return new FileStorageService();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static MultipartFile javaFile(String name, String content) {
        return new MockMultipartFile("projectFiles", name, "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
    }
}
