package org.metrics.promise.calculator;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.WhileStatement;

/**
 * Calculator for Cyclomatic Complexity metrics:
 * - max_cc: Maximum Cyclomatic Complexity among all methods in a class
 * - avg_cc: Average Cyclomatic Complexity across all methods in a class
 *
 * Cyclomatic Complexity (CC) for a method is calculated as:
 *   CC = 1 + number of decision points
 *
 * Decision points counted:
 *   if, while, do-while, for, enhanced-for, case (in switch),
 *   catch, conditional expression (?:), && (conditional AND), || (conditional OR)
 */
public class CyclomaticComplexityCalculator {

    /**
     * Result holder for cyclomatic complexity calculations.
     */
    public static class CCResult {
        private final int maxCC;
        private final double avgCC;

        public CCResult(int maxCC, double avgCC) {
            this.maxCC = maxCC;
            this.avgCC = avgCC;
        }

        public int getMaxCC() {
            return maxCC;
        }

        public double getAvgCC() {
            return avgCC;
        }
    }

    /**
     * Calculate max_cc and avg_cc for a specific type declaration.
     * Only considers methods directly declared in the type (not in nested/inner classes).
     * Includes implicit default constructor (CC=1) if no explicit constructor exists.
     *
     * @param typeDeclaration The type to analyze
     * @return CCResult containing maxCC and avgCC
     */
    public static CCResult calculateCCForType(AbstractTypeDeclaration typeDeclaration) {
        // Collect all top-level methods
        List<MethodDeclaration> methods = new ArrayList<>();
        MethodCollectorVisitor collector = new MethodCollectorVisitor(methods);
        typeDeclaration.accept(collector);

        // Check for explicit constructor
        boolean hasExplicitConstructor = false;
        for (MethodDeclaration method : methods) {
            if (method.isConstructor()) {
                hasExplicitConstructor = true;
                break;
            }
        }

        boolean isInterface = WMCCalculator.isInterfaceType(typeDeclaration);
        int totalMethods = methods.size();

        // Add implicit default constructor if none exists (ckjm counts it)
        if (!hasExplicitConstructor && !isInterface) {
            totalMethods++;
        }

        if (totalMethods == 0) {
            // No methods: max_cc = 0, avg_cc = 0
            return new CCResult(0, 0.0);
        }

        int maxCC = 0;
        int totalCC = 0;

        for (MethodDeclaration method : methods) {
            int cc = calculateMethodCC(method);
            totalCC += cc;
            if (cc > maxCC) {
                maxCC = cc;
            }
        }

        // Add default constructor CC = 0 (constructors have base CC 0)
        if (!hasExplicitConstructor && !isInterface) {
            totalCC += 0;
            if (0 > maxCC) {
                maxCC = 0;
            }
        }

        double avgCC = (double) totalCC / totalMethods;
        return new CCResult(maxCC, avgCC);
    }

    /**
     * Calculate Cyclomatic Complexity for a single method.
     * CC = 1 + number of decision points in the method body.
     *
     * @param method The method declaration
     * @return Cyclomatic complexity value
     */
    public static int calculateMethodCC(MethodDeclaration method) {
        if (method.getBody() == null) {
            // Abstract or interface method with no body
            return 0;
        }

        CCMethodVisitor visitor = new CCMethodVisitor();
        method.getBody().accept(visitor);
        
        if (method.isConstructor()) {
            // Constructors have base CC of 0 in CKJM
            return visitor.decisionPoints;
        } else {
            // Regular methods have base CC of 1
            return 1 + visitor.decisionPoints;
        }
    }

    /**
     * Visitor that collects only top-level methods (not methods in nested/inner classes).
     */
    private static class MethodCollectorVisitor extends ASTVisitor {
        private final List<MethodDeclaration> methods;
        private int nestingLevel = 0;

        MethodCollectorVisitor(List<MethodDeclaration> methods) {
            this.methods = methods;
        }

        @Override
        public boolean visit(TypeDeclaration node) {
            nestingLevel++;
            return true;
        }

        @Override
        public void endVisit(TypeDeclaration node) {
            nestingLevel--;
        }

        @Override
        public boolean visit(EnumDeclaration node) {
            nestingLevel++;
            return true;
        }

        @Override
        public void endVisit(EnumDeclaration node) {
            nestingLevel--;
        }

        @Override
        public boolean visit(AnonymousClassDeclaration node) {
            nestingLevel++;
            return true;
        }

        @Override
        public void endVisit(AnonymousClassDeclaration node) {
            nestingLevel--;
        }

        @Override
        public boolean visit(MethodDeclaration node) {
            if (nestingLevel == 1) {
                methods.add(node);
            }
            return false; // Don't descend into method body for collection
        }
    }

    /**
     * Visitor that counts decision points within a method body.
     * Does NOT descend into anonymous class declarations (their methods are separate).
     */
    private static class CCMethodVisitor extends ASTVisitor {
        int decisionPoints = 0;

        @Override
        public boolean visit(IfStatement node) {
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
        public boolean visit(SwitchCase node) {
            // Each 'case' label is a decision point, but not the 'default' label
            if (!node.isDefault()) {
                decisionPoints++;
            }
            return true;
        }

        // Note: CatchClause is NOT counted as a decision point.
        // In bytecode (ckjm), exception handling uses exception tables, not branch instructions.
        // Removing catch from CC counting aligns with ckjm-extended behavior.

        @Override
        public boolean visit(ConditionalExpression node) {
            // Ternary operator (? :)
            decisionPoints++;
            return true;
        }

        @Override
        public boolean visit(InfixExpression node) {
            // Count && and || as decision points
            InfixExpression.Operator op = node.getOperator();
            if (op == InfixExpression.Operator.CONDITIONAL_AND
                    || op == InfixExpression.Operator.CONDITIONAL_OR) {
                decisionPoints++;

                // Also count extended operands for chained && or ||
                // e.g., a && b && c has 2 decision points (one infix + one extended operand)
                if (node.hasExtendedOperands()) {
                    decisionPoints += node.extendedOperands().size();
                }
            }
            return true;
        }

        @Override
        public boolean visit(AnonymousClassDeclaration node) {
            // Don't count decision points inside anonymous classes
            return false;
        }

        @Override
        public boolean visit(TypeDeclaration node) {
            // Don't count decision points inside local/inner class definitions
            return false;
        }

        @Override
        public boolean visit(EnumDeclaration node) {
            // Don't count decision points inside local enum definitions
            return false;
        }
    }
}
