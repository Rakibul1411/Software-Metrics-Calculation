package org.metrics.defectlab.analysis.promise.calculator;

import org.metrics.defectlab.analysis.promise.bytecode.BytecodeClassModel;
import org.metrics.defectlab.analysis.promise.bytecode.BytecodeMethodModel;

/**
 * Metrics read straight off one compiled class: WMC, NPM, LOC, AMC, Max_CC and
 * Avg_CC. They share the same compiled method universe, which is every method in
 * the class file including constructors and the static initializer.
 */
public final class SizeAndComplexityCalculators {

    private SizeAndComplexityCalculators() {
    }

    /** WMC: one unit of weight per compiled method. */
    public static int wmc(BytecodeClassModel type) {
        return type.getMethods().size();
    }

    /** NPM: compiled methods declared public. */
    public static int npm(BytecodeClassModel type) {
        return (int) type.getMethods().stream()
                .filter(BytecodeMethodModel::isPublicMethod)
                .count();
    }

    /**
     * LOC measured on binary code, not source lines:
     * {@code fields + methods + all bytecode instructions}. A method without a
     * body contributes itself but no instructions.
     */
    public static int loc(BytecodeClassModel type) {
        return type.getFields().size()
                + type.getMethods().size()
                + type.totalInstructionCount();
    }

    /**
     * AMC: average method size in bytecode instructions. Equivalent to
     * {@code (LOC - fields - WMC) / WMC}.
     */
    public static double amc(BytecodeClassModel type) {
        int methodCount = type.getMethods().size();
        return methodCount == 0
                ? 0.0
                : (double) type.totalInstructionCount() / methodCount;
    }

    /** Max_CC over every compiled method, constructors included. */
    public static int maxCc(BytecodeClassModel type) {
        return type.getMethods().stream()
                .mapToInt(BytecodeMethodModel::getCyclomaticComplexity)
                .max()
                .orElse(0);
    }

    /** Avg_CC over the same method collection used by WMC and Max_CC. */
    public static double avgCc(BytecodeClassModel type) {
        return type.getMethods().stream()
                .mapToInt(BytecodeMethodModel::getCyclomaticComplexity)
                .average()
                .orElse(0.0);
    }
}
