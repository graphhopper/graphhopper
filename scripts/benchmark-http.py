#!/usr/bin/env python3
r"""Benchmark repeated /route and /matrix on one local server (stdlib only).

Run MatrixBenchmarkTest first to export the exact fixture requests. Defaults:
    python scripts\benchmark-http.py
Explicit larger run with fewer repetitions, recorded in the output:
    python scripts\benchmark-http.py --workloads 100x100 --include-large --repeats 5

Both backends use persistent http.client connections. /route has no CH-disabling
or algorithm override; use the recorded server config that prepares CH for both
profiles. This is a measured HTTP comparison, not a release certificate.
"""
from __future__ import annotations

import argparse
from collections import defaultdict
import copy
import csv
from datetime import datetime, timezone
import hashlib
import http.client
import json
import math
from pathlib import Path
import platform
import statistics
import sys
import time
from urllib.parse import urlsplit

METRICS = ("weights", "times_ms", "distances", "times")
ROUTE_ERRORS = {
    "com.graphhopper.util.exceptions.ConnectionNotFoundException": "disconnected",
    "com.graphhopper.util.exceptions.PointNotFoundException": "invalid_snap",
    "com.graphhopper.util.exceptions.PointOutOfBoundsException": "invalid_snap",
}


def reject_constant(value):
    raise ValueError(f"Non-finite JSON number: {value}")


def encoded(value):
    return json.dumps(value, separators=(",", ":"), allow_nan=False).encode("utf-8")


def finite(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value) and value >= 0


class PersistentJSON:
    def __init__(self, base_url, timeout):
        parsed = urlsplit(base_url)
        if parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.query or parsed.fragment:
            raise ValueError("Expected an http(s) base URL without query/fragment")
        if parsed.username or parsed.password:
            raise ValueError("URL credentials are unsupported")
        self.prefix = parsed.path.rstrip("/")
        connection = http.client.HTTPSConnection if parsed.scheme == "https" else http.client.HTTPConnection
        self.connection = connection(parsed.hostname, parsed.port, timeout=timeout)
        self.opened = 0
        self.server_closes = 0

    def request(self, endpoint, payload=None):
        if self.connection.sock is None:
            self.opened += 1
        self.connection.request("GET" if payload is None else "POST", self.prefix + endpoint,
                                body=payload, headers={"Content-Type": "application/json", "Connection": "keep-alive",
                                                       "Accept-Encoding": "identity"})
        response = self.connection.getresponse()
        raw = response.read()
        if response.will_close:
            self.server_closes += 1
        body = json.loads(raw, parse_constant=reject_constant)
        if not isinstance(body, dict):
            raise ValueError(f"Expected JSON object, HTTP {response.status}")
        return response.status, body, len(raw)

    def close(self):
        self.connection.close()


def route_outcome(status, body):
    if status == 200 and "message" not in body:
        paths = body.get("paths")
        if not isinstance(paths, list) or len(paths) != 1 or not isinstance(paths[0], dict):
            raise ValueError("Ordinary /route did not return exactly one path")
        path = paths[0]
        if not all(finite(path.get(key)) for key in ("weight", "time", "distance")) or type(path["time"]) is not int:
            raise ValueError("Ordinary /route returned malformed metrics")
        return (path["weight"], path["time"], path["distance"]), "route"
    hints = body.get("hints")
    if status != 400 or not isinstance(hints, list) or not hints:
        raise ValueError(f"Unclassified /route failure HTTP {status}: {body!r}")
    if any(not isinstance(hint, dict) or hint.get("details") not in ROUTE_ERRORS for hint in hints):
        raise ValueError(f"Unclassified /route failure HTTP {status}: {body!r}")
    kinds = {ROUTE_ERRORS[hint["details"]] for hint in hints}
    return None, "invalid_snap" if "invalid_snap" in kinds else "disconnected"


