package org.metrics.promise.calculator;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.WhileStatement;

public final class CyclomaticComplexityPromiseCalculator {
    private CyclomaticComplexityPromiseCalculator() {
    }

    public static int calculate(MethodDeclaration method) {
        // PROMISE stores constructor-only classes with CC 0. For ordinary
        // methods, including abstract/interface methods, McCabe complexity has
        // a base path of 1 ("number of different paths plus one").
        if (method.isConstructor()) {
            return 0;
        }
        if (method.getBody() == null) {
            return 1;
        }
        ComplexityVisitor visitor = new ComplexityVisitor();
        method.getBody().accept(visitor);
        return visitor.complexity;
    }

    private static class ComplexityVisitor extends ASTVisitor {
        int complexity = 1;

        @Override
        public boolean visit(TypeDeclaration node) {
            return false;
        }

        @Override
        public boolean visit(EnumDeclaration node) {
            return false;
        }

        @Override
        public boolean visit(AnonymousClassDeclaration node) {
            return false;
        }

        @Override
        public boolean visit(IfStatement node) {
            complexity++;
            return true;
        }

        @Override
        public boolean visit(ForStatement node) {
            complexity++;
            return true;
        }

        @Override
        public boolean visit(EnhancedForStatement node) {
            complexity++;
            return true;
        }

        @Override
        public boolean visit(WhileStatement node) {
            complexity++;
            return true;
        }

        @Override
        public boolean visit(DoStatement node) {
            complexity++;
            return true;
        }

        @Override
        public boolean visit(CatchClause node) {
            complexity++;
            return true;
        }

        @Override
        public boolean visit(ConditionalExpression node) {
            complexity++;
            return true;
        }

        @Override
        public boolean visit(SwitchStatement node) {
            return true;
        }

        @Override
        public boolean visit(SwitchExpression node) {
            return true;
        }

        @Override
        public boolean visit(SwitchCase node) {
            if (!node.isDefault()) {
                complexity++;
            }
            return true;
        }

        @Override
        public boolean visit(InfixExpression node) {
            InfixExpression.Operator operator = node.getOperator();
            if (InfixExpression.Operator.CONDITIONAL_AND.equals(operator)
                    || InfixExpression.Operator.CONDITIONAL_OR.equals(operator)) {
                complexity += 1 + node.extendedOperands().size();
            }
            return true;
        }
    }
}
