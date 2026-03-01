#!/usr/bin/env python3
"""
Compare calculated metrics with predefined PROMISE dataset metrics
and generate a mismatch report CSV file.
"""

import csv
import os
import argparse
from pathlib import Path


def load_csv_as_dict(filepath, key_column='name'):
    """Load a CSV file and return a dictionary keyed by the specified column."""
    data = {}
    with open(filepath, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            key = row[key_column]
            data[key] = row
    return data


def compare_metrics(calculated_file, predefined_file, output_file):
    """
    Compare calculated metrics with predefined metrics and generate mismatch report.
    
    Args:
        calculated_file: Path to the calculated metrics CSV
        predefined_file: Path to the predefined PROMISE dataset CSV
        output_file: Path to output the mismatch report CSV
    """
    # Metrics to compare (common between both files)
    metrics_to_compare = ['wmc', 'dit', 'noc', 'cbo', 'rfc', 'lcom', 'ca', 'ce', 'npm', 'lcom3', 'loc', 'dam', 'moa', 'mfa', 'cam', 'ic', 'cbm', 'amc', 'max_cc', 'avg_cc']
    
    # Load both CSV files
    calculated_data = load_csv_as_dict(calculated_file)
    predefined_data = load_csv_as_dict(predefined_file)
    
    # Find common classes
    calculated_classes = set(calculated_data.keys())
    predefined_classes = set(predefined_data.keys())
    common_classes = calculated_classes & predefined_classes
    
    print(f"Calculated classes: {len(calculated_classes)}")
    print(f"Predefined classes: {len(predefined_classes)}")
    print(f"Common classes: {len(common_classes)}")
    
    # Classes only in calculated
    only_calculated = calculated_classes - predefined_classes
    if only_calculated:
        print(f"\nClasses only in calculated file ({len(only_calculated)}):")
        for cls in sorted(only_calculated)[:5]:
            print(f"  - {cls}")
        if len(only_calculated) > 5:
            print(f"  ... and {len(only_calculated) - 5} more")
    
    # Classes only in predefined
    only_predefined = predefined_classes - calculated_classes
    if only_predefined:
        print(f"\nClasses only in predefined file ({len(only_predefined)}):")
        for cls in sorted(only_predefined)[:5]:
            print(f"  - {cls}")
        if len(only_predefined) > 5:
            print(f"  ... and {len(only_predefined) - 5} more")
    
    # Collect mismatches
    mismatches = []
    match_count = 0
    mismatch_count = 0
    
    for class_name in sorted(common_classes):
        calc_row = calculated_data[class_name]
        pred_row = predefined_data[class_name]
        
        for metric in metrics_to_compare:
            if metric in calc_row and metric in pred_row:
                try:
                    calc_value = float(calc_row[metric])
                    pred_value = float(pred_row[metric])
                    
                    # Compare values (allowing for floating point comparison)
                    if abs(calc_value - pred_value) > 0.0001:
                        difference = calc_value - pred_value
                        mismatches.append({
                            'class_name': class_name,
                            'metric': metric,
                            'calculated_value': int(calc_value) if calc_value == int(calc_value) else calc_value,
                            'predefined_value': int(pred_value) if pred_value == int(pred_value) else pred_value,
                            'difference': int(difference) if difference == int(difference) else round(difference, 4)
                        })
                        mismatch_count += 1
                    else:
                        match_count += 1
                except (ValueError, TypeError) as e:
                    print(f"Warning: Could not compare {metric} for {class_name}: {e}")
    
    # Create output directory if it doesn't exist
    os.makedirs(os.path.dirname(output_file), exist_ok=True)
    
    # Write mismatch report to CSV
    with open(output_file, 'w', newline='', encoding='utf-8') as f:
        fieldnames = ['class_name', 'metric', 'calculated_value', 'predefined_value', 'difference']
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(mismatches)
    
    print(f"\n{'='*60}")
    print(f"COMPARISON SUMMARY")
    print(f"{'='*60}")
    print(f"Total metric comparisons: {match_count + mismatch_count}")
    print(f"Matches: {match_count}")
    print(f"Mismatches: {mismatch_count}")
    print(f"Match rate: {match_count / (match_count + mismatch_count) * 100:.2f}%")
    print(f"\nMismatch report saved to: {output_file}")
    
    # Print mismatch summary by metric and write summary CSV
    if mismatches:
        print(f"\nMismatches by metric:")
        metric_counts = {}
        for m in mismatches:
            metric = m['metric']
            metric_counts[metric] = metric_counts.get(metric, 0) + 1
        for metric, count in sorted(metric_counts.items(), key=lambda x: -x[1]):
            print(f"  {metric}: {count} mismatches")

        # Write per-metric mismatch summary to a separate CSV file
        output_dir = os.path.dirname(output_file)
        output_basename = os.path.basename(output_file)
        if '-mismatch.csv' in output_basename:
            dataset_prefix = output_basename.replace('-mismatch.csv', '')
        else:
            dataset_prefix = Path(output_basename).stem

        summary_file = os.path.join(output_dir, f"{dataset_prefix}-mismatch-summary.csv")
        with open(summary_file, 'w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=['metric', 'mismatches'])
            writer.writeheader()
            for metric, count in sorted(metric_counts.items(), key=lambda x: -x[1]):
                writer.writerow({'metric': metric, 'mismatches': count})
        print(f"\nMismatch summary saved to: {summary_file}")
    
    return mismatches


def main():
    parser = argparse.ArgumentParser(description='Compare calculated metrics with predefined PROMISE dataset metrics')
    parser.add_argument('--calculated', '-c', required=True, help='Path to calculated metrics CSV file')
    parser.add_argument('--predefined', '-p', required=True, help='Path to predefined PROMISE dataset CSV file')
    parser.add_argument('--output', '-o', help='Path to output mismatch report CSV (optional)')
    
    args = parser.parse_args()
    
    # Determine output filename
    if args.output:
        output_file = args.output
    else:
        # Auto-generate output filename based on input
        calc_name = Path(args.calculated).stem
        output_dir = Path(args.calculated).parent / 'matching-calculated-value'
        output_file = output_dir / f"{calc_name}-mismatch.csv"
    
    compare_metrics(args.calculated, args.predefined, str(output_file))


if __name__ == '__main__':
    main()
