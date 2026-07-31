package org.metrics.aeeem.git;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Parses {@code git log --numstat --find-renames} output. */
public final class GitNumstatParser {

    private GitNumstatParser() {
    }

    public static List<FileChange> parse(String output) {
        if (output == null || output.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<FileChange> changes = new ArrayList<>();
        String commit = "";
        for (String line : output.split("\\R")) {
            if (line.startsWith("commit:")) {
                commit = line.substring("commit:".length()).trim();
                continue;
            }
            String[] fields = line.split("\\t", 3);
            if (fields.length != 3 || "-".equals(fields[0]) || "-".equals(fields[1])) {
                continue;
            }
            try {
                long added = Long.parseLong(fields[0]);
                long deleted = Long.parseLong(fields[1]);
                RenamePath rename = parsePath(fields[2]);
                if (!isJava(rename.oldPath) && !isJava(rename.newPath)) {
                    continue;
                }
                changes.add(new FileChange(commit, rename.oldPath, rename.newPath,
                        added + deleted));
            } catch (NumberFormatException ignored) {
                // Non-numstat output is ignored; Git command failures are handled
                // before this parser is called.
            }
        }
        return changes;
    }

    private static RenamePath parsePath(String displayPath) {
        String path = unquote(displayPath.trim());
        int arrow = path.indexOf(" => ");
        if (arrow < 0) {
            return new RenamePath(path, path);
        }
        int openBrace = path.lastIndexOf('{', arrow);
        int closeBrace = path.indexOf('}', arrow);
        if (openBrace >= 0 && closeBrace > arrow) {
            String prefix = path.substring(0, openBrace);
            String suffix = path.substring(closeBrace + 1);
            String inside = path.substring(openBrace + 1, closeBrace);
            int insideArrow = inside.indexOf(" => ");
            if (insideArrow >= 0) {
                return new RenamePath(
                        prefix + inside.substring(0, insideArrow) + suffix,
                        prefix + inside.substring(insideArrow + 4) + suffix);
            }
        }
        return new RenamePath(path.substring(0, arrow), path.substring(arrow + 4));
    }

    private static String unquote(String path) {
        if (path.length() >= 2 && path.charAt(0) == '"' && path.charAt(path.length() - 1) == '"') {
            return path.substring(1, path.length() - 1)
                    .replace("\\\\", "\\")
                    .replace("\\\"", "\"");
        }
        return path;
    }

    private static boolean isJava(String path) {
        return path != null && path.toLowerCase(java.util.Locale.ROOT).endsWith(".java");
    }

    private static final class RenamePath {
        private final String oldPath;
        private final String newPath;

        private RenamePath(String oldPath, String newPath) {
            this.oldPath = normalize(oldPath);
            this.newPath = normalize(newPath);
        }
    }

    public static final class FileChange {
        private final String commit;
        private final String oldPath;
        private final String newPath;
        private final long changedLines;

        private FileChange(String commit, String oldPath, String newPath, long changedLines) {
            this.commit = commit;
            this.oldPath = oldPath;
            this.newPath = newPath;
            this.changedLines = changedLines;
        }

        public String getCommit() {
            return commit;
        }

        public String getOldPath() {
            return oldPath;
        }

        public String getNewPath() {
            return newPath;
        }

        public long getChangedLines() {
            return changedLines;
        }

        public boolean isRename() {
            return !oldPath.equals(newPath);
        }
    }

    public static String normalize(String path) {
        String value = path == null ? "" : path.replace('\\', '/');
        while (value.startsWith("./")) {
            value = value.substring(2);
        }
        return value;
    }
}
