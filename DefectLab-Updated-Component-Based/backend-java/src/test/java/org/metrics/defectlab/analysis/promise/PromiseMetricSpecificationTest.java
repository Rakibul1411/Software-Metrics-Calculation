package org.metrics.defectlab.analysis.promise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metrics.defectlab.analysis.promise.model.PromiseMetricResult;

/**
 * Specification tests for the 20 PROMISE features.
 *
 * <p>Every expected value is derived by hand from the Jureczko/Madeyski
 * definitions and CKJM's documented measurement decisions. No external metric
 * tool is consulted.
 *
 * <p>Metrics that depend on exact instruction counts (LOC, AMC) are verified
 * through their defining relationship in {@link PromiseInvariantTest} rather
 * than against hard-coded numbers, because the instruction total legitimately
 * varies with the compiler version.
 */
class PromiseMetricSpecificationTest {

    @TempDir
    Path workspace;

    @Test
    void countsConstructorsAndStaticInitializersAsCompiledMethods() throws Exception {
        Map<String, PromiseMetricResult> rows = PromiseFixtureProject.in(workspace)
                .write("p/Universe.java",
                        "package p;\n"
                        + "public class Universe {\n"
                        + "    static int shared;\n"
                        + "    static { shared = 1; }\n"
                        + "    public Universe() { }\n"
                        + "    public void open() { }\n"
                        + "    private void hidden() { }\n"
                        + "    protected void guarded() { }\n"
                        + "}\n")
                .analyze();

        PromiseMetricResult universe = rows.get("p.Universe");
        // <clinit>, <init>, open, hidden, guarded.
        assertEquals(5, universe.getWmc());
        // Only the constructor and open() are public; <clinit> is not.
        assertEquals(2, universe.getNpm());
        assertTrue(universe.getNpm() <= universe.getWmc());
    }

    @Test
    void measuresInheritanceDepthAndImmediateChildrenOnly() throws Exception {
        Map<String, PromiseMetricResult> rows = PromiseFixtureProject.in(workspace)
                .write("p/A.java", "package p;\npublic class A { }\n")
                .write("p/B.java", "package p;\npublic class B extends A { }\n")
                .write("p/C.java", "package p;\npublic class C extends A { }\n")
                .write("p/D.java", "package p;\npublic class D extends B { }\n")
                .write("p/Marker.java", "package p;\npublic interface Marker { }\n")
                .write("p/Impl.java",
                        "package p;\npublic class Impl implements Marker { }\n")
                .analyze();

        assertEquals(1, rows.get("p.A").getDit());
        assertEquals(2, rows.get("p.B").getDit());
        assertEquals(3, rows.get("p.D").getDit());

        // B and C are immediate children of A; D is a grandchild.
        assertEquals(2, rows.get("p.A").getNoc());
        assertEquals(1, rows.get("p.B").getNoc());
        // Implementing an interface is not inheritance.
        assertEquals(0, rows.get("p.Marker").getNoc());
    }

    @Test
    void interfaceImplementationStillCountsAsCoupling() throws Exception {
        Map<String, PromiseMetricResult> rows = PromiseFixtureProject.in(workspace)
                .write("p/Marker.java", "package p;\npublic interface Marker { }\n")
                .write("p/Impl.java",
                        "package p;\npublic class Impl implements Marker { }\n")
                .analyze();

        // NOC ignores the interface, but the coupling graph must not.
        assertEquals(1, rows.get("p.Impl").getCe());
        assertEquals(1, rows.get("p.Marker").getCa());
        assertEquals(1, rows.get("p.Impl").getCbo());
    }

    @Test
    void aggregationCountsProjectTypesIncludingSelfAssociation() throws Exception {
        Map<String, PromiseMetricResult> rows = PromiseFixtureProject.in(workspace)
                .write("p/Node.java",
                        "package p;\n"
                        + "public class Node {\n"
                        + "    Node next;\n"                  // self-association counts
                        + "    Payload payload;\n"            // project type counts
                        + "    int count;\n"                  // primitive never counts
                        + "    String label;\n"               // JDK type never counts
                        + "    java.util.List items;\n"       // JDK type never counts
                        + "}\n")
                .write("p/Payload.java", "package p;\npublic class Payload { }\n")
                .analyze();

        assertEquals(2, rows.get("p.Node").getMoa());
    }

