package org.metrics.promise.calculator;

import java.util.Map;

import org.metrics.promise.analyzer.PromiseProjectAnalyzer.FieldInfo;
import org.metrics.promise.analyzer.PromiseProjectAnalyzer.TypeInfo;

public final class MoaPromiseCalculator {
    private MoaPromiseCalculator() {
    }

    public static int calculate(TypeInfo type, Map<String, TypeInfo> typesByKey) {
        int count = 0;
        for (FieldInfo field : type.fields) {
            for (String key : field.referencedTypeKeys) {
                if (typesByKey.containsKey(key) && !key.equals(type.key)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }
}
