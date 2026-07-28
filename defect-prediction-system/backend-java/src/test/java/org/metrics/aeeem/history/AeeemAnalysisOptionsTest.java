package org.metrics.aeeem.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class AeeemAnalysisOptionsTest {

    @Test
    void exposesThePeriodsAndReferenceSizesFromTheSuppliedDatasets() {
        assertProfile("jdt", "2005-01-01", "2008-06-17", 91, 997);
        assertProfile("pde", "2005-01-01", "2008-09-11", 97, 1497);
        assertProfile("eq", "2005-01-01", "2008-06-25", 91, 324);
        assertProfile("ml", "2005-01-17", "2009-03-17", 98, 1862);
        assertProfile("lc", "2005-01-01", "2008-10-08", 99, 691);
    }

    @Test
    void benchmarkProfileUsesFolderScopeAndCannotBeCappedToRecentHistory() {
        AeeemAnalysisOptions options = AeeemAnalysisOptions.fromRequest(
                "jdt", "master", "org.eclipse.jdt.core", null, null, null, 26);

        assertEquals("master", options.getBranch());
        assertEquals("org.eclipse.jdt.core", options.getModulePath());
        assertEquals("R3_4", options.getReleaseRef());
        assertEquals(Integer.valueOf(0), options.getMaximumSnapshots());
    }

    @Test
    void rejectsUnsafeModulePathsAndInvalidCustomDates() {
        assertThrows(IllegalArgumentException.class,
                () -> AeeemAnalysisOptions.fromRequest(
                        "current", null, "../other", null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> AeeemAnalysisOptions.fromRequest(
                        "current", null, null, "2025/01/01", null, null, null));
    }

    private void assertProfile(
            String id,
            String start,
            String release,
            int snapshots,
            int rows) {
        AeeemBenchmarkProfile profile = AeeemBenchmarkProfile.fromId(id);
        assertEquals(LocalDate.parse(start), profile.getHistoryStart());
        assertEquals(LocalDate.parse(release), profile.getReleaseDate());
        assertEquals(snapshots, profile.getReferenceSnapshotCount());
        assertEquals(rows, profile.getReferenceRowCount());
    }
}
