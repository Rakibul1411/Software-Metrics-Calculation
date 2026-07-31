-- DefectLab uses exactly four relational tables: users plus the three
-- project-specific workflow tables below. Statements use the custom @@
-- separator configured in application.properties so PostgreSQL DO blocks are
-- handled correctly by Spring's SQL initializer.

DROP TABLE IF EXISTS dataset_comparisons CASCADE@@
DROP TABLE IF EXISTS prediction_results CASCADE@@
DROP TABLE IF EXISTS datasets CASCADE@@
DROP TABLE IF EXISTS compare_metrics CASCADE@@
DROP TABLE IF EXISTS flyway_schema_history CASCADE@@

CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(150) UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
)@@

-- Migrate the previous metric_datasets draft without overwriting its file
-- paths. A fresh database simply creates the final table.
DO $$
BEGIN
    IF to_regclass('metric_datasets') IS NULL THEN
        CREATE TABLE metric_datasets (
            id                BIGSERIAL PRIMARY KEY,
            user_id           BIGINT REFERENCES users(id),
            dataset_family    VARCHAR(20) NOT NULL,
            project_name      VARCHAR(150) NOT NULL,
            project_version   VARCHAR(50) NOT NULL,
            dataset_type      VARCHAR(20) NOT NULL,
            has_actual_label  BOOLEAN NOT NULL,
            total_files       INTEGER NOT NULL DEFAULT 0,
            total_metrics     INTEGER NOT NULL DEFAULT 0,
            metrics_file_path TEXT NOT NULL,
            created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
    ELSE
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'metric_datasets'
              AND column_name = 'dataset_origin'
        ) AND NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'metric_datasets'
              AND column_name = 'dataset_type'
        ) THEN
            ALTER TABLE metric_datasets RENAME COLUMN dataset_origin TO dataset_type;
            UPDATE metric_datasets
            SET dataset_type = CASE
                WHEN dataset_type = 'MANUAL_EXTRACTED' THEN 'MANUAL'
                ELSE dataset_type
            END;
        END IF;
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'metric_datasets'
              AND column_name = 'is_labeled'
        ) AND NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'metric_datasets'
              AND column_name = 'has_actual_label'
        ) THEN
            ALTER TABLE metric_datasets RENAME COLUMN is_labeled TO has_actual_label;
        END IF;
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'metric_datasets'
              AND column_name = 'total_rows'
        ) AND NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'metric_datasets'
              AND column_name = 'total_files'
        ) THEN
            ALTER TABLE metric_datasets RENAME COLUMN total_rows TO total_files;
        END IF;
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'metric_datasets'
              AND column_name = 'total_features'
        ) AND NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'metric_datasets'
              AND column_name = 'total_metrics'
        ) THEN
            ALTER TABLE metric_datasets RENAME COLUMN total_features TO total_metrics;
        END IF;
    END IF;
END
$$@@

UPDATE metric_datasets SET project_version = 'unspecified'
WHERE project_version IS NULL OR BTRIM(project_version) = ''@@
ALTER TABLE metric_datasets ALTER COLUMN project_version SET NOT NULL@@
ALTER TABLE metric_datasets DROP COLUMN IF EXISTS source_hash CASCADE@@
ALTER TABLE metric_datasets DROP COLUMN IF EXISTS extractor_version CASCADE@@
ALTER TABLE metric_datasets DROP COLUMN IF EXISTS file_hash CASCADE@@
ALTER TABLE metric_datasets DROP COLUMN IF EXISTS total_buggy CASCADE@@
ALTER TABLE metric_datasets DROP COLUMN IF EXISTS total_clean CASCADE@@
ALTER TABLE metric_datasets DROP COLUMN IF EXISTS label_status CASCADE@@
ALTER TABLE metric_datasets DROP COLUMN IF EXISTS class_identifier_column CASCADE@@

CREATE UNIQUE INDEX IF NOT EXISTS uq_metric_datasets_identity
    ON metric_datasets(user_id, dataset_family, project_name, project_version, dataset_type)@@
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_metric_datasets_identity'
          AND conrelid = 'metric_datasets'::regclass
    ) THEN
        ALTER TABLE metric_datasets
            ADD CONSTRAINT uq_metric_datasets_identity
            UNIQUE USING INDEX uq_metric_datasets_identity;
    END IF;
END
$$@@
CREATE INDEX IF NOT EXISTS ix_metric_datasets_user
    ON metric_datasets(user_id, created_at DESC)@@

CREATE TABLE IF NOT EXISTS metric_comparisons (
    id                              BIGSERIAL PRIMARY KEY,
    user_id                         BIGINT NOT NULL REFERENCES users(id),
    manual_dataset_id               BIGINT NOT NULL REFERENCES metric_datasets(id),
    predefined_dataset_id           BIGINT NOT NULL REFERENCES metric_datasets(id),
    comparison_config               JSONB NOT NULL,
    comparison_report_file_path     TEXT NOT NULL,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_metric_comparison_different
        CHECK (manual_dataset_id <> predefined_dataset_id),
    CONSTRAINT uq_metric_comparison_pair
        UNIQUE (manual_dataset_id, predefined_dataset_id)
)@@
CREATE INDEX IF NOT EXISTS ix_metric_comparisons_user
    ON metric_comparisons(user_id, created_at DESC)@@

CREATE TABLE IF NOT EXISTS prediction_runs (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT NOT NULL REFERENCES users(id),
    comparison_group_id   UUID NULL,
    source_dataset_id     BIGINT NOT NULL REFERENCES metric_datasets(id),
    target_dataset_id     BIGINT NOT NULL REFERENCES metric_datasets(id),
    model_config          JSONB NOT NULL,
    prediction_file_path  TEXT NULL,
    report_file_path      TEXT NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_prediction_different
        CHECK (source_dataset_id <> target_dataset_id),
    CONSTRAINT ck_prediction_model
        CHECK (model_config->>'modelName' IN ('KNN', 'SVM'))
)@@
CREATE INDEX IF NOT EXISTS ix_prediction_runs_user
    ON prediction_runs(user_id, created_at DESC)@@
CREATE INDEX IF NOT EXISTS ix_prediction_runs_group
    ON prediction_runs(user_id, comparison_group_id)@@

ALTER TABLE users DROP COLUMN IF EXISTS dataset_count CASCADE@@
ALTER TABLE users DROP COLUMN IF EXISTS prediction_count CASCADE@@
ALTER TABLE users DROP COLUMN IF EXISTS comparison_count CASCADE@@
ALTER TABLE users DROP COLUMN IF EXISTS role CASCADE@@
ALTER TABLE users DROP COLUMN IF EXISTS updated_at CASCADE@@
ALTER TABLE users DROP COLUMN IF EXISTS last_login_at CASCADE@@
