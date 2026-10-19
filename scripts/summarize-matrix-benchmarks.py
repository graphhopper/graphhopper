#!/usr/bin/env python3
"""Create measured benchmark analysis only after the full 960-sample run completes.

Reads small retained CSV/JSON evidence; never runs routing, imports or benchmarks.
Re-run after HTTP reports are available to append their named comparisons.
"""
from collections import defaultdict
import csv
from datetime import datetime, timezone
import json
import math
from pathlib import Path
import statistics
import sys

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs/matrix"
WORKLOADS = ("local_1x10", "1x100", "1x1000", "10x10", "10x100", "100x10", "100x100", "duplicate_100x100")
BACKENDS = ("reference", "flexible", "ch_pairwise")


def number(value):
    if value in (None, "", "null"):
        return None
    value = float(value)
    if not math.isfinite(value):
        raise ValueError("Nonfinite benchmark measurement")
    return value


def stat(rows, key, p95=False):
    values = [number(row.get(key)) for row in rows]
    if any(value is None for value in values):
        return None
    return sorted(values)[math.ceil(.95 * len(values)) - 1] if p95 else statistics.median(values)


def ratio(baseline, candidate):
    return baseline / candidate if baseline is not None and candidate is not None and candidate > 0 else None


def fmt(value, decimals=3):
    return "n/a" if value is None else f"{value:,.{decimals}f}"


def mib(value):
    return None if value is None else value / 1024 ** 2


