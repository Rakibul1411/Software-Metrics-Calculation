import asyncio
import unittest
from io import BytesIO
from unittest.mock import patch

import numpy as np
from fastapi import HTTPException, UploadFile

from app.api.prediction_routes import run_prediction
from app.services.metrics_service import MetricsService
from app.services.prediction_service import PredictionService
from app.services.project_validation_service import ProjectValidationService


def project(name: str, offset: float = 0.0, rows: int = 8) -> dict:
    clean_count = rows // 2
    buggy_count = rows - clean_count
    clean = np.column_stack((
        np.arange(clean_count, dtype=float) + offset,
        np.arange(clean_count, dtype=float) * 0.5 + offset,
    ))
    buggy = np.column_stack((
        np.arange(buggy_count, dtype=float) + 8 + offset,
        np.arange(buggy_count, dtype=float) * 0.5 + 7 + offset,
    ))
    return {
        "dataset": name,
        "X": np.vstack((clean, buggy)),
        "y": np.array([0] * clean_count + [1] * buggy_count),
    }


def upload(filename: str, content: str) -> UploadFile:
    return UploadFile(filename=filename, file=BytesIO(content.encode()))


SOURCE_CSV = (
    "name,m1,m2,bug\n"
    "S1,0,0,0\nS2,1,1,0\nS3,2,2,0\nS4,3,3,0\n"
    "S5,8,8,1\nS6,9,9,1\nS7,10,10,1\nS8,11,11,1\n"
)


