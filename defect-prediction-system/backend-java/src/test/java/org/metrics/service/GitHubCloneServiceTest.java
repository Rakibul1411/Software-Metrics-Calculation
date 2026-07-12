package org.metrics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
