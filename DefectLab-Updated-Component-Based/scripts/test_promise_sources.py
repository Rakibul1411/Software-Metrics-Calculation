#!/usr/bin/env python3
"""
Automated Test Suite for PROMISE Source Code Extraction & Predefined Matching in DefectLab.
Uses standard library urllib (no pip dependencies required).
"""

import os
import sys
import json
import time
import urllib.request
import urllib.parse
import http.cookiejar
from pathlib import Path

BASE_URL = "http://localhost:8080/api"
WORKSPACE = Path("/Users/md.rakibulislam/IIT/SPL-3/promise-dataset-source-code/DefectLab-Updated-Component-Based")
PROMISE_DIR = WORKSPACE / "PROMISE-backup-copy"
SOURCE_DIR = PROMISE_DIR / "source code"
BUG_DIR = PROMISE_DIR / "bug-data"

cj = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))

def http_post_json(url: str, payload: dict) -> dict:
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    with opener.open(req) as resp:
        return json.loads(resp.read().decode("utf-8"))

def http_get_json(url: str) -> dict:
    req = urllib.request.Request(url)
    with opener.open(req) as resp:
        return json.loads(resp.read().decode("utf-8"))

def http_post_multipart(url: str, fields: dict, files: dict) -> dict:
    boundary = f"----DefectLabBoundary{int(time.time())}"
    body = bytearray()

    for name, value in fields.items():
        body.extend(f"--{boundary}\r\n".encode("utf-8"))
        body.extend(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode("utf-8"))
        body.extend(f"{value}\r\n".encode("utf-8"))

    for name, (filename, file_bytes, content_type) in files.items():
        body.extend(f"--{boundary}\r\n".encode("utf-8"))
        body.extend(f'Content-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'.encode("utf-8"))
        body.extend(f"Content-Type: {content_type}\r\n\r\n".encode("utf-8"))
        body.extend(file_bytes)
        body.extend(b"\r\n")

    body.extend(f"--{boundary}--\r\n".encode("utf-8"))

    req = urllib.request.Request(
        url,
        data=bytes(body),
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"}
    )
    with opener.open(req) as resp:
        return json.loads(resp.read().decode("utf-8"))

def login(email: str, password: str) -> dict:
    user = http_post_json(f"{BASE_URL}/auth/login", {"email": email, "password": password})
    print(f"✓ Logged in as {user.get('name')} ({user.get('email')})")
    return user

def list_datasets() -> list:
    return http_get_json(f"{BASE_URL}/datasets")

def find_dataset(datasets: list, project_name: str, project_version: str, dataset_type: str):
    p_norm = project_name.strip().lower()
    v_norm = project_version.strip().lower()
    for d in datasets:
        if (d.get("projectName", "").strip().lower() == p_norm
            and d.get("projectVersion", "").strip().lower() == v_norm
            and d.get("datasetType") == dataset_type
            and d.get("datasetFamily") == "PROMISE"):
            return d
    return None

def upload_predefined(project_name: str, project_version: str, csv_path: Path):
    print(f"  Uploading PREDEFINED dataset for {project_name} {project_version} ({csv_path.name})...")
    with open(csv_path, "rb") as f:
        file_bytes = f.read()
    fields = {
        "projectName": project_name,
        "projectVersion": project_version,
        "datasetFamily": "PROMISE",
        "datasetType": "PREDEFINED"
    }
    files = {"datasetFile": (csv_path.name, file_bytes, "text/csv")}
    res = http_post_multipart(f"{BASE_URL}/datasets", fields, files)
    print(f"  ✓ Predefined dataset created: ID {res['id']} ({res['totalFiles']} files, {res['totalMetrics']} metrics)")
    return res

def analyze_source(project_name: str, project_version: str, archive_path: Path):
    print(f"  Analyzing SOURCE code archive for {project_name} {project_version} ({archive_path.name})...")
    start = time.time()
    with open(archive_path, "rb") as f:
        file_bytes = f.read()
    fields = {
        "projectName": project_name,
        "projectVersion": project_version,
        "datasetFamily": "PROMISE"
    }
    files = {"projectArchive": (archive_path.name, file_bytes, "application/octet-stream")}
    res = http_post_multipart(f"{BASE_URL}/analysis", fields, files)
    duration = time.time() - start
    print(f"  ✓ Manual extraction complete in {duration:.1f}s: ID {res['id']} ({res['totalFiles']} files, {res['totalMetrics']} metrics)")
    return res

def list_comparisons() -> list:
    return http_get_json(f"{BASE_URL}/metric-comparisons")

def run_comparison(manual_id: int, predefined_id: int) -> dict:
    return http_post_json(f"{BASE_URL}/metric-comparisons", {
        "manualDatasetId": manual_id,
        "predefinedDatasetId": predefined_id
    })