class ProjectValidationServiceTest(unittest.TestCase):
    def setUp(self):
        self.service = ProjectValidationService()

    def test_three_projects_produce_three_loso_folds(self):
        result = self.service.select(
            [project("ant"), project("camel", 1), project("ivy", 2)],
            "knn", top_k=2, coral_enabled=False,
        )
        self.assertEqual("leave-one-source-project-out", result["strategy"])
        self.assertEqual(3, len(result["foldResults"]))

    def test_two_projects_produce_two_loso_folds(self):
        result = self.service.select(
            [project("ant"), project("camel", 1)],
            "knn", top_k=1, coral_enabled=False,
        )
        self.assertEqual(2, len(result["foldResults"]))

    def test_one_project_uses_stratified_fallback(self):
        result = self.service.select(
            [project("ant")], "knn", top_k=1, coral_enabled=False,
        )
        self.assertEqual(
            "source-internal-repeated-stratified-cross-validation",
            result["strategy"],
        )
        self.assertGreaterEqual(len(result["foldResults"]), 2)

    def test_held_out_project_never_enters_classifier_training(self):
        result = self.service.select(
            [project("ant"), project("camel", 1), project("ivy", 2)],
            "knn", top_k=2, coral_enabled=False,
        )
        for fold in result["foldResults"]:
            self.assertNotIn(fold["heldOutProject"], fold["trainingProjects"])
            self.assertNotIn(
                fold["heldOutProject"], fold["selectedTrainingProjects"]
            )

    def test_knn_selects_only_k(self):
        result = self.service.select(
            [project("ant"), project("camel", 1)],
            "knn", top_k=1, coral_enabled=False,
        )
        self.assertIsInstance(result["selectedK"], int)
        self.assertIsNone(result["selectedC"])

    def test_svm_selects_only_c(self):
        result = self.service.select(
            [project("ant"), project("camel", 1)],
            "svm", top_k=1, coral_enabled=False,
        )
        self.assertIsNone(result["selectedK"])
        self.assertIn(result["selectedC"], result["candidateValues"])

    def test_invalid_k_values_are_filtered_by_smallest_training_fold(self):
        result = self.service.select(
            [project("small", rows=4)],
            "knn", top_k=1, coral_enabled=False,
        )
        self.assertEqual([1], [item["k"] for item in result["candidateResults"]])

    def test_candidate_selection_is_driven_by_average_precision(self):
        fold = {
            "fold": 1,
            "repeat": None,
            "fold_within_repeat": None,
            "held_out_project": "held",
            "training_projects": ["train"],
            "selected_training_projects": ["train"],
            "X_train": np.zeros((3, 1)),
            "y_train": np.array([0, 0, 1]),
            "X_validation": np.zeros((4, 1)),
            "y_validation": np.array([0, 1, 0, 1]),
        }

        def scores(_fold, _classifier, candidate):
            values = (
                np.array([0.1, 0.9, 0.2, 0.8])
                if candidate == 1
                else np.array([0.4, 0.3, 0.2, 0.1])
            )
            return {"labels": _fold["y_validation"], "scores": values}

        with patch.object(self.service, "_build_project_folds", return_value=[fold]), \
                patch.object(self.service, "_predict_fold", side_effect=scores):
            result = self.service.select(
                [project("a"), project("b")],
                "knn", top_k=1, coral_enabled=False,
            )
        self.assertEqual(1, result["selectedK"])
        self.assertIn("macroAveragePrecision", result["candidateResults"][0])
        self.assertNotIn("accuracy", result["candidateResults"][0])

    def test_candidate_metrics_are_project_macro_averaged(self):
        labels_a = np.array([0, 1])
        scores_a = np.array([0.1, 0.9])
        labels_b = np.array([0, 0, 0, 1, 1, 1])
        scores_b = np.array([0.8, 0.7, 0.6, 0.5, 0.4, 0.3])
        ap_a = MetricsService.validation_metrics(labels_a, scores_a)[
            "averagePrecision"
        ]
        ap_b = MetricsService.validation_metrics(labels_b, scores_b)[
            "averagePrecision"
        ]
        self.assertAlmostEqual((ap_a + ap_b) / 2, np.mean([ap_a, ap_b]))
        pooled = MetricsService.validation_metrics(
            np.concatenate([labels_a, labels_b]),
            np.concatenate([scores_a, scores_b]),
        )["averagePrecision"]
        self.assertNotAlmostEqual((ap_a + ap_b) / 2, pooled)

    def test_threshold_selection_maximizes_fold_macro_mcc(self):
        folds = [{
            "labels": np.array([0, 0, 1, 1]),
            "scores": np.array([0.1, 0.4, 0.45, 0.8]),
        }]
        threshold, candidates = MetricsService().select_threshold(folds, beta=2)
        best_mcc = max(item["macroMcc"] for item in candidates)
        chosen = next(item for item in candidates if item["threshold"] == threshold)
        self.assertEqual(best_mcc, chosen["macroMcc"])

    def test_threshold_selection_penalizes_false_alarms(self):
        folds = [{
            "labels": np.array([0, 0, 0, 0, 0, 0, 1, 1]),
            "scores": np.array([0.12, 0.14, 0.16, 0.18, 0.20, 0.22, 0.21, 0.80]),
        }]
        threshold, candidates = MetricsService().select_threshold(folds, beta=2)
        chosen = next(item for item in candidates if item["threshold"] == threshold)
        low = next(item for item in candidates if item["threshold"] == 0.1)

        self.assertGreater(chosen["macroMcc"], low["macroMcc"])
        self.assertGreater(chosen["macroSpecificity"], low["macroSpecificity"])

    def test_threshold_does_not_change_continuous_score_metrics(self):
        labels = np.array([0, 0, 1, 1])
        scores = np.array([0.1, 0.2, 0.4, 0.9])
        low = MetricsService.evaluate(labels, scores, 0.3)["metrics"]
        high = MetricsService.evaluate(labels, scores, 0.7)["metrics"]
        self.assertNotEqual(low["accuracy"], high["accuracy"])
        for metric in ("rocAuc", "prAuc", "averagePrecision"):
            self.assertEqual(low[metric], high[metric])

    def test_minority_count_below_two_uses_documented_defaults(self):
        tiny = {
            "dataset": "tiny",
            "X": np.array([[0.0], [1.0], [2.0], [9.0]]),
            "y": np.array([0, 0, 0, 1]),
        }
        result = self.service.select(
            [tiny], "knn", top_k=1, coral_enabled=False,
        )
        self.assertEqual(3, result["selectedK"])
        self.assertEqual(0.5, result["decisionThreshold"])
        self.assertTrue(result["warnings"])


