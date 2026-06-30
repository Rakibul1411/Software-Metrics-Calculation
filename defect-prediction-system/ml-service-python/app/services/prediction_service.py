import pandas as pd
import numpy as np
from typing import List
from fastapi import UploadFile

from app.services.preprocessing_service import PreprocessingService
from app.services.coral_service import CoralService
from app.services.knn_service import KnnService


class PredictionService:
    def __init__(self):
        self.preprocessor = PreprocessingService()
        self.coral = CoralService()
        self.knn = KnnService()

    async def run(self, target_file: UploadFile, source_files: List[UploadFile],
                  label_column: str, knn_value: int, coral_option: bool):
        if knn_value < 1:
            raise ValueError("K neighbors must be at least 1.")

        # 1. Load target CSV
        target_df = pd.read_csv(target_file.file)
        target_df.columns = self._normalize_columns(target_df.columns)
        label_column = label_column.strip().lower()

        # 2. Load and merge source CSVs
        source_dfs = []
        for f in source_files:
            source_df_part = pd.read_csv(f.file)
            source_df_part.columns = self._normalize_columns(source_df_part.columns)
            source_dfs.append(source_df_part)
        source_df = pd.concat(source_dfs, ignore_index=True)

        # 3. Validate and select the target feature schema
        name_col = "name"
        feature_cols = [c for c in target_df.columns if c != name_col]
        if not feature_cols:
            raise ValueError("The target CSV does not contain any metric columns.")
        if label_column not in source_df.columns:
            raise ValueError(
                f"Label column '{label_column}' was not found in the source CSV. "
                f"Available columns: {', '.join(source_df.columns)}"
            )

        missing_features = [column for column in feature_cols if column not in source_df.columns]
        if missing_features:
            raise ValueError(
                "Source CSV schema does not match the extracted target dataset. "
                f"Missing metric columns: {', '.join(missing_features)}"
            )

        X_target = self._numeric_features(target_df, feature_cols, "target")
        X_source = self._numeric_features(source_df, feature_cols, "source")

        labelled_rows = source_df[label_column].notna()
        X_source = X_source[labelled_rows.to_numpy()]
        y_source = self._binary_labels(source_df.loc[labelled_rows, label_column], label_column)
        if len(y_source) == 0:
            raise ValueError(f"Source CSV has no labelled rows in column '{label_column}'.")
        if knn_value > len(y_source):
            raise ValueError(
                f"K neighbors ({knn_value}) cannot exceed the number of labelled source rows ({len(y_source)})."
            )

        # 4. Preprocess
        X_source_scaled, X_target_scaled = self.preprocessor.normalize(X_source, X_target)

        # 5. Apply CORAL domain adaptation
        if coral_option:
            X_source_adapted = self.coral.align(X_source_scaled, X_target_scaled)
        else:
            X_source_adapted = X_source_scaled

        # 6. KNN prediction
        predictions = self.knn.predict(X_source_adapted, y_source, X_target_scaled, k=knn_value)

        # 7. Build result
        result = []
        for i, (_, row) in enumerate(target_df.iterrows()):
            is_buggy = bool(int(predictions[i]))
            result.append({
                "class": row.get(name_col, str(i)),
                "prediction": "1" if is_buggy else "0",
                "label": "Buggy" if is_buggy else "Clean",
                "isBuggy": is_buggy
            })

        buggy_count = sum(1 for item in result if item["isBuggy"])
        return {
            "status": "success",
            "predictions": result,
            "summary": {
                "total": len(result),
                "buggy": buggy_count,
                "clean": len(result) - buggy_count
            }
        }

    async def evaluate(self, target_file: UploadFile, source_files: List[UploadFile],
                       label_column: str, knn_value: int, coral_option: bool):
        """Train on labelled source data and score against labelled target data."""
        if knn_value < 1:
            raise ValueError("K neighbors must be at least 1.")

        target_df = pd.read_csv(target_file.file)
        target_df.columns = self._normalize_columns(target_df.columns)
        label_column = label_column.strip().lower()

        source_dfs = []
        for source_file in source_files:
            source_df_part = pd.read_csv(source_file.file)
            source_df_part.columns = self._normalize_columns(source_df_part.columns)
            source_dfs.append(source_df_part)
        source_df = pd.concat(source_dfs, ignore_index=True)

        for dataset_name, dataframe in (("source", source_df), ("target", target_df)):
            if label_column not in dataframe.columns:
                raise ValueError(
                    f"Label column '{label_column}' was not found in the {dataset_name} CSV. "
                    f"Available columns: {', '.join(dataframe.columns)}"
                )

        name_col = "name"
        feature_cols = [
            column for column in target_df.columns
            if column not in (name_col, label_column)
        ]
        if not feature_cols:
            raise ValueError("The target CSV does not contain any metric columns.")
        missing_features = [column for column in feature_cols if column not in source_df.columns]
        if missing_features:
            raise ValueError(
                "Source CSV schema does not match the target dataset. "
                f"Missing metric columns: {', '.join(missing_features)}"
            )

        source_labelled = source_df[label_column].notna()
        target_labelled = target_df[label_column].notna()
        if not target_labelled.all():
            raise ValueError("Every target row must have a label for accuracy evaluation.")

        X_source = self._numeric_features(
            source_df.loc[source_labelled], feature_cols, "source")
        X_target = self._numeric_features(target_df, feature_cols, "target")
        y_source = self._binary_labels(
            source_df.loc[source_labelled, label_column], label_column)
        y_target = self._binary_labels(target_df[label_column], label_column)
        if len(y_source) == 0:
            raise ValueError(f"Source CSV has no labelled rows in column '{label_column}'.")
        if knn_value > len(y_source):
            raise ValueError(
                f"K neighbors ({knn_value}) cannot exceed the number of labelled source rows ({len(y_source)})."
            )

        X_source_scaled, X_target_scaled = self.preprocessor.normalize(X_source, X_target)
        X_source_model = (
            self.coral.align(X_source_scaled, X_target_scaled)
            if coral_option else X_source_scaled
        )
        predictions = self.knn.predict(
            X_source_model, y_source, X_target_scaled, k=knn_value).astype(int)

        true_positive = int(np.sum((predictions == 1) & (y_target == 1)))
        true_negative = int(np.sum((predictions == 0) & (y_target == 0)))
        false_positive = int(np.sum((predictions == 1) & (y_target == 0)))
        false_negative = int(np.sum((predictions == 0) & (y_target == 1)))
        total = len(y_target)
        accuracy = (true_positive + true_negative) / total if total else 0.0
        precision = true_positive / (true_positive + false_positive) \
            if true_positive + false_positive else 0.0
        recall = true_positive / (true_positive + false_negative) \
            if true_positive + false_negative else 0.0
        f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0

        results = []
        for index, (_, row) in enumerate(target_df.iterrows()):
            predicted_buggy = bool(predictions[index])
            actual_buggy = bool(y_target[index])
            results.append({
                "class": row.get(name_col, str(index)),
                "prediction": "1" if predicted_buggy else "0",
                "label": "Buggy" if predicted_buggy else "Clean",
                "isBuggy": predicted_buggy,
                "actualLabel": "Buggy" if actual_buggy else "Clean",
                "actualIsBuggy": actual_buggy,
                "correct": predicted_buggy == actual_buggy
            })

        return {
            "status": "success",
            "metrics": {
                "accuracy": accuracy,
                "precision": precision,
                "recall": recall,
                "f1": f1
            },
            "confusionMatrix": {
                "truePositive": true_positive,
                "trueNegative": true_negative,
                "falsePositive": false_positive,
                "falseNegative": false_negative
            },
            "predictions": results
        }

    @staticmethod
    def _normalize_columns(columns) -> List[str]:
        normalized = [str(column).strip().lower() for column in columns]
        duplicates = sorted({column for column in normalized if normalized.count(column) > 1})
        if duplicates:
            raise ValueError(f"CSV contains duplicate columns: {', '.join(duplicates)}")
        return normalized

    @staticmethod
    def _binary_labels(labels: pd.Series, label_column: str) -> np.ndarray:
        """Convert defect counts or common boolean labels to clean=0 / buggy=1."""
        numeric = pd.to_numeric(labels, errors="coerce")
        if numeric.notna().all():
            if (numeric < 0).any():
                raise ValueError(f"Label column '{label_column}' cannot contain negative defect counts.")
            return (numeric > 0).astype(int).to_numpy()

        normalized = labels.astype(str).str.strip().str.lower()
        mapping = {
            "clean": 0, "false": 0, "no": 0, "n": 0,
            "buggy": 1, "defective": 1, "true": 1, "yes": 1, "y": 1
        }
        unknown = sorted(set(normalized) - set(mapping))
        if unknown:
            raise ValueError(
                f"Label column '{label_column}' must contain defect counts or clean/buggy values. "
                f"Unsupported values: {', '.join(unknown[:5])}"
            )
        return normalized.map(mapping).to_numpy(dtype=int)

    @staticmethod
    def _numeric_features(dataframe: pd.DataFrame, columns: List[str], dataset_name: str) -> np.ndarray:
        numeric = dataframe[columns].apply(pd.to_numeric, errors="coerce")
        invalid_columns = [
            column for column in columns
            if numeric[column].isna().any() and dataframe[column].notna().any()
        ]
        if invalid_columns:
            raise ValueError(
                f"The {dataset_name} CSV contains nonnumeric or missing metric values in: "
                f"{', '.join(invalid_columns)}"
            )

        values = numeric.fillna(0).to_numpy(dtype=float)
        if not np.isfinite(values).all():
            raise ValueError(f"The {dataset_name} CSV contains infinite metric values.")
        return values
