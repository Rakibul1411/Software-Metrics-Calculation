package org.metrics.defectlab.analysis.promise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metrics.defectlab.analysis.promise.compile.PromiseCompilationException;
import org.metrics.defectlab.analysis.promise.model.PromiseMetricResult;

/**
 * Cross-metric invariants, reproducibility, and the reliability rules that keep
 * an unmeasurable class out of the output instead of giving it fabricated values.
 */
class PromiseInvariantTest {

    @TempDir
    Path workspace;

    private static final String RICH_SOURCE =
            "package p;\n"
            + "import java.util.ArrayList;\n"
            + "public class Rich extends Base {\n"
            + "    private int counter;\n"
            + "    protected String label;\n"
            + "    public Rich() { counter = 0; }\n"
            + "    public int classify(int value) {\n"
            + "        if (value > 10) { return 2; }\n"
            + "        while (value > 0) { value--; counter++; }\n"
            + "        switch (value) {\n"
            + "            case 0: return 0;\n"
            + "            case 1: return 1;\n"
            + "            default: return -1;\n"
            + "        }\n"
            + "    }\n"
            + "    public void collect() { new ArrayList().add(label); }\n"
            + "    private void helper() { counter++; }\n"
            + "}\n";

    private Map<String, PromiseMetricResult> analyzeRichProject() throws Exception {
        return PromiseFixtureProject.in(workspace)
                .write("p/Base.java",
                        "package p;\n"
                        + "public class Base {\n"
                        + "    public void inheritedWork() { }\n"
                        + "}\n")
                .write("p/Rich.java", RICH_SOURCE)
                .analyze();
    }

    @Test
    void ratiosStayWithinTheirDefinedRange() throws Exception {
        for (PromiseMetricResult row : analyzeRichProject().values()) {
            assertTrue(row.getDam() >= 0 && row.getDam() <= 1,
                    "DAM out of range for " + row.getFullyQualifiedName());
            assertTrue(row.getMfa() >= 0 && row.getMfa() <= 1,
                    "MFA out of range for " + row.getFullyQualifiedName());
            assertTrue(row.getCam() >= 0 && row.getCam() <= 1,
                    "CAM out of range for " + row.getFullyQualifiedName());
        }
    }

    @Test
    void sizeMetricsAgreeWithTheCompiledMethodUniverse() throws Exception {
        PromiseMetricResult rich = analyzeRichProject().get("p.Rich");
        assertNotNull(rich);

        // Two declared fields; <init>, classify, collect and helper.
        int fields = 2;
        assertEquals(4, rich.getWmc());
        assertTrue(rich.getNpm() <= rich.getWmc(), "NPM must not exceed WMC");

        // LOC counts fields, methods and every bytecode instruction.
        assertTrue(rich.getLoc() >= fields + rich.getWmc(),
                "LOC must be at least fields + methods");

        // AMC is the average instruction count, so it is pinned to LOC exactly.
        double expectedAmc = (double) (rich.getLoc() - fields - rich.getWmc())
                / rich.getWmc();
        assertEquals(expectedAmc, rich.getAmc(), 1e-9);
    }

    @Test
    void complexityAggregatesMatchTheirMethodCollection() throws Exception {
        PromiseMetricResult rich = analyzeRichProject().get("p.Rich");
        // classify() carries an if, a loop and a switch over dense case values
        // (0, 1), which compiles to a bytecode "tableswitch". CKJM's own CC
        // visitor never special-cases tableswitch (only "lookupswitch"), so it
        // contributes a flat +1 regardless of case count: if(1) + loop(1) +
        // switch(1) + base(1) = 4.
        assertEquals(4, rich.getMaxCc());
        assertTrue(rich.getAvgCc() <= rich.getMaxCc(),
                "the mean can never exceed the maximum");
        assertTrue(rich.getAvgCc() >= 1.0,
                "every compiled method has a base complexity of 1");
    }

    @Test
    void couplingDirectionsComeFromOneSharedGraph() throws Exception {
        Map<String, PromiseMetricResult> rows = analyzeRichProject();
        PromiseMetricResult rich = rows.get("p.Rich");
        PromiseMetricResult base = rows.get("p.Base");

        // Rich extends Base, so the edge shows up on both sides exactly once.
        assertEquals(1, rich.getCe());
        assertEquals(1, base.getCa());
        // CBO is the union, so it never falls below either direction.
        assertTrue(rich.getCbo() >= Math.max(rich.getCa(), rich.getCe()));
        assertTrue(rich.getCbo() <= rich.getCa() + rich.getCe());
    }

    @Test
    void neverEmitsNaNOrInfinity() throws Exception {
        for (PromiseMetricResult row : analyzeRichProject().values()) {
            for (double value : List.of(row.getLcom3(), row.getDam(),
                    row.getMfa(), row.getCam(), row.getAmc(), row.getAvgCc())) {
                assertFalse(Double.isNaN(value),
                        "NaN emitted for " + row.getFullyQualifiedName());
                assertFalse(Double.isInfinite(value),
                        "Infinity emitted for " + row.getFullyQualifiedName());
            }
        }
    }

    @Test
    void repeatedExtractionIsDeterministic() throws Exception {
        Map<String, PromiseMetricResult> first = analyzeRichProject();
        Map<String, PromiseMetricResult> second = analyzeRichProject();

        assertEquals(first.keySet(), second.keySet());
        for (String name : first.keySet()) {
            PromiseMetricResult left = first.get(name);
            PromiseMetricResult right = second.get(name);
            assertEquals(left.getWmc(), right.getWmc(), name);
            assertEquals(left.getLoc(), right.getLoc(), name);
            assertEquals(left.getCbo(), right.getCbo(), name);
            assertEquals(left.getRfc(), right.getRfc(), name);
            assertEquals(left.getLcom(), right.getLcom(), name);
            assertEquals(left.getAmc(), right.getAmc(), 0.0, name);
            assertEquals(left.getAvgCc(), right.getAvgCc(), 0.0, name);
        }
    }

    @Test
    void dropsClassesThatCouldNotBeCompiledInsteadOfGuessing() throws Exception {
        Map<String, PromiseMetricResult> rows = PromiseFixtureProject.in(workspace)
                .write("p/Sound.java",
                        "package p;\npublic class Sound { public void ok() { } }\n")
                .write("p/Broken.java",
                        "package p;\n"
                        + "import totally.absent.Missing;\n"
                        + "public class Broken extends Missing {\n"
                        + "    public void work() { }\n"
                        + "}\n")
                .analyze();

        // The healthy class is still measured.
        assertNotNull(rows.get("p.Sound"));
        // The compiler leaves a Broken.class behind whose bodies only throw, so
        // measuring it would invent an instruction count, complexity and coupling.
        assertNull(rows.get("p.Broken"),
                "a class that failed to compile must not produce a metric row");
    }

    @Test
    void failsLoudlyWhenNothingCompiles() throws Exception {
        PromiseCompilationException failure = assertThrows(
                PromiseCompilationException.class,
                () -> PromiseFixtureProject.in(workspace)
                        .write("p/Broken.java",
                                "package p;\n"
                                + "import totally.absent.Missing;\n"
                                + "public class Broken extends Missing { }\n")
                        .analyze());

        assertTrue(failure.getMessage().contains("PROMISE"),
                "the diagnostic should explain that strict extraction failed");
    }
}
