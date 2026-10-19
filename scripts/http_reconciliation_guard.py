"""Check explicitly approved, case-bound CH precision classifications.

Original HTTP reports are never changed. Retained scalar records and aggregate
repetitions are distinguished; fresh expanded paths prove each indexed case.
"""
from collections import Counter
from decimal import Decimal, localcontext
import hashlib
import json
import math
from pathlib import Path
import re


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def exact_path_metrics(edges):
    cost, distance, millis = Decimal(0), Decimal(0), 0
    previous = None
    for edge in edges:
        require(previous is None or previous == edge.get("from_node"), "Expanded path is not contiguous")
        previous = edge.get("to_node")
        for key in ("edge_weight", "turn_weight", "distance_m"):
            value = edge.get(key)
            require(type(value) in (int, float) and math.isfinite(value) and value >= 0, "Invalid path metric")
        for key in ("edge_time_ms", "turn_time_ms"):
            require(type(edge.get(key)) is int and edge[key] >= 0, "Invalid path milliseconds")
        cost += Decimal.from_float(float(edge["edge_weight"])) + Decimal.from_float(float(edge["turn_weight"]))
        distance += Decimal.from_float(float(edge["distance_m"]))
        millis += edge["edge_time_ms"] + edge["turn_time_ms"]
    return cost, distance, millis


