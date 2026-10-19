"""Refresh source/artifact/data identity from saved evidence, without running builds.

Completed validation and runtime evidence override the initial audit defaults.
This script records facts; it never automatically promotes a release gate.
"""
import ctypes
from datetime import datetime, timezone
import gzip
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def command(args):
    result = subprocess.run(args, cwd=ROOT, capture_output=True, text=True)
    return {"argv": args, "exit_code": result.returncode, "stdout": result.stdout.strip(), "stderr": result.stderr.strip()}


def sha(path):
    return hashlib.sha256((ROOT / path).read_bytes()).hexdigest()


class MemoryStatus(ctypes.Structure):
    _fields_ = [("length", ctypes.c_ulong), ("memory_load", ctypes.c_ulong),
                ("total_phys", ctypes.c_ulonglong), ("avail_phys", ctypes.c_ulonglong),
                ("total_page", ctypes.c_ulonglong), ("avail_page", ctypes.c_ulonglong),
                ("total_virtual", ctypes.c_ulonglong), ("avail_virtual", ctypes.c_ulonglong),
                ("avail_extended", ctypes.c_ulonglong)]


memory = None
cpu_name = platform.processor()
if os.name == "nt":
    status = MemoryStatus()
    status.length = ctypes.sizeof(status)
    if ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status)):
        memory = status.total_phys
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r"HARDWARE\DESCRIPTION\System\CentralProcessor\0") as key:
            cpu_name = winreg.QueryValueEx(key, "ProcessorNameString")[0].strip()
    except OSError:
        pass

fixture = "core/files/monaco.osm.gz"
with gzip.open(ROOT / fixture, "rb") as stream:
    bounds = None
    node_bounds = {"minlat": 90.0, "minlon": 180.0, "maxlat": -90.0, "maxlon": -180.0}
    node_count = 0
    for _, element in ET.iterparse(stream, events=("end",)):
        if element.tag == "bounds":
            bounds = {key: float(value) for key, value in element.attrib.items()}
        if element.tag == "node":
            lat, lon = float(element.attrib["lat"]), float(element.attrib["lon"])
            node_count += 1
            node_bounds["minlat"] = min(node_bounds["minlat"], lat)
            node_bounds["maxlat"] = max(node_bounds["maxlat"], lat)
            node_bounds["minlon"] = min(node_bounds["minlon"], lon)
            node_bounds["maxlon"] = max(node_bounds["maxlon"], lon)
        element.clear()

commands = [command(args) for args in (["git", "rev-parse", "HEAD"], ["git", "branch", "--show-current"],
                                        ["git", "remote", "-v"], ["git", "status", "--short"],
                                        ["java", "-version"], ["javac", "-version"],
                                        ["cmd.exe", "/d", "/c", "mvn", "-version"])]
config = "docs/matrix/config-matrix.yml"
models = ["core/src/main/resources/com/graphhopper/custom_models/foot.json",
          "core/src/main/resources/com/graphhopper/custom_models/car.json"]
