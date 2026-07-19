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

    def test_aeeem_arff_is_read_with_declared_columns_and_labels(self):
        source = UploadFile(filename="LC.arff", file=BytesIO(
            b"@relation lc\n@attribute ck_oo_wmc numeric\n"
            b"@attribute CvsEntropy numeric\n@attribute class {buggy,clean}\n"
            b"@data\n### WMC entropy label\n3,0.25,clean\n8,0.75,buggy\n"))

        dataframe = PredictionService()._read_csv(source)

        self.assertEqual(["ck_oo_wmc", "cvsentropy", "class"], dataframe.columns.tolist())
        self.assertEqual(["clean", "buggy"], dataframe["class"].tolist())

    def test_arff_is_detected_from_content_when_filename_extension_is_wrong(self):
        target = UploadFile(filename="renamed-target.csv", file=BytesIO(
            b"@relation target\n@attribute metric_a numeric\n"
            b"@attribute class {buggy,clean}\n@data\n1,clean\n8,buggy\n"))

        dataframe = PredictionService()._read_csv(target)

        self.assertEqual(["metric_a", "class"], dataframe.columns.tolist())

    def test_converted_arff_recovers_real_headers_from_first_data_row(self):
        target = UploadFile(filename="converted.arff", file=BytesIO(
            b"@relation converted\n@attribute attribute_0 real\n"
            b"@attribute attribute_1 string\n@attribute attribute_2 string\n"
            b"@attribute attribute_3 string\n@data\n"
            b"id,metric_a,metric_b,class\n1,### description,,\n2,,,\n"
            b"3,1,2,clean\n4,8,9,buggy\n"))

        dataframe = PredictionService()._read_csv(target)

        self.assertEqual(["id", "metric_a", "metric_b", "class"], dataframe.columns.tolist())
        self.assertEqual("clean", dataframe.iloc[2]["class"])

    def test_csv_source_and_arff_target_are_rejected(self):
        source = UploadFile(filename="source.csv", file=BytesIO(
            b"id,metric_a,metric_b,class\n1,1,1,clean\n2,2,2,clean\n"
            b"3,8,8,buggy\n4,9,9,buggy\n"))
        target = UploadFile(filename="target.arff", file=BytesIO(
            b"@relation target\n@attribute metric_a numeric\n@attribute metric_b numeric\n"
            b"@attribute class {buggy,clean}\n@data\n1,1,clean\n9,9,buggy\n"))

        with self.assertRaisesRegex(ValueError, "formats must match"):
            __import__("asyncio").run(PredictionService().evaluate(
                target, [source], "class", 1, False))

    def test_extension_that_disagrees_with_content_is_rejected(self):
        source = UploadFile(filename="source.csv", file=BytesIO(
            b"metric_a,class\n1,clean\n9,buggy\n"))
        disguised_target = UploadFile(filename="target.csv", file=BytesIO(
            b"@relation target\n@attribute metric_a numeric\n"
            b"@attribute class {buggy,clean}\n@data\n1,clean\n9,buggy\n"))

        with self.assertRaisesRegex(ValueError, "extension does not match"):
            __import__("asyncio").run(PredictionService().evaluate(
                disguised_target, [source], "class", 1, False))

    def test_mixed_source_formats_are_rejected(self):
        source = UploadFile(filename="source.arff", file=BytesIO(
            b"@relation source\n@attribute metric_a numeric\n@attribute metric_b numeric\n"
            b"@attribute class {buggy,clean}\n@data\n1,1,clean\n2,2,clean\n"
            b"8,8,buggy\n9,9,buggy\n"))
        csv_source = UploadFile(filename="source.csv", file=BytesIO(
            b"metric_a,metric_b,class\n1,1,clean\n9,9,buggy\n"))
        target = UploadFile(filename="target.arff", file=BytesIO(
            b"@relation target\n@attribute metric_a numeric\n@attribute metric_b numeric\n"
            b"@attribute class {buggy,clean}\n@data\n1,1,clean\n9,9,buggy\n"))

        with self.assertRaisesRegex(ValueError, "do not mix"):
            __import__("asyncio").run(PredictionService().evaluate(
                target, [source, csv_source], "class", 1, False))

    def test_csv_source_and_csv_target_ignore_export_id_and_metadata_rows(self):
        source = UploadFile(filename="source.csv", file=BytesIO(
            b"metric_a,metric_b,class\n1,1,clean\n2,2,clean\n8,8,buggy\n9,9,buggy\n"))
        target = UploadFile(filename="target.csv", file=BytesIO(
            b"id,metric_a,metric_b,class\n1,### exported attribute description,,\n2,,,\n"
            b"3,1,1,clean\n4,9,9,buggy\n"))

        result = __import__("asyncio").run(PredictionService().evaluate(
            target, [source], "class", 1, False))

        self.assertEqual(2, len(result["predictions"]))
        self.assertEqual(1.0, result["metrics"]["accuracy"])

    def test_arff_source_and_arff_target_can_be_evaluated(self):
        source = UploadFile(filename="source.arff", file=BytesIO(
            b"@relation source\n@attribute metric_a numeric\n@attribute metric_b numeric\n"
            b"@attribute class {buggy,clean}\n@data\n1,1,clean\n2,2,clean\n"
            b"8,8,buggy\n9,9,buggy\n"))
        target = UploadFile(filename="target.arff", file=BytesIO(
            b"@relation target\n@attribute metric_a numeric\n@attribute metric_b numeric\n"
            b"@attribute class {buggy,clean}\n@data\n1,1,clean\n9,9,buggy\n"))

        result = __import__("asyncio").run(PredictionService().evaluate(
            target, [source], "class", 1, False))

        self.assertEqual(1.0, result["metrics"]["accuracy"])

    def test_generated_arff_target_is_used_for_prediction(self):
        source = UploadFile(filename="source.arff", file=BytesIO(
            b"@relation source\n@attribute 'name' string\n@attribute 'wmc' numeric\n"
            b"@attribute 'cbo' numeric\n@attribute 'bug' numeric\n@data\n"
            b"'CleanOne',1,1,0\n'CleanTwo',2,2,0\n'BugOne',8,8,1\n'BugTwo',9,9,1\n"))
        target = UploadFile(filename="extracted-metrics.arff", file=BytesIO(
            b"@relation target\n@attribute 'name' string\n@attribute 'wmc' numeric\n"
            b"@attribute 'cbo' numeric\n@data\n'TargetClean',1,1\n'TargetBug',9,9\n"))

        result = __import__("asyncio").run(PredictionService().run(
            target, [source], "bug", 1, False))

        self.assertEqual(["TargetClean", "TargetBug"],
                         [row["class"] for row in result["predictions"]])
        self.assertEqual([False, True],
                         [row["isBuggy"] for row in result["predictions"]])

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

    def test_top_k_source_selection_uses_nearest_covariance_dataset(self):
        near_source = UploadFile(filename="near.csv", file=BytesIO(
            b"name,wmc,cbo,bug\nN1,0,0,0\nN2,1,1,0\nN3,2,2,1\n"))
        far_source = UploadFile(filename="far.csv", file=BytesIO(
            b"name,wmc,cbo,bug\nF1,0,2,0\nF2,100,-50,1\nF3,200,-100,1\n"))
        target = UploadFile(filename="target.csv", file=BytesIO(
            b"name,wmc,cbo,bug\nT1,0,0,0\nT2,1,1,0\nT3,2,2,1\n"))

        result = __import__("asyncio").run(PredictionService().evaluate(
            target, [far_source, near_source], "bug", 1, False, top_k=1))

        self.assertEqual("near.csv", result["selectedSources"][0]["dataset"])
        self.assertTrue(result["sourceRanking"][0]["selected"])
        self.assertFalse(result["sourceRanking"][1]["selected"])


if __name__ == "__main__":
    unittest.main()
