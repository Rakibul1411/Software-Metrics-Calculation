import numpy as np
import pandas as pd
from fastapi import UploadFile
from sklearn.metrics import average_precision_score, matthews_corrcoef, roc_auc_score
from sklearn.preprocessing import StandardScaler
from typing import Dict, List, Tuple

from app.services.coral_service import CoralService
from app.services.knn_service import KnnService


class PredictionService:
    PROMISE_FEATURES = [
        "wmc", "dit", "noc", "cbo", "rfc", "lcom", "ca", "ce", "npm", "lcom3",
        "loc", "dam", "moa", "mfa", "cam", "ic", "cbm", "amc", "max_cc", "avg_cc"
    ]

    def __init__(self):
        self.coral = CoralService()
        self.knn = KnnService()

    async def run(self, target_file: UploadFile, source_files: List[UploadFile],
                  label_column: str, knn_value: int, coral_option: bool, top_k: int = 3):
        if knn_value < 1:
            raise ValueError("K neighbors must be at least 1.")

        target_df = self._read_csv(target_file)
        label_column = label_column.strip().lower()
        feature_cols = self._feature_columns(target_df, label_column, target_has_label=False)
        X_target = self._numeric_features(target_df, feature_cols, "target")

        selected_sources, ranking = self._rank_and_select_sources(
            source_files, label_column, feature_cols, X_target, top_k)
        model_data = self._build_model_data(selected_sources, X_target, coral_option)
        y_source = model_data["y_source"]
        if knn_value > len(y_source):
            raise ValueError(
                f"K neighbors ({knn_value}) cannot exceed selected labelled source rows ({len(y_source)})."
            )

        classifier = self.knn.fit(model_data["X_source_model"], y_source, k=knn_value)
        predictions = classifier.predict(model_data["X_target_scaled"]).astype(int)
        risk_scores = self._buggy_probabilities(classifier, model_data["X_target_scaled"])
        neighbor_distances, neighbor_indices = classifier.kneighbors(
            model_data["X_target_scaled"], n_neighbors=min(knn_value, len(y_source)))

        results = []
        for index, (_, row) in enumerate(target_df.iterrows()):
            risk = float(risk_scores[index])
            is_buggy = bool(predictions[index])
            results.append({
                "class": row.get("name", str(index)),
                "prediction": "1" if is_buggy else "0",
                "label": "Buggy" if is_buggy else "Clean",
                "isBuggy": is_buggy,
                "riskScore": risk,
                "riskPercent": round(risk * 100, 2),
                "confidence": self._confidence_label(risk),
                "topRiskyMetrics": self._top_risky_metrics(
                    X_target[index], feature_cols, model_data["X_source_raw"]),
                "nearestBuggyClasses": self._nearest_buggy_classes(
                    neighbor_indices[index], neighbor_distances[index], model_data)
            })

        buggy_count = sum(1 for item in results if item["isBuggy"])
        return {
            "status": "success",
            "method": "Similarity-Based Multi-Source CORAL Defect Prediction",
            "selectedSources": ranking[:len(selected_sources)],
            "sourceRanking": ranking,
            "predictions": results,
            "summary": {
                "total": len(results),
                "buggy": buggy_count,
                "clean": len(results) - buggy_count
            }
        }

    async def evaluate(self, target_file: UploadFile, source_files: List[UploadFile],
                       label_column: str, knn_value: int, coral_option: bool, top_k: int = 3):
        if knn_value < 1:
            raise ValueError("K neighbors must be at least 1.")

        target_df = self._read_csv(target_file)
        label_column = label_column.strip().lower()
        if label_column not in target_df.columns:
            raise ValueError(
                f"Label column '{label_column}' was not found in the target CSV. "
                f"Available columns: {', '.join(target_df.columns)}"
            )

        feature_cols = self._feature_columns(target_df, label_column, target_has_label=True)
        target_labelled = target_df[label_column].notna()
        if not target_labelled.all():
            raise ValueError("Every target row must have a label for accuracy evaluation.")

        X_target = self._numeric_features(target_df, feature_cols, "target")
        y_target = self._binary_labels(target_df[label_column], label_column)

        selected_sources, ranking = self._rank_and_select_sources(
            source_files, label_column, feature_cols, X_target, top_k)
        model_data = self._build_model_data(selected_sources, X_target, coral_option)
        y_source = model_data["y_source"]
        if knn_value > len(y_source):
            raise ValueError(
                f"K neighbors ({knn_value}) cannot exceed selected labelled source rows ({len(y_source)})."
            )

        classifier = self.knn.fit(model_data["X_source_model"], y_source, k=knn_value)
        predictions = classifier.predict(model_data["X_target_scaled"]).astype(int)
        risk_scores = self._buggy_probabilities(classifier, model_data["X_target_scaled"])

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
        mcc = float(matthews_corrcoef(y_target, predictions)) if total else 0.0
        roc_auc = self._safe_auc(roc_auc_score, y_target, risk_scores)
        pr_auc = self._safe_auc(average_precision_score, y_target, risk_scores)

        results = []
        for index, (_, row) in enumerate(target_df.iterrows()):
            predicted_buggy = bool(predictions[index])
            actual_buggy = bool(y_target[index])
            results.append({
                "class": row.get("name", str(index)),
                "prediction": "1" if predicted_buggy else "0",
                "label": "Buggy" if predicted_buggy else "Clean",
                "isBuggy": predicted_buggy,
                "riskScore": float(risk_scores[index]),
                "riskPercent": round(float(risk_scores[index]) * 100, 2),
                "confidence": self._confidence_label(float(risk_scores[index])),
                "actualLabel": "Buggy" if actual_buggy else "Clean",
                "actualIsBuggy": actual_buggy,
                "correct": predicted_buggy == actual_buggy
            })

        return {
            "status": "success",
            "method": "Similarity-Based Multi-Source CORAL Defect Prediction",
            "selectedSources": ranking[:len(selected_sources)],
            "sourceRanking": ranking,
            "metrics": {
                "accuracy": accuracy,
                "precision": precision,
                "recall": recall,
                "f1": f1,
                "mcc": mcc,
                "rocAuc": roc_auc,
                "prAuc": pr_auc
            },
            "confusionMatrix": {
                "truePositive": true_positive,
                "trueNegative": true_negative,
                "falsePositive": false_positive,
                "falseNegative": false_negative
            },
            "predictions": results
        }

    def _rank_and_select_sources(self, source_files: List[UploadFile], label_column: str,
                                 feature_cols: List[str], X_target: np.ndarray,
                                 top_k: int) -> Tuple[List[Dict], List[Dict]]:
        if top_k < 1:
            raise ValueError("Top-K source count must be at least 1.")

        candidates = []
        for source_file in source_files:
            source_df = self._read_csv(source_file)
            filename = source_file.filename or "source.csv"
            if label_column not in source_df.columns:
                raise ValueError(
                    f"Label column '{label_column}' was not found in {filename}. "
                    f"Available columns: {', '.join(source_df.columns)}"
                )
            missing_features = [column for column in feature_cols if column not in source_df.columns]
            if missing_features:
                raise ValueError(
                    f"Source CSV '{filename}' does not match the target schema. "
                    f"Missing metric columns: {', '.join(missing_features)}"
                )

            labelled_rows = source_df[label_column].notna()
            X_source = self._numeric_features(source_df.loc[labelled_rows], feature_cols, filename)
            y_source = self._binary_labels(source_df.loc[labelled_rows, label_column], label_column)
            if len(y_source) == 0:
                continue

            candidates.append({
                "dataset": filename,
                "distance": self._covariance_distance(X_source, X_target),
                "rows": int(len(y_source)),
                "buggyRows": int(np.sum(y_source == 1)),
                "cleanRows": int(np.sum(y_source == 0)),
                "X": X_source,
                "y": y_source,
                "classNames": source_df.loc[labelled_rows, "name"].astype(str).tolist()
                if "name" in source_df.columns else [f"{filename}#{i}" for i in range(len(y_source))]
            })

        if not candidates:
            raise ValueError("No labelled source rows were found in the uploaded source datasets.")

        candidates.sort(key=lambda candidate: candidate["distance"])
        selected_count = min(top_k, len(candidates))
        ranking = [{
            "rank": index + 1,
            "dataset": candidate["dataset"],
            "distance": round(float(candidate["distance"]), 6),
            "rows": candidate["rows"],
            "buggyRows": candidate["buggyRows"],
            "cleanRows": candidate["cleanRows"],
            "selected": index < selected_count
        } for index, candidate in enumerate(candidates)]
        return candidates[:selected_count], ranking

    def _build_model_data(self, selected_sources: List[Dict], X_target: np.ndarray,
                          coral_option: bool) -> Dict:
        X_source_raw = np.vstack([source["X"] for source in selected_sources])
        y_source = np.concatenate([source["y"] for source in selected_sources])
        source_class_names = sum((source["classNames"] for source in selected_sources), [])
        source_dataset_names = sum(
            ([source["dataset"]] * len(source["y"]) for source in selected_sources), [])

        scaler = StandardScaler()
        X_source_scaled = scaler.fit_transform(X_source_raw)
        X_target_scaled = scaler.transform(X_target)

        adapted_parts = []
        offset = 0
        for source in selected_sources:
            length = len(source["y"])
            X_part = X_source_scaled[offset:offset + length]
            adapted_parts.append(self.coral.align(X_part, X_target_scaled) if coral_option else X_part)
            offset += length

        return {
            "X_source_raw": X_source_raw,
            "X_source_model": np.vstack(adapted_parts),
            "X_target_scaled": X_target_scaled,
            "y_source": y_source,
            "sourceClassNames": source_class_names,
            "sourceDatasetNames": source_dataset_names
        }

    def _read_csv(self, upload: UploadFile) -> pd.DataFrame:
        upload.file.seek(0)
        dataframe = pd.read_csv(upload.file)
        dataframe.columns = self._normalize_columns(dataframe.columns)
        return dataframe

    def _feature_columns(self, dataframe: pd.DataFrame, label_column: str,
                         target_has_label: bool) -> List[str]:
        ignored = {"name", label_column}
        feature_cols = [column for column in dataframe.columns if column not in ignored]
        promise_cols = [column for column in self.PROMISE_FEATURES if column in feature_cols]
        selected = promise_cols if len(promise_cols) == len(self.PROMISE_FEATURES) else feature_cols
        if not selected:
            raise ValueError("The target CSV does not contain any metric columns.")
        return selected

    @staticmethod
    def _covariance_distance(X_source: np.ndarray, X_target: np.ndarray) -> float:
        return float(np.linalg.norm(
            PredictionService._covariance(PredictionService._standardize(X_source))
            - PredictionService._covariance(PredictionService._standardize(X_target)),
            ord="fro"
        ))

    @staticmethod
    def _standardize(values: np.ndarray) -> np.ndarray:
        mean = values.mean(axis=0)
        std = values.std(axis=0)
        std[std == 0] = 1.0
        return (values - mean) / std

    @staticmethod
    def _covariance(values: np.ndarray) -> np.ndarray:
        if values.shape[0] < 2:
            return np.zeros((values.shape[1], values.shape[1]))
        return np.nan_to_num(np.cov(values, rowvar=False), nan=0.0, posinf=0.0, neginf=0.0)

    @staticmethod
    def _buggy_probabilities(classifier, X_target: np.ndarray) -> np.ndarray:
        probabilities = classifier.predict_proba(X_target)
        classes = list(classifier.classes_)
        if 1 in classes:
            return probabilities[:, classes.index(1)]
        return np.ones(X_target.shape[0]) if classes and classes[0] == 1 else np.zeros(X_target.shape[0])

    @staticmethod
    def _confidence_label(risk_score: float) -> str:
        margin = abs(risk_score - 0.5)
        if margin >= 0.25:
            return "High"
        if margin >= 0.10:
            return "Medium"
        return "Low"

    @staticmethod
    def _top_risky_metrics(target_row: np.ndarray, feature_cols: List[str],
                           X_source_raw: np.ndarray) -> List[Dict]:
        mean = X_source_raw.mean(axis=0)
        std = X_source_raw.std(axis=0)
        std[std == 0] = 1.0
        z_scores = (target_row - mean) / std
        ranked = sorted(
            [(index, z_scores[index]) for index in range(len(feature_cols)) if z_scores[index] > 0],
            key=lambda item: item[1],
            reverse=True
        )[:4]
        return [{
            "metric": feature_cols[index].upper(),
            "value": float(target_row[index]),
            "sourceMean": round(float(mean[index]), 3),
            "zScore": round(float(score), 3),
            "severity": "High" if score >= 2 else "Moderate"
        } for index, score in ranked]

    @staticmethod
    def _nearest_buggy_classes(indices: np.ndarray, distances: np.ndarray, model_data: Dict) -> List[Dict]:
        examples = []
        y_source = model_data["y_source"]
        for source_index, distance in zip(indices, distances):
            if int(y_source[source_index]) != 1:
                continue
            examples.append({
                "class": model_data["sourceClassNames"][source_index],
                "dataset": model_data["sourceDatasetNames"][source_index],
                "distance": round(float(distance), 4)
            })
            if len(examples) == 3:
                break
        return examples

    @staticmethod
    def _safe_auc(metric_fn, y_true: np.ndarray, y_score: np.ndarray):
        if len(set(y_true.tolist())) < 2:
            return None
        return float(metric_fn(y_true, y_score))

    @staticmethod
    def _normalize_columns(columns) -> List[str]:
        normalized = [str(column).strip().lower() for column in columns]
        duplicates = sorted({column for column in normalized if normalized.count(column) > 1})
        if duplicates:
            raise ValueError(f"CSV contains duplicate columns: {', '.join(duplicates)}")
        return normalized

    @staticmethod
    def _binary_labels(labels: pd.Series, label_column: str) -> np.ndarray:
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