def run_test_for_target(project_name: str, version: str, src_rel: str, bug_rel: str):
    print(f"\n--------------------------------------------------")
    print(f"Testing PROMISE Target: {project_name.upper()} {version}")
    print(f"--------------------------------------------------")

    src_path = SOURCE_DIR / src_rel
    bug_path = BUG_DIR / bug_rel

    if not src_path.exists():
        print(f"  ❌ Source file not found: {src_path}")
        return None
    if not bug_path.exists():
        print(f"  ❌ Bug data file not found: {bug_path}")
        return None

    datasets = list_datasets()
    predefined = find_dataset(datasets, project_name, version, "PREDEFINED")
    if not predefined:
        predefined = upload_predefined(project_name, version, bug_path)

    datasets = list_datasets()
    manual = find_dataset(datasets, project_name, version, "MANUAL")
    if not manual:
        manual = analyze_source(project_name, version, src_path)

    comparisons = list_comparisons()
    existing_comp = None
    for c in comparisons:
        if c.get("manualDatasetId") == manual["id"] and c.get("predefinedDatasetId") == predefined["id"]:
            existing_comp = c
            break

    if existing_comp:
        comp_detail = http_get_json(f"{BASE_URL}/metric-comparisons/{existing_comp['id']}")
    else:
        print(f"  Running Metric Comparison between Manual #{manual['id']} and Predefined #{predefined['id']}...")
        comp_detail = run_comparison(manual["id"], predefined["id"])

    res = comp_detail.get("result", {})
    matched = res.get("matchedIdentifiers", 0)
    manual_files = manual.get("totalFiles", 0)
    predef_files = predefined.get("totalFiles", 0)
    common_metrics = res.get("commonNumericMetricCount", 0)

    match_pct = (matched / predef_files * 100) if predef_files > 0 else 0

    print(f"  Result: {matched} of {predef_files} predefined classes matched ({match_pct:.1f}%) | {manual_files} manual files | {common_metrics} common metrics")
    return {
        "project": project_name,
        "version": version,
        "manualFiles": manual_files,
        "predefFiles": predef_files,
        "matched": matched,
        "matchPct": match_pct,
        "commonMetrics": common_metrics,
        "comparisonId": comp_detail.get("id"),
        "status": "PASS" if matched > 0 else "FAIL"
    }

def main():
    email = sys.argv[1] if len(sys.argv) > 1 else "rakibulislamnatiq@gmail.com"
    password = sys.argv[2] if len(sys.argv) > 2 else "123456789"

    targets_to_run = [
        ("ant", "1.3", "ant/jakarta-ant-1.3-src.zip", "ant/ant-1.3.csv"),
        ("ant", "1.4", "ant/jakarta-ant-1.4-src.zip", "ant/ant-1.4.csv"),
        ("ant", "1.5", "ant/jakarta-ant-1.5-src.zip", "ant/ant-1.5.csv"),
        ("log4j", "1.0", "log4j/log4j-v_1_0.zip", "log4j/log4j-1.0.csv"),
        ("log4j", "1.1", "log4j/log4j-v_1_1.zip", "log4j/log4j-1.1.csv"),
        ("synapse", "1.0", "synapse/synapse-1.0.tar.gz", "synapse/synapse-1.0.csv"),
        ("camel", "1.0", "camel/camel-camel-1.0.0.tar.gz", "camel/camel-1.0.csv"),
        ("velocity", "1.4", "velocity/velocity-1.4.tar.gz", "velocity/velocity-1.4.csv"),
    ]

    login(email, password)

    results = []
    for proj, ver, src, bug in targets_to_run:
        try:
            r = run_test_for_target(proj, ver, src, bug)
            if r:
                results.append(r)
        except Exception as e:
            print(f"  ❌ Error processing {proj} {ver}: {e}")
            results.append({
                "project": proj,
                "version": ver,
                "status": f"ERROR: {e}"
            })

    print("\n" + "="*85)
    print("PROMISE SOURCE CODE ANALYSIS & PREDEFINED COMPARISON TEST RESULTS")
    print("="*85)
    print(f"{'PROJECT':<12} | {'VERSION':<8} | {'MANUAL FILES':<12} | {'PREDEF FILES':<12} | {'MATCHED':<8} | {'MATCH %':<8} | {'METRICS':<8} | {'STATUS'}")
    print("-" * 85)
    for r in results:
        if r.get("matched") is not None:
            print(f"{r['project']:<12} | {r['version']:<8} | {r['manualFiles']:<12} | {r['predefFiles']:<12} | {r['matched']:<8} | {r['matchPct']:>6.1f}% | {r['commonMetrics']:<8} | {r['status']}")
        else:
            print(f"{r['project']:<12} | {r['version']:<8} | {r.get('status')}")

if __name__ == "__main__":
    main()