def matrix_outcome(body, m, n):
    if "message" in body:
        raise ValueError(f"Whole-request matrix failure: {body!r}")
    for key in METRICS:
        rows = body.get(key)
        if not isinstance(rows, list) or len(rows) != m or any(not isinstance(row, list) or len(row) != n for row in rows):
            raise ValueError(f"Malformed {key}: expected {m} x {n}")
    invalid_from, invalid_to, pairs = set(), set(), set()
    hints = body.get("hints", [])
    if not isinstance(hints, list):
        raise ValueError("Matrix hints must be a list")
    for hint in hints:
        if not isinstance(hint, dict) or hint.get("details") not in ("INVALID_SNAP", "CONNECTION_NOT_FOUND"):
            raise ValueError(f"Unknown matrix error diagnostic: {hint!r}")
        for key, bound, target in (("invalid_from_points", m, invalid_from), ("invalid_to_points", n, invalid_to)):
            values = hint.get(key, [])
            if not isinstance(values, list) or any(type(value) is not int or not 0 <= value < bound for value in values):
                raise ValueError(f"Malformed {key} diagnostic")
            target.update(values)
        values = hint.get("point_pairs", [])
        if not isinstance(values, list):
            raise ValueError("Malformed point_pairs diagnostic")
        for pair in values:
            if not isinstance(pair, list) or len(pair) != 2 or any(type(value) is not int for value in pair) or not 0 <= pair[0] < m or not 0 <= pair[1] < n:
                raise ValueError("Out-of-range/malformed point_pairs diagnostic")
            pairs.add(tuple(pair))
    cells, outcomes = [], []
    for i in range(m):
        for j in range(n):
            values = [body[key][i][j] for key in METRICS]
            nulls = [value is None for value in values]
            diagnosed = i in invalid_from or j in invalid_to or (i, j) in pairs
            if any(nulls) != all(nulls):
                raise ValueError(f"Inconsistent null mask at {i},{j}")
            if all(nulls):
                if not diagnosed:
                    raise ValueError(f"Undiagnosed null at {i},{j}")
                cells.append(None)
                outcomes.append("invalid_snap" if i in invalid_from or j in invalid_to else "disconnected")
                continue
            if diagnosed or not all(finite(value) for value in values):
                raise ValueError(f"Invalid numeric metric or contradictory diagnostic at {i},{j}")
            if type(values[1]) is not int or type(values[3]) is not int or values[3] != values[1] // 1000:
                raise ValueError(f"Milliseconds/seconds contract violated at {i},{j}")
            cells.append(tuple(values[:3]))
            outcomes.append("route")
    info = body.get("info", {}).get("local_matrix", {})
    if info.get("algorithm") != "flexible":
        raise ValueError(f"Expected flexible matrix backend; got {info.get('algorithm')!r}")
    for key in ("graph_fingerprint", "profile_fingerprint", "build_commit"):
        if not isinstance(info.get(key), str) or not info[key]:
            raise ValueError(f"Missing matrix provenance {key}")
    return cells, outcomes, info


def measure(transport, backend, matrix_payload, route_payloads, m, n):
    opened_before = transport.opened
    closed_before = transport.server_closes
    start = time.perf_counter_ns()
    info = {}
    if backend == "http_matrix_flexible":
        status, body, output_bytes = transport.request("/matrix", matrix_payload)
        if status != 200:
            raise ValueError(f"Matrix HTTP {status}: {body!r}")
        cells, outcomes, info = matrix_outcome(body, m, n)
        requests_sent = 1
    else:
        cells, outcomes, output_bytes = [], [], 0
        for payload in route_payloads:
            status, body, length = transport.request("/route", payload)
            output_bytes += length
            cell, kind = route_outcome(status, body)
            cells.append(cell)
            outcomes.append(kind)
        requests_sent = len(route_payloads)
    elapsed_ms = (time.perf_counter_ns() - start) / 1e6
    reachable = sum(cell is not None for cell in cells)
    sample = {"elapsed_ms": elapsed_ms, "completed_cells": len(cells), "reachable_cells": reachable,
              "null_cells": len(cells) - reachable, "http_requests": requests_sent, "output_body_bytes": output_bytes,
              "connections_opened": transport.opened - opened_before, "server_connection_closes": transport.server_closes - closed_before,
              "searches": info.get("searches"), "visited_states": info.get("visited_states"),
              "weight_checksum": sum(cell[0] for cell in cells if cell is not None)}
    return sample, cells, outcomes, info