    @Test
    void responseSetSeparatesOverloadsAndKeepsJdkCalls() throws Exception {
        Map<String, PromiseMetricResult> rows = PromiseFixtureProject.in(workspace)
                .write("p/Responder.java",
                        "package p;\n"
                        + "public class Responder {\n"
                        + "    public void call() { over(1); over(\"s\"); }\n"
                        + "    public void over(int x) { }\n"
                        + "    public void over(String s) { }\n"
                        + "}\n")
                .analyze();

        // Own methods: <init>, call, over(int), over(String).
        // Plus the JDK call java.lang.Object.<init>() made by the constructor.
        // Collapsing the two overloads would give 4 instead.
        assertEquals(5, rows.get("p.Responder").getRfc());
    }

    @Test
    void couplingExcludesJdkButKeepsSignatureAndCatchTypes() throws Exception {
        Map<String, PromiseMetricResult> rows = PromiseFixtureProject.in(workspace)
                .write("p/Failure.java",
                        "package p;\npublic class Failure extends Exception { }\n")
                .write("p/Argument.java", "package p;\npublic class Argument { }\n")
                .write("p/Returned.java", "package p;\npublic class Returned { }\n")
                .write("p/Coupled.java",
                        "package p;\n"
                        + "public class Coupled {\n"
                        + "    public Returned convert(Argument a) throws Failure {\n"
                        + "        java.util.List ignored = null;\n"   // JDK: excluded
                        + "        return null;\n"
                        + "    }\n"
                        + "    public void guard() {\n"
                        + "        try { convert(null); } catch (Failure e) { }\n"
                        + "    }\n"
                        + "}\n")
                .analyze();

        PromiseMetricResult coupled = rows.get("p.Coupled");
        // Argument (parameter), Returned (return type) and Failure
        // (throws plus catch). java.util.List and java.lang.String do not count.
        assertEquals(3, coupled.getCe());
        assertEquals(0, coupled.getCa());
        assertEquals(3, coupled.getCbo());
    }

    @Test
    void lackOfCohesionUsesMethodPairsSharingFields() throws Exception {
        Map<String, PromiseMetricResult> rows = PromiseFixtureProject.in(workspace)
                .write("p/Cohesion.java",
                        "package p;\n"
                        + "public class Cohesion {\n"
                        + "    private int a;\n"
                        + "    private int b;\n"
                        + "    public void useA() { a++; }\n"
                        + "    public void useAgain() { a--; }\n"
                        + "    public void useB() { b++; }\n"
                        + "}\n")
                .analyze();

        // Methods: <init>{}, useA{a}, useAgain{a}, useB{b} -> 6 pairs.
        // Sharing: (useA, useAgain). Disjoint: the other 5. LCOM = 5 - 1 = 4.
        assertEquals(4, rows.get("p.Cohesion").getLcom());
    }

    @Test
    void lcom3FollowsSameClassCallsAndReportsTwoWhenUndefined() throws Exception {
        Map<String, PromiseMetricResult> rows = PromiseFixtureProject.in(workspace)
                .write("p/Delegating.java",
                        "package p;\n"
                        + "public class Delegating {\n"
                        + "    private int f;\n"
                        + "    public void direct() { f = 1; }\n"
                        + "    public void indirect() { direct(); }\n"
                        + "    public void unrelated() { }\n"
                        + "}\n")
                .write("p/NoFields.java",
                        "package p;\n"
                        + "public class NoFields {\n"
                        + "    public void one() { }\n"
                        + "    public void two() { }\n"
                        + "}\n")
                .analyze();

        // a = 1, m = 4 (<init>, direct, indirect, unrelated).
        // direct() touches f, and indirect() reaches f through direct(), so
        // mu(f) = 2. LCOM3 = (2/1 - 4) / (1 - 4) = 2/3.
        // Without following same-class calls this would be 1.0.
        assertEquals(2.0 / 3.0, rows.get("p.Delegating").getLcom3(), 1e-9);

        // No attribute at all: the formula is undefined and CKJM reports 2.
        assertEquals(2.0, rows.get("p.NoFields").getLcom3(), 1e-9);
    }

