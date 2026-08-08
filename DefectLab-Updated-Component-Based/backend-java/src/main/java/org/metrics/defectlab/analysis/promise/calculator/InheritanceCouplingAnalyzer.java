package org.metrics.defectlab.analysis.promise.calculator;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.metrics.defectlab.analysis.promise.bytecode.BytecodeClassModel;
import org.metrics.defectlab.analysis.promise.bytecode.BytecodeMethodModel;
import org.metrics.defectlab.analysis.promise.bytecode.BytecodeProjectModel;
import org.metrics.defectlab.analysis.promise.bytecode.FieldRef;
import org.metrics.defectlab.analysis.promise.bytecode.MethodRef;

/**
 * Shared analysis behind IC and CBM, reproducing
 * {@code gr.spinellis.ckjm.IcAndCbmClassVisitor} from CKJM-extended (the tool
 * that generated the published PROMISE values) rather than the Tang et al.
 * paper's prose reading of the three conditions, since the actual
 * implementation is narrower than the prose in two places that materially
 * change the counts:
 *
 * <ol>
 *   <li><b>Case 1</b> &mdash; an inherited method <em>reads</em> (not writes)
 *       an attribute that some method of this class writes. CKJM's
 *       {@code isGetInstruction} check only matches {@code get}* bytecodes, so
 *       a parent method that merely writes the field does not qualify.</li>
 *   <li><b>Case 2</b> &mdash; an inherited method calls a method this class
 *       redefines.</li>
 *   <li><b>Case 3</b> &mdash; a method this class <em>redefines</em> (i.e.
 *       overrides a same-name/same-arguments parent method) calls an
 *       inherited method that takes parameters. CKJM only investigates
 *       {@code hasBeenDefinedInParentToo} methods as case-3 sources
 *       ({@code IcAndCbmClassVisitor#countCase3}); a method the child adds
 *       with no parent counterpart at all is <em>not</em> a case-3 source,
 *       even though the "new or redefined" wording in the paper would suggest
 *       it should be.</li>
 * </ol>
 *
 * <p>IC then counts the distinct parent classes involved and CBM the distinct
 * method-to-method pairs, so the two are always consistent.
 *
 * <p>The conditions need the bodies of the parent methods. When an ancestor is
 * outside the analysed release and cannot be resolved (see
 * {@code ExternalAncestorResolver}) its body cannot be read, so cases 1 and 2
 * cannot fire for it; case 3 still can, because it only needs the child's body.
 */
public final class InheritanceCouplingAnalyzer {

    private final Set<String> coupledParents = new LinkedHashSet<>();
    private final Set<String> couplings = new LinkedHashSet<>();

    private InheritanceCouplingAnalyzer() {
    }

    public static InheritanceCouplingAnalyzer analyze(
            BytecodeClassModel type, BytecodeProjectModel project) {
        InheritanceCouplingAnalyzer analyzer = new InheritanceCouplingAnalyzer();
        analyzer.run(type, project);
        return analyzer;
    }

    /** IC: distinct parent classes this class is coupled to. */
    public int inheritanceCoupling() {
        return coupledParents.size();
    }

    /** CBM: distinct inherited-to-new/redefined method couplings. */
    public int couplingBetweenMethods() {
        return couplings.size();
    }

    private void run(BytecodeClassModel type, BytecodeProjectModel project) {
        Map<String, BytecodeMethodModel> childBySubsignature = new LinkedHashMap<>();
        for (BytecodeMethodModel method : type.getMethods()) {
            if (isCandidate(method)) {
                childBySubsignature.put(method.getRef().subsignature(), method);
            }
        }

        // Nearest ancestor wins, so a method redefined halfway up the chain is
        // inherited from that class rather than from its own parent.
        Map<String, BytecodeMethodModel> inheritedBySubsignature = new LinkedHashMap<>();
        Set<String> redefined = new LinkedHashSet<>();
        List<String> chain = project.getInheritanceGraph().superclassChain(type.getFqn());

        for (String ancestorName : chain) {
            BytecodeClassModel ancestor = project.getClass(ancestorName);
            if (ancestor == null) {
                continue;
            }
            for (BytecodeMethodModel method : ancestor.getMethods()) {
                if (!isCandidate(method)) {
                    continue;
                }
                String subsignature = method.getRef().subsignature();
                if (childBySubsignature.containsKey(subsignature)) {
                    redefined.add(subsignature);
                } else {
                    inheritedBySubsignature.putIfAbsent(subsignature, method);
                }
            }
        }

        Set<BytecodeMethodModel> newOrRedefined = new LinkedHashSet<>(
                childBySubsignature.values());
        Set<BytecodeMethodModel> redefinedOnly = new LinkedHashSet<>();
        for (String subsignature : redefined) {
            BytecodeMethodModel method = childBySubsignature.get(subsignature);
            if (method != null) {
                redefinedOnly.add(method);
            }
        }

        applyAttributeDependency(inheritedBySubsignature.values(), newOrRedefined);
        applyRedefinedCall(inheritedBySubsignature.values(), childBySubsignature, redefined);
        applyInheritedCall(inheritedBySubsignature, redefinedOnly);
    }

    /** Case 1: an inherited method reads a field a child method writes. */
    private void applyAttributeDependency(
            Iterable<BytecodeMethodModel> inherited,
            Set<BytecodeMethodModel> newOrRedefined) {

        for (BytecodeMethodModel parentMethod : inherited) {
            Set<FieldRef> touched = parentMethod.getFieldReads();
            if (touched.isEmpty()) {
                continue;
            }
            for (BytecodeMethodModel childMethod : newOrRedefined) {
                for (FieldRef written : childMethod.getFieldWrites()) {
                    if (touched.contains(written)) {
                        record(parentMethod, childMethod);
                        break;
                    }
                }
            }
        }
    }

    /** Case 2: an inherited method calls a method this class redefines. */
    private void applyRedefinedCall(
            Iterable<BytecodeMethodModel> inherited,
            Map<String, BytecodeMethodModel> childBySubsignature,
            Set<String> redefined) {

        for (BytecodeMethodModel parentMethod : inherited) {
            for (MethodRef invoked : parentMethod.getInvokedMethods()) {
                String subsignature = invoked.subsignature();
                if (!redefined.contains(subsignature)) {
                    continue;
                }
                BytecodeMethodModel childMethod = childBySubsignature.get(subsignature);
                if (childMethod != null) {
                    record(parentMethod, childMethod);
                }
            }
        }
    }

    /**
     * Case 3: a method this class <em>redefines</em> (overrides) calls an
     * inherited method that takes parameters. A method the child adds with no
     * parent counterpart is not a source here (see the class javadoc).
     */
    private void applyInheritedCall(
            Map<String, BytecodeMethodModel> inheritedBySubsignature,
            Set<BytecodeMethodModel> redefinedOnly) {

        for (BytecodeMethodModel childMethod : redefinedOnly) {
            for (MethodRef invoked : childMethod.getInvokedMethods()) {
                BytecodeMethodModel parentMethod =
                        inheritedBySubsignature.get(invoked.subsignature());
                if (parentMethod != null && !parentMethod.getArgumentTypes().isEmpty()) {
                    record(parentMethod, childMethod);
                }
            }
        }
    }

    private void record(BytecodeMethodModel parentMethod, BytecodeMethodModel childMethod) {
        coupledParents.add(parentMethod.getOwner());
        couplings.add(parentMethod.getRef() + " <-> " + childMethod.getRef());
    }

    private static boolean isCandidate(BytecodeMethodModel method) {
        return !method.isConstructor() && !method.isStaticInitializer();
    }
}