def main():
    metadata_path = DOCS / "benchmark-metadata.json"
    if not metadata_path.is_file():
        raise ValueError("No completed in-process benchmark metadata exists; measurements are not analyzed from partial checkpoints")
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    if metadata.get("status") != "MEASURED" or metadata.get("completed_measured_samples") != 960:
        raise ValueError("The complete 960-sample MEASURED run is required")
    with (DOCS / "benchmark-samples.csv").open(newline="", encoding="utf-8") as stream:
        samples = list(csv.DictReader(stream))
    if len(samples) != 960:
        raise ValueError(f"Expected 960 measured samples, found {len(samples)}")
    groups = defaultdict(list)
    for row in samples:
        groups[row["profile"], row["workload"], row["backend"]].append(row)
    expected = {(profile, workload, backend) for profile in ("foot", "car") for workload in WORKLOADS for backend in BACKENDS}
    if set(groups) != expected:
        raise ValueError("Measured profile/workload/backend grid differs from the configured complete grid")
    identities = metadata.get("matrix_provenance", {})
    reduced = {}
    for key, rows in groups.items():
        if len(rows) != 20 or {int(row["repeat"]) for row in rows} != set(range(20)):
            raise ValueError(f"Missing/duplicate repetitions in {key}")
        for count in ("completed_cells", "reachable_cells", "null_cells"):
            if len({row[count] for row in rows}) != 1:
                raise ValueError(f"Nondeterministic {count} in {key}")
        for row in rows:
            if number(row["completed_cells"]) != number(row["origins"]) * number(row["destinations"]):
                raise ValueError("Completed-cell count does not match the actual dimensions")
            if number(row["reachable_cells"]) + number(row["null_cells"]) != number(row["completed_cells"]):
                raise ValueError("Reachable/null counts do not cover all completed cells")
            if key[2] != "ch_pairwise":
                identity = {field: row.get(field) for field in ("graph_fingerprint", "profile_fingerprint", "build_commit")}
                if not all(identity.values()) or identity != identities.get(key[0]):
                    raise ValueError(f"Missing/inconsistent matrix identity in {key}")
        result = {"profile": key[0], "workload": key[1], "backend": key[2], "repeats": 20,
                  "completed_cells": int(float(rows[0]["completed_cells"])), "reachable_cells": int(float(rows[0]["reachable_cells"])),
                  "null_cells": int(float(rows[0]["null_cells"]))}
        fields = ["elapsed_request_ms", "serialization_ms", "request_plus_serialization_ms", "request_thread_allocated_bytes",
                  "serialization_thread_allocated_bytes", "output_json_bytes", "searches", "pair_calls", "visited_states",
                  "stage_snapping_ms", "stage_query_graph_ms", "stage_backend_calls_ms", "stage_core_search_ms", "stage_scatter_output_ms", "stage_total_ms"]
        for field in fields:
            result["median_" + field] = stat(rows, field)
            result["p95_" + field] = stat(rows, field, True)
        result["request_completed_cells_per_second"] = result["completed_cells"] * 1000 / result["median_elapsed_request_ms"]
        result["request_reachable_cells_per_second"] = result["reachable_cells"] * 1000 / result["median_elapsed_request_ms"]
        total = result["median_request_plus_serialization_ms"]
        result["request_plus_serialization_completed_cells_per_second"] = result["completed_cells"] * 1000 / total if total is not None else None
        result["request_plus_serialization_reachable_cells_per_second"] = result["reachable_cells"] * 1000 / total if total is not None else None
        result["max_heap_pools_peak_sum_bytes"] = max(number(row["heap_pools_peak_sum_bytes"]) for row in rows)
        for field in ("gc_collections", "gc_time_ms", "serialization_gc_collections", "serialization_gc_time_ms"):
            values = [number(row.get(field)) for row in rows]
            result["sum_" + field] = None if any(value is None for value in values) else sum(values)
        reduced[key] = result
    comparisons = []
    for profile in ("foot", "car"):
        for workload in WORKLOADS:
            reference, flexible, ch = [reduced[profile, workload, backend] for backend in BACKENDS]
            comparisons.append({"profile": profile, "workload": workload,
                                "speed_ratio_reference_over_flexible_request": ratio(reference["median_elapsed_request_ms"], flexible["median_elapsed_request_ms"]),
                                "speed_ratio_ch_over_flexible_request": ratio(ch["median_elapsed_request_ms"], flexible["median_elapsed_request_ms"]),
                                "allocation_ratio_reference_over_flexible": ratio(reference["median_request_thread_allocated_bytes"], flexible["median_request_thread_allocated_bytes"]),
                                "allocation_ratio_ch_over_flexible": ratio(ch["median_request_thread_allocated_bytes"], flexible["median_request_thread_allocated_bytes"])})
    provenance = json.loads((DOCS / "provenance.json").read_text(encoding="utf-8")) if (DOCS / "provenance.json").exists() else {}
    validation = json.loads((DOCS / "validation-summary.json").read_text(encoding="utf-8")) if (DOCS / "validation-summary.json").exists() else {}
    http = []
    for path in sorted(DOCS.rglob("http-benchmark-report.json")):
        report = json.loads(path.read_text(encoding="utf-8"))
        retained_issues = [issue for case in report.get("comparisons", []) for issue in case.get("mismatches_first_100", [])]
        indexed_cases = {(case.get("profile"), case.get("workload"), issue.get("from_index"), issue.get("to_index"))
                         for case in report.get("comparisons", []) for issue in case.get("mismatches_first_100", [])}
        diagnostic_kinds = sorted({message.partition(":")[0] for issue in retained_issues for message in issue.get("issues", [])})
        http.append({"path": str(path.relative_to(DOCS)).replace("\\", "/"), "status": report.get("status"),
                     "summary": report.get("summary", []), "comparisons": report.get("comparisons", []),
                     "connections": report.get("connections"), "requests_sha256": report.get("requests_sha256"),
                     "warmups": report.get("warmups"), "repeats": report.get("repeats"), "url": report.get("url"),
                     "measured_samples": len(report.get("samples", [])), "matrix_provenance": report.get("matrix_provenance"),
                     "server_config_path": report.get("server_config_path"), "server_config_sha256": report.get("server_config_sha256"),
                     "indexed_cases_in_retained_diagnostics": len(indexed_cases), "retained_diagnostic_kinds": diagnostic_kinds,
                     "completed_cells_per_workload": sorted({row["completed_cells_min"] for row in report.get("summary", [])}),
                     "classification_review": validation.get("http_reconciliation_reviews", {}).get(path.relative_to(DOCS).as_posix(), {})})
    analysis = {"created_utc": datetime.now(timezone.utc).isoformat(), "status": "MEASURED_FIXTURE_COMPARISONS",
                "metadata": metadata, "machine_cpu": provenance.get("cpu"), "physical_ram_bytes_snapshot": provenance.get("ram_bytes"),
                "samples": len(samples), "measurements": list(reduced.values()), "comparisons": comparisons, "http_runs": http,
                "ratio_rule": "Named baseline divided by flexible; >1 favors flexible, <1 favors the named baseline. Allocation ratios compare current-thread allocation volume, not peak/RSS.",
                "correctness_note": "Performance statistics do not prove equal paths or waive discrepancies; independent correctness/replay evidence is separate."}
    (DOCS / "benchmark-analysis.json").write_text(json.dumps(analysis, indent=2) + "\n", encoding="utf-8")
    lines = ["# Measured matrix benchmarks", "", "The complete in-process grid produced **960 measured requests**: 20 repetitions after 5 warm-ups for each of two profiles, eight workloads and three backends. Results below compare the shared flexible matrix with named pairwise Dijkstra and prepared-CH baselines on the same imported Monaco fixture. They do not establish commercial or regional throughput.", "",
             "A ratio is the named baseline median divided by the flexible median: values above 1 favor flexible; values below 1 favor that baseline. Request timings include production validation and exclude the harness's later consistency checks. Matrix JSON serialization is measured separately and added per sample; CH has no matrix serialization stage. HTTP end-to-end comparisons are reported separately.", "",
             f"Machine: `{provenance.get('cpu', 'not recorded')}`; {metadata.get('available_processors')} JVM processors; physical RAM snapshot {fmt(mib(provenance.get('ram_bytes')), 0)} MiB. OS `{metadata.get('os')}`. JVM `{metadata.get('java_vm')}` {metadata.get('java_version')}; actual arguments `{metadata.get('jvm_arguments')}`.", "",
             f"Data: bundled `core/files/monaco.osm.gz`, SHA-256 `{metadata.get('fixture_sha256')}`, {metadata.get('graph_nodes'):,} imported nodes/{metadata.get('graph_edges'):,} edges. Foot is node-based; car uses the configured turn costs and 60-second U-turn penalty. Seed {metadata.get('seed')}; exact tower-node coordinates and duplicates are exported in [benchmark-requests.json](benchmark-requests.json). These workloads use the explicit benchmark-only limits in [benchmark-metadata.json](benchmark-metadata.json).", "",
             f"Cold import/preparation: {fmt(metadata.get('import_and_prepare_ms'))} ms total; OSM reader {fmt(metadata.get('osm_reader_ms'))} ms; CH preparation for both profiles {fmt(metadata.get('ch_preparation_ms'))} ms. These phases are outside warm request timings. Backends rotate order within one JVM; these are warm repeated-request measurements, not independent cold-JVM samples. p95 uses nearest rank over 20 measured values.", ""]
    headline = []
    for profile in ("foot", "car"):
        reference, flexible, ch = [reduced[profile, "100x100", backend] for backend in BACKENDS]
        headline.append(f"For **{profile} 100×100**, flexible request median was **{fmt(flexible['median_elapsed_request_ms'])} ms**, versus {fmt(reference['median_elapsed_request_ms'])} ms for pairwise Dijkstra and {fmt(ch['median_elapsed_request_ms'])} ms for prepared CH. Median current-thread allocation was {fmt(mib(flexible['median_request_thread_allocated_bytes']))} MiB, versus {fmt(mib(reference['median_request_thread_allocated_bytes']))} MiB and {fmt(mib(ch['median_request_thread_allocated_bytes']))} MiB respectively.")
    for run in http:
        grouped = defaultdict(dict)
        for row in run["summary"]:
            grouped[row["profile"], row["workload"]][row["backend"]] = row
        paired = [values for values in grouped.values() if "http_route_configured_ch" in values and "http_matrix_flexible" in values]
        route_faster = sum(values["http_route_configured_ch"]["median_ms"] < values["http_matrix_flexible"]["median_ms"] for values in paired)
        mismatches = sum(case.get("mismatched_cells_across_repetitions", 0) for case in run["comparisons"])
        classification = (" The separate case-bound CH precision proof is approved in the recorded final review; original flags remain unchanged."
                          if run["classification_review"].get("eligible") and run["classification_review"].get("approved") else
                          " Case-bound path classification/final approval remains separate from timing completion.")
        headline.append(f"The [{run['path']}]({run['path']}) HTTP run covers workloads of {', '.join(str(count) for count in run['completed_cells_per_workload'])} completed cells. The configured-CH route baseline had lower median latency in **{route_faster} of {len(paired)}** profile/workload groups. The raw comparison recorded **{mismatches}** metric/reachability flags across repetitions.{classification}")
    lines[4:4] = [part for paragraph in headline for part in (paragraph, "")]
    for profile in ("foot", "car"):
        lines += ["## " + profile + " latency", "", "All values are median / p95 milliseconds.", "",
                  "| Workload | Pairwise Dijkstra | Flexible matrix | Prepared CH calls | Dijkstra/flex | CH/flex | Flexible + JSON |",
                  "| --- | ---: | ---: | ---: | ---: | ---: | ---: |"]
        for workload in WORKLOADS:
            reference, flexible, ch = [reduced[profile, workload, backend] for backend in BACKENDS]
            pair = lambda row, field: fmt(row["median_" + field]) + " / " + fmt(row["p95_" + field])
            lines.append(f"| {workload} | {pair(reference, 'elapsed_request_ms')} | {pair(flexible, 'elapsed_request_ms')} | {pair(ch, 'elapsed_request_ms')} | {fmt(ratio(reference['median_elapsed_request_ms'], flexible['median_elapsed_request_ms']), 2)} | {fmt(ratio(ch['median_elapsed_request_ms'], flexible['median_elapsed_request_ms']), 2)} | {pair(flexible, 'request_plus_serialization_ms')} |")
        lines += ["", "## " + profile + " current-thread allocation", "", "MiB allocated per request (median); these are allocation volumes, not retained/peak/RSS memory.", "",
                  "| Workload | Dijkstra MiB | Flexible MiB | CH MiB | Dijkstra/flex | CH/flex | Flexible JSON MiB |",
                  "| --- | ---: | ---: | ---: | ---: | ---: | ---: |"]
        for workload in WORKLOADS:
            reference, flexible, ch = [reduced[profile, workload, backend] for backend in BACKENDS]
            allocation = "median_request_thread_allocated_bytes"
            lines.append(f"| {workload} | {fmt(mib(reference[allocation]))} | {fmt(mib(flexible[allocation]))} | {fmt(mib(ch[allocation]))} | {fmt(ratio(reference[allocation], flexible[allocation]), 2)} | {fmt(ratio(ch[allocation], flexible[allocation]), 2)} | {fmt(mib(flexible['median_serialization_thread_allocated_bytes']))} |")
        lines.append("")
    lines += ["## Work counters and output", "", "Completed cells include diagnosed null outcomes. Reachable cells count numeric routes only; nulls never become successful routes. Flexible throughput here includes its separately measured serialization. Searches/visited states are measured counters; unavailable upstream counts stay null.", "",
              "| Profile / workload | Completed / reachable / null | Flexible searches / visited | Completed / reachable cells/s with JSON | JSON KiB | Request GC count / ms over 20 repeats |",
              "| --- | ---: | ---: | ---: | ---: | ---: |"]
    for profile in ("foot", "car"):
        for workload in WORKLOADS:
            row = reduced[profile, workload, "flexible"]
            lines.append(f"| {profile} / {workload} | {row['completed_cells']} / {row['reachable_cells']} / {row['null_cells']} | {fmt(row['median_searches'], 0)} / {fmt(row['median_visited_states'], 0)} | {fmt(row['request_plus_serialization_completed_cells_per_second'], 0)} / {fmt(row['request_plus_serialization_reachable_cells_per_second'], 0)} | {fmt(row['median_output_json_bytes'] / 1024 if row['median_output_json_bytes'] is not None else None)} | {fmt(row['sum_gc_collections'], 0)} / {fmt(row['sum_gc_time_ms'], 0)} |")
    lines += ["", "The raw CSV retains request, serializer and snapping/QueryGraph/backend/core-search/scatter phase timings, actual counters, output bytes, allocation deltas, heap-pool peak sums and GC observations for every repetition. Reference internals not exposed by ordinary routing are null. Phase medians do not necessarily sum to the median total.", "",
              "`ThreadMXBean` allocation is recorded only when supported and already enabled; the benchmark does not enable it. It measures the serial calling thread and excludes allocations on other threads. Heap-pool peak sums include the live shared-JVM footprint and are not simultaneous peak heap, per-request retained memory or RSS. They must not be used to claim a smaller resident footprint. See [benchmark-analysis.json](benchmark-analysis.json) for median/p95 allocation, request/serialization GC totals and measured heap values for every named baseline.", "", "## HTTP comparisons", ""]
    if not http:
        lines += ["No completed HTTP benchmark report is recorded yet.", ""]
    for run in http:
        dataset_note = ("Regional inputs are pinned in [regional-data.json](regional-data.json)." if run["path"].startswith("regional-http-benchmark/")
                        else "Bundled Monaco data and exact coordinates are pinned in [benchmark-metadata.json](benchmark-metadata.json) and [benchmark-requests.json](benchmark-requests.json).")
        lines += [f"### {run['path']}", "", f"Status **{run['status']}** at `{run['url']}`; {run['measured_samples']} measured samples, {run['warmups']} warm-ups and {run['repeats']} measured repeats per group. Request-set SHA-256 `{run['requests_sha256']}`. Timings include persistent HTTP transport, server serialization and client JSON parsing/validation; request bodies are pre-encoded. Output bytes exclude headers.", "",
                  f"Server configuration SHA-256 `{run['server_config_sha256']}`. Actual server JVM flags, JAR/config identities, isolated graph directories and ports are retained in [runtime-manifest.json](runtime-manifest.json); each report records matrix graph/profile/build fingerprints. {dataset_note} These are warm requests on an already prepared server; cold import/CH preparation and server RSS/allocation were not measured by this HTTP harness.", "",
                  "| Profile / workload | Configured-CH HTTP routes median / p95 ms | Flexible HTTP matrix median / p95 ms | Route/matrix ratio | Matrix completed / reachable cells/s | Matrix body bytes |",
                  "| --- | ---: | ---: | ---: | ---: | ---: |"]
        grouped = defaultdict(dict)
        for row in run["summary"]:
            grouped[row["profile"], row["workload"]][row["backend"]] = row
        for (profile, workload), values in grouped.items():
            route = values.get("http_route_configured_ch", {})
            matrix = values.get("http_matrix_flexible", {})
            lines.append(f"| {profile} / {workload} | {fmt(route.get('median_ms'))} / {fmt(route.get('p95_ms'))} | {fmt(matrix.get('median_ms'))} / {fmt(matrix.get('p95_ms'))} | {fmt(ratio(route.get('median_ms'), matrix.get('median_ms')), 2)} | {fmt(matrix.get('completed_cells_per_second'), 0)} / {fmt(matrix.get('reachable_cells_per_second'), 0)} | {fmt(matrix.get('median_output_body_bytes'), 0)} |")
        mismatches = sum(case.get("mismatched_cells_across_repetitions", 0) for case in run["comparisons"])
        diagnostic_note = (f" Retained diagnostics identify {run['indexed_cases_in_retained_diagnostics']} distinct profile/workload/index cases; diagnostic kinds: `{run['retained_diagnostic_kinds']}`."
                           if mismatches else "")
        review = run["classification_review"]
        classification_note = (f" Separate [{review['reconciliation']}]({review['reconciliation']}) status **{review.get('status')}**, eligible **{review.get('eligible')}**, explicitly approved **{review.get('approved')}**. It checks {review.get('indexed_cases')} indexed cases/{review.get('unique_pair_proofs')} unique pairs and {review.get('retained_scalar_records')} retained scalar records. Fresh paths prove the recorded cases; capped diagnostics omit some historical repetition paths/scalars, and stable aggregate checksums cannot exclude compensating changes in omitted records."
                               if review.get("eligible") else " Any flagged CH-weight differences require separate expanded-path/shortcut-precision evidence before final acceptance.")
        lines += ["", f"Paired HTTP metric/reachability disagreements across repetitions: **{mismatches}**; individual cases remain in the report and are not automatically waived.{diagnostic_note} Connection evidence: `{run['connections']}`. Upstream HTTP JSON does not identify the chosen CH backend; captured startup/runtime and actual route debug logs independently document CH selection.{classification_note}", ""]
    lines += ["## Coverage limits", "", "The first interrupted recorder-only benchmark attempt is preserved separately and is excluded from these 960 samples. No commercial-speed claim, national-scale throughput or regional performance result is inferred from Monaco. Ordinary CH is a strong pairwise baseline; a dedicated CH matrix is not implemented. Imported graphs, selected profiles and these coordinate distributions bound the comparison. Correctness evidence remains in [validation-report.md](validation-report.md), including every individually classified virtual-split/restriction-copy discrepancy.", ""]
    (DOCS / "benchmark-report.md").write_text("\n".join(lines), encoding="utf-8")
    print(json.dumps({"status": analysis["status"], "samples": len(samples), "comparisons": len(comparisons), "http_runs": len(http)}))


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError) as error:
        print(f"Benchmark analysis not written: {error}", file=sys.stderr)
        raise SystemExit(1)
