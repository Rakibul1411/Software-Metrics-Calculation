package org.metrics.defectlab.analysis.promise.bytecode;

import org.apache.bcel.generic.BranchInstruction;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;

/**
 * Method-level McCabe complexity measured on bytecode, reproducing
 * {@code gr.spinellis.ckjm.MethodVisitor#visitBranchInstruction} from
 * CKJM-extended &mdash; the tool that generated the published PROMISE
 * Max_CC/Avg_CC values.
 *
 * <p>CKJM classifies each branch by <b>string-matching its rendered form</b>:
 *
 * <pre>
 * String instruction = obj.toString();
 * if (instruction.contains("lookupswitch"))  cc += count of '[' in the text;
 * else if (!instruction.contains("goto"))    cc += 1;
 * </pre>
 *
 * <p>That rule is far narrower than it looks, because BCEL renders a branch
 * together with <em>the instruction it jumps to</em>:
 * {@code "ifeq[153](3) -> goto"}. So a conditional branch whose jump target
 * happens to be a {@code goto} contains the substring "goto" and is
 * <b>silently not counted</b>. Compilers emit exactly that shape constantly
 * (if/else arms, loop bodies, short-circuit operands), which is why real
 * PROMISE Avg_CC values sit near 1.4 rather than the ~2.3 a textbook
 * branch count produces.
 *
 * <p>We therefore reproduce the string test literally rather than the
 * "correct" structural rule: matching the published dataset is the goal, and
 * a structurally-correct counter provably diverges from it (validated against
 * ant/log4j/synapse/velocity ground truth).
 *
 * <p>A method without code (abstract or native) keeps the base complexity of 1.
 */
final class CyclomaticComplexityReader {

    private CyclomaticComplexityReader() {
    }

    static int complexityOf(
            InstructionList instructions, boolean declaresThrows, boolean initializer) {
        // Constructors and static initializers carry no CC entry in CKJM, so
        // they contribute 0 to both Max_CC and Avg_CC while still counting
        // toward WMC (which remains Avg_CC's denominator). This is directly
        // visible in the published data: a class whose only compiled method is
        // a constructor has max_cc = 0 and avg_cc = 0, and e.g. PathTokenizer
        // (wmc 3, one constructor plus two methods of CC 2 and 1) is published
        // as max_cc = 2, avg_cc = 3/3 = 1.
        if (initializer) {
            return 0;
        }
        // CKJM keys its per-method CC map by the method's rendered signature.
        // addMethod() normalises that key with signature.split("\n")[0] (to drop
        // the "\n\t\tthrows X" tail MethodGen.toString() appends) but getCC()
        // looks up with the UN-normalised key. So for any method that declares
        // checked exceptions the lookup always misses and returns 0, and each
        // branch merely re-stores 0 + 1. The complexity can never accumulate:
        // it is pinned at 1. This is why the published PROMISE data contains
        // whole classes at max_cc = 1 despite heavy branching.
        if (declaresThrows) {
            return 1;
        }
        int complexity = 1;
        if (instructions == null) {
            return complexity;
        }
        for (InstructionHandle handle : instructions) {
            Instruction instruction = handle.getInstruction();
            if (!(instruction instanceof BranchInstruction)) {
                continue;
            }
            // Rendered exactly as CKJM sees it: BCEL's verbose form, which
            // includes the target instruction's name after " -> ".
            String rendered = instruction.toString();
            if (rendered.contains("lookupswitch")) {
                complexity += countBrackets(rendered);
            } else if (!rendered.contains("goto")) {
                complexity++;
            }
        }
        return complexity;
    }

    /**
     * CKJM counts '[' characters in a lookupswitch's rendered text as a proxy
     * for its case count, since BCEL prints one bracketed opcode per entry.
     */
    private static int countBrackets(String rendered) {
        int count = 0;
        for (int i = 0; i < rendered.length(); i++) {
            if (rendered.charAt(i) == '[') {
                count++;
            }
        }
        return count;
    }
}
