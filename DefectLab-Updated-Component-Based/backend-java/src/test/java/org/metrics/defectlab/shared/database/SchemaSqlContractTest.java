package org.metrics.defectlab.shared.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class SchemaSqlContractTest {

    private static final Pattern CREATE_TABLE =
            Pattern.compile(
                    "^\\s*create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?(\\w+)",
                    Pattern.MULTILINE);

    @Test
    void schemaCreatesOnlyUsersAndThreeWorkflowTablesAndCleansLegacyTables()
            throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream("/schema.sql")) {
            assertTrue(stream != null, "schema.sql must be on the classpath");
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .toLowerCase(Locale.ROOT);
        }

        Matcher matcher = CREATE_TABLE.matcher(sql);
        java.util.Set<String> created = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            created.add(matcher.group(1));
        }

        assertEquals(DatabaseSchemaContract.REQUIRED_TABLES, created);
        assertTrue(sql.contains("drop table if exists datasets"));
        assertTrue(sql.contains("drop table if exists prediction_results"));
        assertTrue(sql.contains("drop table if exists dataset_comparisons"));
        assertTrue(sql.contains("drop table if exists compare_metrics"));
        assertTrue(sql.contains("drop table if exists flyway_schema_history"));
        assertTrue(sql.contains("create table if not exists metric_comparisons"));
        assertTrue(sql.contains("create table if not exists prediction_runs"));
        assertTrue(sql.contains("comparison_group_id"));
        assertTrue(sql.contains("prediction_file_path"));
        assertTrue(sql.contains("comparison_report_file_path"));
        assertTrue(sql.contains("model_config->>'modelname' = 'knn'"));
        assertTrue(sql.contains("model_config ? 'k'"));
        assertTrue(sql.contains("(model_config->>'k')::integer between 1 and 5"));
        assertTrue(sql.contains("ck_prediction_model\n    check"));
        assertTrue(sql.contains("not valid"));
    }
}
