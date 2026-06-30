import unittest

import pandas as pd
from fastapi import UploadFile
from io import BytesIO

from app.services.prediction_service import PredictionService


class PredictionLabelTest(unittest.TestCase):
    def test_defect_counts_are_converted_to_binary_labels(self):
        labels = PredictionService._binary_labels(pd.Series([0, 1, 2, 7]), "bug")
        self.assertEqual([0, 1, 1, 1], labels.tolist())

    def test_clean_and_buggy_text_labels_are_supported(self):
        labels = PredictionService._binary_labels(pd.Series(["clean", "Buggy", "false", "true"]), "bug")
        self.assertEqual([0, 1, 0, 1], labels.tolist())

    def test_unknown_labels_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "Unsupported values"):
            PredictionService._binary_labels(pd.Series(["maybe"]), "bug")

    def test_labelled_target_evaluation_reports_accuracy(self):
        source = UploadFile(filename="source.csv", file=BytesIO(
            b"name,wmc,cbo,bug\nS1,1,1,0\nS2,2,2,0\nS3,8,8,2\nS4,9,9,1\nS5,10,10,3\n"))
        target = UploadFile(filename="target.csv", file=BytesIO(
            b"name,wmc,cbo,bug\nT1,1,1,0\nT2,10,10,4\n"))
        result = __import__("asyncio").run(PredictionService().evaluate(
            target, [source], "bug", 1, False))
        self.assertEqual(1.0, result["metrics"]["accuracy"])
        self.assertEqual(1, result["confusionMatrix"]["truePositive"])
        self.assertEqual(1, result["confusionMatrix"]["trueNegative"])


if __name__ == "__main__":
    unittest.main()
