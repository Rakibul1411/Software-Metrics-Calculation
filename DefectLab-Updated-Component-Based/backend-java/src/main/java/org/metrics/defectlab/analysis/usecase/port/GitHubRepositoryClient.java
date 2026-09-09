package org.metrics.defectlab.analysis.usecase.port;

import java.io.IOException;
import java.nio.file.Path;

import org.metrics.defectlab.analysis.aeeem.history.AeeemAnalysisOptions;

/** Output port: how use cases resolve a GitHub URL and materialise its source on disk. */
public interface GitHubRepositoryClient {

    GitHubTarget parseTarget(String gitUrl);

    Path cloneRepository(GitHubTarget target, boolean fullHistory) throws IOException;

    void checkoutHead(Path repository) throws IOException;

    /** A parsed GitHub repository URL, optionally scoped to a branch and module folder. */
    final class GitHubTarget {
        private final String repositoryUrl;
        private final String branch;
        private final String modulePath;

        public GitHubTarget(String repositoryUrl, String branch, String modulePath) {
            this.repositoryUrl = repositoryUrl;
            this.branch = branch == null || branch.trim().isEmpty() ? null : branch.trim();
            this.modulePath = AeeemAnalysisOptions.normalizeModulePath(modulePath);
        }

        public String getRepositoryUrl() {
            return repositoryUrl;
        }

        public String getBranch() {
            return branch;
        }

        public String getModulePath() {
            return modulePath;
        }
    }
}
