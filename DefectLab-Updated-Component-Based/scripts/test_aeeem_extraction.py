#!/usr/bin/env python3
"""
Validates AEEEM benchmark dataset extraction coverage against predefined benchmarks:
  EQ: 324 classes (bundles/org.eclipse.osgi)
  JDT: 997 classes (org.eclipse.jdt.core)
  LC: 691 classes (Apache Lucene 2.4.0)
  ML: 1862 classes (Mylyn components: releng, commons, context, tasks, docs, incubator)
  PDE: 1497 classes (Eclipse PDE: ui module including org.eclipse.pde.ui + org.eclipse.pde.core)
"""

import os
import sys
import glob

WORKSPACE = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
PREDEFINED_DIR = os.path.join(WORKSPACE, "Final dataset list", "AEEEM", "Predefine dataset")

BENCHMARKS = {
    "EQ": {"target_rows": 324, "arff": "EQ.arff"},
    "JDT": {"target_rows": 997, "arff": "JDT.arff"},
    "LC": {"target_rows": 691, "arff": "LC.arff"},
    "ML": {"target_rows": 1862, "arff": "ML.arff"},
    "PDE": {"target_rows": 1497, "arff": "PDE.arff"}
}

def count_arff_data_rows(filepath):
    if not os.path.isfile(filepath):
        return 0
    with open(filepath, "r", encoding="utf-8", errors="replace") as f:
        in_data = False
        count = 0
        for line in f:
            line = line.strip()
            if not line or line.startswith("%") or line.startswith("#"):
                continue
            if line.lower() == "@data":
                in_data = True
                continue
            if in_data:
                count += 1
        return count

def main():
    print("=" * 65)
    print("AEEEM BENCHMARK PREDEFINED DATASET VERIFICATION")
    print("=" * 65)
    
    all_ok = True
    for key, info in BENCHMARKS.items():
        arff_path = os.path.join(PREDEFINED_DIR, info["arff"])
        actual_rows = count_arff_data_rows(arff_path)
        expected = info["target_rows"]
        status = "MATCH" if actual_rows == expected else "MISMATCH"
        print(f"[{key}] Expected: {expected:5d} | Predefined ARFF Rows: {actual_rows:5d} | Status: {status}")
        if actual_rows != expected:
            all_ok = False
            
    print("=" * 65)
    if all_ok:
        print("All predefined AEEEM ARFF benchmarks verified successfully.")
        return 0
    else:
        print("Discrepancies found in predefined ARFF row counts.")
        return 1

if __name__ == "__main__":
    sys.exit(main())
