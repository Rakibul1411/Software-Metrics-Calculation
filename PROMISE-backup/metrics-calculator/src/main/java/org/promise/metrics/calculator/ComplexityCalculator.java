package org.promise.metrics.calculator;

import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculator for Cyclomatic Complexity (CC) and related metrics.
 * CC = 1 + (# if + for + while + do-while + case + catch + && + || + ?:)
 */
public class ComplexityCalculator {

    /**
     * Calculate complexity metrics for all methods in a compilation unit.
     *
     * @param compilationUnit The parsed Java file
     * @return ComplexityResult containing WMC, MAX_CC, AVG_CC, and method count
     */
    public static ComplexityResult calculateComplexity(CompilationUnit compilationUnit) {
        ComplexityVisitor visitor = new ComplexityVisitor();
        compilationUnit.accept(visitor);

        int wmc = 0;
        int maxCC = 0;
        int methodCount = visitor.methodComplexities.size();

        for (int cc : visitor.methodComplexities) {
            wmc += cc;
            if (cc > maxCC) {
                maxCC = cc;
            }
        }

        double avgCC = methodCount > 0 ? (double) wmc / methodCount : 0.0;

        return new ComplexityResult(wmc, maxCC, avgCC, methodCount);
    }

    /**
     * AST Visitor to calculate cyclomatic complexity for each method.
     */
    private static class ComplexityVisitor extends ASTVisitor {
        List<Integer> methodComplexities = new ArrayList<>();

        @Override
        public boolean visit(MethodDeclaration node) {
            int complexity = calculateMethodComplexity(node);
            methodComplexities.add(complexity);
            return false; // Don't visit nested types
        }

        /**
         * Calculate cyclomatic complexity for a single method.
         * CC = 1 + count of decision points
         */
        private int calculateMethodComplexity(MethodDeclaration method) {
            MethodComplexityVisitor complexityVisitor = new MethodComplexityVisitor();
            if (method.getBody() != null) {
                method.getBody().accept(complexityVisitor);
            }
            return 1 + complexityVisitor.decisionPoints;
        }
    }

    /**
     * Visitor to count decision points within a method.
     */
    private static class MethodComplexityVisitor extends ASTVisitor {
        int decisionPoints = 0;

        @Override
        public boolean visit(IfStatement node) {
            decisionPoints++;
            return true;
        }

        @Override
        public boolean visit(ForStatement node) {
            decisionPoints++;
            return true;
        }

        @Override
        public boolean visit(EnhancedForStatement node) {
            decisionPoints++;
            return true;
        }

        @Override
        public boolean visit(WhileStatement node) {
            decisionPoints++;
            return true;
        }

        @Override
        public boolean visit(DoStatement node) {
            decisionPoints++;
            return true;
        }

        @Override
        public boolean visit(SwitchCase node) {
            // Count each case (including default)
            decisionPoints++;
            return true;
        }

        @Override
        public boolean visit(CatchClause node) {
            decisionPoints++;
            return true;
        }

        @Override
        public boolean visit(ConditionalExpression node) {
            // Ternary operator (?:)
            decisionPoints++;
            return true;
        }

        @Override
        public boolean visit(InfixExpression node) {
            // Count && and || operators
            InfixExpression.Operator operator = node.getOperator();
            if (operator == InfixExpression.Operator.CONDITIONAL_AND ||
                    operator == InfixExpression.Operator.CONDITIONAL_OR) {
                decisionPoints++;

                // Handle extended operands (e.g., a && b && c)
                if (node.hasExtendedOperands()) {
                    decisionPoints += node.extendedOperands().size();
                }
            }
            return true;
        }
    }

    /**
     * Result object containing complexity metrics.
     */
    public static class ComplexityResult {
        private final int wmc;
        private final int maxCC;
        private final double avgCC;
        private final int methodCount;

        public ComplexityResult(int wmc, int maxCC, double avgCC, int methodCount) {
            this.wmc = wmc;
            this.maxCC = maxCC;
            this.avgCC = avgCC;
            this.methodCount = methodCount;
        }

        public int getWmc() {
            return wmc;
        }

        public int getMaxCC() {
            return maxCC;
        }

        public double getAvgCC() {
            return avgCC;
        }

        public int getMethodCount() {
            return methodCount;
        }
    }
}
