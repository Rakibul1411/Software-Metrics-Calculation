from __future__ import annotations

import math
from typing import Any

import numpy as np
from sklearn.model_selection import RepeatedStratifiedKFold

from app.services.shallow_coral_service import ShallowCoralService
from app.services.knn_service import KnnService
from app.services.metrics_service import MetricsService
from app.services.preprocessing_service import PreprocessingService
from app.services.svm_service import SvmService


class ProjectValidationService:
    """
    Leakage-safe K/C and decision-threshold selection.

    Multiple labelled source projects use Leave-One-Source-Project-Out (LOSO)
    validation. A single source project uses repeated stratified validation,
    which tunes the model but must not be reported as cross-project validation.
    """

    def __init__(
        self,
        coral: ShallowCoralService | None = None,
        preprocessing: PreprocessingService | None = None,
        knn: KnnService | None = None,
        svm: SvmService | None = None,
        metrics: MetricsService | None = None,
        random_state: int = 42,
        single_source_repeats: int = 3,
    ) -> None:
        self.coral = coral or ShallowCoralService()
        self.preprocessing = preprocessing or PreprocessingService()
        self.knn = knn or KnnService()
        self.svm = svm or SvmService()
        self.metrics = metrics or MetricsService()
        self.random_state = int(random_state)
        self.single_source_repeats = int(single_source_repeats)

        if self.single_source_repeats < 1:
            raise ValueError("Single-source CV repeats must be at least 1.")

    def select(
        self,
        source_projects: list[dict],
        classifier: str,
        top_k: int,
        coral_enabled: bool,
        threshold_beta: float = 2.0,
    ) -> dict:
        """Select K or C and a false-alarm-aware MCC threshold."""
        normalized_classifier = str(classifier).strip().lower()
        if normalized_classifier not in {"knn", "svm"}:
            raise ValueError("Classifier must be either 'knn' or 'svm'.")
        if not source_projects:
            raise ValueError("At least one labelled source project is required.")
        if int(top_k) < 1:
            raise ValueError("Top-K source count must be at least 1.")
        if float(threshold_beta) <= 0:
            raise ValueError("Threshold F-beta value must be greater than zero.")

        projects = sorted(
            (self._validated_project(project) for project in source_projects),
            key=lambda project: project["dataset"].strip().lower(),
        )
        warnings: list[str] = []

        if len(projects) >= 2:
            strategy = "leave-one-source-project-out"
            folds = self._build_project_folds(
                projects,
                top_k=int(top_k),
                coral_enabled=bool(coral_enabled),
            )
            if len(projects) == 2:
                warnings.append(
                    "Only two source projects were supplied. LOSO is valid, but "
                    "the two project-level folds can have high variance."
                )
        else:
            strategy = "source-internal-repeated-stratified-cross-validation"
            folds = self._build_single_project_folds(
                projects[0],
                coral_enabled=bool(coral_enabled),
                warnings=warnings,
            )

        if not folds:
            fallback = self._fallback_selection(
                project=projects[0],
                classifier=normalized_classifier,
                strategy=strategy,
                warnings=warnings,
            )
            return fallback

        if normalized_classifier == "knn":
            candidate_results, outputs_by_candidate, candidates = (
                self._evaluate_knn_candidates(
                    projects=projects,
                    folds=folds,
                    top_k=int(top_k),
                )
            )
        else:
            candidate_results, outputs_by_candidate, candidates = (
                self._evaluate_svm_candidates(folds)
            )

        parameter_name = "k" if normalized_classifier == "knn" else "c"
        selected_result = self._select_candidate(
            candidate_results,
            parameter_name=parameter_name,
        )
        selected_value = selected_result[parameter_name]
        selected_outputs = outputs_by_candidate[float(selected_value)]

        decision_threshold, threshold_results = self.metrics.select_threshold(
            selected_outputs,
            beta=float(threshold_beta),
        )

        fold_results = self._build_fold_results(
            folds=folds,
            outputs=selected_outputs,
            threshold=decision_threshold,
            beta=float(threshold_beta),
        )
        aggregate_metrics = self._aggregate_fold_results(fold_results)

        response = {
            "strategy": strategy,
            "sourceProjectCount": len(projects),
            "foldCount": len(folds),
            "selectedClassifier": normalized_classifier,
            "selectedK": int(selected_value) if normalized_classifier == "knn" else None,
            "selectedC": float(selected_value) if normalized_classifier == "svm" else None,
            "decisionThreshold": float(decision_threshold),
            "hyperparameterSelectionMetric": "macro Average Precision",
            "hyperparameterTieBreakers": [
                "macro MCC",
                "macro recall",
                f"smaller {parameter_name.upper()}",
            ],
            "thresholdSelectionMetric": "macro MCC",
            "thresholdTieBreakers": [
                "macro balanced accuracy",
                "macro F1",
                "macro precision",
                "threshold closest to 0.5",
            ],
            "candidateValues": candidates,
            "candidateResults": candidate_results,
            "thresholdCandidateResults": threshold_results,
            "aggregateMetrics": aggregate_metrics,
            "foldResults": fold_results,
            "warnings": warnings,
        }
        return response

    def _evaluate_knn_candidates(
        self,
        projects: list[dict],
        folds: list[dict],
        top_k: int,
    ) -> tuple[list[dict], dict[float, list[dict]], list[int]]:
        minimum_fold_rows = min(len(fold["y_train"]) for fold in folds)

        # The final target may rank any source combination. This conservative
        # bound guarantees the selected K also fits the smallest possible final
        # top-K source combination.
        selected_count = min(int(top_k), len(projects))
        minimum_final_rows = sum(
            sorted(int(len(project["y"])) for project in projects)[:selected_count]
        )
        safe_minimum_rows = min(minimum_fold_rows, minimum_final_rows)
        candidates = self.knn.build_dynamic_k_candidates(safe_minimum_rows)

        candidate_results: list[dict] = []
        outputs_by_candidate: dict[float, list[dict]] = {}
        for candidate in candidates:
            outputs = [
                self._predict_fold(fold, "knn", candidate)
                for fold in folds
            ]
            outputs_by_candidate[float(candidate)] = outputs
            candidate_results.append(
                self._candidate_summary(
                    outputs=outputs,
                    parameter_name="k",
                    parameter_value=int(candidate),
                    stage="dynamic-odd-square-root-grid",
                )
            )

        return candidate_results, outputs_by_candidate, candidates

    def _evaluate_svm_candidates(
        self,
        folds: list[dict],
    ) -> tuple[list[dict], dict[float, list[dict]], list[float]]:
        candidate_results: list[dict] = []
        outputs_by_candidate: dict[float, list[dict]] = {}

        coarse_candidates = self.svm.coarse_c_candidates()
        for candidate in coarse_candidates:
            outputs = [
                self._predict_fold(fold, "svm", candidate)
                for fold in folds
            ]
            outputs_by_candidate[float(candidate)] = outputs
            candidate_results.append(
                self._candidate_summary(
                    outputs=outputs,
                    parameter_name="c",
                    parameter_value=float(candidate),
                    stage="coarse-log-grid",
                )
            )

        coarse_best = self._select_candidate(candidate_results, parameter_name="c")
        refined_candidates = self.svm.refined_c_candidates(coarse_best["c"])

        for candidate in refined_candidates:
            candidate_key = float(candidate)
            if any(math.isclose(candidate_key, existing) for existing in outputs_by_candidate):
                continue
            outputs = [
                self._predict_fold(fold, "svm", candidate_key)
                for fold in folds
            ]
            outputs_by_candidate[candidate_key] = outputs
            candidate_results.append(
                self._candidate_summary(
                    outputs=outputs,
                    parameter_name="c",
                    parameter_value=candidate_key,
                    stage="refined-log-grid",
                )
            )

        all_candidates = sorted(outputs_by_candidate)
        candidate_results.sort(key=lambda item: float(item["c"]))
        return candidate_results, outputs_by_candidate, all_candidates

    def _candidate_summary(
        self,
        outputs: list[dict],
        parameter_name: str,
        parameter_value: int | float,
        stage: str,
    ) -> dict:
        fold_metrics = [
            self.metrics.validation_metrics(
                output["labels"],
                output["scores"],
                threshold=0.5,
            )
            for output in outputs
        ]
        ap_summary = self.metrics.aggregate_metric(
            item["averagePrecision"] for item in fold_metrics
        )
        mcc_summary = self.metrics.aggregate_metric(
            item["mcc"] for item in fold_metrics
        )
        recall_summary = self.metrics.aggregate_metric(
            item["recall"] for item in fold_metrics
        )

        return {
            parameter_name: parameter_value,
            "stage": stage,
            "macroAveragePrecision": ap_summary["mean"],
            "averagePrecisionStd": ap_summary["std"],
            "validAveragePrecisionFolds": ap_summary["validFoldCount"],
            "macroMcc": mcc_summary["mean"],
            "macroRecall": recall_summary["mean"],
            "foldCount": len(outputs),
        }

    @staticmethod
    def _select_candidate(
        candidate_results: list[dict],
        parameter_name: str,
    ) -> dict:
        valid = [
            result
            for result in candidate_results
            if result.get("macroAveragePrecision") is not None
        ]
        if not valid:
            raise ValueError(
                "Average Precision is undefined in every validation fold. "
                "At least one validation project/fold must contain both classes."
            )

        return max(
            valid,
            key=lambda item: (
                float(item["macroAveragePrecision"]),
                float(item["macroMcc"]),
                float(item["macroRecall"]),
                -float(item[parameter_name]),
            ),
        )

    def _build_project_folds(
        self,
        projects: list[dict],
        top_k: int,
        coral_enabled: bool,
    ) -> list[dict]:
        """Build deterministic LOSO folds with source ranking inside each fold."""
        folds: list[dict] = []
        for index, held_out in enumerate(projects):
            available_training = [
                project
                for position, project in enumerate(projects)
                if position != index
            ]
            ranked = sorted(
                available_training,
                key=lambda project: (
                    self.covariance_distance(project["X"], held_out["X"]),
                    project["dataset"].strip().lower(),
                ),
            )
            selected = ranked[: min(int(top_k), len(ranked))]
            folds.append(
                self._prepare_fold(
                    fold=index + 1,
                    repeat=None,
                    fold_within_repeat=None,
                    training_projects=selected,
                    validation_project=held_out,
                    all_training_names=[
                        item["dataset"] for item in available_training
                    ],
                    coral_enabled=coral_enabled,
                )
            )
        return folds

    def _build_single_project_folds(
        self,
        project: dict,
        coral_enabled: bool,
        warnings: list[str],
    ) -> list[dict]:
        labels = np.asarray(project["y"], dtype=int)
        _, counts = np.unique(labels, return_counts=True)
        minority_count = int(counts.min())
        if minority_count < 2:
            warnings.append(
                "Repeated stratified validation was skipped because the "
                "minority class has fewer than two rows. Safe defaults were used."
            )
            return []

        n_splits = min(5, minority_count)
        splitter = RepeatedStratifiedKFold(
            n_splits=n_splits,
            n_repeats=self.single_source_repeats,
            random_state=self.random_state,
        )
        folds: list[dict] = []
        for index, (train_indices, validation_indices) in enumerate(
            splitter.split(project["X"], labels)
        ):
            repeat = index // n_splits + 1
            fold_within_repeat = index % n_splits + 1
            training = {
                "dataset": project["dataset"],
                "X": project["X"][train_indices],
                "y": labels[train_indices],
            }
            validation = {
                "dataset": (
                    f"{project['dataset']}#repeat-{repeat}-fold-{fold_within_repeat}"
                ),
                "X": project["X"][validation_indices],
                "y": labels[validation_indices],
            }
            folds.append(
                self._prepare_fold(
                    fold=index + 1,
                    repeat=repeat,
                    fold_within_repeat=fold_within_repeat,
                    training_projects=[training],
                    validation_project=validation,
                    all_training_names=[project["dataset"]],
                    coral_enabled=coral_enabled,
                )
            )

        warnings.append(
            "Only one labelled source project was uploaded. K/C and the decision "
            "threshold were tuned inside that same source using repeated stratified "
            "validation. Predictions can still be generated, but this does not "
            "measure cross-project performance. Upload at least two labelled source "
            "projects, or use Evaluate with a labelled target to report performance."
        )
        return folds

    def _prepare_fold(
        self,
        fold: int,
        repeat: int | None,
        fold_within_repeat: int | None,
        training_projects: list[dict],
        validation_project: dict,
        all_training_names: list[str],
        coral_enabled: bool,
    ) -> dict:
        # Only pseudo-target features are passed into preprocessing/CORAL.
        validation_model = self.preprocessing.standardize_domain(
            validation_project["X"]
        )

        training_parts: list[np.ndarray] = []
        training_labels: list[np.ndarray] = []
        for project in training_projects:
            source_model = self.preprocessing.standardize_domain(project["X"])
            if coral_enabled:
                source_model = self.coral.align(source_model, validation_model)
            training_parts.append(source_model)
            training_labels.append(np.asarray(project["y"], dtype=int))

        y_train = np.concatenate(training_labels)
        if set(np.unique(y_train).tolist()) != {0, 1}:
            raise ValueError(
                "Every validation training fold must jointly contain both clean "
                "and buggy rows."
            )

        # The pseudo-target labels are attached only after feature adaptation is
        # complete and are consumed only by metric calculation.
        y_validation = np.asarray(validation_project["y"], dtype=int)
        return {
            "fold": int(fold),
            "repeat": repeat,
            "fold_within_repeat": fold_within_repeat,
            "held_out_project": validation_project["dataset"],
            "training_projects": all_training_names,
            "selected_training_projects": [
                project["dataset"] for project in training_projects
            ],
            "X_train": np.vstack(training_parts),
            "y_train": y_train,
            "X_validation": validation_model,
            "y_validation": y_validation,
        }

    def _predict_fold(
        self,
        fold: dict,
        classifier: str,
        candidate: int | float,
    ) -> dict:
        if classifier == "knn":
            model = self.knn.fit(
                fold["X_train"],
                fold["y_train"],
                k=int(candidate),
            )
            scores = self.knn.positive_scores(model, fold["X_validation"])
        else:
            model = self.svm.fit(
                fold["X_train"],
                fold["y_train"],
                c_value=float(candidate),
            )
            scores = self.svm.positive_scores(model, fold["X_validation"])

        return {
            "labels": fold["y_validation"],
            "scores": scores,
        }

    def _build_fold_results(
        self,
        folds: list[dict],
        outputs: list[dict],
        threshold: float,
        beta: float,
    ) -> list[dict]:
        fold_results: list[dict] = []
        for fold, output in zip(folds, outputs):
            evaluation = self.metrics.evaluate(
                y_true=output["labels"],
                risk_scores=output["scores"],
                threshold=float(threshold),
                beta=float(beta),
            )
            fold_results.append({
                "fold": fold["fold"],
                "repeat": fold["repeat"],
                "foldWithinRepeat": fold["fold_within_repeat"],
                "heldOutProject": fold["held_out_project"],
                "trainingProjects": fold["training_projects"],
                "selectedTrainingProjects": fold["selected_training_projects"],
                "trainingRows": int(len(fold["y_train"])),
                "validationRows": int(len(fold["y_validation"])),
                "metrics": evaluation["metrics"],
                "confusionMatrix": evaluation["confusionMatrix"],
            })
        return fold_results

    def _aggregate_fold_results(self, fold_results: list[dict]) -> dict:
        metric_names = (
            "accuracy",
            "balancedAccuracy",
            "precision",
            "recall",
            "specificity",
            "f1",
            "f2",
            "mcc",
            "gMean",
            "rocAuc",
            "prAuc",
            "averagePrecision",
        )
        return {
            metric_name: self.metrics.aggregate_metric(
                fold["metrics"].get(metric_name) for fold in fold_results
            )
            for metric_name in metric_names
        }

    def _fallback_selection(
        self,
        project: dict,
        classifier: str,
        strategy: str,
        warnings: list[str],
    ) -> dict:
        selected_k = (
            self.knn.safe_default_k(len(project["y"]))
            if classifier == "knn"
            else None
        )
        selected_c = 1.0 if classifier == "svm" else None
        return {
            "strategy": strategy,
            "sourceProjectCount": 1,
            "foldCount": 0,
            "selectedClassifier": classifier,
            "selectedK": selected_k,
            "selectedC": selected_c,
            "decisionThreshold": 0.5,
            "hyperparameterSelectionMetric": None,
            "thresholdSelectionMetric": None,
            "candidateValues": [],
            "candidateResults": [],
            "thresholdCandidateResults": [],
            "aggregateMetrics": {},
            "foldResults": [],
            "warnings": warnings,
        }

    @staticmethod
    def _validated_project(project: dict[str, Any]) -> dict:
        required = {"dataset", "X", "y"}
        missing = required - set(project)
        if missing:
            raise ValueError(
                f"Source project record is missing: {', '.join(sorted(missing))}."
            )

        X = np.asarray(project["X"], dtype=np.float64)
        y = np.asarray(project["y"], dtype=int)
        if X.ndim != 2 or y.ndim != 1 or len(X) != len(y) or len(y) == 0:
            raise ValueError(
                f"Source project '{project['dataset']}' has invalid feature/label shapes."
            )
        if not np.isfinite(X).all():
            raise ValueError(
                f"Source project '{project['dataset']}' contains non-finite features."
            )
        unique_labels = set(np.unique(y).tolist())
        if unique_labels != {0, 1}:
            raise ValueError(
                f"Source project '{project['dataset']}' must contain both clean "
                "and buggy rows for leakage-safe validation."
            )

        validated = dict(project)
        validated["X"] = X
        validated["y"] = y
        return validated

    @staticmethod
    def covariance_distance(X_source: np.ndarray, X_target: np.ndarray) -> float:
        source = PreprocessingService.standardize_domain(X_source)
        target = PreprocessingService.standardize_domain(X_target)
        return float(
            np.linalg.norm(
                ProjectValidationService._covariance(source)
                - ProjectValidationService._covariance(target),
                ord="fro",
            )
        )

    @staticmethod
    def _covariance(values: np.ndarray) -> np.ndarray:
        matrix = np.asarray(values, dtype=np.float64)
        if len(matrix) < 2:
            return np.zeros((matrix.shape[1], matrix.shape[1]), dtype=np.float64)

        covariance = np.atleast_2d(np.cov(matrix, rowvar=False, ddof=1))
        covariance = np.nan_to_num(
            covariance,
            nan=0.0,
            posinf=0.0,
            neginf=0.0,
        )
        return 0.5 * (covariance + covariance.T)
