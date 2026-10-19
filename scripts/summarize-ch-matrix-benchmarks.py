r"""Validate and summarize the opt-in certified CH matrix milestone experiments.

Python standard library only. Run from the repository root, for example:
    python scripts\summarize-ch-matrix-benchmarks.py

This reads the new CH matrix experiment directories and leaves all earlier matrix
benchmark and reconciliation artifacts unchanged.
"""
from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import json
import math
import statistics
import struct
from collections import defaultdict
from collections import Counter
from fractions import Fraction
from pathlib import Path
from ch_candidate_trace_guard import inspect_candidate_paths


WORKLOADS = {"1x3", "1x10", "1x100", "10x10", "10x100", "100x10", "100x100", "duplicate_100x100"}


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def finite(value: str) -> float | None:
    if value == "":
        return None
    number = float(value)
    assert math.isfinite(number) and number >= 0, value
    return number


def quantile95(values: list[float]) -> float:
    return sorted(values)[math.ceil(.95 * len(values)) - 1]


def flatten_numbers(raw: dict, prefix: str = "") -> dict[str, float]:
    values = {}
    for key, value in raw.items():
        name = f"{prefix}.{key}" if prefix else key
        if isinstance(value, dict):
            values.update(flatten_numbers(value, name))
        elif isinstance(value, (int, float)) and not isinstance(value, bool):
            assert math.isfinite(value) and value >= 0, (name, value)
            values[name] = value
    return values


def independent_comparison(actual: dict, expected: dict) -> list[dict]:
    assert actual.keys() == expected.keys() == {"weights", "distances", "times", "times_ms"}
    assert len(actual["weights"]) == len(expected["weights"])
    mismatches = []
    for i, expected_row in enumerate(expected["weights"]):
        assert len(actual["weights"][i]) == len(expected_row)
        for j, expected_weight in enumerate(expected_row):
            a = {key: value[i][j] for key, value in actual.items()}
            b = {key: value[i][j] for key, value in expected.items()}
            for metrics in (a, b):
                nulls = {value is None for value in metrics.values()}
                assert len(nulls) == 1
                if metrics["weights"] is not None:
                    assert all(isinstance(value, (int, float)) and math.isfinite(value) and value >= 0 for value in metrics.values())
                    assert metrics["times"] == metrics["times_ms"] // 1000
            issues = []
            if (a["weights"] is None) != (expected_weight is None):
                issues.append("null_mask")
            if a["weights"] is not None and expected_weight is not None:
                if abs(a["weights"] - expected_weight) > 1e-7 + abs(expected_weight) * 1e-10:
                    issues.append("weight")
                if abs(a["distances"] - b["distances"]) > .002:
                    issues.append("distance")
                if abs(a["times_ms"] - b["times_ms"]) > 1:
                    issues.append("duration")
            if issues:
                mismatches.append({"origin_index": i, "destination_index": j, "issues": issues, "actual": a, "expected": b})
    return mismatches


def exact(value: int | float | str) -> Fraction:
    return Fraction.from_float(value) if isinstance(value, float) else Fraction(value)


def audit_path(path: dict, source: int, target: int) -> tuple[Fraction, Fraction, int]:
    assert path["legal_and_requested_endpoints"] is True
    objective = distance = Fraction(0)
    sequential_weight = sequential_distance = 0.
    millis, previous = 0, source
    canonical = []
    for edge in path["edge_contributions"]:
        assert edge["from_node"] == previous
        previous = edge["to_node"]
        for field in ("edge_weight", "turn_weight", "distance_m"):
            assert isinstance(edge[field], (int, float)) and math.isfinite(edge[field]) and edge[field] >= 0
        for field in ("edge_time_ms", "turn_time_ms"):
            assert type(edge[field]) is int and edge[field] >= 0
        assert edge["turn_weight"] == 0 and edge["turn_time_ms"] == 0, "Measured audit paths use the static node-based foot profile"
        family = edge["upstream_copy_family_directed_keys"]
        assert edge["original_edge_key"] in family and edge["copy_canonical_edge_key"] in family
        if not canonical or canonical[-1] != edge["copy_canonical_edge_key"]:
            canonical.append(edge["copy_canonical_edge_key"])
        objective += exact(edge["edge_weight"]) + exact(edge["turn_weight"])
        distance += exact(edge["distance_m"])
        millis += edge["edge_time_ms"] + edge["turn_time_ms"]
        sequential_weight += edge["edge_weight"] + edge["turn_weight"]
        sequential_distance += edge["distance_m"]
    assert previous == target
    assert canonical == path["copy_canonical_directed_edge_keys"]
    assert objective == exact(path["exact_objective_terms_sum"])
    assert distance == exact(path["exact_distance_terms_sum"])
    assert path["metrics"] == {"weights": sequential_weight, "times_ms": millis, "distances": sequential_distance}
    return objective, distance, millis


def stored_weight(value: float) -> float:
    return math.floor(max(.001, value) * 1000 + .5) / 1000


def audit_quantization(raw: dict, expected_objective: Fraction, source: int, target: int) -> None:
    assert raw["proved"] is True and raw["failures"] == []
    selected, shortcuts = raw["selected_edges"], raw["selected_shortcuts"]
    fields = ("edge", "base", "adj", "reverse_search")
    assert Counter(tuple(edge[field] for field in fields) + (edge["edge_weight"],) for edge in selected if edge["shortcut"]) == Counter(
        tuple(shortcut[field] for field in fields) + (shortcut["stored_weight"],) for shortcut in shortcuts)
    shortcut_residual = shortcut_bounds = Fraction(0)
    for shortcut in shortcuts:
        leaves = shortcut["expanded_terms"]
        assert len(leaves) == shortcut["original_edge_occurrences"] >= 2
        assert shortcut["internal_shortcut_occurrences"] == len(leaves) - 1
        assert shortcut["preparation_arithmetic_operation_bound"] == len(leaves) - 1, "This measurement uses node-based foot CH"
        expanded = prepared = float_abs = Fraction(0)
        for leaf in leaves:
            f32 = float(struct.unpack(">f", struct.pack(">f", leaf["edge_weight"]))[0])
            assert leaf["preparation_float_edge_weight"] == f32
            assert leaf["internal_turn"] == 0
            residual = exact(f32) - exact(leaf["edge_weight"])
            assert residual == exact(leaf["exact_float_residual"])
            expanded += exact(leaf["edge_weight"]) + exact(leaf["internal_turn"])
            prepared += exact(f32) + exact(leaf["internal_turn"])
            float_abs += abs(residual)
        assert expanded == exact(shortcut["exact_unrounded_expanded_cost"])
        assert prepared == exact(shortcut["exact_float_preparation_terms_sum"])
        assert prepared - expanded == exact(shortcut["exact_float_leaf_residual_sum"])
        assert float_abs == exact(shortcut["float_leaf_absolute_residual_sum"])
        operations = len(leaves) - 1
        unit = 2. ** -53
        gamma = math.nextafter((operations * unit) / (1 - operations * unit), math.inf)
        assert shortcut["max_finite_source_initial_turn"] == 0
        center = float(prepared)
        arithmetic = exact(gamma) * prepared + exact(math.ulp(center))
        assert arithmetic == exact(shortcut["preparation_arithmetic_absolute_bound"])
        lower = max(0, math.nextafter(center - float(arithmetic), -math.inf))
        upper = math.nextafter(center + float(arithmetic), math.inf)
        assert lower == shortcut["possible_pre_storage_lower"] and upper == shortcut["possible_pre_storage_upper"]
        stored = shortcut["stored_weight"]
        assert shortcut["proved"] is True and shortcut["unique_predicted_stored_weight"] is True
        assert stored == stored_weight(lower) == stored_weight(upper) == shortcut["CHStorage_rounded_lower"] == shortcut["CHStorage_rounded_upper"]
        quantization = .001 if upper < .001 else max(.0005, max(0., .001 - lower))
        quantization = math.nextafter(quantization + math.ulp(upper * 1000) / 1000 + math.ulp(stored), math.inf)
        assert quantization == shortcut["storage_rounding_absolute_bound"]
        bound = float_abs + arithmetic + exact(quantization)
        residual = exact(stored) - expanded
        assert residual == exact(shortcut["exact_stored_minus_expanded"])
        assert bound == exact(shortcut["derived_absolute_bound"]) and abs(residual) <= bound
        shortcut_residual += residual
        shortcut_bounds += bound
    chains = {False: [], True: []}
    for edge in selected:
        assert type(edge["reverse_search"]) is bool and type(edge["shortcut"]) is bool
        chains[edge["reverse_search"]].append(edge)
    chain_totals = {}
    stored_sum = Fraction(0)
    for reverse, chain in chains.items():
        node, cumulative = (target if reverse else source), 0.
        for edge in chain:
            assert edge["base"] == node
            assert edge["connecting_turn"] == 0
            node = edge["adj"]
            cumulative = (edge["edge_weight"] + edge["connecting_turn"]) + cumulative
            assert cumulative == edge["cumulative_visited_weight"]
            stored_sum += exact(edge["edge_weight"]) + exact(edge["connecting_turn"])
        assert node == raw["meeting_node"]
        chain_totals[reverse] = cumulative
    bridge = raw["meeting_turn"]["weight"]
    assert bridge == 0
    stored_sum += exact(bridge)
    assert raw["forward_chain_weight"] == chain_totals[False] and raw["backward_chain_weight"] == chain_totals[True]
    reconstructed = (chain_totals[False] + chain_totals[True]) + bridge
    assert reconstructed == raw["reported_weight"] == raw["reconstructed_reported_weight"]
    reported = exact(raw["reported_weight"])
    assert stored_sum == exact(raw["exact_selected_stored_terms_sum"])
    assert expected_objective == exact(raw["exact_selected_expanded_terms_sum"])
    assert shortcut_residual == stored_sum - expected_objective == exact(raw["exact_shortcut_residual_sum"])
    assert reported - stored_sum == exact(raw["exact_query_addition_residual"])
    assert reported - expected_objective == exact(raw["exact_reported_minus_expanded"])
    assert shortcut_bounds + abs(reported - stored_sum) == exact(raw["derived_absolute_bound"])
    assert abs(reported - expected_objective) <= exact(raw["derived_absolute_bound"])


