package org.metrics.defectlab.analysis.promise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metrics.defectlab.analysis.promise.model.PromiseMetricResult;

class PromisePipelineSmokeTest {

    @TempDir
    Path workspace;

    @Test
    void compilesSourceAndMeasuresBytecode() throws Exception {
        Map<String, PromiseMetricResult> rows = PromiseFixtureProject.in(workspace)
                .write("p/Simple.java",
                        "package p;\n"
                        + "public class Simple {\n"
                        + "    private int n;\n"
                        + "    public int go(int x) { if (x > 0) { n++; } return n; }\n"
                        + "}\n")
                .analyze();

        PromiseMetricResult simple = rows.get("p.Simple");
        assertNotNull(simple, "the class should produce a row");

        // The default constructor plus go(int): the compiled method universe.
        assertEquals(2, simple.getWmc());
        assertEquals(1, simple.getDit());
        // Both are public, and CKJM's NPM iterates compiled methods, so the
        // implicit constructor of a public class counts.
        assertEquals(2, simple.getNpm());
        // go() has a single conditional branch, the constructor none.
        assertEquals(2, simple.getMaxCc());
    }
}
