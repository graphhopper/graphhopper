#!/usr/bin/env python3
"""Record a final-JAR smoke against an already running isolated localhost:8997 server.

Run from cmd.exe after starting run-matrix.cmd. This script never starts or stops Java.
After stopping the isolated server, run the same command with --confirm-stopped.
Only Python's standard library is used. Existing reports and raw captures are preserved.
"""
from __future__ import annotations

import argparse
import copy
import errno
import hashlib
import json
import math
import os
from pathlib import Path
import re
import socket
import sys
from datetime import datetime, timezone
import urllib.error
import urllib.parse
import urllib.request
import zipfile

ARRAYS = ("times", "times_ms", "distances", "weights")
UNITS = {"times": "seconds", "times_ms": "milliseconds", "distances": "metres", "weights": "profile_cost"}
A, B, C = [7.42135, 43.730729], [7.419199, 43.727697], [7.4277, 43.739213]


def utc():
    return datetime.now(timezone.utc).isoformat()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest_bytes(value):
    return hashlib.sha256(value).hexdigest()


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def parse_json(value):
    def reject(token):
        raise ValueError(f"Nonfinite JSON number: {token}")
    return json.loads(value, parse_constant=reject)


def save_new(path, value):
    with path.open("xb") as stream:
        stream.write(value)


def encoded(value):
    return (json.dumps(value, indent=2, allow_nan=False) + "\n").encode("utf-8")


def java_lines(raw):
    # Helper.readFile uses BufferedReader.readLine. Preserve interior empty
    # lines (including a possible empty commit message), normalize CR/LF, and
    # do not invent a final empty line for a terminating newline.
    text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")
    lines = text.split("\n")
    if lines and lines[-1] == "":
        lines.pop()
    return lines


def artifact_identity(jar, expected_commit):
    require(jar.is_file(), f"Built JAR is missing: {jar}")
    with zipfile.ZipFile(jar) as archive:
        resources = {name: archive.read("com/graphhopper/" + name) for name in ("gitinfo", "version", "builddate")}
    lines = java_lines(resources["gitinfo"])
    require(len(lines) == 6 and lines[0].startswith("#"), "GraphHopper gitinfo must contain the comment and five positional fields")
    require(lines[1] == expected_commit, "JAR GraphHopper gitinfo does not bind the expected complete source commit")
    require(lines[5] == "false", "Final JAR must identify a clean committed build")
    versions, dates = java_lines(resources["version"]), java_lines(resources["builddate"])
    require(versions and dates and versions[0] and dates[0] and not versions[0].startswith("$") and not dates[0].startswith("$"), "Unfiltered GraphHopper version/builddate resource")
    identity = {"entry": "com/graphhopper/gitinfo", "commit": lines[1], "commit_time": lines[2],
                "commit_message": lines[3], "branch": lines[4], "dirty": lines[5]}
    return {"path": str(jar), "bytes": jar.stat().st_size, "sha256": sha256(jar), "graphhopper_gitinfo": identity,
            "version": versions[0], "builddate": dates[0],
            "resource_sha256": {"com/graphhopper/" + name: digest_bytes(raw) for name, raw in resources.items()}}


def request_payload(profile, algorithm, invalid=False, fail_fast=False):
    return {"profile": profile, "from_points": [A, [0, 0] if invalid else B, A], "to_points": [C, A, C],
            "out_arrays": list(ARRAYS), "fail_fast": fail_fast, "local": {"algorithm": algorithm}}


def exchange(name, payload, url, raw_directory, timeout, method="POST", path="/matrix"):
    sent = json.dumps(payload, separators=(",", ":"), allow_nan=False).encode("utf-8") if payload is not None else None
    if sent is not None:
        save_new(raw_directory / f"{name}.request.json", sent)
    request = urllib.request.Request(url + path, data=sent, method=method,
                                     headers={"Content-Type": "application/json"} if sent is not None else {})
    started = utc()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            status, raw, headers = response.status, response.read(), dict(response.headers)
    except urllib.error.HTTPError as error:
        status, raw, headers = error.code, error.read(), dict(error.headers)
    save_new(raw_directory / f"{name}.response.json", raw)
    body = parse_json(raw)
    require(isinstance(body, dict), f"{name}: expected JSON response object")
    result = {"http_status": status, "request": payload, "response": body, "method": method, "path": path,
              "started_utc": started, "completed_utc": utc(), "response_headers": headers,
              "raw_response_sha256": digest_bytes(raw), "raw_response_file": str(raw_directory / f"{name}.response.json")}
    if sent is not None:
        result.update(raw_request_sha256=digest_bytes(sent), raw_request_file=str(raw_directory / f"{name}.request.json"))
    return result


def integer(value):
    return isinstance(value, int) and not isinstance(value, bool)