def audit_witness(witness: dict, metadata: dict, posthoc: dict | None = None) -> None:
    if posthoc is None:
        assert witness["proved"] is True and witness["failures"] == []
        assert witness["classification"] in {"INDEPENDENTLY_PROVEN_UNREACHABLE", "ACTUAL_CH_QUANTIZATION_AND_SAME_PATH_QUERY_SPLITS", "ACTUAL_CH_QUANTIZATION_AND_EXACT_OPTIMAL_ALTERNATIVE_PATHS"}
    else:
        assert witness["proved"] is False and witness["classification"] == "UNEXPLAINED"
        assert witness["failures"] == posthoc["original_failure"] == ["IllegalStateException: selected CH actual objective differs from independent pair Dijkstra exact edge/turn sum"]
        assert posthoc["classification"] in {"IEEE_SEQUENTIAL_SUM_TIE_WITH_NONZERO_EXACT_DELTA", "UPSTREAM_CH_ONE_ULP_ORIGINAL_OBJECTIVE_DIFFERENCE"}
        assert posthoc["original_rational_optimality_proven"] is False and posthoc["exact_rational_costs_equal"] is False
    assert witness["graph_fingerprint"] == metadata["graph_fingerprint"]
    assert witness["profile_fingerprint"] == metadata["profile_fingerprints"]["foot"]
    assert witness["build_commit"] == metadata["build_commit"]
    assert witness["profile"] == "foot"
    assert witness["default_ch_algorithm"] == "dijkstrabi|ch"
    assert witness["default_ch_algorithm"] in witness["ordinary_route_debug_info"]
    if witness["classification"] == "INDEPENDENTLY_PROVEN_UNREACHABLE":
        assert not any(witness[key] for key in ("selected_ch_found", "plain_pair_found", "plain_shared_found", "shared_search_found"))
        assert all(value is None for value in witness["observed_ch"].values())
        assert all(value is None for value in witness["observed_flexible"].values())
        return
    pair_source, pair_target = witness["pair_source_node"], witness["pair_target_node"]
    shared_source, shared_target = witness["combined_source_node"], witness["combined_target_node"]
    ch = audit_path(witness["ch_path"], pair_source, pair_target)
    pair = audit_path(witness["pair_dijkstra_path"], pair_source, pair_target)
    shared_plain = audit_path(witness["shared_dijkstra_path"], shared_source, shared_target)
    shared = audit_path(witness["shared_selected_path"], shared_source, shared_target)
    assert shared[0] == shared_plain[0]
    if posthoc is None:
        assert ch[0] == pair[0]
    else:
        gap = ch[0] - pair[0]
        assert gap > 0 and gap == exact(posthoc["exact_ch_minus_pair_objective_fraction"]) == exact(posthoc["exact_ch_minus_pair_objective_decimal"])
        assert shared[0] - shared_plain[0] == exact(posthoc["shared_selected_minus_shared_plain_exact_objective_fraction"]) == 0
        for name in ("ch_path", "pair_dijkstra_path", "shared_dijkstra_path", "shared_selected_path"):
            recorded = posthoc["path_facts"][name]
            path = witness[name]
            assert recorded["all_terms_and_endpoint_checks_passed"] is True
            assert recorded["metrics"] == path["metrics"]
            assert recorded["exact_objective_fraction"] == str(exact(path["exact_objective_terms_sum"]))
            assert recorded["directed_edge_keys"] == [edge["edge_key"] for edge in path["edge_contributions"]]
        a, b = witness["ch_path"]["metrics"], witness["pair_dijkstra_path"]["metrics"]
        assert posthoc["ch_minus_pair_time_ms"] == a["times_ms"] - b["times_ms"]
        assert posthoc["ch_minus_pair_distance_m"] == a["distances"] - b["distances"]
        assert posthoc["ch_minus_pair_sequential_double_weight"] == a["weights"] - b["weights"]
        if posthoc["classification"] == "IEEE_SEQUENTIAL_SUM_TIE_WITH_NONZERO_EXACT_DELTA":
            assert a["weights"] == b["weights"] and posthoc["sequential_double_costs_equal"] is True
        else:
            assert a["weights"] == math.nextafter(b["weights"], math.inf) and posthoc["sequential_double_costs_equal"] is False
        branch = posthoc["alternative_branch"]
        left, right = witness["ch_path"]["edge_contributions"], witness["pair_dijkstra_path"]["edge_contributions"]
        prefix = 0
        while prefix < min(len(left), len(right)) and left[prefix]["edge_key"] == right[prefix]["edge_key"]:
            prefix += 1
        suffix = 0
        while suffix < min(len(left), len(right)) - prefix and left[-suffix - 1]["edge_key"] == right[-suffix - 1]["edge_key"]:
            suffix += 1
        assert (prefix, suffix) == (branch["common_prefix_edges"], branch["common_suffix_edges"])
        left = left[prefix:len(left) - suffix if suffix else None]
        right = right[prefix:len(right) - suffix if suffix else None]
        assert [edge["edge_key"] for edge in left] == branch["ch_alternative_directed_edge_keys"]
        assert [edge["edge_key"] for edge in right] == branch["plain_alternative_directed_edge_keys"]
        assert left[0]["from_node"] == right[0]["from_node"] == branch["alternative_source_node"]
        assert left[-1]["to_node"] == right[-1]["to_node"] == branch["alternative_target_node"]
        left_cost = sum((exact(edge["edge_weight"]) + exact(edge["turn_weight"]) for edge in left), Fraction(0))
        right_cost = sum((exact(edge["edge_weight"]) + exact(edge["turn_weight"]) for edge in right), Fraction(0))
        cached = lambda edges: sum((exact(float(struct.unpack(">f", struct.pack(">f", edge["edge_weight"]))[0])) + exact(edge["turn_weight"]) for edge in edges), Fraction(0))
        assert left_cost == exact(branch["exact_ch_branch_objective"])
        assert right_cost == exact(branch["exact_plain_branch_objective"])
        assert cached(left) == exact(branch["exact_float32_cached_ch_branch_objective"])
        assert cached(right) == exact(branch["exact_float32_cached_plain_branch_objective"])
        assert cached(left) - cached(right) == exact(branch["exact_float32_cached_ch_minus_plain_branch_objective"]) < 0
        assert branch["historical_witness_or_omitted_shortcut_cause_proven"] is False
    assert witness["actual_observed_scalars_exactly_bound"] is True
    assert witness["observed_flexible"] == witness["shared_selected_path"]["metrics"]
    assert witness["observed_ch"]["weights"] == witness["quantization_audit"]["reported_weight"]
    assert witness["observed_ch"]["times_ms"] == ch[2]
    actual_distance = witness["ch_path"]["metrics"]["distances"]
    reported_distance = witness["observed_ch"]["distances"]
    assert abs(actual_distance - reported_distance) <= math.ulp(max(actual_distance, reported_distance)) * max(1, len(witness["ch_path"]["edge_contributions"]) + 1)
    audit_quantization(witness["quantization_audit"], ch[0], pair_source, pair_target)
    if witness["classification"] == "ACTUAL_CH_QUANTIZATION_AND_EXACT_OPTIMAL_ALTERNATIVE_PATHS":
        assert pair[0] == shared[0]
    if pair[0] != shared[0] or witness["classification"] == "ACTUAL_CH_QUANTIZATION_AND_SAME_PATH_QUERY_SPLITS":
        left, right = witness["pair_dijkstra_path"], witness["shared_selected_path"]
        assert left["copy_canonical_directed_edge_keys"] == right["copy_canonical_directed_edge_keys"]
        grouped = []
        for path in (left, right):
            group = defaultdict(list)
            for edge in path["edge_contributions"]:
                assert edge["encoded_flags_equal_original"] is True
                group[edge["copy_canonical_edge_key"]].append(edge)
            grouped.append(group)
        assert grouped[0].keys() == grouped[1].keys()
        for key in grouped[0]:
            a, b = grouped[0][key], grouped[1][key]
            assert len({edge["normalized_distance_one_weight"] for edge in a + b}) == 1
            assert sum((exact(edge["turn_weight"]) for edge in a), Fraction(0)) == sum((exact(edge["turn_weight"]) for edge in b), Fraction(0))
            assert sum(edge["turn_time_ms"] for edge in a) == sum(edge["turn_time_ms"] for edge in b)


