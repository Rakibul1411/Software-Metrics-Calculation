package org.metrics.aeeem.calculator;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.metrics.aeeem.model.AeeemMetricResult;
import org.metrics.aeeem.calculator.legacy.CBOCalculator;
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
        ITypeBinding typeBinding = typeDeclaration.resolveBinding();
        String packageName = compilationUnit.getPackage() == null
                ? "" : compilationUnit.getPackage().getName().getFullyQualifiedName();
        String className = typeDeclaration.getName().getIdentifier();
        String fullyQualifiedName = typeBinding == null || typeBinding.getQualifiedName().isEmpty()
                ? (packageName.isEmpty() ? className : packageName + "." + className)
                : typeBinding.getQualifiedName();
        AeeemMetricResult metrics = new AeeemMetricResult(fullyQualifiedName);
        metrics.setCkOoDit(inheritanceDepth(typeBinding));

        int privateMethods = 0;
        int publicMethods = 0;
        int methods = 0;
        int privateAttributes = 0;
        int publicAttributes = 0;
        int attributes = 0;
        boolean interfaceType = typeDeclaration instanceof TypeDeclaration
                && ((TypeDeclaration) typeDeclaration).isInterface();

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
        Set<String> dependencies = semanticDependencies(typeDeclaration, typeBinding);
        if (dependencies.isEmpty()) {
            dependencies = CBOCalculator.extractDependencies(typeDeclaration, fullyQualifiedName, imports);
        }
        metrics.setDependencies(dependencies);
        ITypeBinding superclass = typeBinding == null ? null : typeBinding.getSuperclass();
        metrics.setSuperclassName(superclass == null
                ? NOCCalculator.extractSuperclassName(typeDeclaration) : superclass.getQualifiedName());
        metrics.setInterface(interfaceType);
        metrics.setMethodNames(ICCalculator.extractMethodNames(typeDeclaration));
        metrics.setDeclaredAttributeCount(attributes);

        applyLocalMetrics(metrics, privateMethods, publicMethods, methods, privateAttributes,
                publicAttributes, attributes, wmc, loc, lcom, rfc, dependencies.size());
        return metrics;
    }

    private static Set<String> semanticDependencies(AbstractTypeDeclaration declaration, ITypeBinding owner) {
        Set<String> dependencies = new java.util.LinkedHashSet<>();
        declaration.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
            @Override
            public boolean visit(SimpleName node) {
                IBinding binding = node.resolveBinding();
                if (binding instanceof ITypeBinding) {
                    addType((ITypeBinding) binding, owner, dependencies);
                } else if (binding instanceof IVariableBinding) {
                    IVariableBinding variable = (IVariableBinding) binding;
                    addType(variable.getType(), owner, dependencies);
                    addType(variable.getDeclaringClass(), owner, dependencies);
                } else if (binding instanceof IMethodBinding) {
                    IMethodBinding method = (IMethodBinding) binding;
                    addType(method.getDeclaringClass(), owner, dependencies);
                    addType(method.getReturnType(), owner, dependencies);
                    for (ITypeBinding parameter : method.getParameterTypes()) {
                        addType(parameter, owner, dependencies);
                    }
                }
                return true;
            }
        });
        return dependencies;
    }

    private static void addType(ITypeBinding binding, ITypeBinding owner, Set<String> dependencies) {
        if (binding == null) {
            return;
        }
        ITypeBinding type = binding;
        while (type.isArray()) {
            type = type.getElementType();
        }
        if (type.isPrimitive() || type.isTypeVariable() || type.isWildcardType()) {
            return;
        }
        ITypeBinding declaration = type.getTypeDeclaration();
        String name = declaration.getQualifiedName();
        String ownerName = owner == null ? "" : owner.getTypeDeclaration().getQualifiedName();
        if (!name.isEmpty() && !name.equals(ownerName)) {
            dependencies.add(name);
        }
        for (ITypeBinding argument : type.getTypeArguments()) {
            addType(argument, owner, dependencies);
        }
    }

    private static int inheritanceDepth(ITypeBinding binding) {
        if (binding == null || binding.isInterface()) {
            return 0;
        }
        int depth = 0;
        Set<String> visited = new java.util.HashSet<>();
        ITypeBinding current = binding.getSuperclass();
        while (current != null && visited.add(current.getTypeDeclaration().getKey())) {
            depth++;
            current = current.getSuperclass();
        }
        return depth;
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
