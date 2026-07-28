package org.metrics.aeeem.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;

class GitNumstatParserTest {

    @Test
    void parsesJavaLineChangesAndRenameForms() {
        String output = "commit:one\n"
                + "10\t4\tsrc/main/java/demo/A.java\n"
                + "-\t-\tsrc/main/resources/logo.png\n"
                + "commit:two\n"
                + "3\t1\tsrc/main/java/demo/{Old.java => New.java}\n"
                + "2\t2\tsrc/legacy/Before.java => src/main/java/demo/After.java\n";

        List<GitNumstatParser.FileChange> changes = GitNumstatParser.parse(output);

        assertEquals(3, changes.size());
        assertEquals("one", changes.get(0).getCommit());
        assertEquals(14d, changes.get(0).getChangedLines(), 1.0e-12);
        assertEquals("src/main/java/demo/Old.java",
                changes.get(1).getOldPath());
        assertEquals("src/main/java/demo/New.java",
                changes.get(1).getNewPath());
        assertEquals("src/legacy/Before.java",
                changes.get(2).getOldPath());
        assertEquals("src/main/java/demo/After.java",
                changes.get(2).getNewPath());
        assertFalse(changes.stream()
                .anyMatch(file -> file.getNewPath().endsWith(".png")));
    }
}