def approved_posthoc(directory: Path, root: Path, metadata: dict) -> tuple[dict, dict | None, dict | None]:
    posthoc_path = directory / "pairwise-ch-posthoc-review.json"
    raw_path = directory / "raw-candidate-reconciliation.json"
    if not posthoc_path.exists():
        assert metadata["pairwise_ch_unexplained_unique_case_proofs"] == 0
        raw = json.loads(raw_path.read_text()) if raw_path.exists() else None
        return {}, raw, None
    posthoc = json.loads(posthoc_path.read_text())
    assert posthoc["status"] == "EXPLAINED_REPORTED_OBJECTIVE_DIFFERENCES"
    assert posthoc["dataset"] == metadata["dataset"]
    assert posthoc["metadata_sha256"] == sha256(directory / "metadata.json")
    assert posthoc["reconciliation_sha256"] == sha256(directory / "pairwise-ch-reconciliation.json")
    assert posthoc["witnesses_sha256"] == sha256(directory / "pairwise-ch-witnesses.jsonl.gz")
    assert posthoc["original_outputs_modified"] is False and posthoc["public_certified_matrix_changed"] is False
    assert posthoc["original_rational_optimality_pass_claimed"] is False
    cases = {case["proof_id"]: case for case in posthoc["cases"]}
    assert len(cases) == len(posthoc["cases"]) == posthoc["case_count"] == metadata["pairwise_ch_unexplained_unique_case_proofs"]
    approval = {"dataset": metadata["dataset"], "posthoc_review_sha256": sha256(posthoc_path),
                "metadata_sha256": posthoc["metadata_sha256"], "reconciliation_sha256": posthoc["reconciliation_sha256"],
                "witnesses_sha256": posthoc["witnesses_sha256"]}
    raw = None
    if raw_path.exists():
        raw = json.loads(raw_path.read_text())
        assert raw["dataset"] == metadata["dataset"]
        assert raw["status"] == "RAW_CANDIDATE_FLAGS_REJECTED_BY_MANDATORY_CERTIFICATE"
        assert raw["remaining_unhandled_raw_flags"] == 0
        for name, digest in raw["original_evidence_sha256"].items():
            assert sha256(directory / name) == digest
        approval["raw_candidate_reconciliation_sha256"] = sha256(raw_path)
    review = json.loads((root / "docs/matrix/ch-matrix-review.json").read_text())
    assert approval in review["approved_posthoc_reconciliations"], "Root has not approved this exact case-bound posthoc/raw-rejection evidence tuple"
    return cases, raw, approval


def audit_raw_rejection_context(witness: dict, case: dict) -> None:
    assert case["classification"] == "REJECTED_BY_MANDATORY_FLEXIBLE_CERTIFICATE"
    assert case["actual_raw_candidate_path_captured"] is False
    assert case["legal_ch_context_time_exactly_matches_raw_candidate"] is True
    assert case["raw_candidate"]["times_ms"] == witness["ch_path"]["metrics"]["times_ms"]
    edges = witness["ch_path"]["edge_contributions"]
    for metric, term in (("weights", "edge_weight"), ("distances", "distance_m")):
        context = case["legal_ch_context_scalar_association"][metric]
        total = sum((exact(edge[term]) for edge in edges), Fraction(0))
        operations = max(0, len(edges) - 1)
        u = Fraction(1, 2**53)
        bound = Fraction(0) if not operations else operations * u / (1 - operations * u) * total
        assert total == exact(context["exact_legal_path_terms_sum_fraction"])
        assert context["context_path_nonnegative_term_count"] == len(edges)
        assert context["context_path_binary_addition_operation_bound"] == operations
        assert bound == exact(context["context_path_sum_association_absolute_bound_fraction"])
        difference = exact(case["raw_candidate"][metric]) - total
        assert difference == exact(context["observed_raw_minus_context_exact_sum_fraction"])
        assert abs(difference) <= bound and context["raw_scalar_within_context_path_association_bound"] is True