report = {
    "status": "AUDIT_SNAPSHOT_NOT_RELEASE_CERTIFICATION",
    "created_utc": datetime.now(timezone.utc).isoformat(),
    "assessed_release": "11.1", "assessed_upstream_commit": "ebb578f4e94db72e82f2f8d3bc69417758e16e1c",
    "actual_upstream_commit": commands[0]["stdout"], "fork_commit": None,
    "artifact_version": "11.0-SNAPSHOT", "branch": commands[1]["stdout"],
    "dirty_worktree": bool(commands[3]["stdout"]), "worktree_snapshot": commands[3]["stdout"],
    "jdk": commands[4], "javac": commands[5], "maven": commands[6],
    "os": platform.platform(), "cpu": cpu_name, "processor_identifier": platform.processor(), "logical_cpu_count": os.cpu_count(),
    "ram_bytes": memory, "jvm_flags": None,
    "data": {"source": fixture,
             "upstream_url": "https://github.com/graphhopper/graphhopper/blob/ebb578f4e94db72e82f2f8d3bc69417758e16e1c/core/files/monaco.osm.gz",
             "acquired_utc": None, "osm_snapshot_date": None,
             "sha256": sha(fixture), "bbox": bounds,
             "source_node_bounds": node_bounds if node_count else None, "source_node_count": node_count,
             "acquisition_note": "Bundled upstream fixture obtained with the pinned source checkout; original OSM acquisition date is unknown."},
    "configuration_path": config, "configuration_sha256": sha(config),
    "custom_models_sha256": {name: sha(name) for name in models},
    "graph_fingerprint": None, "profile_fingerprint": None,
    "profiles": [{"name": "foot", "turn_costs": False, "custom_model_files": ["foot.json"]},
                 {"name": "car", "turn_costs": True, "custom_model_files": ["car.json"],
                  "vehicle_types": ["motorcar", "motor_vehicle"], "u_turn_costs_seconds": 60}],
    "encoded_values": "car_access,car_average_speed,road_access,foot_access,foot_priority,foot_average_speed,foot_road_access,hike_rating,mtb_rating,country,road_class".split(","),
    "graph_directory": "target/matrix-monaco-server", "server_ports": {"application": 8997, "admin": 8998},
    "algorithm": None, "threads": None, "seed": 20261019, "commands": commands,
    "gates": {"G0": "IN_PROGRESS", "G1": "NOT_RUN", "G2": "NOT_RUN", "G3": "NOT_RUN", "G4": "NOT_RUN"},
    "unknown_value_notes": {"fork_commit": "Development changes are not committed at this audit snapshot.",
                            "jvm_flags": "Recorded per executed test/server/benchmark, not assumed from documentation.",
                            "graph_fingerprint": "Server fixture import has not been executed at this snapshot.",
                            "profile_fingerprint": "Capture from implemented server/benchmark after import.",
                            "algorithm": "No completed matrix run at this audit snapshot.",
                            "threads": "No completed matrix run at this audit snapshot."},
    "unresolved_issues": ["No user configuration, service details, graph cache or Manchester/regional PBF was supplied.",
                          "Monaco fixture coverage cannot substitute for regional release validation.",
                          "Baseline/release outcomes are maintained in the validation report and must not be inferred from this audit snapshot."],
}
destination = ROOT / "docs/matrix/provenance.json"


def saved(name, default=None):
    path = ROOT / "docs/matrix" / name
    return json.loads(path.read_text(encoding="utf-8")) if path.is_file() else default