class PredictionWorkflowTest(unittest.TestCase):
    def test_labelled_and_unlabelled_final_targets_work(self):
        prediction = asyncio.run(PredictionService().run(
            upload("target.csv", "name,m1,m2\nT1,1,1\nT2,9,9\n"),
            [upload("source.csv", SOURCE_CSV)],
            "bug", 5, False,
        ))
        evaluation = asyncio.run(PredictionService().evaluate(
            upload("target.csv", "name,m1,m2,bug\nT1,1,1,0\nT2,9,9,1\n"),
            [upload("source.csv", SOURCE_CSV)],
            "bug", 5, False,
        ))
        self.assertNotIn("metrics", prediction)
        self.assertIn("metrics", evaluation)

    def test_final_target_labels_are_evaluation_only(self):
        clean_labels = asyncio.run(PredictionService().evaluate(
            upload("target.csv", "name,m1,m2,bug\nT1,1,1,0\nT2,9,9,0\n"),
            [upload("source.csv", SOURCE_CSV)],
            "bug", 5, False,
        ))
        flipped_labels = asyncio.run(PredictionService().evaluate(
            upload("target.csv", "name,m1,m2,bug\nT1,1,1,1\nT2,9,9,1\n"),
            [upload("source.csv", SOURCE_CSV)],
            "bug", 5, False,
        ))
        self.assertEqual(
            clean_labels["modelSelection"], flipped_labels["modelSelection"]
        )
        self.assertEqual(
            [item["riskScore"] for item in clean_labels["predictions"]],
            [item["riskScore"] for item in flipped_labels["predictions"]],
        )

    def test_existing_request_defaults_to_knn(self):
        result = asyncio.run(PredictionService().run(
            upload("target.csv", "name,m1,m2\nT1,1,1\nT2,9,9\n"),
            [upload("source.csv", SOURCE_CSV)],
            "bug", 5, False,
        ))
        self.assertEqual("knn", result["modelConfiguration"]["classifier"])
        self.assertIsNotNone(result["modelSelection"]["selectedK"])
        self.assertIsNone(result["modelSelection"]["selectedC"])

    def test_svm_prediction_returns_c_without_k(self):
        result = asyncio.run(PredictionService().run(
            upload("target.csv", "name,m1,m2\nT1,1,1\nT2,9,9\n"),
            [upload("source.csv", SOURCE_CSV)],
            "bug", 5, False, classifier_type="svm",
        ))
        self.assertEqual("linear-svm", result["modelConfiguration"]["classifier"])
        self.assertIsNone(result["modelSelection"]["selectedK"])
        self.assertIsNotNone(result["modelSelection"]["selectedC"])

    def test_unsupported_classifier_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Unsupported classifier"):
            asyncio.run(PredictionService().run(
                upload("target.csv", "name,m1,m2\nT1,1,1\nT2,9,9\n"),
                [upload("source.csv", SOURCE_CSV)],
                "bug", 5, False, classifier_type="auto",
            ))

    def test_api_returns_http_400_for_unsupported_classifier(self):
        with self.assertRaises(HTTPException) as context:
            asyncio.run(run_prediction(
                target_file=upload(
                    "target.csv", "name,m1,m2\nT1,1,1\nT2,9,9\n"
                ),
                source_files=[upload("source.csv", SOURCE_CSV)],
                label_column="bug",
                classifier="auto",
                classifier_type="knn",
                knn_value=5,
                svm_c=1.0,
                coral_option=False,
                top_k=1,
                auto_tune_k=False,
                auto_tune_svm_c=False,
                decision_threshold=None,
                threshold_beta=2.0,
            ))
        self.assertEqual(400, context.exception.status_code)
        self.assertIn("Unsupported classifier", context.exception.detail)


if __name__ == "__main__":
    unittest.main()