def compare(cells_a, outcomes_a, cells_b, outcomes_b, n):
    issues = []
    for index, (a, b, kind_a, kind_b) in enumerate(zip(cells_a, cells_b, outcomes_a, outcomes_b)):
        mismatch = []
        if kind_a != kind_b:
            mismatch.append(f"reachability/diagnosis: route={kind_a}, matrix={kind_b}")
        if a is not None and b is not None:
            for metric, actual, expected, absolute in zip(("weight", "time_ms", "distance_m"), b, a, (1e-6, 1, .002)):
                tolerance = absolute if metric == "time_ms" else absolute + 1e-9 * abs(expected)
                if abs(actual - expected) > tolerance:
                    mismatch.append(f"{metric}: route={expected}, matrix={actual}, tolerance={tolerance}")
        if mismatch:
            issues.append({"from_index": index // n, "to_index": index % n, "issues": mismatch})
    return issues


def percentile95(values):
    return sorted(values)[math.ceil(.95 * len(values)) - 1] if len(values) >= 2 else None


def write_csv(path, rows, fields):
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fields, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def summarize(samples):
    groups = defaultdict(list)
    for row in samples:
        groups[row["profile"], row["workload"], row["backend"]].append(row)
    result = []
    for (profile, workload, backend), rows in groups.items():
        times = [row["elapsed_ms"] for row in rows]
        elapsed = statistics.median(times)
        # Counts are measured for every repeat; disagreement is exposed as min/max.
        completed = [row["completed_cells"] for row in rows]
        reachable = [row["reachable_cells"] for row in rows]
        result.append({"profile": profile, "workload": workload, "backend": backend, "repeats": len(rows),
                       "median_ms": elapsed, "p95_ms": percentile95(times),
                       "completed_cells_min": min(completed), "completed_cells_max": max(completed),
                       "reachable_cells_min": min(reachable), "reachable_cells_max": max(reachable),
                       "completed_cells_per_second": statistics.median(row["completed_cells"] * 1000 / row["elapsed_ms"] for row in rows),
                       "reachable_cells_per_second": statistics.median(row["reachable_cells"] * 1000 / row["elapsed_ms"] for row in rows),
                       "median_output_body_bytes": statistics.median(row["output_body_bytes"] for row in rows),
                       "median_http_requests": statistics.median(row["http_requests"] for row in rows),
                       "connections_opened": sum(row["connections_opened"] for row in rows),
                       "server_connection_closes": sum(row["server_connection_closes"] for row in rows)})
    return result


def run(args):
    args.output_dir.mkdir(parents=True, exist_ok=True)
    report = {"scope": "MEASURED_HTTP_COMPARISON_NOT_RELEASE_CERTIFICATION", "status": "INCOMPLETE",
              "created_utc": datetime.now(timezone.utc).isoformat(), "url": args.url,
              "python_executable": sys.executable, "python_version": sys.version, "os": platform.platform(),
              "warmups": args.warmups, "repeats": args.repeats, "workloads": args.workloads, "profiles": args.profiles,
              "prepared_ch_selection": "Ordinary /route requests have no algorithm override or CH-disabling hints. Verify the running server used the recorded CH-prepared configuration; upstream JSON does not expose its selected backend.",
              "timing_scope": "Persistent HTTP request/response and JSON parsing/validation; pre-encoded request bodies; no import/CH preparation. Response-body bytes exclude headers and use identity encoding.",
              "percentile_method": "Nearest-rank p95 when at least two repeats; low repeat counts have limited percentile resolution.",
              "limits_note": "Defaults select small workloads; >1000 cells requires --include-large; repeat/warmup overrides are recorded.",
              "comparisons": [], "matrix_provenance": {}, "samples": []}
    transports = {name: PersistentJSON(args.url, args.timeout) for name in ("http_route_configured_ch", "http_matrix_flexible")}
    try:
        raw_requests = args.requests.read_bytes()
        report["requests_file"] = str(args.requests)
        report["requests_sha256"] = hashlib.sha256(raw_requests).hexdigest()
        bundle = json.loads(raw_requests, parse_constant=reject_constant)
        report["seed"] = bundle.get("seed")
        if args.server_config.exists():
            report["server_config_path"] = str(args.server_config)
            report["server_config_sha256"] = hashlib.sha256(args.server_config.read_bytes()).hexdigest()
        status, server_info, _ = transports["http_route_configured_ch"].request("/info")
        if status != 200:
            raise ValueError(f"Server /info returned HTTP {status}")
        report["server_info"] = server_info
        entries = [entry for entry in bundle["workloads"] if entry["name"] in args.workloads and entry["profile"] in args.profiles]
        if {(entry["profile"], entry["name"]) for entry in entries} != {(profile, workload) for profile in args.profiles for workload in args.workloads}:
            raise ValueError("Requested workload/profile combination is absent from benchmark-requests.json")
        for entry in entries:
            request = copy.deepcopy(entry["request"])
            request["local"] = {"algorithm": "flexible"}
            request["out_arrays"] = list(METRICS)
            request["fail_fast"] = False
            origins, destinations = request["from_points"], request["to_points"]
            m, n = len(origins), len(destinations)
            if m * n > 1000 and not args.include_large:
                raise ValueError(f"{entry['name']} has {m*n} cells; explicitly pass --include-large")
            matrix_payload = encoded(request)
            route_payloads = [encoded({"profile": request["profile"], "points": [origin, destination],
                                       "instructions": False, "calc_points": False, "snap_preventions": []})
                              for origin in origins for destination in destinations]
            comparison = {"profile": entry["profile"], "workload": entry["name"], "measured_repetitions": 0,
                          "mismatched_cells_across_repetitions": 0, "mismatches_first_100": []}
            report["comparisons"].append(comparison)
            backends = list(transports)
            for repeat in range(-args.warmups, args.repeats):
                rotation = (repeat + args.warmups) % len(backends)
                results = {}
                for backend in backends[rotation:] + backends[:rotation]:
                    sample, cells, outcomes, info = measure(transports[backend], backend, matrix_payload, route_payloads, m, n)
                    results[backend] = cells, outcomes
                    if info:
                        if info.get("profile") != entry["profile"]:
                            raise ValueError("Matrix metadata profile differs from the requested profile")
                        identity = {key: info[key] for key in ("graph_fingerprint", "profile_fingerprint", "build_commit")}
                        previous = report["matrix_provenance"].get(entry["profile"])
                        if previous is not None and previous != identity:
                            raise ValueError("Matrix graph/profile/build identity changed during the benchmark")
                        report["matrix_provenance"][entry["profile"]] = identity
                    if repeat >= 0:
                        sample.update(profile=entry["profile"], workload=entry["name"], backend=backend, repeat=repeat)
                        report["samples"].append(sample)
                if repeat >= 0:
                    route_cells, route_kinds = results["http_route_configured_ch"]
                    matrix_cells, matrix_kinds = results["http_matrix_flexible"]
                    issues = compare(route_cells, route_kinds, matrix_cells, matrix_kinds, n)
                    comparison["measured_repetitions"] += 1
                    comparison["mismatched_cells_across_repetitions"] += len(issues)
                    for issue in issues[:max(0, 100 - len(comparison["mismatches_first_100"]))]:
                        comparison["mismatches_first_100"].append({"repeat": repeat, **issue})
            comparison["status"] = "MISMATCH" if comparison["mismatched_cells_across_repetitions"] else "MATCH_WITHIN_HTTP_THRESHOLDS"
            print(f"Measured {entry['profile']} {entry['name']}: {args.repeats} repetitions/backend; {comparison['status']}", flush=True)
        report["status"] = "MEASURED"
    except Exception as exc:
        report["fatal_error"] = f"{type(exc).__name__}: {exc}"
    finally:
        for transport in transports.values():
            transport.close()
        report["connections"] = {name: {"opened": transport.opened, "server_closes": transport.server_closes} for name, transport in transports.items()}
        summaries = summarize(report["samples"])
        write_csv(args.output_dir / "http-benchmark-samples.csv", report["samples"],
                  ["profile", "workload", "backend", "repeat", "elapsed_ms", "completed_cells", "reachable_cells", "null_cells", "http_requests", "output_body_bytes", "connections_opened", "server_connection_closes", "searches", "visited_states", "weight_checksum"])
        write_csv(args.output_dir / "http-benchmark-results.csv", summaries,
                  ["profile", "workload", "backend", "repeats", "median_ms", "p95_ms", "completed_cells_min", "completed_cells_max", "reachable_cells_min", "reachable_cells_max", "completed_cells_per_second", "reachable_cells_per_second", "median_output_body_bytes", "median_http_requests", "connections_opened", "server_connection_closes"])
        report["summary"] = summaries
        (args.output_dir / "http-benchmark-report.json").write_text(json.dumps(report, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    print(f"{report['status']}; {len(report['samples'])} measured samples; {args.output_dir}")
    if "fatal_error" in report:
        print(report["fatal_error"], file=sys.stderr)
    return 0 if report["status"] == "MEASURED" else 1


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", default="http://localhost:8997")
    parser.add_argument("--requests", type=Path, default=root / "docs/matrix/benchmark-requests.json")
    parser.add_argument("--server-config", type=Path, default=root / "docs/matrix/config-matrix.yml")
    parser.add_argument("--output-dir", type=Path, default=root / "docs/matrix")
    parser.add_argument("--workloads", nargs="+", default=["local_1x10", "1x100"])
    parser.add_argument("--profiles", nargs="+", choices=("foot", "car"), default=["foot", "car"])
    parser.add_argument("--warmups", type=int, default=5)
    parser.add_argument("--repeats", type=int, default=20)
    parser.add_argument("--include-large", action="store_true")
    parser.add_argument("--timeout", type=float, default=120)
    args = parser.parse_args()
    if args.warmups < 0 or args.repeats < 1 or args.timeout <= 0:
        parser.error("warmups must be nonnegative, repeats positive, timeout positive")
    return run(args)


if __name__ == "__main__":
    raise SystemExit(main())