def number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value) and value >= 0


def matrix_checks(case, expected_commit, invalid=False):
    name = case["name"]
    require(case["http_status"] == 200, f"{name}: expected HTTP 200")
    body = case["response"]
    for metric in ARRAYS:
        matrix = body.get(metric)
        require(isinstance(matrix, list) and len(matrix) == 3
                and all(isinstance(row, list) and len(row) == 3 for row in matrix), f"{name}: wrong {metric} dimensions")
    for row in range(3):
        for column in range(3):
            values = [body[metric][row][column] for metric in ARRAYS]
            require(all(value is None for value in values) or all(number(value) for value in values), f"{name}: invalid metric/null mask")
            if values[0] is not None:
                require(integer(values[0]) and integer(values[1]) and values[0] == values[1] // 1000, f"{name}: incorrect time units")
            require(all(body[metric][row][0] == body[metric][row][2] for metric in ARRAYS), f"{name}: duplicate destination scatter differs")
    for metric in ARRAYS:
        require(body[metric][0] == body[metric][2], f"{name}: duplicate origin scatter differs")
        require(body[metric][0][1] == 0, f"{name}: valid zero-length cell differs from zero")
        if invalid:
            require(body[metric][1] == [None, None, None], f"{name}: invalid row must be all null")
        else:
            require(all(value is not None for row in body[metric] for value in row), f"{name}: fixture points should connect")
    info = body.get("info", {}).get("local_matrix", {})
    require(info.get("build_commit") == expected_commit, f"{name}: wrong/dirty build commit: {info.get('build_commit')}")
    require(info.get("contract_version") == "1.0" and info.get("units") == UNITS, f"{name}: contract/units differ")
    require(info.get("origin_count") == 3 and info.get("destination_count") == 3 and info.get("unique_origin_count") == 2, f"{name}: axis counters differ")
    require(info.get("threads") == 1 and info.get("visited_states_complete") is True, f"{name}: worker/counter completeness differs")
    require(integer(info.get("visited_states")) and info["visited_states"] == info.get("visited_states_known"), f"{name}: state counters differ")
    for field in ("graph_fingerprint", "profile_fingerprint"):
        require(isinstance(info.get(field), str) and re.fullmatch("[0-9a-f]{64}", info[field]), f"{name}: invalid {field}")
    payload = case["request"]
    profile, requested = payload["profile"], payload["local"]["algorithm"]
    require(info.get("profile") == profile and info.get("turn_costs") == (profile == "car"), f"{name}: profile/turn mode differs")
    require(info.get("requested_algorithm") == requested, f"{name}: requested algorithm was lost")
    expected_algorithm = "ch_experimental" if profile == "foot" and requested == "ch_experimental" else "flexible"
    require(info.get("algorithm") == expected_algorithm, f"{name}: wrong selected backend")
    if profile == "car" and requested == "ch_experimental":
        require(info.get("fallback_reason") == "TURN_COST_PROFILE_UNSUPPORTED", f"{name}: missing explicit turn-profile fallback")
    else:
        require("fallback_reason" not in info, f"{name}: unexpected fallback")
    if expected_algorithm == "ch_experimental":
        ch = info.get("ch_matrix", {})
        expected_forward = 1 if invalid else 2
        require(info.get("experimental") is True and info.get("optimality_verification") == "flexible_each_origin", f"{name}: missing certification")
        require(ch.get("backward_searches") == 2 and ch.get("forward_searches") == expected_forward
                and ch.get("certificate_searches") == expected_forward, f"{name}: CH search/dedup counters differ")
        require(info.get("searches") == 2 + 2 * expected_forward, f"{name}: total searches differ")
        require(ch.get("candidate_optimality_guaranteed") is False
                and ch.get("corrected_cells_basis") == "distinct_origin_coordinate_by_destination_index", f"{name}: candidate limits/basis missing")
        require(integer(ch.get("corrected_cells")) and 0 <= ch["corrected_cells"] <= expected_forward * 3, f"{name}: invalid correction count")
        require(info["visited_states"] == ch.get("candidate_visited_states", -1) + ch.get("certificate_visited_states", -1), f"{name}: aggregate state budget counters differ")
        for field in ("bucket_entries", "metric_cache_entries", "unpacked_original_edges"):
            require(integer(ch.get(field)) and ch[field] > 0, f"{name}: no genuine {field}")
        preparation = info.get("ch_preparation", {})
        require(preparation.get("graph_fingerprint") == info["graph_fingerprint"]
                and preparation.get("profile_fingerprint") == info["profile_fingerprint"]
                and preparation.get("traversal") == "node", f"{name}: CH preparation identity differs")
        for field in ("ch_bucket_preparation", "ch_candidates", "ch_verification"):
            require(number(info.get("timings_ms", {}).get(field)), f"{name}: missing CH phase timing {field}")
    else:
        require(info.get("searches") == (1 if invalid else 2), f"{name}: flexible origin dedup differs")
    if invalid:
        hint = next((hint for hint in body.get("hints", []) if hint.get("details") == "INVALID_SNAP"), {})
        require(hint.get("invalid_from_points") == [1], f"{name}: invalid origin index hint missing")
        require(info.get("from_snapped_points", [None, None])[1] == {"coordinates": None, "snap_distance_m": None}, f"{name}: invalid snapped fields differ")
    return info


def error_checks(case, code):
    require(case["http_status"] == 400, f"{case['name']}: expected HTTP 400")
    body = case["response"]
    require(isinstance(body.get("message"), str) and any(hint.get("details") == code for hint in body.get("hints", [])), f"{case['name']}: wrong error code")
    require(not any(metric in body for metric in ARRAYS), f"{case['name']}: error leaked partial matrix")


def closed_ports():
    details, ports = {}, {}
    for port in (8997, 8998):
        attempts = []
        addresses = {(family, kind, protocol, address) for family, kind, protocol, _, address
                     in socket.getaddrinfo("localhost", port, type=socket.SOCK_STREAM)}
        for family, kind, protocol, address in sorted(addresses, key=str):
            with socket.socket(family, kind, protocol) as connection:
                connection.settimeout(1)
                try:
                    result = connection.connect_ex(address)
                except OSError as error:
                    result = error.errno
            refused = result in {errno.ECONNREFUSED, 10061}
            attempts.append({"address": list(address), "family": family, "connect_ex": result, "connection_refused": refused})
        ports[str(port)] = bool(attempts) and all(row["connection_refused"] for row in attempts)
        details[str(port)] = attempts
    return ports, details


def confirm_stopped(args, report_path, jar):
    prior = report_path.read_bytes()
    report = parse_json(prior)
    require(report.get("status") == "PASS" and report.get("server_stopped") is False, "Expected a passing, not-yet-stopped report")
    require(report.get("source_commit") == args.source_commit, "Source commit differs from the live smoke")
    require(sha256(jar) == report.get("artifact_sha256"), "JAR changed after the live smoke")
    repository = Path(__file__).resolve().parents[1]
    require(report.get("config", {}).get("path") == "docs/matrix/config-matrix.yml"
            and sha256(repository / report["config"]["path"]) == report["config"]["sha256"], "Config changed after the live smoke")
    ports, details = closed_ports()
    require(all(ports.values()), f"Both isolated app/admin ports must refuse connections: {details}")
    archive = report_path.with_name(report_path.stem + ".pre-stop" + report_path.suffix)
    save_new(archive, prior)
    report.update(server_stopped=True, ports_closed=ports, server_stop_confirmation={"confirmed_utc": utc(), "port_checks": details,
                  "artifact_sha256_unchanged": sha256(jar), "config_sha256_unchanged": report["config"]["sha256"],
                  "preserved_pre_stop_report": str(archive), "pre_stop_report_sha256": digest_bytes(prior)})
    temporary = report_path.with_name(report_path.name + ".stopped-update.tmp")
    save_new(temporary, encoded(report))
    os.replace(temporary, report_path)
    print(json.dumps({"status": "PASS", "server_stopped": True, "report": str(report_path), "sha256": sha256(report_path)}))


def main():
    repository = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-commit", required=True, help="Complete source commit embedded in the final clean GraphHopper JAR")
    parser.add_argument("--jar", type=Path, default=repository / "web/target/graphhopper-web-11.0-SNAPSHOT.jar")
    parser.add_argument("--output", type=Path, default=repository / "docs/matrix/ch-matrix-live-smoke.json")
    parser.add_argument("--url", default="http://localhost:8997")
    parser.add_argument("--timeout", type=float, default=60)
    parser.add_argument("--check-route", action="store_true", help="Also capture an existing prepared-CH /route response")
    parser.add_argument("--confirm-stopped", action="store_true", help="After external shutdown, verify both ports refuse connections")
    args = parser.parse_args()
    require(re.fullmatch("[0-9a-f]{40}", args.source_commit), "Expected the complete 40-character source commit")
    require(args.timeout > 0, "Timeout must be positive")
    url = urllib.parse.urlparse(args.url)
    require(url.scheme == "http" and url.hostname in {"localhost", "127.0.0.1", "::1"} and url.port == 8997
            and not url.path.strip("/") and not url.query and not url.fragment, "Smoke URL must be the isolated loopback app on port 8997")
    jar, output = args.jar.resolve(), args.output.resolve()
    if args.confirm_stopped:
        confirm_stopped(args, output, jar)
        return 0
    raw_directory = output.parent / (output.stem + "-raw")
    require(not output.exists(), f"Refusing to overwrite an existing smoke report: {output}")
    require(not raw_directory.exists(), f"Refusing to overwrite raw responses: {raw_directory}")
    identity = artifact_identity(jar, args.source_commit)
    config = repository / "docs/matrix/config-matrix.yml"
    report = {"status": "RUNNING", "started_utc": utc(), "source_commit": args.source_commit,
              "artifact_sha256": identity["sha256"], "artifact": identity,
              "config": {"path": "docs/matrix/config-matrix.yml", "sha256": sha256(config)},
              "base_url": args.url.rstrip("/"), "server_stopped": False, "ports_closed": {"8997": False, "8998": False},
              "script_sha256": sha256(Path(__file__)), "cases": {}, "checks": []}
    output.parent.mkdir(parents=True, exist_ok=True)
    raw_directory.mkdir()
    try:
        specs = [("foot_experimental", "foot", "ch_experimental"), ("foot_flexible", "foot", "flexible"),
                 ("foot_auto", "foot", "auto"), ("car_experimental", "car", "ch_experimental"), ("car_flexible", "car", "flexible")]
        infos = {}
        for name, profile, algorithm in specs:
            case = exchange(name, request_payload(profile, algorithm), report["base_url"], raw_directory, args.timeout)
            case["name"] = name
            report["cases"][name] = case
            infos[name] = matrix_checks(case, args.source_commit)
        for actual, expected in (("foot_experimental", "foot_flexible"), ("foot_auto", "foot_flexible"), ("car_experimental", "car_flexible")):
            for metric in ARRAYS:
                require(report["cases"][actual]["response"][metric] == report["cases"][expected]["response"][metric], f"{actual}/{expected}: metric arrays differ")
            require(report["cases"][actual]["response"]["hints"] == report["cases"][expected]["response"]["hints"], f"{actual}/{expected}: hints differ")
        require(len({info["graph_fingerprint"] for info in infos.values()}) == 1, "Matrix requests used different loaded graph identities")
        report["graph_fingerprint"] = infos["foot_experimental"]["graph_fingerprint"]
        report["profile_fingerprints"] = {profile: infos[profile + "_experimental"]["profile_fingerprint"] for profile in ("foot", "car")}
        case = exchange("invalid_snap", request_payload("foot", "ch_experimental", invalid=True), report["base_url"], raw_directory, args.timeout)
        case["name"] = "invalid_snap"; report["cases"]["invalid_snap"] = case
        matrix_checks(case, args.source_commit, invalid=True)
        case = exchange("fail_fast", request_payload("foot", "ch_experimental", invalid=True, fail_fast=True), report["base_url"], raw_directory, args.timeout)
        case["name"] = "fail_fast"; report["cases"]["fail_fast"] = case
        error_checks(case, "INVALID_SNAP")
        payload = request_payload("foot", "ch_experimental"); payload["headings"] = [0, 0, 0]
        case = exchange("unsupported_request", payload, report["base_url"], raw_directory, args.timeout)
        case["name"] = "unsupported_request"; report["cases"]["unsupported_request"] = case
        error_checks(case, "INVALID_REQUEST")
        if args.check_route:
            query = urllib.parse.urlencode([("point", f"{A[1]},{A[0]}"), ("point", f"{C[1]},{C[0]}"),
                                           ("profile", "foot"), ("instructions", "false"), ("calc_points", "false")])
            route = exchange("existing_route", None, report["base_url"], raw_directory, args.timeout, "GET", "/route?" + query)
            require(route["http_status"] == 200 and route["response"].get("paths"), "Existing route endpoint failed")
            report["existing_route"] = route
        require(sha256(jar) == report["artifact_sha256"], "JAR changed during smoke requests")
        require(sha256(config) == report["config"]["sha256"], "Config changed during smoke requests")
        report["checks"] = ["duplicate_3x3_exact_arrays", "foot_bucket_and_independent_certificate_counters", "auto_stays_flexible",
                            "car_explicit_turn_profile_fallback", "whole_request_state_counters", "source_commit_and_units",
                            "invalid_snap_null_row", "fail_fast_400", "unsupported_option_400", "artifact_sha_unchanged"]
        report["status"] = "PASS"
    except Exception as error:
        report["status"] = "FAIL"
        report["failure"] = f"{type(error).__name__}: {error}"
    finally:
        report["completed_utc"] = utc()
        save_new(output, encoded(report))
    print(json.dumps({"status": report["status"], "server_stopped": False, "report": str(output), "sha256": sha256(output)}))
    if report["status"] != "PASS":
        print(report.get("failure"), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"Release smoke failed: {error}", file=sys.stderr)
        raise SystemExit(2)
