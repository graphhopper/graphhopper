"""Turn actual saved two-point route inputs into four 1x1 pack-verifier checks."""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import time


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8999")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    geometry = root / "docs/matrix/geometry-spot-checks"
    summary_path = geometry / "summary.json"
    summary = json.loads(summary_path.read_text(encoding="utf-8"))
    if summary.get("automated_export_status") != "PASS" or not summary.get("opposite_carriageway_distinct_snaps"):
        raise ValueError("Need completed geometry exports with distinct actual carriageway snaps")
    validator = root.parent / "prompt_pack/GraphHopper_Local_Matrix_Codex_Pack/validation/validate_http.py"
    output = root / "docs/matrix/geometry-matrix-requests"
    output.mkdir(parents=True, exist_ok=True)
    definitions = [("opposing-forward", "opposite-carriageways-source-forward", "car"),
                   ("opposing-reverse", "opposite-carriageways-source-reverse", "car"),
                   ("trinity-foot", "foot-only-source-way-29419166", "foot"),
                   ("restriction-car", "restriction-1645783", "car")]
    executions = []
    for label, name, profile in definitions:
        cases = [case for case in summary["cases"] if case["case"] == name and case["profile"] == profile]
        if len(cases) != 1 or cases[0]["status"] != "EXPORTED":
            raise ValueError("Expected one completed geometry case: " + name + " " + profile)
        case = cases[0]
        source_request = geometry / (case["file_prefix"] + ".request.json")
        source_response = geometry / (case["file_prefix"] + ".response.json")
        source = json.loads(source_request.read_text(encoding="utf-8"))
        if source.get("profile") != profile or len(source.get("points", [])) != 2:
            raise ValueError("Expected the actual two-point route profile/coordinates")
        request_path = output / (label + ".json")
        manifest_path = output / (label + ".provenance.json")
        report_path = root / ("docs/matrix/http-smoke-geometry-" + label + ".json")
        if request_path.exists() or report_path.exists():
            raise ValueError("Existing geometry smoke evidence preserved; choose a new output naming scheme before rerunning")
        request = {"profile": profile, "from_points": [source["points"][0]], "to_points": [source["points"][1]],
                   "out_arrays": ["times", "times_ms", "distances", "weights"], "fail_fast": False,
                   "local": {"algorithm": "flexible"}}
        request_path.write_text(json.dumps(request, indent=2, allow_nan=False) + "\n", encoding="utf-8")
        manifest = {
            "scope": "ACTUAL_SAVED_ROUTE_INPUT_TO_MATRIX_1X1",
            "created_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "case": name, "profile": profile, "base_url": args.base_url,
            "source_geometry_summary": str(summary_path), "source_geometry_summary_sha256": sha256(summary_path),
            "source_geometry_summary_generated_utc": summary["generated_utc"],
            "source_route_request": str(source_request), "source_route_request_sha256": sha256(source_request),
            "source_route_response": str(source_response), "source_route_response_sha256": sha256(source_response),
            "matrix_request": str(request_path), "matrix_request_file_sha256": sha256(request_path),
            "conversion": "Retain the actual profile and exact raw endpoint coordinates; use singleton axes and the four matrix metrics.",
            "removed_route_only_fields": sorted(set(source) - {"profile", "points"}),
            "coordinate_provenance": case["coordinate_provenance"],
            "validator": str(validator), "validator_sha256": sha256(validator),
            "manual_review": "Recorded separately; this verifier only checks HTTP metrics/null masks.",
        }
        manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        command = [sys.executable, str(validator), "--base-url", args.base_url, "--request", str(request_path),
                   "--manifest", str(manifest_path), "--output", str(report_path)]
        started = time.monotonic()
        completed = subprocess.run(command, cwd=root, text=True, capture_output=True, timeout=180)
        duration = time.monotonic() - started
        log_path = output / (label + ".log")
        log_path.write_text(completed.stdout + completed.stderr, encoding="utf-8")
        print(completed.stdout.strip(), flush=True)
        if completed.stderr:
            print(completed.stderr.strip(), file=sys.stderr, flush=True)
        report = json.loads(report_path.read_text(encoding="utf-8")) if report_path.exists() else {}
        executions.append({"case": label, "command": command, "exit_code": completed.returncode, "duration_seconds": duration,
                           "report": str(report_path), "report_sha256": sha256(report_path) if report_path.exists() else None,
                           "report_status": report.get("status"), "checked_cells": report.get("checked_cells", 0),
                           "failed_cells": report.get("failed_cells", 0), "log": str(log_path)})
    passed = all(item["exit_code"] == 0 and item["report_status"] == "SMOKE_PASS" for item in executions)
    (output / "execution.json").write_text(json.dumps({"status": "PASS" if passed else "FAIL",
                "completed_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                "sequential_execution": True, "executions": executions}, indent=2) + "\n", encoding="utf-8")
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