def sha_file(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


previous = saved("provenance.json", {})
validation = saved("validation-summary.json", {})
full = saved("full-verification.json", {})
baseline = saved("baseline-report.json", {})
monaco = saved("fixture-validation.json", {})
regional = saved("regional-data.json", {})
regional_validation = saved("regional-validation.json", {})
reconciliation = saved("regional-reconciliation.json", {})
benchmark = saved("benchmark-metadata.json", {})
http_benchmark = saved("http-benchmark-report.json", {})
gates = validation.get("gates", previous.get("gates", report["gates"]))
report["gates"] = {key: value.get("status") if isinstance(value, dict) else value for key, value in gates.items()}
report["status"] = "EVIDENCE_SNAPSHOT_NOT_AUTOMATIC_RELEASE_CERTIFICATION"
report["previous_snapshot_utc"] = previous.get("created_utc")
report["checkout_head"] = commands[0]["stdout"]
report["actual_upstream_commit"] = report["assessed_upstream_commit"]
report["source_worktree_note"] = "Development remains intentionally uncommitted. The packaged artifact embeds base commit plus dirty state; its SHA-256 identifies the actual binary."
source_names = set(command(["git", "diff", "--name-only"])["stdout"].splitlines())
source_names.update(command(["git", "ls-files", "--others", "--exclude-standard"])["stdout"].splitlines())
source_names = sorted(name for name in source_names if (ROOT / name).is_file() and
                      (name.endswith(".java") or name == "pom.xml" or name.endswith("/pom.xml")
                       or name.startswith("scripts/") and name.endswith(".py") or name == "run-matrix.cmd"))
report["changed_source_sha256"] = {name: sha(name) for name in source_names}
report["changed_source_manifest_fingerprint"] = hashlib.sha256(json.dumps(report["changed_source_sha256"], sort_keys=True, separators=(",", ":")).encode()).hexdigest()
report["source_manifest_scope"] = "Current changed Java/POM/tool/launcher inputs; this is a content snapshot, not a claim of a separate Git commit."
report["sample_configuration_sha256"] = {name: sha(name) for name in ("docs/matrix/config-matrix.yml", "docs/matrix/config-manchester.yml") if (ROOT / name).is_file()}
report["configuration_role"] = "Example configs; actual loaded identity and flags belong to each runtime evidence record."

artifact = {"status": "NOT_BUILT", "path": "web/target/graphhopper-web-11.0-SNAPSHOT.jar"}
jar = ROOT / artifact["path"]
if jar.is_file():
    artifact.update(status="PRESENT_CHECKSUM_RECORDED", size_bytes=jar.stat().st_size, sha256=sha_file(jar))
    with zipfile.ZipFile(jar) as archive:
        bad_member = archive.testzip()
        artifact["zip_crc_status"] = "PASS" if bad_member is None else "FAIL"
        artifact["zip_crc_bad_member"] = bad_member
        resources = {}
        for name in ("META-INF/MANIFEST.MF", "META-INF/maven/com.graphhopper/graphhopper-web/pom.properties", "com/graphhopper/gitinfo", "com/graphhopper/builddate", "com/graphhopper/version"):
            if name in archive.namelist():
                resources[name] = archive.read(name).decode("utf-8", errors="replace").replace("\r", "").strip()
        artifact["embedded_build_resources"] = resources
        gitinfo = resources.get("com/graphhopper/gitinfo", "").splitlines()
        if len(gitinfo) == 6:
            artifact.update(embedded_commit=gitinfo[1], embedded_branch=gitinfo[4], embedded_dirty=gitinfo[5] == "true",
                            embedded_build_identity=gitinfo[1] + ("+dirty" if gitinfo[5] == "true" else ""))
    sidecar = jar.with_suffix(jar.suffix + ".sha256")
    sidecar.write_text(artifact["sha256"] + "  " + jar.name + "\n", encoding="ascii")
    artifact["checksum_sidecar"] = str(sidecar.relative_to(ROOT)).replace("\\", "/")
report["artifact"] = artifact

runtime = {}
if monaco:
    runtime["monaco_correctness"] = {"status": monaco["status"], "graph_fingerprint": monaco.get("graph_fingerprint"),
                                    "profile_fingerprints": {name: values.get("profile_fingerprint") for name, values in monaco.get("profile_counts", {}).items()},
                                    "evidence_sha256": sha("docs/matrix/fixture-validation.json")}
if reconciliation:
    runtime["regional_reconciliation"] = {"status": reconciliation["status"], "graph_fingerprint": reconciliation.get("replay_graph_fingerprint"),
                                          "profile_fingerprint": reconciliation.get("replay_profile_fingerprint"), "profile": "car",
                                          "build_commit": reconciliation.get("replay_build_commit"), "matrix_workers": regional_validation.get("actual_matrix_workers"),
                                          "remaining_unexplained_mismatches": reconciliation.get("remaining_unexplained_mismatches"),
                                          "evidence_sha256": sha("docs/matrix/regional-reconciliation.json")}
    flags_path = ROOT.parent / "audit/regional-replay-jvm-flags.txt"
    if flags_path.is_file():
        runtime["regional_reconciliation"]["captured_jcmd_vm_flags"] = flags_path.read_text(encoding="utf-8").strip()
if benchmark:
    runtime["in_process_benchmark"] = {"status": benchmark.get("status"), "jvm_arguments": benchmark.get("jvm_arguments"),
                                       "completed_measured_samples": benchmark.get("completed_measured_samples"),
                                       "scope_note": "Monaco fixture only; Greater Manchester data was acquired separately.",
                                       "evidence_sha256": sha("docs/matrix/benchmark-metadata.json")}
for path in sorted((ROOT / "docs/matrix").rglob("http-benchmark-report.json")):
    http_report = json.loads(path.read_text(encoding="utf-8"))
    relative = str(path.relative_to(ROOT / "docs/matrix")).replace("\\", "/")
    runtime["http_benchmark:" + relative] = {"status": http_report.get("status"), "matrix_provenance": http_report.get("matrix_provenance"),
                                           "evidence_path": relative, "evidence_sha256": sha_file(path)}
for path in sorted((ROOT / "docs/matrix").rglob("http-smoke*.json")):
    smoke = json.loads(path.read_text(encoding="utf-8"))
    relative = str(path.relative_to(ROOT / "docs/matrix")).replace("\\", "/")
    runtime["http_smoke:" + relative] = {"status": smoke.get("status"), "checked_cells": smoke.get("checked_cells"),
                         "matrix_info": smoke.get("matrix_info"), "evidence_sha256": sha_file(path)}
for path in sorted((ROOT / "docs/matrix").rglob("ch-weight-reconciliation.json")):
    proof = json.loads(path.read_text(encoding="utf-8"))
    relative = path.relative_to(ROOT / "docs/matrix").as_posix()
    runtime["ch_weight_reconciliation:" + relative] = {key: proof.get(key) for key in (
        "status", "original_report_sha256", "original_requests_sha256", "original_samples_sha256",
        "replay_graph_fingerprint", "replay_profile_fingerprints", "replay_build_commit", "indexed_flagged_cases",
        "unique_profile_coordinate_pairs", "retained_original_issue_records_checked", "aggregate_original_flag_instances",
        "remaining_unexplained_cases", "historic_evidence_scope")}
    runtime["ch_weight_reconciliation:" + relative].update(evidence_path=relative, evidence_sha256=sha_file(path))
report["runtime_evidence"] = runtime
for name, key in (("binary-source-binding.json", "production_binary_binding"), ("final-review.json", "final_review")):
    evidence_record = saved(name)
    if evidence_record is not None:
        report[key] = {"contents": evidence_record, "path": name, "sha256": sha("docs/matrix/" + name)}
report["post_snapshot_ch_precision_diagnostics"] = validation.get("post_snapshot_ch_precision_diagnostics", {})
deployment = saved("runtime-manifest.json")
if deployment is not None:
    report["deployment_runtime_manifest"] = deployment
    report["deployment_runtime_manifest_sha256"] = sha("docs/matrix/runtime-manifest.json")
captured_flags = {}
for pattern in ("*jvm-flags.txt", "*jvm-command-line.txt"):
    for path in sorted((ROOT.parent / "audit").glob(pattern)):
        captured_flags[path.name] = {"contents": path.read_text(encoding="utf-8", errors="replace").strip(), "sha256": sha_file(path)}
report["captured_runtime_jvm_text"] = captured_flags
if regional:
    regional_record = {key: regional.get(key) for key in ("status", "source_page", "resolved_url", "acquired_utc", "size_bytes", "sha256", "boundary", "license")}
    regional_path = Path(regional["local_path"])
    regional_record.update(local_path=str(regional_path), present=regional_path.is_file(),
                           current_sha256_matches_manifest=sha_file(regional_path) == regional["sha256"] if regional_path.is_file() else None,
                           validation_status=reconciliation.get("status", regional_validation.get("status", "NOT_RUN")))
    report["regional_data"] = regional_record
report["server_ports"] = {"monaco_application": 8997, "monaco_admin": 8998, "regional_application": 8999, "regional_admin": 9000}
report["full_maven_verify"] = {key: full.get(key) for key in ("status", "command", "exit_code", "totals", "finished_at_log")}
report["pristine_baseline_status"] = baseline.get("status")
report["validation_summary_sha256"] = sha("docs/matrix/validation-summary.json") if validation else None
report["unknown_value_notes"].update(jvm_flags="No single JVM covers every run. Captured per-run flags appear in runtime_evidence, deployment_runtime_manifest and captured_runtime_jvm_text; absent flags are not guessed.",
                                      graph_fingerprint="Multiple immutable imports exist; use run-specific identities under runtime_evidence.",
                                      profile_fingerprint="Use run/profile-specific identities under runtime_evidence.",
                                      algorithm="Both reference/flexible and independent upstream algorithms were tested; use per-run evidence.",
                                      threads="Measured per-run values are recorded where available; no cross-run count is invented.")
report["limitations"] = validation.get("limitations", previous.get("limitations", []))
report["unresolved_issues"] = ["Gate " + name + " remains " + str(status) + "; see validation-summary.json"
                               for name, status in report["gates"].items() if status not in ("PASS", "PASS_WITH_SCOPED_LIMITATIONS")]
destination.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(json.dumps({"destination": str(destination), "artifact_sha256": artifact.get("sha256"), "jar_crc": artifact.get("zip_crc_status"),
                  "regional_data_matches": report.get("regional_data", {}).get("current_sha256_matches_manifest"), "gates": report["gates"]}))
