#!/usr/bin/env python3
"""Compile Java changes so Spring Boot DevTools can restart the application."""

from __future__ import annotations

import os
import signal
import subprocess
import time
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
BACKEND_DIR = PROJECT_ROOT / "backend-java"
SOURCE_DIR = BACKEND_DIR / "src" / "main"
POM_FILE = BACKEND_DIR / "pom.xml"
WATCHED_SUFFIXES = {".java", ".xml", ".properties", ".json", ".yml", ".yaml"}
POLL_SECONDS = 0.5
DEBOUNCE_SECONDS = 0.4


def source_snapshot() -> dict[str, tuple[int, int]]:
    state: dict[str, tuple[int, int]] = {}
    for path in SOURCE_DIR.rglob("*"):
        if not path.is_file() or path.suffix.lower() not in WATCHED_SUFFIXES:
            continue
        try:
            stat = path.stat()
        except FileNotFoundError:
            continue
        state[str(path)] = (stat.st_mtime_ns, stat.st_size)
    return state


def pom_snapshot() -> tuple[int, int] | None:
    try:
        stat = POM_FILE.stat()
    except FileNotFoundError:
        return None
    return stat.st_mtime_ns, stat.st_size


def start_backend() -> subprocess.Popen[bytes]:
    print("Spring DevTools: starting Spring Boot...", flush=True)
    return subprocess.Popen(
        [
            "mvn",
            "spring-boot:run",
            "-Dspring-boot.run.jvmArguments=-Xmx2g",
        ],
        cwd=BACKEND_DIR,
        start_new_session=True,
    )


def stop_backend(process: subprocess.Popen[bytes]) -> None:
    if process.poll() is not None:
        return
    try:
        os.killpg(process.pid, signal.SIGTERM)
        process.wait(timeout=10)
    except (ProcessLookupError, subprocess.TimeoutExpired):
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait(timeout=5)


def compile_changes() -> bool:
    print("Spring DevTools: change detected; compiling...", flush=True)
    result = subprocess.run(
        ["mvn", "-q", "-DskipTests", "compile"],
        cwd=BACKEND_DIR,
        check=False,
    )
    if result.returncode == 0:
        print("Spring DevTools: compile complete; application restart triggered.", flush=True)
        return True
    print("Spring DevTools: compile failed; fix the error and save again.", flush=True)
    return False


def interrupt(_signum: int, _frame: object) -> None:
    raise KeyboardInterrupt


def main() -> None:
    signal.signal(signal.SIGTERM, interrupt)
    sources = source_snapshot()
    pom = pom_snapshot()
    process = start_backend()
    try:
        while True:
            time.sleep(POLL_SECONDS)
            updated_sources = source_snapshot()
            updated_pom = pom_snapshot()
            if updated_sources == sources and updated_pom == pom:
                continue

            time.sleep(DEBOUNCE_SECONDS)
            sources = source_snapshot()
            current_pom = pom_snapshot()
            pom_changed = current_pom != pom
            pom = current_pom

            if pom_changed:
                print("Spring DevTools: pom.xml changed; restarting Maven...", flush=True)
                stop_backend(process)
                process = start_backend()
                continue

            compiled = compile_changes()
            if compiled and process.poll() is not None:
                process = start_backend()
    except KeyboardInterrupt:
        pass
    finally:
        stop_backend(process)


if __name__ == "__main__":
    main()
