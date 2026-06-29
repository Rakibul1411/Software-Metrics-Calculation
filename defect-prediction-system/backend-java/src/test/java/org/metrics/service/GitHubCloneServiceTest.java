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
    }

    @Test
    void rejectsNonGitHubAndCredentialBearingUrls() throws Exception {
        GitHubCloneService service = new GitHubCloneService();
        assertThrows(IllegalArgumentException.class,
                () -> service.validateAndNormalizeUrl("https://example.com/owner/repository"));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateAndNormalizeUrl("https://token@github.com/owner/repository"));
    }
}
