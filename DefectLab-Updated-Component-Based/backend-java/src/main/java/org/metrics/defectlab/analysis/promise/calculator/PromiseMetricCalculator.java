package org.metrics.defectlab.analysis.promise.calculator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.metrics.defectlab.analysis.promise.bytecode.BytecodeClassModel;
import org.metrics.defectlab.analysis.promise.bytecode.BytecodeProjectModel;
import org.metrics.defectlab.analysis.promise.model.PromiseMetricResult;

/**
 * Produces the 20 PROMISE features for every class that may become a row.
 * All values come from the compiled-bytecode model, so a single method universe
 * underpins the whole row.
 */
public final class PromiseMetricCalculator {

    private PromiseMetricCalculator() {
    }

    public static List<PromiseMetricResult> calculate(BytecodeProjectModel project) {
        List<PromiseMetricResult> results = new ArrayList<>();
        for (BytecodeClassModel type : project.getRowClasses()) {
            results.add(calculate(type, project));
        }
        results.sort(Comparator.comparing(PromiseMetricResult::getFullyQualifiedName));
        return results;
    }

    public static PromiseMetricResult calculate(
            BytecodeClassModel type, BytecodeProjectModel project) {

        PromiseMetricResult result = new PromiseMetricResult(type.getFqn());
        InheritanceCouplingAnalyzer inheritance =
                InheritanceCouplingAnalyzer.analyze(type, project);

        result.setWmc(SizeAndComplexityCalculators.wmc(type));
        result.setDit(AbstractionCalculators.dit(type, project));
        result.setNoc(AbstractionCalculators.noc(type, project));
        result.setCbo(CouplingCalculators.cbo(type, project));
        result.setRfc(CouplingCalculators.rfc(type));
        result.setLcom(CohesionCalculators.lcom(type));
        result.setCa(CouplingCalculators.ca(type, project));
        result.setCe(CouplingCalculators.ce(type, project));
        result.setNpm(SizeAndComplexityCalculators.npm(type));
        result.setLcom3(CohesionCalculators.lcom3(type));
        result.setLoc(SizeAndComplexityCalculators.loc(type));
        result.setDam(AbstractionCalculators.dam(type));
        result.setMoa(AbstractionCalculators.moa(type, project));
        result.setMfa(AbstractionCalculators.mfa(type, project));
        result.setCam(CohesionCalculators.cam(type));
        result.setIc(inheritance.inheritanceCoupling());
        result.setCbm(inheritance.couplingBetweenMethods());
        result.setAmc(SizeAndComplexityCalculators.amc(type));
        result.setMaxCc(SizeAndComplexityCalculators.maxCc(type));
        result.setAvgCc(SizeAndComplexityCalculators.avgCc(type));

        result.setSuperclassName(type.getSuperclass());
        result.setInterface(type.isInterfaceType());
        return result;
    }
}
