package org.metrics.aeeem.calculator;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.metrics.aeeem.model.AeeemMetricResult;
import org.metrics.aeeem.calculator.legacy.CBOCalculator;
import org.metrics.aeeem.calculator.legacy.DITCalculator;
import org.metrics.aeeem.calculator.legacy.ICCalculator;
import org.metrics.aeeem.calculator.legacy.LCOMCalculator;
import org.metrics.aeeem.calculator.legacy.LOCCalculator;
import org.metrics.aeeem.calculator.legacy.NOCCalculator;
import org.metrics.aeeem.calculator.legacy.RFCCalculator;
import org.metrics.aeeem.calculator.legacy.WMCCalculator;

/** Calculates the AEEEM metrics that can be derived from a Java AST. */
public final class AeeemStaticMetricsCalculator {

    private AeeemStaticMetricsCalculator() {}

    public static AeeemMetricResult calculateAeeemForType(CompilationUnit compilationUnit,
                                                           AbstractTypeDeclaration typeDeclaration,
                                                           String sourceCode) {
        String packageName = compilationUnit.getPackage() == null
                ? "" : compilationUnit.getPackage().getName().getFullyQualifiedName();
        String className = typeDeclaration.getName().getIdentifier();
        String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;
        AeeemMetricResult metrics = new AeeemMetricResult(fullyQualifiedName);

        int privateMethods = 0;
        int publicMethods = 0;
        int methods = 0;
        int privateAttributes = 0;
        int publicAttributes = 0;
        int attributes = 0;
        boolean interfaceType = DITCalculator.isInterface(typeDeclaration);

        for (Object declaration : typeDeclaration.bodyDeclarations()) {
            if (declaration instanceof MethodDeclaration) {
                MethodDeclaration method = (MethodDeclaration) declaration;
                if (method.isConstructor()) continue;
                methods++;
                int modifiers = method.getModifiers();
                if (Modifier.isPrivate(modifiers)) privateMethods++;
                if (Modifier.isPublic(modifiers) || (interfaceType && !Modifier.isPrivate(modifiers))) {
                    publicMethods++;
                }
            } else if (declaration instanceof FieldDeclaration) {
                FieldDeclaration field = (FieldDeclaration) declaration;
                int fieldCount = field.fragments().size();
                attributes += fieldCount;
                if (Modifier.isPrivate(field.getModifiers())) privateAttributes += fieldCount;
                if (Modifier.isPublic(field.getModifiers()) || interfaceType) publicAttributes += fieldCount;
            }
        }

        int wmc = WMCCalculator.calculateWMCForType(typeDeclaration);
        int loc = LOCCalculator.calculateLOCForType(compilationUnit, typeDeclaration, sourceCode);
        int lcom = LCOMCalculator.calculateLCOMForType(typeDeclaration);
        int rfc = RFCCalculator.calculateRFCForType(typeDeclaration);

        @SuppressWarnings("unchecked")
        List<ImportDeclaration> imports = compilationUnit.imports();
        Set<String> dependencies = CBOCalculator.extractDependencies(typeDeclaration, fullyQualifiedName, imports);
        metrics.setDependencies(dependencies);
        metrics.setSuperclassName(NOCCalculator.extractSuperclassName(typeDeclaration));
        metrics.setInterface(interfaceType);
        metrics.setMethodNames(ICCalculator.extractMethodNames(typeDeclaration));
        metrics.setDeclaredAttributeCount(attributes);

        applyLocalMetrics(metrics, privateMethods, publicMethods, methods, privateAttributes,
                publicAttributes, attributes, wmc, loc, lcom, rfc, dependencies.size());
        return metrics;
    }

    private static void applyLocalMetrics(AeeemMetricResult metrics, int privateMethods, int publicMethods,
                                          int methods, int privateAttributes, int publicAttributes, int attributes,
                                          int wmc, int loc, int lcom, int rfc, int fanOut) {
        metrics.setCkOoNumberOfPrivateMethods(privateMethods);
        metrics.setCkOoNumberOfPublicMethods(publicMethods);
        metrics.setCkOoNumberOfMethods(methods);
        metrics.setCkOoNumberOfPrivateAttributes(privateAttributes);
        metrics.setCkOoNumberOfPublicAttributes(publicAttributes);
        metrics.setCkOoNumberOfAttributes(attributes);
        metrics.setCkOoWmc(wmc);
        metrics.setCkOoNumberOfLinesOfCode(loc);
        metrics.setCkOoLcom(lcom);
        metrics.setCkOoRfc(rfc);
        metrics.setCkOoFanOut(fanOut);
        metrics.setCkOoCbo(fanOut);

        metrics.setLdhhNumberOfPrivateMethods(privateMethods);
        metrics.setLdhhNumberOfPublicMethods(publicMethods);
        metrics.setLdhhNumberOfMethods(methods);
        metrics.setLdhhNumberOfPrivateAttributes(privateAttributes);
        metrics.setLdhhNumberOfPublicAttributes(publicAttributes);
        metrics.setLdhhNumberOfAttributes(attributes);
        metrics.setLdhhWmc(wmc);
        metrics.setLdhhNumberOfLinesOfCode(loc);
        metrics.setLdhhLcom(lcom);
        metrics.setLdhhRfc(rfc);
        metrics.setLdhhFanOut(fanOut);
        metrics.setLdhhCbo(fanOut);

        metrics.setWchuNumberOfPrivateMethods(privateMethods);
        metrics.setWchuNumberOfPublicMethods(publicMethods);
        metrics.setWchuNumberOfMethods(methods);
        metrics.setWchuNumberOfPrivateAttributes(privateAttributes);
        metrics.setWchuNumberOfPublicAttributes(publicAttributes);
        metrics.setWchuNumberOfAttributes(attributes);
        metrics.setWchuWmc(wmc);
        metrics.setWchuNumberOfLinesOfCode(loc);
        metrics.setWchuLcom(lcom);
        metrics.setWchuRfc(rfc);
        metrics.setWchuFanOut(fanOut);
        metrics.setWchuCbo(fanOut);
    }
}
