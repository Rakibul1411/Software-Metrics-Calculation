package org.metrics.common.validation;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FeatureSchemaValidator {
    public boolean validateSchema(List<String> sourceColumns, List<String> targetColumns) {
        if (sourceColumns == null || targetColumns == null) return false;
        // Verify source has at least target features
        return sourceColumns.containsAll(targetColumns);
    }
}