def inspect_reconciliation(name, report, docs, approvals, runtime, artifact):
    """Return eligibility and exact approval object; approval is always explicit."""
    docs = Path(docs)
    repo = docs.parents[1]
    proof_name = str(Path(name).with_name("ch-weight-reconciliation.json")).replace("\\", "/")
    result = {"http_report": name, "reconciliation": proof_name, "eligible": False, "approved": False}
    try:
        report_path, proof_path = docs / name, docs / proof_name
        require(proof_path.is_file(), "No retained CH path reconciliation exists")
        proof = json.loads(proof_path.read_text(encoding="utf-8"))
        approval = {"http_report": name, "http_report_sha256": sha256(report_path),
                    "reconciliation": proof_name, "reconciliation_sha256": sha256(proof_path)}
        result["required_approval"] = approval
        result["status"] = proof.get("status")
        require(proof.get("status") == "PASS_WITH_PATH_PROVEN_CH_STORAGE_PRECISION", "Reconciliation is not passing")
        require(proof.get("production_algorithm_changed") is False, "Classification changed the production algorithm")
        require(proof.get("remaining_unexplained_cases") == 0 and proof.get("unexplained_cases") == [], "Unexplained cases remain")
        require(proof.get("original_report_sha256") == approval["http_report_sha256"], "Original report SHA differs")
        require(proof.get("original_report_status") == report.get("status") == "MEASURED", "Original report timing is incomplete")
        request_path = Path(report["requests_file"])
        request_path = request_path if request_path.is_absolute() else repo / request_path
        require(request_path.is_file(), "Original request set is missing")
        require(proof.get("original_requests_sha256") == report.get("requests_sha256") == sha256(request_path), "Request SHA differs")
        samples_path = report_path.with_name("http-benchmark-samples.csv")
        require(samples_path.is_file() and proof.get("original_samples_sha256") == sha256(samples_path), "Sample CSV SHA differs")
        require(proof.get("csv_repeat_weight_checksums_stable") is True, "Repeat checksums were not stable")
        workloads = json.loads(request_path.read_text(encoding="utf-8"))["workloads"]
        requests = {(entry["profile"], entry["name"]): entry["request"] for entry in workloads}
        retained, observed = Counter(), {}
        aggregate = 0
        for comparison in report.get("comparisons", []):
            aggregate += comparison.get("mismatched_cells_across_repetitions", 0)
            for issue in comparison.get("mismatches_first_100", []):
                key = comparison["profile"], comparison["workload"], issue["from_index"], issue["to_index"]
                require(len(issue.get("issues", [])) == 1, "Only individually proved weight-only cases are accepted")
                match = re.fullmatch(r"weight: route=([^,]+), matrix=([^,]+), tolerance=([^,]+)", issue["issues"][0])
                require(match is not None, "Non-weight or malformed original diagnostic")
                scalars = tuple(float(match.group(index)) for index in (1, 2))
                require(all(math.isfinite(value) for value in scalars), "Nonfinite original scalar")
                require(key not in observed or observed[key] == scalars, "Retained original scalars changed across repetitions")
                observed[key] = scalars
                retained[key] += 1
        require(bool(retained), "No flagged indexed cases need classification")
        indexed = proof.get("indexed_cases", [])
        keys = [(case.get("profile"), case.get("workload"), case.get("from_index"), case.get("to_index")) for case in indexed]
        require(len(set(keys)) == len(keys) and set(keys) == set(retained), "Indexed classification coverage differs")
        require(proof.get("indexed_flagged_cases") == len(keys), "Indexed count differs")
        require(proof.get("retained_original_issue_records_checked") == sum(retained.values()), "Retained scalar count differs")
        require(proof.get("aggregate_original_flag_instances") == aggregate == len(keys) * report["repeats"], "Aggregate flag count differs")
        pairs = proof.get("unique_pair_proofs", [])
        pair_map = {pair.get("id"): pair for pair in pairs}
        require(len(pair_map) == len(pairs) == proof.get("unique_profile_coordinate_pairs"), "Unique-pair proof IDs/count differ")
        require({case.get("unique_pair_proof_id") for case in indexed} == set(pair_map), "Linked unique-pair coverage differs")
        identities = report.get("matrix_provenance", {})
        require(set(identities) == set(proof.get("replay_profile_fingerprints", {})), "Replay profiles differ")
        for profile, identity in identities.items():
            require(identity.get("graph_fingerprint") == proof.get("replay_graph_fingerprint"), "Replay graph fingerprint differs")
            require(identity.get("profile_fingerprint") == proof["replay_profile_fingerprints"][profile], "Replay profile fingerprint differs")
            require(identity.get("build_commit") == proof.get("replay_build_commit") == artifact.get("embedded_build_identity"), "Replay/build identity differs")
        server_name = "manchester" if name.startswith("regional-http-benchmark/") else "monaco"
        server = runtime.get("servers", {}).get(server_name, {})
        require(server.get("artifact_sha256") == artifact.get("sha256"), "Replay server JAR identity differs")
        require(server.get("configuration_sha256") == report.get("server_config_sha256"), "Server config identity differs")
        require(Path(proof.get("prepared_graph", "__missing__")).resolve() == Path(server.get("graph_directory_resolved", "__absent__")).resolve(), "Prepared graph directory differs")
        with localcontext() as context:
            context.prec = 2048
            for pair in pairs:
                flags = ("expanded_path_legal", "plain_path_legal", "expanded_metrics_match_plain_dijkstra",
                         "expanded_metrics_match_ch_extracted_time_distance", "path_endpoints_match",
                         "shortcut_expansion_exactly_matches_actual_path_cost_terms", "accepted_with_independent_path_evidence")
                require(all(pair.get(flag) is True for flag in flags), "A unique-pair path proof was not accepted")
                require(pair.get("same_copy_canonical_expanded_path") is True or pair.get("explicit_exact_metric_optimal_alternative") is True, "Alternate path lacks exact objective proof")
                expanded = exact_path_metrics(pair["expanded_ch_edges"])
                plain = exact_path_metrics(pair["plain_dijkstra_edges"])
                require(expanded == plain, "Expanded/plain paths have unequal exact cost, distance or milliseconds")
                require(expanded[0] == Decimal(pair["exact_actual_path_cost_terms_sum"]) == Decimal(pair["exact_plain_path_cost_terms_sum"]), "Exact objective scalar differs from path terms")
                require(expanded[1] == Decimal(pair["exact_ch_path_distance_terms_sum"]) == Decimal(pair["exact_plain_path_distance_terms_sum"]), "Exact distance scalar differs from path terms")
                require(expanded[2] == pair.get("fresh_time_ms"), "Fresh milliseconds differ from path terms")
                quantization = pair.get("quantization_audit", {})
                require(quantization.get("proved") is True and quantization.get("failures") == [], "CH quantization proof failed")
                selected_edges, shortcuts = quantization.get("selected_edges"), quantization.get("selected_shortcuts")
                require(isinstance(selected_edges, list) and isinstance(shortcuts, list), "Selected CH edge/shortcut evidence is missing")
                require(all(type(edge.get("shortcut")) is bool for edge in selected_edges), "Selected edge shortcut flag is invalid")
                fields = ("edge", "base", "adj", "reverse_search")
                selected_keys = Counter(tuple(edge.get(field) for field in fields) + (edge.get("edge_weight"),)
                                        for edge in selected_edges if edge["shortcut"])
                shortcut_keys = Counter(tuple(shortcut.get(field) for field in fields) + (shortcut.get("stored_weight"),)
                                        for shortcut in shortcuts)
                require(selected_keys == shortcut_keys, "Selected shortcuts are not completely covered by storage predictions")
                require(all(shortcut.get("proved") is True and shortcut.get("unique_predicted_stored_weight") is True
                            and shortcut.get("stored_weight") == shortcut.get("CHStorage_rounded_lower") == shortcut.get("CHStorage_rounded_upper")
                            for shortcut in shortcuts), "A selected shortcut has no unique storage prediction")
            for case, key in zip(indexed, keys):
                require(case.get("original_report_sha256") == approval["http_report_sha256"], "Indexed report binding differs")
                require(case.get("retained_original_scalar_records_checked") == retained[key], "Indexed retained scalar count differs")
                require((case.get("observed_ch_weight"), case.get("observed_matrix_weight")) == observed[key], "Indexed original weights differ")
                flags = ("exact_route_serializer_round6_matches", "matrix_matches_actual_recomputed_path",
                         "ch_path_and_quantization_proved", "accepted_with_independent_path_evidence")
                require(all(case.get(flag) is True for flag in flags), "An indexed case was not individually accepted")
                pair = pair_map[case["unique_pair_proof_id"]]
                request = requests[key[:2]]
                require(pair.get("profile") == key[0] and pair.get("from") == request["from_points"][key[2]]
                        and pair.get("to") == request["to_points"][key[3]], "Indexed coordinates/profile differ from linked path")
                independent = case.get("matrix_vs_independent_path_evidence", {})
                require(all(independent.get(flag) is True for flag in ("same_query_graph_exact", "independent_matches_route",
                            "all_reconstructed_paths_legal", "same_snapped_coordinates")), "Independent matrix path checks failed")
        result.update(eligible=True, approved=approval in approvals, indexed_cases=len(indexed), unique_pair_proofs=len(pairs),
                      retained_scalar_records=sum(retained.values()), aggregate_flag_events=aggregate,
                      scope_note=proof.get("historic_evidence_scope"))
        if not result["approved"]:
            result["pending"] = "Root has not explicitly approved this exact report/reconciliation SHA pair"
    except (OSError, ValueError, KeyError, TypeError, IndexError) as error:
        result["failure"] = str(error)
    return result
