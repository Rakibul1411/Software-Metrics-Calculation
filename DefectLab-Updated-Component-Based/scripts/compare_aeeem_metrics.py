#!/usr/bin/env python3
"""Compare an extracted AEEEM CSV with a labelled predefined ARFF."""

import argparse
import csv
import math
import statistics
from pathlib import Path


def read_arff(path: Path):
    attributes = []
    rows = []
    in_data = False
    for raw_line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw_line.strip()
        if not line or line.startswith(("%", "#")):
            continue
        if line.lower().startswith("@attribute"):
            declaration = line[len("@attribute"):].strip()
            if declaration[0] in ("'", '"'):
                quote = declaration[0]
                end = declaration.find(quote, 1)
                name = declaration[1:end]
            else:
                name = declaration.split(None, 1)[0]
            attributes.append(name)
        elif line.lower() == "@data":
            in_data = True
        elif in_data:
            row = next(csv.reader([line]))
            if len(row) == len(attributes):
                rows.append(row)
    return attributes, rows


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("extracted_csv", type=Path)
    parser.add_argument("predefined_arff", type=Path)
    parser.add_argument("output_csv", type=Path)
    args = parser.parse_args()

    with args.extracted_csv.open(newline="", encoding="utf-8-sig") as stream:
        extracted = list(csv.DictReader(stream))
    attributes, predefined = read_arff(args.predefined_arff)
    positions = {name: index for index, name in enumerate(attributes)}
    shared = [name for name in attributes if extracted and name in extracted[0]]

    args.output_csv.parent.mkdir(parents=True, exist_ok=True)
    with args.output_csv.open("w", newline="", encoding="utf-8") as stream:
        fields = [
            "metric", "extracted_mean", "predefined_mean",
            "relative_mean_difference", "extracted_median", "predefined_median",
            "extracted_zero_rate", "predefined_zero_rate",
            "mean_within_1_percent", "mean_within_10_percent",
        ]
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for metric in shared:
            generated_values = [float(row[metric]) for row in extracted]
            predefined_values = [
                float(row[positions[metric]]) for row in predefined
                if row[positions[metric]] not in ("", "?")
            ]
            generated_mean = statistics.mean(generated_values)
            predefined_mean = statistics.mean(predefined_values)
            relative_difference = abs(generated_mean - predefined_mean) / (
                abs(predefined_mean) + 1e-12
            )
            writer.writerow({
                "metric": metric,
                "extracted_mean": generated_mean,
                "predefined_mean": predefined_mean,
                "relative_mean_difference": relative_difference,
                "extracted_median": statistics.median(generated_values),
                "predefined_median": statistics.median(predefined_values),
                "extracted_zero_rate": sum(value == 0 for value in generated_values)
                / len(generated_values),
                "predefined_zero_rate": sum(value == 0 for value in predefined_values)
                / len(predefined_values),
                "mean_within_1_percent": relative_difference <= 0.01,
                "mean_within_10_percent": relative_difference <= 0.10,
            })

    within_one = 0
    within_ten = 0
    with args.output_csv.open(newline="", encoding="utf-8") as stream:
        for row in csv.DictReader(stream):
            within_one += row["mean_within_1_percent"] == "True"
            within_ten += row["mean_within_10_percent"] == "True"
    print(f"Extracted rows: {len(extracted)}")
    print(f"Predefined rows: {len(predefined)}")
    print(f"Shared predictors: {len(shared)}")
    print(f"Means within 1%: {within_one}/{len(shared)}")
    print(f"Means within 10%: {within_ten}/{len(shared)}")
    print(f"Report: {args.output_csv}")


if __name__ == "__main__":
    main()
