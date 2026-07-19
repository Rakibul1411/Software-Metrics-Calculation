package org.metrics.aeeem.history;

import org.metrics.aeeem.model.AeeemMetricResult;

public final class AeeemMetricAccess {

    public static final int FEATURE_COUNT = 17;

    private AeeemMetricAccess() {
    }

    public static double[] staticValues(AeeemMetricResult value) {
        return new double[] {
                value.getCkOoWmc(), value.getCkOoDit(), value.getCkOoRfc(), value.getCkOoNoc(),
                value.getCkOoCbo(), value.getCkOoLcom(), value.getCkOoFanIn(), value.getCkOoFanOut(),
                value.getCkOoNumberOfAttributes(), value.getCkOoNumberOfPublicAttributes(),
                value.getCkOoNumberOfPrivateAttributes(), value.getCkOoNumberOfAttributesInherited(),
                value.getCkOoNumberOfMethods(), value.getCkOoNumberOfPublicMethods(),
                value.getCkOoNumberOfPrivateMethods(), value.getCkOoNumberOfMethodsInherited(),
                value.getCkOoNumberOfLinesOfCode()
        };
    }

    public static void setWchuValues(AeeemMetricResult target, double[] values) {
        target.setWchuWmc(values[0]);
        target.setWchuDit(values[1]);
        target.setWchuRfc(values[2]);
        target.setWchuNoc(values[3]);
        target.setWchuCbo(values[4]);
        target.setWchuLcom(values[5]);
        target.setWchuFanIn(values[6]);
        target.setWchuFanOut(values[7]);
        target.setWchuNumberOfAttributes(values[8]);
        target.setWchuNumberOfPublicAttributes(values[9]);
        target.setWchuNumberOfPrivateAttributes(values[10]);
        target.setWchuNumberOfAttributesInherited(values[11]);
        target.setWchuNumberOfMethods(values[12]);
        target.setWchuNumberOfPublicMethods(values[13]);
        target.setWchuNumberOfPrivateMethods(values[14]);
        target.setWchuNumberOfMethodsInherited(values[15]);
        target.setWchuNumberOfLinesOfCode(values[16]);
    }

    public static void setLdhhValues(AeeemMetricResult target, double[] values) {
        target.setLdhhWmc(values[0]);
        target.setLdhhDit(values[1]);
        target.setLdhhRfc(values[2]);
        target.setLdhhNoc(values[3]);
        target.setLdhhCbo(values[4]);
        target.setLdhhLcom(values[5]);
        target.setLdhhFanIn(values[6]);
        target.setLdhhFanOut(values[7]);
        target.setLdhhNumberOfAttributes(values[8]);
        target.setLdhhNumberOfPublicAttributes(values[9]);
        target.setLdhhNumberOfPrivateAttributes(values[10]);
        target.setLdhhNumberOfAttributesInherited(values[11]);
        target.setLdhhNumberOfMethods(values[12]);
        target.setLdhhNumberOfPublicMethods(values[13]);
        target.setLdhhNumberOfPrivateMethods(values[14]);
        target.setLdhhNumberOfMethodsInherited(values[15]);
        target.setLdhhNumberOfLinesOfCode(values[16]);
    }
}
