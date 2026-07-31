package org.metrics.defectlab.shared.database;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fails startup when the configured PostgreSQL schema drifts from DefectLab's
 * final users-plus-three-workflow-table design.
 */
@Component
public class DatabaseSchemaContract implements ApplicationRunner {

    public static final Set<String> REQUIRED_TABLES = Set.of(
            "users", "metric_datasets", "metric_comparisons", "prediction_runs");

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaContract(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_catalog.pg_tables "
                + "WHERE schemaname = current_schema()",
                String.class);
        Set<String> actual = new LinkedHashSet<>(rows);
        if (!actual.equals(REQUIRED_TABLES)) {
            Set<String> missing = new LinkedHashSet<>(REQUIRED_TABLES);
            missing.removeAll(actual);
            Set<String> unexpected = new LinkedHashSet<>(actual);
            unexpected.removeAll(REQUIRED_TABLES);
            throw new IllegalStateException(
                    "Database schema must contain exactly users, metric_datasets, "
                    + "metric_comparisons and prediction_runs. Missing=" + missing
                    + ", unexpected=" + unexpected);
        }
    }
}