def check_directory(directory: Path, root: Path) -> tuple[dict, list[dict], list[dict]]:
    metadata = json.loads((directory / "metadata.json").read_text(encoding="utf-8"))
    expected_dataset = {"monaco": (1217, 1619, 5, 20), "manchester": (500226, 636908, 2, 5)}
    assert metadata["dataset"] in expected_dataset
    nodes, edges, warmups, repeats = expected_dataset[metadata["dataset"]]
    assert (metadata["graph_nodes"], metadata["graph_edges"], metadata["warmups"], metadata["repeats"]) == (nodes, edges, warmups, repeats)
    assert Path(metadata["prepared_graph"]).name == "matrix-" + metadata["dataset"] + "-server"
    assert metadata["profile_fingerprints"].keys() == {"foot", "car"}
    assert metadata["status"] in {"MEASURED_NO_EXPERIMENTAL_FLEXIBLE_FLAGS", "FAIL"}, metadata["status"]
    if metadata["status"] == "FAIL":
        assert "Every new ordinary CH baseline flag needs fresh independent path evidence" in metadata["failure"]
    assert metadata["prepared_graph_read_only"] is True
    assert metadata["cache_file_bytes_unchanged_after_close"] is True
    assert metadata["new_imports"] == metadata["new_ch_preparations"] == 0
    assert metadata["experimental_vs_flexible_mismatched_cells"] == 0
    assert metadata["experimental_vs_flexible_exact_metric_equality"] is True
    assert metadata["independent_oracle_vs_flexible_flagged_cells"] == 0
    posthoc_cases, raw_rejection, approved_review = approved_posthoc(directory, root, metadata)
    candidate_trace = inspect_candidate_paths(directory, root)
    assert metadata["warmups"] >= 1 and metadata["repeats"] >= 3
    assert metadata["evidence_sha256"].keys() >= {"requests.json", "first-outcomes.json", "comparisons.json", "samples.csv", "car-fallback.json", "raw-candidates.json", "pairwise-ch-reconciliation.json", "pairwise-ch-witnesses.jsonl.gz"}
    for name, digest in metadata["evidence_sha256"].items():
        assert sha256(directory / name) == digest, f"Evidence changed: {directory / name}"
    expected_sources = {path.relative_to(root).as_posix() for path in (root / "core/src/main/java/com/graphhopper/routing/matrix").glob("*.java")}
    expected_sources.update("core/src/test/java/com/graphhopper/routing/matrix/" + name + ".java"
                            for name in ("MatrixCHMatrixBenchmarkTest", "CHWeightAudit", "CHMatrixPairwiseAudit", "MatrixPrecisionDiagnostics"))
    assert metadata["source_sha256"].keys() == expected_sources
    for name, digest in metadata["source_sha256"].items():
        assert sha256(root / name) == digest, f"Source changed after measurement: {name}"
    expected_classes = set()
    for relative in ("core/target/classes/com/graphhopper/routing/matrix", "core/target/test-classes/com/graphhopper/routing/matrix", "web-api/target/classes/com/graphhopper/matrix"):
        for path in (root / relative).glob("*.class"):
            if "test-classes" in relative and not any(path.name == name + ".class" or path.name.startswith(name + "$")
                                                       for name in ("MatrixCHMatrixBenchmarkTest", "CHWeightAudit", "CHMatrixPairwiseAudit", "MatrixPrecisionDiagnostics")):
                continue
            expected_classes.add(path.relative_to(root).as_posix())
    assert metadata["compiled_class_sha256"].keys() == expected_classes
    for name, digest in metadata["compiled_class_sha256"].items():
        assert sha256(root / name) == digest, f"Compiled class changed after measurement: {name}"
    cache = Path(metadata["prepared_graph"])
    assert {row["file"] for row in metadata["cache_file_manifest"]} >= {"nodes", "edges", "geometry", "location_index", "nodes_ch_foot", "shortcuts_foot", "properties"}
    for row in metadata["cache_file_manifest"]:
        path = cache / row["file"]
        assert path.stat().st_size == row["bytes"] and sha256(path) == row["sha256"]
    fallback = json.loads((directory / "car-fallback.json").read_text(encoding="utf-8"))
    assert fallback["profile"] == "car" and fallback["mismatched_cells"] == 0
    assert fallback["all_mismatches"] == []
    assert fallback["requested_ch_metrics"] == fallback["flexible_metrics"]
    assert independent_comparison(fallback["requested_ch_metrics"], fallback["flexible_metrics"]) == []
    assert fallback["requested_ch_info"]["algorithm"] == "flexible"
    assert fallback["requested_ch_info"]["requested_algorithm"] == metadata["experimental_algorithm"]
    assert fallback["requested_ch_info"]["fallback_reason"] == "TURN_COST_PROFILE_UNSUPPORTED"
    comparisons = json.loads((directory / "comparisons.json").read_text(encoding="utf-8"))
    outcomes = json.loads((directory / "first-outcomes.json").read_text(encoding="utf-8"))
    assert outcomes.keys() == WORKLOADS
    assert len(comparisons) == len(WORKLOADS) * 3
    experimental = metadata["experimental_algorithm"]
    expected_pairs = {(experimental, "flexible"), ("ch_pairwise", "flexible"), (experimental, "ch_pairwise")}
    requests = json.loads((directory / "requests.json").read_text(encoding="utf-8"))["workloads"]
    assert {(request["workload"], request["backend"]) for request in requests} == {
        (workload, backend) for workload in WORKLOADS for backend in (experimental, "flexible")}
    assert len(requests) == len(WORKLOADS) * 2
    shapes = {}
    request_axes = {}
    request_variants = {}
    axis_diversity = {}
    expected_shapes = {"1x3": (1, 3), "1x10": (1, 10), "1x100": (1, 100), "10x10": (10, 10),
                       "10x100": (10, 100), "100x10": (100, 10), "100x100": (100, 100), "duplicate_100x100": (100, 100)}
    for exported in requests:
        request = exported["request"]
        assert request["profile"] == "foot"
        assert request["local"]["algorithm"] == exported["backend"]
        shape = (len(request["from_points"]), len(request["to_points"]))
        assert shape == expected_shapes[exported["workload"]]
        assert shapes.setdefault(exported["workload"], shape) == shape
        comparable_request = dict(request)
        comparable_request.pop("local")
        assert request_variants.setdefault(exported["workload"], comparable_request) == comparable_request
        distinct = tuple(len(set(map(tuple, request[axis]))) for axis in ("from_points", "to_points"))
        assert axis_diversity.setdefault(exported["workload"], distinct) == distinct
        if exported["workload"] == "duplicate_100x100":
            assert distinct == (5, 5)
        else:
            assert all(unique >= math.ceil(count * .9) for unique, count in zip(distinct, shape))
        if exported["backend"] == "flexible":
            request_axes[exported["workload"]] = request
    for workload, backends in outcomes.items():
        assert backends.keys() == {experimental, "flexible", "ch_pairwise"}
        origins, destinations = shapes[workload]
        for metrics in backends.values():
            for matrix in metrics.values():
                assert len(matrix) == origins
                assert all(len(row) == destinations for row in matrix)
        assert sum(value is not None and value > 0 for row in backends["flexible"]["weights"] for value in row) > 0, "A workload must include actual nonzero routes"
    assert {(row["workload"], row["actual"], row["expected"]) for row in comparisons} == {
        (workload, actual, expected) for workload in WORKLOADS for actual, expected in expected_pairs
    }
    for comparison in comparisons:
        independently_flagged = independent_comparison(outcomes[comparison["workload"]][comparison["actual"]], outcomes[comparison["workload"]][comparison["expected"]])
        assert comparison["all_mismatches"] == independently_flagged, "Raw discrepancy list does not match actual full outcome cells"
        assert len(comparison["all_mismatches"]) == comparison["mismatched_cells"]
        assert comparison["expected_reachable_cells"] + comparison["expected_null_cells"] == comparison["cells"]
        if comparison["actual"] == experimental and comparison["expected"] == "flexible":
            assert comparison["mismatched_cells"] == 0
            assert outcomes[comparison["workload"]][experimental] == outcomes[comparison["workload"]]["flexible"]
    raw_candidates = json.loads((directory / "raw-candidates.json").read_text(encoding="utf-8"))
    assert raw_candidates.keys() == WORKLOADS
    raw_candidate_flags = 0
    raw_flag_cells = {}
    for workload, raw in raw_candidates.items():
        assert raw["status"] == "RAW_CANDIDATE_ONLY_NOT_AN_EXACT_API_BACKEND"
        independently_flagged = independent_comparison(raw["candidate_metrics"], raw["independent_plain_dijkstra_metrics"])
        assert independently_flagged == raw["raw_candidate_vs_independent_oracle"]["all_mismatches"]
        assert len(independently_flagged) == raw["raw_candidate_vs_independent_oracle"]["mismatched_cells"]
        raw_candidate_flags += len(independently_flagged)
        for issue in independently_flagged:
            raw_flag_cells[(workload, issue["origin_index"], issue["destination_index"])] = issue
        assert independent_comparison(raw["independent_plain_dijkstra_metrics"], outcomes[workload]["flexible"]) == []
        assert raw["independent_oracle_vs_flexible"]["mismatched_cells"] == 0
    raw_cases_by_proof = defaultdict(list)
    if raw_candidate_flags:
        assert raw_rejection is not None and (approved_review is not None or candidate_trace["approval"]), "Raw candidate fixed-threshold flags need individual bound rejection before final comparison acceptance"
        assert raw_rejection["raw_flagged_cells"] == len(raw_rejection["rejected_indexed_cases"]) == raw_candidate_flags
        rejected_indices = set()
        for case in raw_rejection["rejected_indexed_cases"]:
            key = case["workload"], case["origin_index"], case["destination_index"]
            assert key not in rejected_indices
            rejected_indices.add(key)
            assert case["classification"] == "REJECTED_BY_MANDATORY_FLEXIBLE_CERTIFICATE"
            assert case["raw_candidate"] == {metric: raw_flag_cells[key]["actual"][metric] for metric in ("weights", "times_ms", "distances")}
            assert case["independent_plain_dijkstra"] == {metric: raw_flag_cells[key]["expected"][metric] for metric in ("weights", "times_ms", "distances")}
            assert case["original_raw_issues"] == raw_flag_cells[key]["issues"]
            for backend, name in ((experimental, "returned_certified"), ("flexible", "returned_flexible")):
                assert case[name] == {metric: outcomes[key[0]][backend][metric][key[1]][key[2]] for metric in ("weights", "times_ms", "distances")}
            assert case["returned_certified"] == case["returned_flexible"]
            assert case["returned_certified_exactly_matches_flexible"] is True and case["returned_certified_matches_independent_oracle_fixed_thresholds"] is True
            assert case["raw_candidate"] != case["returned_certified"]
            assert case["from"] == request_axes[key[0]]["from_points"][key[1]] and case["to"] == request_axes[key[0]]["to_points"][key[2]]
            if "linked_legal_ordinary_ch_context_proof_id" in case:
                raw_cases_by_proof[case["linked_legal_ordinary_ch_context_proof_id"]].append(case)
            else:
                assert case["fresh_replay_actual_candidate_path_captured"] is True and case["original_raw_candidate_path_captured"] is False
        assert rejected_indices == raw_flag_cells.keys()
    else:
        assert raw_rejection is None or raw_rejection["raw_flagged_cells"] == 0
    reconciliation = json.loads((directory / "pairwise-ch-reconciliation.json").read_text(encoding="utf-8"))
    assert reconciliation["unexplained_unique_case_proofs"] == len(posthoc_cases)
    proof_summaries = {proof["proof_id"]: proof for proof in reconciliation["unique_case_proofs"]}
    assert len(proof_summaries) == len(reconciliation["unique_case_proofs"]) == metadata["pairwise_ch_unique_case_proofs"]
    assert {proof_id for proof_id, proof in proof_summaries.items() if proof["proved"] is False} == posthoc_cases.keys()
    expected_flags = {(comparison["workload"], issue["origin_index"], issue["destination_index"])
                      for comparison in comparisons if comparison["actual"] == "ch_pairwise" and comparison["expected"] == "flexible"
                      for issue in comparison["all_mismatches"]}
    actual_flags = set()
    used_proofs = set()
    proof_bindings = {}
    for link in reconciliation["indexed_flag_cases"]:
        assert link["proved"] is (link["proof_id"] not in posthoc_cases)
        assert proof_summaries[link["proof_id"]]["proved"] is (link["proof_id"] not in posthoc_cases)
        used_proofs.add(link["proof_id"])
        key = (link["workload"], link["origin_index"], link["destination_index"])
        assert key not in actual_flags
        actual_flags.add(key)
        for backend, name in (("ch_pairwise", "observed_ch"), ("flexible", "observed_flexible")):
            expected = {metric: outcomes[link["workload"]][backend][metric][link["origin_index"]][link["destination_index"]]
                        for metric in ("weights", "times_ms", "distances")}
            assert link[name] == expected
        request = request_axes[link["workload"]]
        binding = {"observed_ch": link["observed_ch"], "observed_flexible": link["observed_flexible"],
                   "from": request["from_points"][link["origin_index"]], "to": request["to_points"][link["destination_index"]]}
        assert proof_bindings.setdefault(link["proof_id"], binding) == binding
    assert actual_flags == expected_flags
    assert len(actual_flags) == metadata["pairwise_ch_indexed_flag_cases"]
    assert used_proofs == proof_summaries.keys()
    seen_witnesses = set()
    with gzip.open(directory / "pairwise-ch-witnesses.jsonl.gz", "rt", encoding="utf-8") as stream:
        for line in stream:
            line = line.rstrip("\r\n")
            witness = json.loads(line)
            proof_id = witness["proof_id"]
            assert proof_id not in seen_witnesses
            seen_witnesses.add(proof_id)
            assert hashlib.sha256(line.encode("utf-8")).hexdigest() == proof_summaries[proof_id]["witness_line_sha256"]
            for name, value in proof_bindings[proof_id].items():
                assert witness[name] == value
            assert witness["from"] == proof_summaries[proof_id]["from"] and witness["to"] == proof_summaries[proof_id]["to"]
            if proof_id in posthoc_cases:
                assert posthoc_cases[proof_id]["witness_line_sha256"] == proof_summaries[proof_id]["witness_line_sha256"]
                assert posthoc_cases[proof_id]["from"] == witness["from"] and posthoc_cases[proof_id]["to"] == witness["to"]
            audit_witness(witness, metadata, posthoc_cases.get(proof_id))
            for raw_case in raw_cases_by_proof.get(proof_id, []):
                assert raw_case["linked_witness_line_sha256"] == proof_summaries[proof_id]["witness_line_sha256"]
                assert raw_case["from"] == witness["from"] and raw_case["to"] == witness["to"]
                audit_raw_rejection_context(witness, raw_case)
    assert seen_witnesses == proof_summaries.keys()
    with (directory / "samples.csv").open(encoding="utf-8", newline="") as stream:
        rows = list(csv.DictReader(stream))
    assert len(rows) == metadata["completed_measured_samples"] == metadata["expected_measured_samples"] == len(WORKLOADS) * 3 * metadata["repeats"]
    groups = defaultdict(list)
    for row in rows:
        assert row["profile"] == "foot"
        assert row["workload"] in WORKLOADS
        assert row["backend"] in {experimental, "flexible", "ch_pairwise"}
        assert (int(row["origins"]), int(row["destinations"])) == shapes[row["workload"]]
        assert int(row["origins"]) * int(row["destinations"]) == int(row["reachable_cells"]) + int(row["null_cells"])
        row["info"] = json.loads(row["local_matrix_info_json"])
        row["flat_info"] = flatten_numbers(row["info"])
        for name in ("elapsed_request_ms", "request_thread_allocated_bytes", "serialization_ms", "serialization_thread_allocated_bytes", "request_plus_serialization_ms"):
            row[name] = finite(row[name])
        assert row["elapsed_request_ms"] > 0
        if row["backend"] == "ch_pairwise":
            assert row["serialization_ms"] is None
            assert row["info"]["pair_calls"] == int(row["origins"]) * int(row["destinations"])
        else:
            assert row["info"]["algorithm"] == row["backend"]
            assert row["info"]["threads"] == 1
            assert row["info"]["requested_algorithm"] == row["backend"]
            assert "fallback_reason" not in row["info"]
            assert row["info"]["graph_fingerprint"] == metadata["graph_fingerprint"]
            assert row["info"]["profile_fingerprint"] == metadata["profile_fingerprints"]["foot"]
            assert row["info"]["build_commit"] == metadata["build_commit"]
            assert row["serialization_ms"] is not None
            assert math.isclose(row["request_plus_serialization_ms"], row["elapsed_request_ms"] + row["serialization_ms"], abs_tol=1e-8)
            if row["backend"] == experimental:
                assert row["info"]["experimental"] is True
                assert row["info"]["optimality_verification"] == "flexible_each_origin"
                ch = row["info"]["ch_matrix"]
                assert ch["candidate_optimality_guaranteed"] is False
                assert ch["corrected_cells_basis"] == "distinct_origin_coordinate_by_destination_index"
                assert ch["forward_searches"] == ch["certificate_searches"] > 0
                assert ch["backward_searches"] > 0
                assert row["info"]["ch_preparation"]["traversal"] == "node"
                assert row["info"]["ch_preparation"]["graph_fingerprint"] == metadata["graph_fingerprint"]
                assert row["info"]["ch_preparation"]["profile_fingerprint"] == metadata["profile_fingerprints"]["foot"]
                for stage in ("ch_bucket_preparation", "ch_candidates", "ch_verification"):
                    assert row["info"]["timings_ms"][stage] >= 0
                row["flat_info"]["experimental_phases.bucket_and_candidate_rows_ms"] = (
                    row["info"]["timings_ms"]["ch_bucket_preparation"] + row["info"]["timings_ms"]["ch_candidates"])
        groups[(row["workload"], row["backend"])].append(row)
    assert len(groups) == len(WORKLOADS) * 3
    summaries = []
    for (workload, backend), group in groups.items():
        assert {int(row["repeat"]) for row in group} == set(range(metadata["repeats"]))
        assert len(group) == metadata["repeats"]
        for stable in ("weight_checksum", "reachable_cells", "null_cells", "origins", "destinations"):
            assert len({row[stable] for row in group}) == 1, (workload, backend, stable)
        first = group[0]
        summary = {"dataset": metadata["dataset"], "workload": workload, "backend": backend, "repeats": len(group),
                   "origins": int(first["origins"]), "destinations": int(first["destinations"]),
                   "distinct_raw_origins": axis_diversity[workload][0], "distinct_raw_destinations": axis_diversity[workload][1],
                   "reachable_cells": int(first["reachable_cells"]), "null_cells": int(first["null_cells"])}
        for name in ("elapsed_request_ms", "request_thread_allocated_bytes", "serialization_ms", "serialization_thread_allocated_bytes", "request_plus_serialization_ms"):
            values = [row[name] for row in group]
            summary["median_" + name] = None if any(value is None for value in values) else statistics.median(values)
            summary["p95_" + name] = None if any(value is None for value in values) else quantile95(values)
        all_info_names = set().union(*(row["flat_info"].keys() for row in group))
        for name in sorted(all_info_names):
            values = [row["flat_info"].get(name) for row in group]
            summary["median_info." + name] = None if any(value is None for value in values) else statistics.median(values)
        if backend != "ch_pairwise":
            summary["max_matrix_visited_states"] = max(row["info"]["visited_states"] for row in group)
            summary["max_matrix_total_ms"] = max(row["info"]["timings_ms"]["total"] for row in group)
            summary["exceeds_shipped_default_state_budget"] = summary["max_matrix_visited_states"] > 1000000
            summary["exceeds_shipped_default_deadline"] = summary["max_matrix_total_ms"] >= 30000
        summaries.append(summary)
    identity = {"directory": str(directory.relative_to(root)).replace("\\", "/"), "metadata_sha256": sha256(directory / "metadata.json"),
                "dataset": metadata["dataset"], "warmups": metadata["warmups"], "repeats": metadata["repeats"],
                "samples": len(rows), "graph_nodes": metadata["graph_nodes"], "graph_edges": metadata["graph_edges"],
                "graph_fingerprint": metadata["graph_fingerprint"], "jvm_arguments": metadata["jvm_arguments"],
                "prepared_graph_load_ms": metadata["prepared_graph_load_ms"], "coordinate_policy": metadata["coordinate_policy"]}
    identity.update({"raw_candidate_flagged_cells": raw_candidate_flags, "pairwise_ch_indexed_flag_cases": len(actual_flags),
                     "pairwise_ch_fresh_unique_case_proofs": len(proof_summaries),
                     "known_pairwise_original_objective_difference_cases": len(posthoc_cases),
                     "known_pairwise_difference_classifications": dict(Counter(case["classification"] for case in posthoc_cases.values())),
                     "original_experiment_status": metadata["status"],
                     "fresh_actual_candidate_path_review": {key: value for key, value in candidate_trace.items() if key != "raw_rejection"},
                     "case_bound_posthoc_approval": approved_review,
                     "axis_diversity": {name: list(diversity) for name, diversity in axis_diversity.items()},
                     "prepared_cache_total_file_bytes": sum(row["bytes"] for row in metadata["cache_file_manifest"]),
                     "foot_ch_preparation_file_bytes": sum(row["bytes"] for row in metadata["cache_file_manifest"] if row["file"] in {"nodes_ch_foot", "shortcuts_foot"})})
    return identity, summaries, comparisons


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directories", nargs="*", type=Path)
    parser.add_argument("--output-prefix", type=Path, default=Path("docs/matrix/ch-matrix-benchmark"))
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    directories = args.directories or [Path("docs/matrix/ch-matrix-benchmark-monaco"), Path("docs/matrix/ch-matrix-benchmark-manchester")]
    identities, summaries, comparisons = [], [], []
    for directory in directories:
        directory = directory if directory.is_absolute() else root / directory
        identity, summary, comparison = check_directory(directory, root)
        identities.append(identity)
        summaries.extend(summary)
        for row in comparison:
            row = dict(row)
            row["dataset"] = identity["dataset"]
            comparisons.append(row)
    ratios = []
    for identity in identities:
        dataset = identity["dataset"]
        for workload in sorted(WORKLOADS):
            group = {row["backend"]: row for row in summaries if row["dataset"] == dataset and row["workload"] == workload}
            candidate_name = next(name for name in group if name not in {"flexible", "ch_pairwise"})
            candidate, flexible, pairwise = group[candidate_name], group["flexible"], group["ch_pairwise"]
            ratios.append({"dataset": dataset, "workload": workload, "experimental_algorithm": candidate_name,
                           "experimental_ms": candidate["median_elapsed_request_ms"], "experimental_p95_ms": candidate["p95_elapsed_request_ms"],
                           "flexible_ms": flexible["median_elapsed_request_ms"], "pairwise_ch_ms": pairwise["median_elapsed_request_ms"],
                           "flexible_over_certified_ch": flexible["median_elapsed_request_ms"] / candidate["median_elapsed_request_ms"],
                           "pairwise_ch_over_certified_ch": pairwise["median_elapsed_request_ms"] / candidate["median_elapsed_request_ms"],
                           "experimental_allocation_mib": None if candidate["median_request_thread_allocated_bytes"] is None else candidate["median_request_thread_allocated_bytes"] / 2**20,
                           "flexible_allocation_mib": None if flexible["median_request_thread_allocated_bytes"] is None else flexible["median_request_thread_allocated_bytes"] / 2**20,
                           "pairwise_allocation_mib": None if pairwise["median_request_thread_allocated_bytes"] is None else pairwise["median_request_thread_allocated_bytes"] / 2**20,
                           "reachable_cells": candidate["reachable_cells"], "null_cells": candidate["null_cells"]})
    output = args.output_prefix if args.output_prefix.is_absolute() else root / args.output_prefix
    output.parent.mkdir(parents=True, exist_ok=True)
    known_differences = sum(identity["known_pairwise_original_objective_difference_cases"] for identity in identities)
    analysis = {"status": "MEASURED_WITH_SCOPED_BASELINE_ACCURACY_DIFFERENCES" if known_differences else "MEASURED_WITH_CERTIFIED_FLEXIBLE_CHECK", "experiments": identities, "summaries": summaries, "ratios": ratios,
                "ordinary_ch_original_rational_optimality_claimed": False,
                "execution_configuration": {"actual_matrix_workers": 1, "admitted_matrix_requests": 1, "request_streams": 1,
                                            "benchmark_matrix_limits": {"max_axis_points": 1000, "max_cells": 100000, "max_concurrent_requests": 1,
                                                                        "timeout_ms": 600000, "max_visited_states": 2147483647},
                                            "shipped_default_matrix_limits": {"max_axis_points": 1000, "max_cells": 100000, "max_concurrent_requests": 2,
                                                                              "timeout_ms": 30000, "max_visited_states": 1000000},
                                            "source_of_benchmark_limits": "Frozen MatrixCHMatrixBenchmarkTest.java source and compiled-class SHA manifests; the original metadata did not repeat these constants."},
                "raw_comparison_flags": [{key: row[key] for key in ("dataset", "workload", "actual", "expected", "mismatched_cells", "status")} for row in comparisons],
                "limitations": ["Experimental backend includes mandatory full flexible row verification/correction. Candidate-phase timings are not certified end-to-end timings.",
                                "Every raw candidate fixed-threshold flag is rejected by the mandatory certificate under explicit case-bound review. The original timing and initial oracle passes did not capture candidate paths; a later read-only replay of the unchanged production algorithm captured actual candidate paths covering all indexed flags, bound to original scalar/source/class/cache identities and explicit root SHA approvals.",
                                "Manchester's fresh candidate paths match the independent oracle's physical routes and exact normalized weight, distance and time sums after explicitly proved shared-overlay leaf replacements. Monaco's actual candidate paths retain different legal branches and nonzero original-objective deltas. Counts are recorded in each experiment's fresh_actual_candidate_path_review. These replay results do not establish candidate-only exactness or capture every timed path.",
                                "Ordinary pairwise CH scalar flags remain raw. Fresh selected-path proofs cover every flagged indexed case; the witnesses are fresh replays bound to all observed scalar cells, rather than captured paths for every timed repetition. Earlier proof files were not reused as waivers.",
                                "Current-thread allocation volume is not retained heap or RSS. No concurrency, national-scale, edge-based CH matrix, or CH-only exactness claim."]}
    output.with_name(output.name + "-analysis.json").write_text(json.dumps(analysis, indent=2) + "\n", encoding="utf-8")
    columns = list(dict.fromkeys(key for row in summaries for key in row))
    with output.with_name(output.name + "-results.csv").open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, columns)
        writer.writeheader()
        writer.writerows(summaries)
    slower = sum(row["experimental_ms"] > row["flexible_ms"] for row in ratios)
    outcome = f"The certified experimental CH backend was slower than flexible in {slower} of {len(ratios)} measured workloads; `auto` stays flexible."
    manchester_large = next((row for row in ratios if row["dataset"] == "manchester" and row["workload"] == "100x100"), None)
    if manchester_large:
        outcome += (f" Manchester's 100x100 took {manchester_large['experimental_ms'] / 1000:.3f} seconds versus "
                    f"{manchester_large['flexible_ms'] / 1000:.3f} seconds for flexible and {manchester_large['pairwise_ch_ms'] / 1000:.3f} seconds for ordinary CH calls.")
    lines = ["# Certified experimental CH matrix comparisons", "",
             outcome + " It beat ordinary per-cell CH calls in some Monaco and duplicate workloads. The matrix measurements used the larger experiment limits described below.", "",
             "`CH` means **Contraction Hierarchies**. Preprocessing contracts graph nodes and adds shortcuts so later searches can use a much smaller upward graph. This experiment adds target buckets shared across origin searches for static node-based profiles.", "",
             "The experimental API backend also runs a complete flexible verification row and corrects its candidate results. The timings below include this correctness check. Candidate-only phase timings are retained in the CSV/JSON and do not describe an independently exact API backend.", "",
             "The benchmark uses one serial request stream, one matrix worker and one admission slot, with a 600-second deadline and 2,147,483,647-state limit. These are explicit experiment limits in the hash-bound source, rather than the shipped defaults of 30 seconds, 1,000,000 states and two admission slots. A completed measurement does not show that the same request completes under the shipped defaults.", "",
             "| Dataset | Workload | Certified CH median / p95 ms | Flexible median ms | Ordinary CH calls median ms | Flexible / certified CH | CH calls / certified CH |",
             "|---|---:|---:|---:|---:|---:|---:|"]
    for row in ratios:
        lines.append(f"| {row['dataset']} | {row['workload']} | {row['experimental_ms']:.3f} / {row['experimental_p95_ms']:.3f} | {row['flexible_ms']:.3f} | {row['pairwise_ch_ms']:.3f} | {row['flexible_over_certified_ch']:.2f}x | {row['pairwise_ch_over_certified_ch']:.2f}x |")
    lines += ["", "A ratio above one means the certified experimental backend was faster for that comparison. These are complete serial in-process request medians; JSON serialization is measured separately. Ordinary CH runs one routing call and extracts a path for each cell, so its cost includes more than its core search algorithm.", "",
              "| Dataset | Workload | Target buckets ms | Candidate rows ms | Flexible certificate ms | Original-edge metric expansion ms (overlaps CH phases) | Binary-triple corrections |",
              "|---|---:|---:|---:|---:|---:|---:|"]
    for row in summaries:
        if row["backend"] in {"flexible", "ch_pairwise"} or row["workload"] not in {"1x3", "100x100", "duplicate_100x100"}:
            continue
        lines.append(f"| {row['dataset']} | {row['workload']} | {row['median_info.timings_ms.ch_bucket_preparation']:.3f} | {row['median_info.timings_ms.ch_candidates']:.3f} | {row['median_info.timings_ms.ch_verification']:.3f} | {row['median_info.ch_matrix.metric_extraction_ms']:.3f} | {row['median_info.ch_matrix.corrected_cells']:g} |")
    lines += ["", "The phase table shows medians; separate phase medians need not sum to the median request. Original-edge metric expansion is nested within the bucket/candidate phases and must not be added to them. Corrections count any candidate-versus-certificate binary weight/time/distance difference (including harmless floating-point association and equal-cost route differences), plus reachability differences; they do not by themselves count suboptimal routes. Counts apply to each searched distinct origin and raw destination column before duplicate-origin rows are scattered.", "",
              "| Dataset, 100x100 | Flexible calling-thread allocation MiB | Certified CH calling-thread allocation MiB | Ordinary CH calls allocation MiB | CH request bucket entries | CH request metric-cache entries |",
              "|---|---:|---:|---:|---:|---:|"]
    for row in ratios:
        if row["workload"] != "100x100":
            continue
        candidate = next(summary for summary in summaries if summary["dataset"] == row["dataset"] and summary["workload"] == "100x100" and summary["backend"] == row["experimental_algorithm"])
        values = [row["flexible_allocation_mib"], row["experimental_allocation_mib"], row["pairwise_allocation_mib"]]
        allocation = ["unavailable" if value is None else f"{value:.3f}" for value in values]
        lines.append(f"| {row['dataset']} | {' | '.join(allocation)} | {candidate['median_info.ch_matrix.bucket_entries']:g} | {candidate['median_info.ch_matrix.metric_cache_entries']:g} |")
    lines += ["", "Allocation medians count cumulative bytes allocated by the calling thread during the complete request. They exclude JSON serialization and other threads, and do not measure retained heap, peak process memory or RSS. Buckets and exact edge-metric caches are rebuilt for each request; their entries are counts rather than byte-size estimates.", "",
              "Every certified experimental matrix metric cell exactly matched the shared flexible matrix on the same overlay, and also passed the fixed original thresholds (weight 1e-7 + 1e-10 relative, distance 0.002 m, duration 1 ms). The harness checks every metric cell for exact stability across repetitions and checks turn-aware car fallback separately. This measurement gate supplements the dedicated algorithm tests; it does not prove all possible paths.", ""]
    for identity in identities:
        diversity = identity["axis_diversity"]["100x100"]
        lines.append(f"{identity['dataset'].title()}: {identity['graph_nodes']:,} nodes, {identity['graph_edges']:,} edges; {identity['warmups']} warmups and {identity['repeats']} measured repetitions per backend/workload, {identity['samples']} samples. The 100x100 axes contain {diversity[0]} distinct raw origin coordinates and {diversity[1]} distinct raw destination coordinates; the explicit duplicate workload contains five of each. The existing prepared cache loaded in {identity['prepared_graph_load_ms']:.1f} ms and its file bytes were verified unchanged. Cache loading is not CH preprocessing: no new global import or CH preparation occurred, and cold CH preparation time was not remeasured. Existing foot CH node/shortcut files occupy {identity['foot_ch_preparation_file_bytes'] / 2**20:.3f} MiB on disk; this is disk storage, not memory use. Request bucket construction is separately measured in the phase table. {identity['coordinate_policy']} JVM arguments: `{identity['jvm_arguments']}`. [Raw evidence]({Path(identity['directory']).name}/metadata.json).")
        lines.append("")
    default_exceeded = [row for row in summaries if row.get("exceeds_shipped_default_state_budget") or row.get("exceeds_shipped_default_deadline")]
    if default_exceeded:
        lines += ["| Workloads exceeding a shipped default | Maximum measured settled states | Maximum engine total ms | Exceeded default |",
                  "|---|---:|---:|---|"]
        for row in default_exceeded:
            flags = []
            if row["exceeds_shipped_default_state_budget"]:
                flags.append("1,000,000-state budget")
            if row["exceeds_shipped_default_deadline"]:
                flags.append("30-second deadline")
            lines.append(f"| {row['dataset']} / {row['workload']} / {row['backend']} | {row['max_matrix_visited_states']:,} | {row['max_matrix_total_ms']:.1f} | {', '.join(flags)} |")
        lines += ["", "These rows completed only with the experiment's larger budgets. The limits are enforced by the API; increasing them is an explicit operator choice and does not remove their compute cost.", ""]
        if manchester_large:
            selected = {row["backend"]: row for row in summaries if row["dataset"] == "manchester" and row["workload"] == "100x100"}
            lines += [f"Manchester's 100x100 contained {manchester_large['null_cells']:,} null cells and visited {selected['flexible']['max_matrix_visited_states']:,} states for flexible and {selected[manchester_large['experimental_algorithm']]['max_matrix_visited_states']:,} for certified CH. Inference from the target-set search termination: unreachable targets can keep a row searching until its reachable component is exhausted, contributing to this cost. This local sample does not establish a regional or national workload distribution.", ""]
    else:
        lines += ["All measured matrix groups stayed below the shipped deadline and state-count ceilings on this run; admission/concurrent throughput was not measured.", ""]
    raw_flags = sum(row["mismatched_cells"] for row in comparisons if row["actual"] == "ch_pairwise" and row["expected"] == "flexible")
    fresh_proofs = sum(identity["pairwise_ch_fresh_unique_case_proofs"] for identity in identities)
    candidate_flags = sum(identity["raw_candidate_flagged_cells"] for identity in identities)
    classification_counts = Counter()
    for identity in identities:
        classification_counts.update(identity["known_pairwise_difference_classifications"])
    known_note = (f"The original proof gate remains FAIL for {known_differences} cases with nonzero exact rational CH-versus-Dijkstra objective differences. "
                  f"{classification_counts['IEEE_SEQUENTIAL_SUM_TIE_WITH_NONZERO_EXACT_DELTA']} have identical recomputed sequential IEEE-754 double labels despite their nonzero exact deltas; "
                  f"{classification_counts['UPSTREAM_CH_ONE_ULP_ORIGINAL_OBJECTIVE_DIFFERENCE']} case has a one-ULP larger recomputed ordinary-CH label. "
                  "The independently legal alternatives also have different actual time and distance. New case-bound posthoc ledgers and explicit root SHA approvals permit a scoped performance comparison while preserving those accuracy differences; they do not turn the original FAIL into an exact-optimality pass. Float32 branch-cost ranking is recorded as context, and the particular historical contraction/witness decision causing selection was not replayed or proved."
                  if known_differences else "The fresh ordinary-CH proof audit found no unresolved original-objective discrepancy in this sample.")
    lines += [f"The untimed raw bucket-CH candidate pass produced {candidate_flags:,} flagged cells versus independent upstream Dijkstra on the identical complete QueryGraph. Each flagged raw triple is rejected by the mandatory certificate; returned API cells exactly match flexible and pass the independent oracle thresholds. Full candidate and independent outcomes, every flag, and raw statistics are preserved in each `raw-candidates.json`, with individually bound `raw-candidate-reconciliation.json` records. The original timing and initial oracle passes did not capture candidate edge paths. A later read-only replay of the unchanged production algorithm captured {sum(identity['fresh_actual_candidate_path_review']['unique_proofs'] for identity in identities):,} actual selected candidate paths covering all {candidate_flags:,} indexed flags; `candidate-path-reconciliation.json` and `candidate-path-witnesses.jsonl.gz` bind every replay to the original scalars, source/class/cache identities and exact root approvals. This is fresh replay evidence, rather than a capture of every timed path. Candidate-only exactness is not claimed.", "",
              f"Manchester's {next(identity['fresh_actual_candidate_path_review']['unique_proofs'] for identity in identities if identity['dataset'] == 'manchester') if any(identity['dataset'] == 'manchester' for identity in identities) else 0} fresh candidate paths follow the same physical routes as the independent oracle. Explicitly replacing removed original whole-edge shortcut leaves with the actual shared-overlay virtual chains reproduces the oracle's exact weight, distance and time sums. The {sum(identity['fresh_actual_candidate_path_review']['split_leaf_occurrences'] for identity in identities):,} recorded leaf replacements show the individual stored-distance/geometry-length and per-piece rounding differences. Monaco retains the separately described alternative-route cases. These are scoped candidate accounting and rejection records; every returned API cell still comes from the mandatory flexible certificate.", "",
              f"Ordinary pairwise CH versus flexible produced {raw_flags:,} flagged first-outcome cells across these workloads. Every flag and full outcome is preserved and linked to {fresh_proofs:,} fresh selected-path proofs in `pairwise-ch-reconciliation.json` and `pairwise-ch-witnesses.jsonl.gz`. The fresh proof reconstructs the actual default CH search trees, original-edge expansion, independent pair/shared Dijkstra paths and the specific shortcut arithmetic. The independent Python analyzer checks exact rational path sums and source-derived float/shortcut rounding arithmetic, all indexed coverage and scalar/coordinate bindings. Earlier CH precision reports were not reused as waivers. Timed repetitions were checked for exact metric-cell stability; the proof paths themselves are fresh replays, not captures of every timed repetition.", "",
              known_note, "",
              "The samples retain all production counters and phase timings, current-thread allocation deltas, serialization costs, reachable/null counts and heap-pool peak sums. Allocation counts measure cumulative bytes allocated by the calling thread; they are not retained memory, peak process memory or RSS. Dataset source fingerprints, prepared-cache hashes, source hashes, JVM arguments and evidence hashes are recorded in each metadata file.", "",
              "The experiment is limited to a serial request stream on the bundled Monaco fixture and a local sample within the checksum-pinned Greater Manchester extract. It provides no concurrent-throughput, national-scale, edge-based CH matrix, or unverified CH-only exactness claim.", "",
              "To replay the measurements in cmd.exe, first prepare the existing `target/matrix-monaco-server` and `target/matrix-manchester-server` caches using the documented run configurations, then stop both servers. The harness loads these caches with writes disabled and refuses to overwrite a prior experiment directory. These commands write separate replay artifacts:", "",
              "```bat",
              "mvn -B -pl core -am test -Dtest=MatrixCHMatrixBenchmarkTest -Dsurefire.failIfNoSpecifiedTests=false -Dtest.heap.max=2g -Dmatrix.ch.benchmark=true -Dmatrix.ch.benchmark.dataset=monaco -Dmatrix.ch.benchmark.output=docs/matrix/ch-matrix-benchmark-monaco-replay",
              "mvn -B -pl core -am test -Dtest=MatrixCHMatrixBenchmarkTest -Dsurefire.failIfNoSpecifiedTests=false -Dtest.heap.max=2g -Dmatrix.ch.benchmark=true -Dmatrix.ch.benchmark.dataset=manchester -Dmatrix.ch.benchmark.output=docs/matrix/ch-matrix-benchmark-manchester-replay",
              "```", "",
              "A measurement replay can retain a FAIL for the known ordinary-CH objective cases. New output directories require fresh case-bound diagnostic traces, rejection/posthoc ledgers and checksum-bound reviews before the strict analyzer accepts them; this report's approvals do not transfer to a new run. To recheck the shipped frozen evidence and regenerate its derivative tables:", "",
              "```bat",
              "python scripts\\summarize-ch-matrix-benchmarks.py",
              "```", "",
              f"[Machine-readable analysis]({output.name}-analysis.json) · [Summary CSV]({output.name}-results.csv)", ""]
    output.with_name(output.name + "-report.md").write_text("\n".join(lines), encoding="utf-8")
    print(json.dumps({"status": analysis["status"], "samples": sum(item["samples"] for item in identities), "groups": len(summaries), "output_prefix": str(output)}))


if __name__ == "__main__":
    main()
