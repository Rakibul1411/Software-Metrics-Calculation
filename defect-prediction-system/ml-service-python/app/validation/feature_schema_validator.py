from typing import List


class FeatureSchemaValidator:
    """Validates that target and source CSVs have compatible feature schemas."""

    def validate(self, source_columns: List[str], target_columns: List[str],
                 label_column: str) -> bool:
        """
        Returns True if source contains all target feature columns.
        """
        source_feature_cols = set(source_columns) - {label_column, "name"}
        target_feature_cols = set(target_columns) - {"name"}
        return target_feature_cols.issubset(source_feature_cols)
