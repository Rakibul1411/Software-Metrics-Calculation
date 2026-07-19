from __future__ import annotations

import re
from io import StringIO
from typing import Dict, List, Optional, Tuple

import numpy as np
import pandas as pd
from fastapi import UploadFile

from app.services.coral_service import CoralService
from app.services.knn_service import KnnService
from app.services.metrics_service import MetricsService
from app.services.preprocessing_service import PreprocessingService
from app.services.svm_service import SvmService


class PredictionService:
    """
    Multi-source cross-project defect prediction using shallow CORAL followed
    by a user-selected classifier: distance-weighted KNN or balanced linear SVM.

    Both classifiers are supervised and are trained with labelled source rows.
    The target project may be completely unlabelled during prediction. Target
    feature values may be used for source selection, standardization, and CORAL
    covariance alignment, but target labels are used only by the explicit
    evaluation endpoint after all predictions have already been produced.
    """

    PROMISE_FEATURES = [
        "wmc", "dit", "noc", "cbo", "rfc", "lcom", "ca", "ce", "npm", "lcom3",
        "loc", "dam", "moa", "mfa", "cam", "ic", "cbm", "amc", "max_cc", "avg_cc"
    ]

    SUPPORTED_CLASSIFIERS = {"knn", "svm"}

    def __init__(self) -> None:
        self.coral = CoralService(
            regularization=1.0,
            eigenvalue_floor=1e-12,
        )
        self.knn = KnnService()
        self.svm = SvmService(
            class_weight="balanced",
            random_state=42,
        )
        self.preprocessing = PreprocessingService()
        self.metrics_service = MetricsService()

    async def run(
        self,
        target_file: UploadFile,
        source_files: List[UploadFile],
        label_column: str,
        knn_value: int,
        coral_option: bool,
        top_k: int = 3,
        auto_tune_k: bool = False,
        decision_threshold: Optional[float] = None,
        threshold_beta: float = 2.0,
        classifier_type: str = "knn",
        svm_c: float = 1.0,
        auto_tune_svm_c: bool = False,
    ) -> Dict:
        """Predict defects for a target project whose labels may be absent."""
        selected_classifier = self._normalize_classifier_type(classifier_type)
        self._validate_model_options(
            classifier_type=selected_classifier,
            knn_value=knn_value,
            svm_c=svm_c,
            top_k=top_k,
            decision_threshold=decision_threshold,
            threshold_beta=threshold_beta,
        )
        self._validate_dataset_formats(target_file, source_files)

        target_df = self._read_dataset(target_file)
        label_column = label_column.strip().lower()
        feature_cols = self._feature_columns(
            target_df,
            label_column,
            target_has_label=False,
        )
        X_target = self._numeric_features(target_df, feature_cols, "target")

        selected_sources, ranking = self._rank_and_select_sources(
            source_files,
            label_column,
            feature_cols,
            X_target,
            top_k,
        )
        model_data = self._build_model_data(
            selected_sources,
            X_target,
            coral_option,
        )
        prediction_output = self._fit_and_predict(
            model_data=model_data,
            classifier_type=selected_classifier,
            requested_k=knn_value,
            requested_svm_c=svm_c,
            auto_tune_k=auto_tune_k,
            auto_tune_svm_c=auto_tune_svm_c,
            decision_threshold=decision_threshold,
            threshold_beta=threshold_beta,
        )

        classifier = prediction_output["classifier"]
        predictions = prediction_output["predictions"]
        risk_scores = prediction_output["riskScores"]
        selected_threshold = prediction_output["decisionThreshold"]

        neighbor_distances = None
        neighbor_indices = None
        if selected_classifier == "knn":
            selected_k = int(prediction_output["selectedK"])
            neighbor_distances, neighbor_indices = classifier.kneighbors(
                model_data["X_target_model"],
                n_neighbors=min(selected_k, len(model_data["y_source"])),
            )

        results = []
        for index, (_, row) in enumerate(target_df.iterrows()):
            risk = float(risk_scores[index])
            is_buggy = bool(predictions[index])
            nearest_buggy = []
            if neighbor_indices is not None and neighbor_distances is not None:
                nearest_buggy = self._nearest_buggy_classes(
                    neighbor_indices[index],
                    neighbor_distances[index],
                    model_data,
                )

            results.append({
                "class": row.get("name", str(index)),
                "prediction": "1" if is_buggy else "0",
                "label": "Buggy" if is_buggy else "Clean",
                "isBuggy": is_buggy,
                "riskScore": risk,
                "riskPercent": round(risk * 100, 2),
                "confidence": self._confidence_label(risk, selected_threshold),
                "topRiskyMetrics": self._top_risky_metrics(
                    X_target[index],
                    feature_cols,
                    model_data["X_source_raw"],
                ),
                # KNN has natural neighbor explanations; linear SVM does not.
                "nearestBuggyClasses": nearest_buggy,
            })

        buggy_count = int(np.sum(predictions == 1))
        return {
            "status": "success",
            "method": self._method_name(selected_classifier),
            "selectedSources": ranking[:len(selected_sources)],
            "sourceRanking": ranking,
            "modelConfiguration": self._model_configuration(
                classifier_type=selected_classifier,
                selected_k=prediction_output["selectedK"],
                selected_svm_c=prediction_output["selectedSvmC"],
                decision_threshold=selected_threshold,
                coral_option=coral_option,
                auto_tune_k=auto_tune_k,
                auto_tune_svm_c=auto_tune_svm_c,
                threshold_beta=threshold_beta,
                threshold_was_tuned=decision_threshold is None,
                model_data=model_data,
            ),
            "predictions": results,
            "summary": {
                "total": len(results),
                "buggy": buggy_count,
                "clean": len(results) - buggy_count,
            },
        }

    async def evaluate(
        self,
        target_file: UploadFile,
        source_files: List[UploadFile],
        label_column: str,
        knn_value: int,
        coral_option: bool,
        top_k: int = 3,
        auto_tune_k: bool = False,
        decision_threshold: Optional[float] = None,
        threshold_beta: float = 2.0,
        classifier_type: str = "knn",
        svm_c: float = 1.0,
        auto_tune_svm_c: bool = False,
    ) -> Dict:
        """
        Evaluate on a labelled target dataset without target-label leakage.

        The target labels are read only after training and prediction and are
        used solely to calculate metrics. The same endpoint can therefore
        simulate the true unlabelled-target prediction process correctly.
        """
        selected_classifier = self._normalize_classifier_type(classifier_type)
        self._validate_model_options(
            classifier_type=selected_classifier,
            knn_value=knn_value,
            svm_c=svm_c,
            top_k=top_k,
            decision_threshold=decision_threshold,
            threshold_beta=threshold_beta,
        )
        self._validate_dataset_formats(target_file, source_files)

        target_df = self._read_dataset(target_file)
        label_column = label_column.strip().lower()
        if label_column not in target_df.columns:
            raise ValueError(
                f"Label column '{label_column}' was not found in the target dataset. "
                f"Available columns: {', '.join(target_df.columns)}"
            )

        target_df = self._remove_non_data_rows(target_df, label_column)
        feature_cols = self._feature_columns(
            target_df,
            label_column,
            target_has_label=True,
        )
        if not target_df[label_column].notna().all():
            raise ValueError("Every target row must have a label for evaluation.")

        X_target = self._numeric_features(target_df, feature_cols, "target")
        y_target = self._binary_labels(target_df[label_column], label_column)

        selected_sources, ranking = self._rank_and_select_sources(
            source_files,
            label_column,
            feature_cols,
            X_target,
            top_k,
        )
        model_data = self._build_model_data(
            selected_sources,
            X_target,
            coral_option,
        )
        prediction_output = self._fit_and_predict(
            model_data=model_data,
            classifier_type=selected_classifier,
            requested_k=knn_value,
            requested_svm_c=svm_c,
            auto_tune_k=auto_tune_k,
            auto_tune_svm_c=auto_tune_svm_c,
            decision_threshold=decision_threshold,
            threshold_beta=threshold_beta,
        )

        risk_scores = prediction_output["riskScores"]
        selected_threshold = prediction_output["decisionThreshold"]

        evaluation = self.metrics_service.evaluate(
            y_true=y_target,
            risk_scores=risk_scores,
            threshold=selected_threshold,
        )
        predictions = evaluation["predictions"]

        results = []
        for index, (_, row) in enumerate(target_df.iterrows()):
            risk = float(risk_scores[index])
            predicted_buggy = bool(predictions[index])
            actual_buggy = bool(y_target[index])
            results.append({
                "class": row.get("name", str(index)),
                "prediction": "1" if predicted_buggy else "0",
                "label": "Buggy" if predicted_buggy else "Clean",
                "isBuggy": predicted_buggy,
                "riskScore": risk,
                "riskPercent": round(risk * 100, 2),
                "confidence": self._confidence_label(risk, selected_threshold),
                "actualLabel": "Buggy" if actual_buggy else "Clean",
                "actualIsBuggy": actual_buggy,
                "correct": predicted_buggy == actual_buggy,
            })

        return {
            "status": "success",
            "method": self._method_name(selected_classifier),
            "selectedSources": ranking[:len(selected_sources)],
            "sourceRanking": ranking,
            "modelConfiguration": self._model_configuration(
                classifier_type=selected_classifier,
                selected_k=prediction_output["selectedK"],
                selected_svm_c=prediction_output["selectedSvmC"],
                decision_threshold=selected_threshold,
                coral_option=coral_option,
                auto_tune_k=auto_tune_k,
                auto_tune_svm_c=auto_tune_svm_c,
                threshold_beta=threshold_beta,
                threshold_was_tuned=decision_threshold is None,
                model_data=model_data,
            ),
            "metrics": evaluation["metrics"],
            "confusionMatrix": evaluation["confusionMatrix"],
            "predictions": results,
        }

    def _fit_and_predict(
        self,
        model_data: Dict,
        classifier_type: str,
        requested_k: int,
        requested_svm_c: float,
        auto_tune_k: bool,
        auto_tune_svm_c: bool,
        decision_threshold: Optional[float],
        threshold_beta: float,
    ) -> Dict:
        X_source = model_data["X_source_model"]
        y_source = model_data["y_source"]
        X_target = model_data["X_target_model"]

        selected_k: Optional[int] = None
        selected_svm_c: Optional[float] = None

        if classifier_type == "knn":
            selected_k = (
                self.knn.tune_k_source_cv(X_source, y_source)
                if auto_tune_k
                else int(requested_k)
            )
            if selected_k > len(y_source):
                raise ValueError(
                    f"K neighbors ({selected_k}) cannot exceed selected labelled "
                    f"source rows ({len(y_source)})."
                )

            selected_threshold = (
                self.knn.tune_threshold_source_cv(
                    X_source,
                    y_source,
                    k=selected_k,
                    beta=threshold_beta,
                )
                if decision_threshold is None
                else float(decision_threshold)
            )
            classifier = self.knn.fit(
                X_source,
                y_source,
                k=selected_k,
            )
            risk_scores = self.knn.positive_scores(classifier, X_target)
            predictions = self.knn.predictions_from_scores(
                risk_scores,
                selected_threshold,
            )
        else:
            selected_svm_c = (
                self.svm.tune_c_source_cv(X_source, y_source)
                if auto_tune_svm_c
                else float(requested_svm_c)
            )
            selected_threshold = (
                self.svm.tune_threshold_source_cv(
                    X_source,
                    y_source,
                    c_value=selected_svm_c,
                    beta=threshold_beta,
                )
                if decision_threshold is None
                else float(decision_threshold)
            )
            classifier = self.svm.fit(
                X_source,
                y_source,
                c_value=selected_svm_c,
            )
            risk_scores = self.svm.positive_scores(classifier, X_target)
            predictions = self.svm.predictions_from_scores(
                risk_scores,
                selected_threshold,
            )

        return {
            "classifier": classifier,
            "riskScores": risk_scores,
            "predictions": predictions,
            "selectedK": selected_k,
            "selectedSvmC": selected_svm_c,
            "decisionThreshold": float(selected_threshold),
        }
    def _rank_and_select_sources(
        self,
        source_files: List[UploadFile],
        label_column: str,
        feature_cols: List[str],
        X_target: np.ndarray,
        top_k: int,
    ) -> Tuple[List[Dict], List[Dict]]:
        """
        Rank sources using covariance distance to the unlabelled target.

        This source-ranking step is a project-specific extension and is not part
        of the original CORAL algorithm.
        """
        candidates = []

        for source_file in source_files:
            source_df = self._read_dataset(source_file)
            filename = source_file.filename or "source.csv"

            if label_column not in source_df.columns:
                raise ValueError(
                    f"Label column '{label_column}' was not found in {filename}. "
                    f"Available columns: {', '.join(source_df.columns)}"
                )

            missing_features = [
                column for column in feature_cols
                if column not in source_df.columns
            ]
            if missing_features:
                raise ValueError(
                    f"Source dataset '{filename}' does not match the target schema. "
                    f"Missing metric columns: {', '.join(missing_features)}"
                )

            labelled_rows = source_df[label_column].notna()
            X_source = self._numeric_features(
                source_df.loc[labelled_rows],
                feature_cols,
                filename,
            )
            y_source = self._binary_labels(
                source_df.loc[labelled_rows, label_column],
                label_column,
            )
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
                "classNames": (
                    source_df.loc[labelled_rows, "name"].astype(str).tolist()
                    if "name" in source_df.columns
                    else [f"{filename}#{i}" for i in range(len(y_source))]
                ),
            })

        if not candidates:
            raise ValueError(
                "No labelled source rows were found in the uploaded source datasets."
            )

        candidates.sort(key=lambda candidate: candidate["distance"])
        selected_count = min(top_k, len(candidates))
        ranking = [
            {
                "rank": index + 1,
                "dataset": candidate["dataset"],
                "distance": round(float(candidate["distance"]), 6),
                "rows": candidate["rows"],
                "buggyRows": candidate["buggyRows"],
                "cleanRows": candidate["cleanRows"],
                "selected": index < selected_count,
            }
            for index, candidate in enumerate(candidates)
        ]
        return candidates[:selected_count], ranking

    def _build_model_data(
        self,
        selected_sources: List[Dict],
        X_target: np.ndarray,
        coral_option: bool,
    ) -> Dict:
        X_source_raw = np.vstack([source["X"] for source in selected_sources])
        y_source = np.concatenate([source["y"] for source in selected_sources])
        source_class_names = sum(
            (source["classNames"] for source in selected_sources),
            [],
        )
        source_dataset_names = sum(
            (
                [source["dataset"]] * len(source["y"])
                for source in selected_sources
            ),
            [],
        )

        # The target is standardized with target-domain feature statistics.
        # Target labels are not used.
        X_target_model = self.preprocessing.standardize_domain(X_target)

        source_model_parts = []
        for source in selected_sources:
            # Each source is independently standardized before being aligned to
            # the same standardized target domain.
            X_source_model = self.preprocessing.standardize_domain(source["X"])

            if coral_option:
                X_source_model = self.coral.align(
                    X_source_model,
                    X_target_model,
                )

            source_model_parts.append(X_source_model)

        X_source_model = np.vstack(source_model_parts)

        unique_source_labels = set(np.unique(y_source).tolist())
        if unique_source_labels != {0, 1}:
            raise ValueError(
                "The selected source datasets must jointly contain both clean "
                "and buggy rows."
            )

        source_rows = [int(len(source["y"])) for source in selected_sources]
        total_source_rows = int(sum(source_rows))
        largest_source_share = (
            max(source_rows) / total_source_rows
            if total_source_rows > 0
            else 0.0
        )

        return {
            "X_source_raw": X_source_raw,
            "X_source_model": X_source_model,
            "X_target_model": X_target_model,
            # Backward-compatible alias used by older code/frontends.
            "X_target_scaled": X_target_model,
            "y_source": y_source,
            "sourceClassNames": source_class_names,
            "sourceDatasetNames": source_dataset_names,
            "largestSourceShare": float(largest_source_share),
            "sourceDominanceWarning": (
                len(selected_sources) > 1 and largest_source_share > 0.75
            ),
        }

    @staticmethod
    def _model_configuration(
        classifier_type: str,
        selected_k: Optional[int],
        selected_svm_c: Optional[float],
        decision_threshold: float,
        coral_option: bool,
        auto_tune_k: bool,
        auto_tune_svm_c: bool,
        threshold_beta: float,
        threshold_was_tuned: bool,
        model_data: Dict,
    ) -> Dict:
        is_knn = classifier_type == "knn"
        classifier_name = (
            "distance-weighted Euclidean KNN"
            if is_knn
            else "class-weighted linear SVM"
        )
        risk_meaning = (
            "distance-weighted KNN vote for the buggy class; not guaranteed "
            "to be a calibrated probability"
            if is_knn
            else "Platt-scaled linear SVM estimate for the buggy class; "
            "cross-project calibration may still be imperfect"
        )

        return {
            "classifierType": classifier_type,
            "classifier": classifier_name,
            "selectedK": int(selected_k) if selected_k is not None else None,
            "autoTuneK": bool(auto_tune_k) if is_knn else False,
            "selectedSvmC": (
                float(selected_svm_c)
                if selected_svm_c is not None
                else None
            ),
            "autoTuneSvmC": bool(auto_tune_svm_c) if not is_knn else False,
            "classWeight": "balanced" if not is_knn else None,
            "decisionThreshold": float(decision_threshold),
            "thresholdSelection": (
                f"source-only stratified CV maximizing F{threshold_beta:g}"
                if threshold_was_tuned
                else "explicit user-provided threshold"
            ),
            "thresholdBeta": float(threshold_beta),
            "coralEnabled": bool(coral_option),
            "coralType": "shallow/linear CORAL",
            "targetLabelUsage": (
                "not used for training, adaptation, hyperparameter tuning, or "
                "threshold selection"
            ),
            "riskScoreMeaning": risk_meaning,
            "sourceRankingMethod": (
                "custom covariance-distance ranking using unlabelled target features"
            ),
            "sourceDominanceWarning": bool(
                model_data["sourceDominanceWarning"]
            ),
            "largestSourceRowShare": float(
                model_data["largestSourceShare"]
            ),
        }

    @classmethod
    def _validate_model_options(
        cls,
        classifier_type: str,
        knn_value: int,
        svm_c: float,
        top_k: int,
        decision_threshold: Optional[float],
        threshold_beta: float,
    ) -> None:
        if classifier_type not in cls.SUPPORTED_CLASSIFIERS:
            raise ValueError("Classifier must be either 'knn' or 'svm'.")
        if classifier_type == "knn" and knn_value < 1:
            raise ValueError("K neighbors must be at least 1.")
        if classifier_type == "svm" and svm_c <= 0:
            raise ValueError("SVM C must be greater than zero.")
        if top_k < 1:
            raise ValueError("Top-K source count must be at least 1.")
        if decision_threshold is not None and not 0.0 <= decision_threshold <= 1.0:
            raise ValueError("Decision threshold must be between 0 and 1.")
        if threshold_beta <= 0:
            raise ValueError("Threshold F-beta value must be greater than zero.")

    @classmethod
    def _normalize_classifier_type(cls, value: str) -> str:
        normalized = str(value or "knn").strip().lower().replace("-", "_")
        aliases = {
            "knn": "knn",
            "k_nearest_neighbors": "knn",
            "knearestneighbors": "knn",
            "svm": "svm",
            "linear_svm": "svm",
            "linearsvm": "svm",
        }
        if normalized not in aliases:
            raise ValueError(
                "Unsupported classifier. Use 'knn' or 'svm' (linear SVM)."
            )
        return aliases[normalized]

    @staticmethod
    def _method_name(classifier_type: str) -> str:
        classifier_name = "KNN" if classifier_type == "knn" else "Linear SVM"
        return (
            "Similarity-Based Multi-Source Shallow CORAL "
            f"{classifier_name} Defect Prediction"
        )

    def _read_dataset(self, upload: UploadFile) -> pd.DataFrame:
        upload.file.seek(0)
        filename = (upload.filename or "").lower()
        contents = upload.file.read()
        upload.file.seek(0)

        text = (
            contents.decode("utf-8-sig")
            if isinstance(contents, bytes)
            else str(contents)
        )
        is_arff = filename.endswith(".arff") or self._looks_like_arff(text)
        dataframe = self._read_arff(text) if is_arff else pd.read_csv(StringIO(text))
        dataframe.columns = self._normalize_columns(dataframe.columns)
        return dataframe

    # Backward-compatible name used by older application code.
    def _read_csv(self, upload: UploadFile) -> pd.DataFrame:
        return self._read_dataset(upload)

    @classmethod
    def _validate_dataset_formats(
        cls,
        target_file: UploadFile,
        source_files: List[UploadFile],
    ) -> None:
        if not source_files:
            raise ValueError("At least one labelled source dataset is required.")

        target_format = cls._dataset_file_format(target_file)
        source_formats = {
            cls._dataset_file_format(source)
            for source in source_files
        }

        if len(source_formats) != 1:
            raise ValueError(
                "All source datasets must use one format; do not mix CSV and ARFF files."
            )
        if target_format not in source_formats:
            raise ValueError(
                "Source and target dataset formats must match: use CSV with CSV "
                "or ARFF with ARFF."
            )

    @classmethod
    def _dataset_file_format(cls, upload: UploadFile) -> str:
        filename = (upload.filename or "").strip().lower()
        if not filename.endswith((".csv", ".arff")):
            raise ValueError("Dataset files must use the .csv or .arff extension.")

        declared_format = "arff" if filename.endswith(".arff") else "csv"
        upload.file.seek(0)
        contents = upload.file.read()
        upload.file.seek(0)

        text = (
            contents.decode("utf-8-sig")
            if isinstance(contents, bytes)
            else str(contents)
        )
        content_format = "arff" if cls._looks_like_arff(text) else "csv"

        if declared_format != content_format:
            raise ValueError(
                f"Dataset extension does not match its content: "
                f"{upload.filename} is {content_format.upper()} data."
            )

        return declared_format

    @staticmethod
    def _looks_like_arff(text: str) -> bool:
        directives = {
            line.strip().split(maxsplit=1)[0].lower()
            for line in text.splitlines()
            if line.strip().startswith("@")
        }
        return "@attribute" in directives and "@data" in directives

    @staticmethod
    def _read_arff(text: str) -> pd.DataFrame:
        attributes = []
        data_lines = []
        in_data = False
        attribute_pattern = re.compile(
            r"^@attribute\s+(?:'([^']+)'|\"([^\"]+)\"|(\S+))\s+.+$",
            re.IGNORECASE,
        )

        for raw_line in text.splitlines():
            line = raw_line.strip()
            if not line or line.startswith(("%", "#")):
                continue
            if line.lower() == "@data":
                in_data = True
                continue
            if not in_data:
                match = attribute_pattern.match(line)
                if match:
                    attributes.append(
                        next(
                            value
                            for value in match.groups()
                            if value is not None
                        )
                    )
            else:
                data_lines.append(line)

        if not attributes or not data_lines:
            raise ValueError(
                "The ARFF file must contain @attribute declarations and an @data section."
            )

        dataframe = pd.read_csv(
            StringIO("\n".join(data_lines)),
            names=attributes,
            header=None,
            na_values=["?"],
            quotechar="'",
            escapechar="\\",
        )
        if dataframe.shape[1] != len(attributes):
            raise ValueError(
                "The ARFF data rows do not match the declared attributes."
            )

        generic_attributes = all(
            re.fullmatch(r"attribute_\d+", name.lower())
            for name in attributes
        )
        if generic_attributes and not dataframe.empty:
            recovered_headers = [
                str(value).strip()
                for value in dataframe.iloc[0].tolist()
            ]
            normalized_headers = [
                value.lower()
                for value in recovered_headers
            ]
            if (
                "class" in normalized_headers
                and len(set(normalized_headers)) == len(normalized_headers)
            ):
                dataframe = dataframe.iloc[1:].copy()
                dataframe.columns = recovered_headers

        return dataframe

    def _feature_columns(
        self,
        dataframe: pd.DataFrame,
        label_column: str,
        target_has_label: bool,
    ) -> List[str]:
        del target_has_label  # Kept in the signature for backward compatibility.

        ignored = {
            "name", "id", "index", "row_id", "rowid", label_column
        }
        feature_cols = [
            column
            for column in dataframe.columns
            if column not in ignored
        ]
        promise_cols = [
            column
            for column in self.PROMISE_FEATURES
            if column in feature_cols
        ]

        selected = (
            promise_cols
            if len(promise_cols) == len(self.PROMISE_FEATURES)
            else feature_cols
        )
        if not selected:
            raise ValueError(
                "The target dataset does not contain any metric columns."
            )

        return selected

    @staticmethod
    def _remove_non_data_rows(
        dataframe: pd.DataFrame,
        label_column: str,
    ) -> pd.DataFrame:
        labelled = dataframe[label_column].notna()
        if labelled.all():
            return dataframe.reset_index(drop=True)

        ignored = {
            "name", "id", "index", "row_id", "rowid", label_column
        }
        feature_columns = [
            column
            for column in dataframe.columns
            if column not in ignored
        ]
        unlabelled = dataframe.loc[~labelled, feature_columns]
        contains_numeric_data = unlabelled.apply(
            lambda column: pd.to_numeric(column, errors="coerce").notna()
        ).any(axis=1)

        if contains_numeric_data.any():
            raise ValueError(
                "Every target data row must have a label for evaluation."
            )

        cleaned = dataframe.loc[labelled].copy().reset_index(drop=True)
        if cleaned.empty:
            raise ValueError(
                "The labelled target dataset does not contain any data rows."
            )

        return cleaned

    @staticmethod
    def _covariance_distance(
        X_source: np.ndarray,
        X_target: np.ndarray,
    ) -> float:
        source_standardized = PredictionService._standardize(X_source)
        target_standardized = PredictionService._standardize(X_target)

        return float(
            np.linalg.norm(
                PredictionService._covariance(source_standardized)
                - PredictionService._covariance(target_standardized),
                ord="fro",
            )
        )

    @staticmethod
    def _standardize(values: np.ndarray) -> np.ndarray:
        matrix = np.asarray(values, dtype=np.float64)
        mean = matrix.mean(axis=0)
        std = matrix.std(axis=0)
        std[std == 0] = 1.0
        return (matrix - mean) / std

    @staticmethod
    def _covariance(values: np.ndarray) -> np.ndarray:
        matrix = np.asarray(values, dtype=np.float64)
        if matrix.shape[0] < 2:
            return np.zeros((matrix.shape[1], matrix.shape[1]), dtype=np.float64)

        covariance = np.cov(matrix, rowvar=False, ddof=1)
        covariance = np.atleast_2d(np.asarray(covariance, dtype=np.float64))
        covariance = np.nan_to_num(
            covariance,
            nan=0.0,
            posinf=0.0,
            neginf=0.0,
        )
        return 0.5 * (covariance + covariance.T)

    @staticmethod
    def _confidence_label(
        risk_score: float,
        decision_threshold: float,
    ) -> str:
        margin = abs(risk_score - decision_threshold)
        if margin >= 0.25:
            return "High"
        if margin >= 0.10:
            return "Medium"
        return "Low"

    @staticmethod
    def _top_risky_metrics(
        target_row: np.ndarray,
        feature_cols: List[str],
        X_source_raw: np.ndarray,
    ) -> List[Dict]:
        """
        Return target metrics unusually high relative to pooled sources.

        These z-scores are descriptive comparisons, not KNN feature importance.
        """
        mean = X_source_raw.mean(axis=0)
        std = X_source_raw.std(axis=0)
        std[std == 0] = 1.0
        z_scores = (target_row - mean) / std

        ranked = sorted(
            [
                (index, z_scores[index])
                for index in range(len(feature_cols))
                if z_scores[index] > 0
            ],
            key=lambda item: item[1],
            reverse=True,
        )[:4]

        return [
            {
                "metric": feature_cols[index].upper(),
                "value": float(target_row[index]),
                "sourceMean": round(float(mean[index]), 3),
                "zScore": round(float(score), 3),
                "severity": "High" if score >= 2 else "Moderate",
                "meaning": "unusually high relative to selected source data",
            }
            for index, score in ranked
        ]

    @staticmethod
    def _nearest_buggy_classes(
        indices: np.ndarray,
        distances: np.ndarray,
        model_data: Dict,
    ) -> List[Dict]:
        examples = []
        y_source = model_data["y_source"]

        for source_index, distance in zip(indices, distances):
            if int(y_source[source_index]) != 1:
                continue

            examples.append({
                "class": model_data["sourceClassNames"][source_index],
                "dataset": model_data["sourceDatasetNames"][source_index],
                "distance": round(float(distance), 4),
            })
            if len(examples) == 3:
                break

        return examples

    @staticmethod
    def _normalize_columns(columns) -> List[str]:
        normalized = [
            str(column).strip().lower()
            for column in columns
        ]
        duplicates = sorted(
            {
                column
                for column in normalized
                if normalized.count(column) > 1
            }
        )
        if duplicates:
            raise ValueError(
                f"Dataset contains duplicate columns: {', '.join(duplicates)}"
            )

        return normalized

    @staticmethod
    def _binary_labels(
        labels: pd.Series,
        label_column: str,
    ) -> np.ndarray:
        numeric = pd.to_numeric(labels, errors="coerce")
        if numeric.notna().all():
            if (numeric < 0).any():
                raise ValueError(
                    f"Label column '{label_column}' cannot contain negative "
                    "defect counts."
                )
            return (numeric > 0).astype(int).to_numpy()

        normalized = labels.astype(str).str.strip().str.lower()
        mapping = {
            "clean": 0,
            "false": 0,
            "no": 0,
            "n": 0,
            "buggy": 1,
            "defective": 1,
            "true": 1,
            "yes": 1,
            "y": 1,
        }
        unknown = sorted(set(normalized) - set(mapping))
        if unknown:
            raise ValueError(
                f"Label column '{label_column}' must contain defect counts or "
                f"clean/buggy values. Unsupported values: {', '.join(unknown[:5])}"
            )

        return normalized.map(mapping).to_numpy(dtype=int)

    @staticmethod
    def _numeric_features(
        dataframe: pd.DataFrame,
        columns: List[str],
        dataset_name: str,
    ) -> np.ndarray:
        numeric = dataframe[columns].apply(pd.to_numeric, errors="coerce")
        invalid_columns = [
            column
            for column in columns
            if numeric[column].isna().any()
            and dataframe[column].notna().any()
        ]
        if invalid_columns:
            raise ValueError(
                f"The {dataset_name} dataset contains nonnumeric or missing "
                f"metric values in: {', '.join(invalid_columns)}"
            )

        values = numeric.fillna(0).to_numpy(dtype=np.float64)
        if not np.isfinite(values).all():
            raise ValueError(
                f"The {dataset_name} dataset contains infinite metric values."
            )

        return values
