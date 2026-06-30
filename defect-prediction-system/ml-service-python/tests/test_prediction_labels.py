import unittest

import pandas as pd

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


if __name__ == "__main__":
    unittest.main()
