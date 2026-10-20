r"""Freeze full Maven verification evidence before later focused runs replace XML reports.

Use the Python standard library from an existing environment. Example in cmd.exe:
    python scripts\snapshot-matrix-verification.py --log ..\audit\matrix-ch-full-verify.log --output docs\matrix\ch-matrix-full-verification.json --command mvn -B verify -Dmatrix.fixture.report=../docs/matrix/ch-matrix-fixture-validation.json

The output must not exist. A successful log alone is insufficient: every XML suite
must match a class-level result in this same log, and its modification time must
fall within the logged build interval. Run this after Maven has finished and before
any subsequent Maven invocation. Diagnostic tests added or run later need separate
evidence; this snapshot never retroactively includes them.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timedelta, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET


COUNTS = ("tests", "failures", "errors", "skipped")
POM_NS = {"m": "http://maven.apache.org/POM/4.0.0"}
ANSI = re.compile(r"\x1b\[[0-?]*[ -/]*[@-~]")
PLUGIN = re.compile(r"^\[INFO\] --- ([^: ]+):[^: ]+:([^ ]+) \([^\r\n]*\) @ ([^ ]+) ---\s*$")
SUITE_RESULT = re.compile(
    r"^\[(?:INFO|WARNING|ERROR)\] Tests run:\s*(\d+), Failures:\s*(\d+), Errors:\s*(\d+), Skipped:\s*(\d+)"
    r"[^\r\n]* - in (\S+)\s*$"
)
TIMESTAMP_SLOP_SECONDS = 3


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def display_path(path: Path, root: Path) -> str:
    return os.path.relpath(path, root).replace("\\", "/")


def duration_seconds(value: str) -> float:
    seconds = re.fullmatch(r"(\d+(?:\.\d+)?) s", value)
    if seconds:
        return float(seconds.group(1))
    clock = re.fullmatch(r"(\d+):(\d{2}) (min|h)", value)
    if clock:
        first, second, unit = clock.groups()
        require(int(second) < 60, "Invalid Maven duration: " + value)
        return (int(first) * 60 + int(second)) * (60 if unit == "h" else 1)
    raise ValueError("Unsupported Maven duration format; cannot bound report freshness: " + value)


def module_inventory(root: Path) -> dict[str, Path]:
    """Use declared reactor modules, not arbitrary directories left in the worktree."""
    modules: dict[str, Path] = {}
    pending = [root]
    while pending:
        directory = pending.pop(0).resolve()
        require(directory.is_relative_to(root), "Reactor module escapes repository: " + str(directory))
        pom = ET.parse(directory / "pom.xml").getroot()
        artifact = pom.findtext("m:artifactId", namespaces=POM_NS)
        require(bool(artifact) and artifact not in modules, "Missing or duplicate reactor artifact ID")
        modules[artifact] = directory
        for module in pom.findall("m:modules/m:module", POM_NS):
            require(bool(module.text), "Empty reactor module")
            pending.append(directory / module.text)
    return modules


def logged_run(raw: bytes, modules: dict[str, Path]) -> tuple[dict, dict]:
    text = ANSI.sub("", raw.decode("utf-8", errors="replace"))
    successes = re.findall(r"^\[INFO\] BUILD SUCCESS\s*$", text, re.MULTILINE)
    require(len(successes) == 1, "Expected exactly one completed successful Maven build")
    require(not re.search(r"^\[(?:INFO|ERROR)\] BUILD FAILURE\s*$", text, re.MULTILINE), "Log contains BUILD FAILURE")
    durations = re.findall(r"^\[INFO\] Total time:\s*([^\r\n]+)", text, re.MULTILINE)
    finishes = re.findall(r"^\[INFO\] Finished at:\s*([^\r\n]+)", text, re.MULTILINE)
    require(len(durations) == len(finishes) == 1, "Expected one Maven duration and finish timestamp")
    finished = datetime.fromisoformat(finishes[0].strip())
    require(finished.tzinfo is not None, "Maven finish timestamp needs an explicit timezone")
    duration = duration_seconds(durations[0].strip())
    require(duration > 0, "Maven duration must be positive")
    started = finished - timedelta(seconds=duration)
    require(finished <= datetime.now(timezone.utc) + timedelta(seconds=TIMESTAMP_SLOP_SECONDS), "Maven finish timestamp is in the future")
    # Maven omits padding dots when a project's human-readable name fills the
    # display column (the hand-crafted Java client is such a module here).
    reactor = re.findall(r"^\[INFO\] (.+?)\s+(SUCCESS|FAILURE|SKIPPED)\s+\[[^\r\n]*\]\s*$", text, re.MULTILINE)
    require(len(reactor) == len(modules), "Reactor summary does not cover every declared module")
    require(all(outcome == "SUCCESS" for _, outcome in reactor), "Reactor summary contains a failed or skipped module")
    results, seen_artifacts = {}, set()
    current = None
    for line in text.splitlines():
        plugin = PLUGIN.match(line)
        if plugin:
            name, goal, artifact = plugin.groups()
            require(artifact in modules, "Log plugin refers to an undeclared reactor artifact: " + artifact)
            seen_artifacts.add(artifact)
            phase = "surefire" if name in {"surefire", "maven-surefire-plugin"} and goal == "test" else (
                "failsafe" if name in {"failsafe", "maven-failsafe-plugin"} and goal == "integration-test" else None)
            current = (artifact, phase) if phase else None
        summary = SUITE_RESULT.match(line)
        if summary:
            require(current is not None, "Class result outside a recognized Surefire/Failsafe test phase: " + line)
            *numbers, name = summary.groups()
            key = (*current, name)
            require(key not in results, "A suite was executed more than once; use an unambiguous single-run log: " + str(key))
            results[key] = dict(zip(COUNTS, map(int, numbers)))
    require(seen_artifacts == set(modules), "Log does not show all declared reactor artifacts")
    require(bool(results), "Log contains no class-level test results")
    return {
        "wall_time_log": durations[0].strip(), "finished_at_log": finishes[0].strip(),
        "started_at_derived_utc": started.astimezone(timezone.utc).isoformat(),
        "finished_at_utc": finished.astimezone(timezone.utc).isoformat(),
        "timestamp_tolerance_seconds": TIMESTAMP_SLOP_SECONDS,
        "reactor_summary": [{"name": name.strip().rstrip(". "), "status": status} for name, status in reactor],
    }, results


def collect_reports(root: Path, modules: dict[str, Path], run: dict, logged: dict) -> tuple[list, list, dict]:
    started = datetime.fromisoformat(run["started_at_derived_utc"]).timestamp() - TIMESTAMP_SLOP_SECONDS
    finished = datetime.fromisoformat(run["finished_at_utc"]).timestamp() + TIMESTAMP_SLOP_SECONDS
    module_rows, suites, seen = [], [], set()
    for artifact, directory in sorted(modules.items(), key=lambda item: display_path(item[1], root)):
        module = display_path(directory, root)
        totals = dict.fromkeys(COUNTS, 0)
        phases = {}
        for phase in ("surefire", "failsafe"):
            phase_totals = dict.fromkeys(COUNTS, 0)
            reports = sorted((directory / "target" / (phase + "-reports")).glob("TEST-*.xml"))
            for report in reports:
                stat_before = report.stat()
                require(started <= stat_before.st_mtime <= finished,
                        "Stale or later-overwritten XML report outside this build interval: " + display_path(report, root))
                raw = report.read_bytes()
                suite = ET.fromstring(raw)
                require(suite.tag == "testsuite", "Expected a Surefire/Failsafe testsuite root: " + str(report))
                counts = {key: int(suite.attrib.get(key, 0)) for key in COUNTS}
                require(all(value >= 0 for value in counts.values()), "Negative suite counts: " + str(report))
                key = (artifact, phase, suite.attrib.get("name"))
                require(key in logged, "XML suite absent from this exact Maven log: " + str(key))
                require(key not in seen, "Duplicate XML suite: " + str(key))
                require(counts == logged[key], "XML counts disagree with this Maven log: " + str(key))
                seen.add(key)
                cases, case_counts = [], dict.fromkeys(COUNTS, 0)
                for case in suite.findall("testcase"):
                    outcomes = [(tag, status) for tag, status in (("failure", "FAIL"), ("error", "ERROR"), ("skipped", "SKIPPED")) if case.find(tag) is not None]
                    require(len(outcomes) <= 1, "A testcase has conflicting outcomes: " + str(key))
                    case_counts["tests"] += 1
                    entry = {"name": case.attrib.get("name"), "classname": case.attrib.get("classname"),
                             "time_seconds": case.attrib.get("time"), "status": outcomes[0][1] if outcomes else "PASS"}
                    if outcomes:
                        tag, _ = outcomes[0]
                        case_counts[{"failure": "failures", "error": "errors", "skipped": "skipped"}[tag]] += 1
                        detail = case.find(tag)
                        entry["detail"] = {"message": detail.attrib.get("message"), "type": detail.attrib.get("type")}
                    cases.append(entry)
                require(case_counts == counts, "Actual XML testcase outcomes disagree with suite counts: " + str(key))
                require(report.stat().st_mtime_ns == stat_before.st_mtime_ns and report.read_bytes() == raw,
                        "XML report changed while snapshotting: " + str(report))
                for name in COUNTS:
                    totals[name] += counts[name]
                    phase_totals[name] += counts[name]
                suites.append({"module": module, "artifact": artifact, "phase": phase, "name": key[2], **counts,
                               "time_seconds": suite.attrib.get("time"), "report": display_path(report, root),
                               "report_modified_utc": datetime.fromtimestamp(stat_before.st_mtime, timezone.utc).isoformat(),
                               "sha256": sha256(raw), "testcases": cases})
            phases[phase] = {"suites": len(reports), **phase_totals}
        totals["passed"] = totals["tests"] - totals["failures"] - totals["errors"] - totals["skipped"]
        module_rows.append({"module": module, "artifact": artifact, **totals, "phases": phases})
    require(seen == set(logged), "Maven log has test suites without corresponding current XML: " + str(sorted(set(logged) - seen)))
    totals = {name: sum(row[name] for row in module_rows) for name in (*COUNTS, "passed")}
    require(totals["failures"] == totals["errors"] == 0, "Full verification has failed tests")
    return module_rows, suites, totals


def git_output(root: Path, *args: str) -> str:
    return subprocess.run(["git", *args], cwd=root, capture_output=True, text=True, check=True).stdout.strip()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--log", type=Path, required=True, help="Completed full-reactor Maven log, relative to the repository root")
    parser.add_argument("--output", type=Path, required=True, help="New JSON path; existing output is never overwritten")
    parser.add_argument("--command", nargs=argparse.REMAINDER, help="Exact Maven argv; put this option last. Omit if the invocation is unknown.")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    log = (root / args.log).resolve() if not args.log.is_absolute() else args.log.resolve()
    output = (root / args.output).resolve() if not args.output.is_absolute() else args.output.resolve()
    require(not output.exists(), "Preserve prior evidence; choose a new output path: " + str(output))
    require(args.command is None or bool(args.command), "--command needs exact argv")
    if args.command is not None:
        require("verify" in args.command, "Exact command must contain the verify goal")
    log_raw = log.read_bytes()
    modules = module_inventory(root)
    run, logged = logged_run(log_raw, modules)
    module_rows, suites, totals = collect_reports(root, modules, run, logged)
    status = git_output(root, "status", "--short")
    require(log.read_bytes() == log_raw, "Maven log changed during snapshot; wait for the build to finish")
    report = {
        "created_utc": datetime.now(timezone.utc).isoformat(), "status": "PASS_FULL_MAVEN_VERIFY",
        "scope": "Full declared Maven reactor. Conditional tests are counted exactly as XML records them; skipped opt-in tests are not counted as passed. Later diagnostics require separate evidence.",
        "command": args.command, "command_provenance": "caller_supplied_exact_argv" if args.command is not None else "not_supplied",
        "exit_code": None, "success_evidence": "One BUILD SUCCESS, all declared reactor entries SUCCESS, matching current class-level XML/log results, zero failures/errors",
        "cwd": str(root), "head_commit": git_output(root, "rev-parse", "HEAD"),
        "head_commit_scope": "Current checkout HEAD; uncommitted implementation changes are indicated by worktree status, not represented by this commit alone",
        "dirty_worktree": bool(status), "worktree_status_at_snapshot": status,
        "log": display_path(log, root), "log_sha256": sha256(log_raw), **run,
        "freshness_guard": "Exact suite/count coverage in log plus XML modification times inside derived Maven build interval; XML and log bytes rechecked unchanged during collection",
        "totals": totals, "modules": module_rows, "suites": suites,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(report, stream, indent=2)
        stream.write("\n")
    print(json.dumps({"destination": str(output), "sha256": sha256(output.read_bytes()), "totals": totals,
                      "conditional_skip_suites": [{"name": suite["name"], "tests": suite["tests"], "skipped": suite["skipped"]}
                                                  for suite in suites if suite["skipped"] and ".matrix." in suite["name"]]}))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, ET.ParseError, subprocess.CalledProcessError) as failure:
        print("Verification snapshot rejected: " + str(failure), file=sys.stderr)
        raise SystemExit(1)
