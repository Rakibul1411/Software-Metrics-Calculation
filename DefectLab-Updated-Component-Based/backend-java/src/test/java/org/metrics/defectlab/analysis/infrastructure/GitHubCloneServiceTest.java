package org.metrics.defectlab.analysis.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

class GitHubCloneServiceTest {

    @Test
    void acceptsAndNormalizesPublicGitHubUrls() throws Exception {
        GitHubCloneService service = new GitHubCloneService();
        assertEquals("https://github.com/junit-team/junit4",
                service.validateAndNormalizeUrl("https://www.github.com/junit-team/junit4/"));
        assertEquals("https://github.com/feiwww/PROMISE-backup",
                service.validateAndNormalizeUrl(
                        "https://github.com/feiwww/PROMISE-backup/tree/master/source%20code"));
    }

    @Test
    void preservesBranchAndFolderFromGitHubTreeUrls() throws Exception {
        GitHubCloneService service = new GitHubCloneService();
        GitHubCloneService.GitHubTarget target = service.parseTarget(
                "https://github.com/eclipse-jdt/eclipse.jdt.core/tree/master/org.eclipse.jdt.core");

        assertEquals("https://github.com/eclipse-jdt/eclipse.jdt.core",
                target.getRepositoryUrl());
        assertEquals("master", target.getBranch());
        assertEquals("org.eclipse.jdt.core", target.getModulePath());
    }

    @Test
    void acceptsGitHubBlobLinksToZipFiles() throws Exception {
        GitHubCloneService service = new GitHubCloneService();
        assertEquals(
                "https://raw.githubusercontent.com/feiwww/PROMISE-backup/master/source%20code/ant/apache-ant-1.6.0-src.zip",
                service.validateAndBuildRawZipUrl(
                        "https://github.com/feiwww/PROMISE-backup/blob/master/source%20code/ant/apache-ant-1.6.0-src.zip")
                        .toString());
    }

    @Test
    void rejectsNonGitHubAndCredentialBearingUrls() throws Exception {
        GitHubCloneService service = new GitHubCloneService();
        assertThrows(IllegalArgumentException.class,
                () -> service.validateAndNormalizeUrl("https://example.com/owner/repository"));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateAndNormalizeUrl("https://token@github.com/owner/repository"));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateAndNormalizeUrl("https://github.com/owner/repository/issues"));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateAndBuildRawZipUrl(
                        "https://github.com/owner/repository/blob/main/project.jar"));
    }

    @Test
    void aeeemCloneDownloadsHistoricalBlobsBeforeAnalysis() throws Exception {
        GitHubCloneService service = new GitHubCloneService();
        List<List<String>> attempts = service.cloneAttempts(
                "https://github.com/eclipse-jdt/eclipse.jdt.core",
                Paths.get("storage/extracted-projects/test-clone"),
                true);

        assertFalse(attempts.isEmpty());
        for (List<String> attempt : attempts) {
            assertFalse(attempt.contains("--filter=blob:none"));
            assertFalse(attempt.contains("--depth"));
            assertTrue(attempt.contains("--no-checkout"));
        }
        assertTrue(attempts.get(0).contains("--single-branch"));
    }
}