    @Test
    void cohesionAmongMethodsTreatsStaticMethodsWithoutImplicitThis() throws Exception {
        Map<String, PromiseMetricResult> rows = PromiseFixtureProject.in(workspace)
                .write("p/Params.java",
                        "package p;\n"
                        + "public class Params {\n"
                        + "    public void instanceMethod(int x) { }\n"
                        + "    public static void staticMethod(int x) { }\n"
                        + "}\n")
                .analyze();

        // <init>        -> {this}       = 1
        // instanceMethod-> {int, this}  = 2
        // staticMethod  -> {int}        = 1
        // global = {this, int} = 2, methods = 3 -> 4 / (2 * 3) = 2/3.
        assertEquals(2.0 / 3.0, rows.get("p.Params").getCam(), 1e-9);
    }

    @Test
    void functionalAbstractionIgnoresConstructorsAndObjectMethods() throws Exception {
        Map<String, PromiseMetricResult> rows = PromiseFixtureProject.in(workspace)
                .write("p/Root.java",
                        "package p;\n"
                        + "public class Root {\n"
                        + "    public void one() { }\n"
                        + "    public void two() { }\n"
                        + "}\n")
                .write("p/Leaf.java",
                        "package p;\n"
                        + "public class Leaf extends Root {\n"
                        + "    public void three() { }\n"
                        + "}\n")
                .analyze();

        // Root sits directly under Object, whose methods are ignored.
        assertEquals(0.0, rows.get("p.Root").getMfa(), 1e-9);
        // Leaf declares one method and inherits two.
        assertEquals(2.0 / 3.0, rows.get("p.Leaf").getMfa(), 1e-9);
    }

    @Test
    void dataAccessMetricUsesDeclaredFieldVisibility() throws Exception {
        Map<String, PromiseMetricResult> rows = PromiseFixtureProject.in(workspace)
                .write("p/Access.java",
                        "package p;\n"
                        + "public class Access {\n"
                        + "    private int hidden;\n"
                        + "    protected int guarded;\n"
                        + "    public int open;\n"
                        + "    int packagePrivate;\n"
                        + "}\n")
                .write("p/NoField.java", "package p;\npublic class NoField { }\n")
                .analyze();

        assertEquals(0.5, rows.get("p.Access").getDam(), 1e-9);
        // No declared field: deterministic zero rather than a division by zero.
        assertEquals(0.0, rows.get("p.NoField").getDam(), 1e-9);
    }

    @Test
    void inheritanceCouplingDetectsAttributeDependency() throws Exception {
        Map<String, PromiseMetricResult> rows = PromiseFixtureProject.in(workspace)
                .write("p/StateParent.java",
                        "package p;\n"
                        + "public class StateParent {\n"
                        + "    protected int state;\n"
                        + "    public int read() { return state; }\n"
                        + "}\n")
                .write("p/StateChild.java",
                        "package p;\n"
                        + "public class StateChild extends StateParent {\n"
                        + "    public void update() { state = 5; }\n"
                        + "}\n")
                .analyze();

        // Case 1: the inherited read() uses a field the new update() writes.
        assertEquals(1, rows.get("p.StateChild").getIc());
        assertEquals(1, rows.get("p.StateChild").getCbm());
        // The parent itself inherits nothing, so it is uncoupled.
        assertEquals(0, rows.get("p.StateParent").getIc());
    }

    @Test
    void inheritanceCouplingDetectsCallToRedefinedMethod() throws Exception {
        Map<String, PromiseMetricResult> rows = PromiseFixtureProject.in(workspace)
                .write("p/TemplateParent.java",
                        "package p;\n"
                        + "public class TemplateParent {\n"
                        + "    public void template() { step(); }\n"
                        + "    public void step() { }\n"
                        + "}\n")
                .write("p/TemplateChild.java",
                        "package p;\n"
                        + "public class TemplateChild extends TemplateParent {\n"
                        + "    public void step() { }\n"
                        + "}\n")
                .analyze();

        // Case 2: the inherited template() calls step(), which the child redefines.
        assertEquals(1, rows.get("p.TemplateChild").getIc());
        assertEquals(1, rows.get("p.TemplateChild").getCbm());
    }

