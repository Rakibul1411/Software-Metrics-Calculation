package org.metrics.defectlab.analysis.promise;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metrics.defectlab.analysis.promise.model.PromiseMetricResult;

/**
 * A single class whose every PROMISE value is derived by hand from its compiled
 * form, so the whole 20-feature row is pinned by one readable example.
 *
 * <pre>
 * public p.Counter();          aload_0, invokespecial, return          -> 3 instructions
 * public void add(int);        iload_1, ifle, aload_0, dup, getfield,
 *                              iload_1, iadd, putfield, return         -> 9 instructions
 * public int get();            aload_0, getfield, ireturn              -> 3 instructions
 * </pre>
 */
class PromiseWorkedExampleTest {

    @TempDir
    Path workspace;

    @Test
    void everyFeatureMatchesTheHandDerivedValue() throws Exception {
        Map<String, PromiseMetricResult> rows = PromiseFixtureProject.in(workspace)
                .write("p/Counter.java",
                        "package p;\n"
                        + "public class Counter {\n"
                        + "    private int total;\n"
                        + "    public void add(int value) {\n"
                        + "        if (value > 0) { total = total + value; }\n"
                        + "    }\n"
                        + "    public int get() { return total; }\n"
                        + "}\n")
                .analyze();

        PromiseMetricResult counter = rows.get("p.Counter");

        // WMC: <init>, add(int), get().
        assertEquals(3, counter.getWmc());
        // DIT: extends Object directly.
        assertEquals(1, counter.getDit());
        // NOC: nothing extends Counter.
        assertEquals(0, counter.getNoc());
        // Only java.lang.Object is referenced, and the JDK is excluded from coupling.
        assertEquals(0, counter.getCbo());
        assertEquals(0, counter.getCa());
        assertEquals(0, counter.getCe());
        // RFC: 3 own methods plus the JDK call java.lang.Object.<init>().
        assertEquals(4, counter.getRfc());
        // LCOM: <init>{} add{total} get{total}. Pairs: 2 disjoint, 1 sharing.
        assertEquals(1, counter.getLcom());
        // NPM: all three compiled methods are public.
        assertEquals(3, counter.getNpm());
        // LCOM3: a=1, m=3, mu(total)=2 -> (2/1 - 3) / (1 - 3) = 0.5.
        assertEquals(0.5, counter.getLcom3(), 1e-9);
        // LOC: 1 field + 3 methods + (3 + 9 + 3) instructions.
        assertEquals(19, counter.getLoc());
        // DAM: the single field is private.
        assertEquals(1.0, counter.getDam(), 1e-9);
        // MOA: int is primitive, so nothing aggregates.
        assertEquals(0, counter.getMoa());
        // MFA: Object's methods are ignored, so nothing is inherited.
        assertEquals(0.0, counter.getMfa(), 1e-9);
        // CAM: {this}=1, {int,this}=2, {this}=1 over |{this,int}| * 3 = 6.
        assertEquals(4.0 / 6.0, counter.getCam(), 1e-9);
        // IC/CBM: no superclass in the project, so no inheritance coupling.
        assertEquals(0, counter.getIc());
        assertEquals(0, counter.getCbm());
        // AMC: 15 instructions over 3 methods.
        assertEquals(5.0, counter.getAmc(), 1e-9);
        // Max_CC: add() has one conditional branch; the others are straight-line.
        assertEquals(2, counter.getMaxCc());
        // Avg_CC: the constructor contributes 0 (CKJM records no CC entry for
        // <init>), so (0 + 2 + 1) / 3 = 1.0.
        assertEquals(1.0, counter.getAvgCc(), 1e-9);

        // The defining AMC/LOC relationship holds for this row.
        assertEquals((double) (counter.getLoc() - 1 - counter.getWmc()) / counter.getWmc(),
                counter.getAmc(), 1e-9);
    }
}
