package org.metrics.defectlab.analysis.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.metrics.defectlab.shared.storage.StorageRoot;

class GitHubCloneServiceTest {

    @Test
    void acceptsAndNormalizesPublicGitHubUrls() throws Exception {
        GitHubCloneService service = new GitHubCloneService(new StorageRoot("storage"));
        assertEquals("https://github.com/junit-team/junit4",
                service.validateAndNormalizeUrl("https://www.github.com/junit-team/junit4/"));
        assertEquals("https://github.com/feiwww/PROMISE-backup",
                service.validateAndNormalizeUrl(
                        "https://github.com/feiwww/PROMISE-backup/tree/master/source%20code"));
    }

    @Test
    void preservesBranchAndFolderFromGitHubTreeUrls() throws Exception {
        GitHubCloneService service = new GitHubCloneService(new StorageRoot("storage"));
        GitHubCloneService.GitHubTarget target = service.parseTarget(
                "https://github.com/eclipse-jdt/eclipse.jdt.core/tree/master/org.eclipse.jdt.core");

        assertEquals("https://github.com/eclipse-jdt/eclipse.jdt.core",
                target.getRepositoryUrl());
        assertEquals("master", target.getBranch());
        assertEquals("org.eclipse.jdt.core", target.getModulePath());
    }

    @Test
    void rejectsNonGitHubAndCredentialBearingUrls() throws Exception {
        GitHubCloneService service = new GitHubCloneService(new StorageRoot("storage"));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateAndNormalizeUrl("https://example.com/owner/repository"));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateAndNormalizeUrl("https://token@github.com/owner/repository"));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateAndNormalizeUrl("https://github.com/owner/repository/issues"));
    }

    @Test
    void aeeemCloneStartsBloblessAndRetainsARegularCloneFallback() throws Exception {
        GitHubCloneService service = new GitHubCloneService(new StorageRoot("storage"));
        List<List<String>> attempts = service.cloneAttempts(
                "https://github.com/eclipse-jdt/eclipse.jdt.core",
                Paths.get("storage/extracted-projects/test-clone"),
                true);

        assertEquals(2, attempts.size());
        assertTrue(attempts.get(0).contains("--filter=blob:none"));
        assertTrue(attempts.get(0).contains("--single-branch"));
        assertFalse(attempts.get(1).contains("--filter=blob:none"));
        for (List<String> attempt : attempts) {
            assertFalse(attempt.contains("--depth"));
            assertTrue(attempt.contains("--no-checkout"));
        }
    }
}
