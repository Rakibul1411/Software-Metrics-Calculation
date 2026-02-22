package org.promise.metrics.calculator;

import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Calculator for DAM (Data Access Metric).
 *
 * DAM measures the encapsulation of a class. It is defined as:
 *   DAM = number of private or protected attributes / total number of attributes
 *
 * Where attributes are instance and static fields declared directly in the class.
 *
 * Special cases:
 *   - If there are no attributes, DAM = 0
 *
 * DAM ranges from 0.0 to 1.0:
 *   - 1.0 = all attributes are private/protected (maximum encapsulation)
 *   - 0.0 = no attributes are private/protected
 */
public class DAMCalculator {

    /**
     * Calculate DAM for a specific type declaration.
     *
     * @param typeDeclaration The type to analyze
     * @return DAM value (ratio of private/protected attributes to total attributes)
     */
    public static double calculateDAMForType(AbstractTypeDeclaration typeDeclaration) {
        DAMVisitor visitor = new DAMVisitor();
        typeDeclaration.accept(visitor);

        if (visitor.totalAttributes == 0) {
            return 0.0;
        }

        return (double) visitor.privateProtectedAttributes / visitor.totalAttributes;
    }

    /**
     * Visitor to count total attributes and private/protected attributes.
     * Only considers fields directly in the top-level type, not nested classes.
     */
    private static class DAMVisitor extends ASTVisitor {
        int totalAttributes = 0;
        int privateProtectedAttributes = 0;
        int nestingLevel = 0;

        @Override
        public boolean visit(TypeDeclaration node) {
            nestingLevel++;
            return nestingLevel == 1;
        }

        @Override
        public void endVisit(TypeDeclaration node) {
            nestingLevel--;
        }

        @Override
        public boolean visit(EnumDeclaration node) {
            nestingLevel++;
            return nestingLevel == 1;
        }

        @Override
        public void endVisit(EnumDeclaration node) {
            nestingLevel--;
        }

        @Override
        public boolean visit(AnonymousClassDeclaration node) {
            nestingLevel++;
            return false;
        }

        @Override
        public void endVisit(AnonymousClassDeclaration node) {
            nestingLevel--;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean visit(FieldDeclaration node) {
            if (nestingLevel == 1) {
                // Count each variable declarator as a separate attribute
                List<VariableDeclarationFragment> fragments = node.fragments();
                int count = fragments.size();
                totalAttributes += count;

                int modifiers = node.getModifiers();
                if (Modifier.isPrivate(modifiers) || Modifier.isProtected(modifiers)) {
                    privateProtectedAttributes += count;
                }
            }
            return false;
        }
    }
}