    @Test
    void inheritanceCouplingDetectsCallIntoParameterisedInheritedMethod() throws Exception {
        Map<String, PromiseMetricResult> rows = PromiseFixtureProject.in(workspace)
                .write("p/HelperParent.java",
                        "package p;\n"
                        + "public class HelperParent {\n"
                        + "    public void helper(int value) { }\n"
                        + "    public void template() { }\n"
                        + "    public void noArguments() { }\n"
                        + "}\n")
                .write("p/HelperChild.java",
                        "package p;\n"
                        + "public class HelperChild extends HelperParent {\n"
                        + "    public void template() { helper(7); }\n"
                        + "    public void plain() { noArguments(); }\n"
                        + "}\n")
                .analyze();

        // CKJM's case 3 only fires when the CALLING method genuinely redefines
        // (overrides) a parent method - plain() calls the parameterless
        // inherited noArguments(), but plain() itself is a brand new method
        // with no parent counterpart, so it is not a case-3 source. template()
        // does override HelperParent.template(), and calling the parameterised
        // inherited helper(int) from inside it is exactly case 3.
        assertEquals(1, rows.get("p.HelperChild").getIc());
        assertEquals(1, rows.get("p.HelperChild").getCbm());
    }

    @Test
    void couplingBetweenMethodsCountsDistinctPairsAcrossCases() throws Exception {
        Map<String, PromiseMetricResult> rows = PromiseFixtureProject.in(workspace)
                .write("p/MultiParent.java",
                        "package p;\n"
                        + "public class MultiParent {\n"
                        + "    protected int state;\n"
                        + "    public int read() { return state; }\n"
                        + "    public void template() { step(); }\n"
                        + "    public void step() { }\n"
                        + "}\n")
                .write("p/MultiChild.java",
                        "package p;\n"
                        + "public class MultiChild extends MultiParent {\n"
                        + "    public void step() { state = 1; }\n"
                        + "    public void alsoWrites() { state = 2; }\n"
                        + "}\n")
                .analyze();

        PromiseMetricResult child = rows.get("p.MultiChild");
        // Inherited read() and template() both couple, but to a single parent.
        assertEquals(1, child.getIc());
        // Distinct pairs: read/step, read/alsoWrites, template/step.
        assertEquals(3, child.getCbm());
    }

    @Test
    void cyclomaticComplexityFollowsBytecodeBranchRules() throws Exception {
        Map<String, PromiseMetricResult> rows = PromiseFixtureProject.in(workspace)
                .write("p/Straight.java",
                        "package p;\n"
                        + "public class Straight {\n"
                        + "    public int plain() { int a = 1; return a; }\n"
                        + "}\n")
                .write("p/Branching.java",
                        "package p;\n"
                        + "public class Branching {\n"
                        + "    public int branch(int x) {\n"
                        + "        if (x > 0) { return 1; } else { return 2; }\n"
                        + "    }\n"
                        + "}\n")
                .write("p/Looping.java",
                        "package p;\n"
                        + "public class Looping {\n"
                        + "    public int loop(int x) {\n"
                        + "        while (x > 0) { x--; }\n"
                        + "        return x;\n"
                        + "    }\n"
                        + "}\n")
                .write("p/DenseSwitching.java",
                        "package p;\n"
                        + "public class DenseSwitching {\n"
                        + "    public int choose(int x) {\n"
                        + "        switch (x) {\n"
                        + "            case 1: return 1;\n"
                        + "            case 2: return 2;\n"
                        + "            case 3: return 3;\n"
                        + "            default: return 0;\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n")
                .write("p/SparseSwitching.java",
                        "package p;\n"
                        + "public class SparseSwitching {\n"
                        + "    public int choose(int x) {\n"
                        + "        switch (x) {\n"
                        + "            case 10: return 1;\n"
                        + "            case 5000: return 2;\n"
                        + "            case -100: return 3;\n"
                        + "            default: return 0;\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n")
                .analyze();

        // A method with no branch keeps the base complexity of 1.
        assertEquals(1, rows.get("p.Straight").getMaxCc());
        // if/else emits one conditional branch plus a goto, and goto never counts.
        assertEquals(2, rows.get("p.Branching").getMaxCc());
        // The loop back-edge is a goto; only the condition counts.
        assertEquals(2, rows.get("p.Looping").getMaxCc());
        // Dense, sequential case values (1,2,3) compile to a bytecode
        // "tableswitch". CKJM's own visitBranchInstruction only special-cases
        // "lookupswitch" (a string match) and never handles tableswitch, so it
        // falls through to the generic +1 rule regardless of case count. We
        // reproduce that bug rather than the textbook per-case count, because
        // this is the metric CKJM actually published for PROMISE.
        assertEquals(2, rows.get("p.DenseSwitching").getMaxCc());
        // Sparse case values compile to "lookupswitch", which CKJM expands by
        // counting '[' characters in the instruction's rendered text. BCEL
        // renders one bracketed opcode for the switch itself plus one per case,
        // so a 3-case lookupswitch contributes 4: base 1 + 4 = 5. Validated as
        // marginally closer to the published data than a plain case count.
        assertEquals(5, rows.get("p.SparseSwitching").getMaxCc());
    }

