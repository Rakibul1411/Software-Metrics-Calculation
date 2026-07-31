package org.metrics.defectlab.analysis.promise.calculator;

import org.eclipse.jdt.core.dom.ASTNode;
import org.metrics.defectlab.analysis.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class LocPromiseCalculator {
    private LocPromiseCalculator() {
    }

    public static int calculate(TypeInfo type) {
        return countSourceLines(type, type.node);
    }

    public static int countSourceLines(TypeInfo type, ASTNode node) {
        int start = node.getStartPosition();
        int end = start + node.getLength();
        int count = 0;
        boolean hasCode = false;
        for (int index = start; index < end && index < type.commentFreeSource.length; index++) {
            char current = type.commentFreeSource[index];
            if (current == '\n' || current == '\r') {
                if (hasCode) {
                    count++;
                    hasCode = false;
                }
            } else if (!Character.isWhitespace(current)) {
                hasCode = true;
            }
        }
        if (hasCode) {
            count++;
        }
        return count;
    }
}