    @Test
    void averageComplexityCoversEveryCompiledMethod() throws Exception {
        Map<String, PromiseMetricResult> rows = PromiseFixtureProject.in(workspace)
                .write("p/Mixed.java",
                        "package p;\n"
                        + "public class Mixed {\n"
                        + "    public void plain() { }\n"
                        + "    public int branch(int x) { return x > 0 ? 1 : 2; }\n"
                        + "}\n")
                .analyze();

        PromiseMetricResult mixed = rows.get("p.Mixed");
        // Constructors carry no CC entry in CKJM, so <init> contributes 0 while
        // still counting toward WMC: (0 + 1 + 2) / 3 = 1.0.
        assertEquals(3, mixed.getWmc());
        assertEquals(2, mixed.getMaxCc());
        assertEquals(1.0, mixed.getAvgCc(), 1e-9);
    }

    @Test
    void onlyTopLevelTypesBecomeRows() throws Exception {
        Map<String, PromiseMetricResult> rows = PromiseFixtureProject.in(workspace)
                .write("p/Outer.java",
                        "package p;\n"
                        + "public class Outer {\n"
                        + "    public static class Nested { }\n"
                        + "    public void use() { Runnable r = new Runnable() {\n"
                        + "        public void run() { }\n"
                        + "    }; }\n"
                        + "}\n")
                .analyze();

        assertNotNull(rows.get("p.Outer"));
        // The published PROMISE datasets contain no nested or anonymous rows.
        assertNull(rows.get("p.Outer$Nested"));
        assertNull(rows.get("p.Outer.Nested"));
        assertNull(rows.get("p.Outer$1"));
    }

    @Test
    void hierarchyWalkResolvesAncestorsOutsideTheProject() throws Exception {
        // java.io.FilterOutputStream extends java.io.OutputStream extends
        // Object, and (verified via javap) FilterOutputStream's own inherited
        // write(byte[],int,int) calls this.write(int) in a loop. Neither class
        // is compiled as part of this project, so this exercises CKJM's
        // classpath-backed hierarchy walk (JavaClass.getSuperClasses()) that
        // our ExternalAncestorResolver reproduces.
        Map<String, PromiseMetricResult> rows = PromiseFixtureProject.in(workspace)
                .write("p/MyStream.java",
                        "package p;\n"
                        + "import java.io.FilterOutputStream;\n"
                        + "import java.io.OutputStream;\n"
                        + "public class MyStream extends FilterOutputStream {\n"
                        + "    public MyStream(OutputStream out) { super(out); }\n"
                        + "    public void write(int b) { }\n"
                        + "}\n")
                .analyze();

        PromiseMetricResult myStream = rows.get("p.MyStream");
        // MyStream -> FilterOutputStream -> OutputStream -> Object: 3 edges.
        assertEquals(3, myStream.getDit());
        // FilterOutputStream itself inherits nothing further of interest, but
        // MyStream inherits several of its non-overridden methods (write(byte[]),
        // write(byte[],int,int), flush(), close()), on top of the one it declares.
        assertTrue(myStream.getMfa() > 0,
                "methods inherited through the JDK ancestor should count towards MFA");
        // Case 2: the inherited write(byte[],int,int) calls the redefined
        // write(int), coupling MyStream to its JDK ancestor.
        assertEquals(1, myStream.getIc());
        assertTrue(myStream.getCbm() >= 1);
    }
}
