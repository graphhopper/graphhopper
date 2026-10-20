"""Record rejection of each raw CH candidate flag by the mandatory API certificate.

This creates new derivative evidence and never changes the original measurements.
The actual BucketCHMatrix edge path was not captured. A fresh ordinary CH path is
linked only as independent legal-route context, never as the captured candidate.
Python standard library only.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import math
from fractions import Fraction
from pathlib import Path


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def fraction(value: float | int | str) -> Fraction:
    return Fraction.from_float(value) if isinstance(value, float) else Fraction(value)


def triple(body: dict, i: int, j: int) -> dict:
    return {key: body[key][i][j] for key in ("weights", "times_ms", "distances")}


def compatible(actual: dict, expected: dict) -> bool:
    if actual["weights"] is None or expected["weights"] is None:
        return actual == expected
    return (abs(actual["weights"] - expected["weights"]) <= 1e-7 + abs(expected["weights"]) * 1e-10
            and abs(actual["distances"] - expected["distances"]) <= .002
            and abs(actual["times_ms"] - expected["times_ms"]) <= 1)


def build_fresh(directory: Path, metadata: dict, raw: dict, outcomes: dict, axes: dict) -> dict:
    trace = json.loads((directory / "candidate-path-reconciliation.json").read_text())
    assert trace["status"] == "FRESH_ACTUAL_CANDIDATE_PATHS_PROVEN" and trace["unexplained_unique_case_proofs"] == 0
    summaries = {proof["proof_id"]: proof for proof in trace["unique_case_proofs"]}
    links = {(row["workload"], row["origin_index"], row["destination_index"]): row for row in trace["indexed_raw_flags"]}
    cases = []
    for workload, report in raw.items():
        for issue in report["raw_candidate_vs_independent_oracle"]["all_mismatches"]:
            i, j = issue["origin_index"], issue["destination_index"]
            candidate = triple(report["candidate_metrics"], i, j)
            oracle = triple(report["independent_plain_dijkstra_metrics"], i, j)
            certified = triple(outcomes[workload][metadata["experimental_algorithm"]], i, j)
            flexible = triple(outcomes[workload]["flexible"], i, j)
            assert candidate != certified and certified == flexible and compatible(certified, oracle)
            link = links[(workload, i, j)]
            proof = summaries[link["proof_id"]]
            assert link["observed_raw_candidate"] == candidate and link["proved"] is True and proof["proved"] is True
            assert proof["actual_candidate_path_captured"] is True
            assert proof["candidate_original_objective_optimality_claimed"] is False
            assert proof["classification"] == "ACTUAL_CANDIDATE_SAME_PHYSICAL_ROUTE_WITH_EXPLICIT_QUERY_SPLIT_METRICS"
            assert proof["represented_candidate_same_physical_route_as_oracle"] is True
            assert proof["represented_candidate_exact_all_metrics_equal_oracle"] is True
            cases.append({"workload": workload, "origin_index": i, "destination_index": j,
                          "from": axes[workload]["from_points"][i], "to": axes[workload]["to_points"][j],
                          "classification": "REJECTED_BY_MANDATORY_FLEXIBLE_CERTIFICATE",
                          "raw_candidate": candidate, "independent_plain_dijkstra": oracle, "returned_certified": certified, "returned_flexible": flexible,
                          "original_raw_issues": issue["issues"], "returned_certified_exactly_matches_flexible": True,
                          "returned_certified_matches_independent_oracle_fixed_thresholds": True,
                          "original_raw_candidate_path_captured": False, "fresh_replay_actual_candidate_path_captured": True,
                          "fresh_candidate_proof_id": link["proof_id"], "fresh_candidate_witness_line_sha256": proof["witness_line_sha256"],
                          "fresh_candidate_classification": proof["classification"],
                          "same_physical_route_after_explicit_query_split_accounting": True,
                          "represented_candidate_exact_all_metrics_equal_oracle": True})
    assert len(cases) == len(links) == trace["actual_indexed_raw_flags"]
    names = ("metadata.json", "raw-candidates.json", "first-outcomes.json", "requests.json", "samples.csv", "candidate-path-reconciliation.json", "candidate-path-witnesses.jsonl.gz")
    return {"status": "RAW_CANDIDATE_FLAGS_REJECTED_BY_MANDATORY_CERTIFICATE", "dataset": metadata["dataset"],
            "scope": "Every indexed raw flag is rejected by the mandatory API certificate. A fresh trace of the unchanged production algorithm captures the actual replay-selected arcs, joins, labels and expanded path, with exact bindings to original raw scalar outputs. The same physical route represented using the complete shared QueryGraph's actual virtual chains has exact oracle metrics. The original benchmark/untimed-pass paths themselves were not captured. This is per-case representation accounting and rejection, not candidate-only exactness or original-objective optimality.",
            "uses_fresh_actual_candidate_path_proofs": True,
            "original_evidence_sha256": {name: sha256(directory / name) for name in names},
            "raw_flagged_cells": len(cases), "rejected_indexed_cases": cases, "remaining_unhandled_raw_flags": 0}


def build(directory: Path, subset: Path | None = None, fresh: bool = False) -> dict:
    metadata = json.loads((directory / "metadata.json").read_text())
    raw = json.loads((directory / "raw-candidates.json").read_text())
    outcomes = json.loads((directory / "first-outcomes.json").read_text())
    requests = json.loads((directory / "requests.json").read_text())["workloads"]
    axes = {row["workload"]: row["request"] for row in requests if row["backend"] == "flexible"}
    if fresh:
        return build_fresh(directory, metadata, raw, outcomes, axes)
    reconciliation = json.loads((directory / "pairwise-ch-reconciliation.json").read_text())
    proof_links = {(row["workload"], row["origin_index"], row["destination_index"]): row for row in reconciliation["indexed_flag_cases"]}
    summaries = {row["proof_id"]: row for row in reconciliation["unique_case_proofs"]}
    flagged = [(workload, issue) for workload, report in raw.items() for issue in report["raw_candidate_vs_independent_oracle"]["all_mismatches"]]
    required = {proof_links[(workload, issue["origin_index"], issue["destination_index"])]["proof_id"] for workload, issue in flagged}
    witnesses = {}
    if subset is not None:
        for witness in json.loads(subset.read_text()):
            if witness["proof_id"] in required:
                assert witness["witness_line_sha256"] == summaries[witness["proof_id"]]["witness_line_sha256"]
                witnesses[witness["proof_id"]] = witness
    else:
        with gzip.open(directory / "pairwise-ch-witnesses.jsonl.gz", "rt", encoding="utf-8") as stream:
            for line in stream:
                witness = json.loads(line)
                if witness["proof_id"] not in required:
                    continue
                assert hashlib.sha256(line.rstrip("\r\n").encode()).hexdigest() == summaries[witness["proof_id"]]["witness_line_sha256"]
                witnesses[witness["proof_id"]] = witness
    assert witnesses.keys() == required
    cases = []
    for workload, issue in flagged:
        i, j = issue["origin_index"], issue["destination_index"]
        candidate = triple(raw[workload]["candidate_metrics"], i, j)
        oracle = triple(raw[workload]["independent_plain_dijkstra_metrics"], i, j)
        certified = triple(outcomes[workload][metadata["experimental_algorithm"]], i, j)
        flexible = triple(outcomes[workload]["flexible"], i, j)
        assert candidate == {key: issue["actual"][key] for key in candidate}
        assert oracle == {key: issue["expected"][key] for key in oracle}
        assert candidate != certified
        assert certified == flexible and compatible(certified, oracle)
        link = proof_links[(workload, i, j)]
        witness = witnesses[link["proof_id"]]
        assert witness["from"] == axes[workload]["from_points"][i] and witness["to"] == axes[workload]["to_points"][j]
        assert witness["graph_fingerprint"] == metadata["graph_fingerprint"]
        assert witness["profile_fingerprint"] == metadata["profile_fingerprints"]["foot"]
        assert witness["build_commit"] == metadata["build_commit"]
        path = witness["ch_path"]
        assert path["legal_and_requested_endpoints"] is True
        assert all(edge["turn_weight"] == 0 and edge["turn_time_ms"] == 0 for edge in path["edge_contributions"])
        context = {}
        for metric, term in (("weights", "edge_weight"), ("distances", "distance_m")):
            values = [edge[term] for edge in path["edge_contributions"]]
            assert all(math.isfinite(value) and value >= 0 for value in values)
            total = sum(map(fraction, values), Fraction(0))
            # A full binary parenthesization of n nonnegative terms has n-1 additions.
            # This is a bound for THIS LEGAL CONTEXT PATH, not for the uncaptured
            # BucketCHMatrix candidate path or its unknown arithmetic operation count.
            operations = max(0, len(values) - 1)
            u = Fraction(1, 2**53)
            bound = Fraction(0) if not operations else operations * u / (1 - operations * u) * total
            error = abs(fraction(candidate[metric]) - total)
            context[metric] = {"exact_legal_path_terms_sum_fraction": str(total), "context_path_nonnegative_term_count": len(values),
                               "context_path_binary_addition_operation_bound": operations, "context_path_sum_association_absolute_bound_fraction": str(bound),
                               "observed_raw_minus_context_exact_sum_fraction": str(fraction(candidate[metric]) - total),
                               "raw_scalar_within_context_path_association_bound": error <= bound}
            assert error <= bound, "The linked legal route does not provide the claimed scalar context"
        assert candidate["times_ms"] == path["metrics"]["times_ms"]
        cases.append({"workload": workload, "origin_index": i, "destination_index": j,
                      "from": axes[workload]["from_points"][i], "to": axes[workload]["to_points"][j],
                      "classification": "REJECTED_BY_MANDATORY_FLEXIBLE_CERTIFICATE",
                      "raw_candidate": candidate, "independent_plain_dijkstra": oracle, "returned_certified": certified, "returned_flexible": flexible,
                      "original_raw_issues": issue["issues"], "returned_certified_exactly_matches_flexible": True,
                      "returned_certified_matches_independent_oracle_fixed_thresholds": True,
                      "actual_raw_candidate_path_captured": False,
                      "linked_legal_ordinary_ch_context_proof_id": link["proof_id"], "linked_witness_line_sha256": summaries[link["proof_id"]]["witness_line_sha256"],
                      "legal_ch_context_time_exactly_matches_raw_candidate": True, "legal_ch_context_scalar_association": context})
    names = ("metadata.json", "raw-candidates.json", "first-outcomes.json", "requests.json", "samples.csv", "pairwise-ch-reconciliation.json", "pairwise-ch-witnesses.jsonl.gz")
    return {"status": "RAW_CANDIDATE_FLAGS_REJECTED_BY_MANDATORY_CERTIFICATE", "dataset": metadata["dataset"],
            "scope": "Every indexed raw candidate flag is a rejected diagnostic result. The actual BucketCHMatrix path was not captured. Linked fresh ordinary CH paths provide legal-route context and source-derived bounds only for those linked paths; they do not establish the candidate's actual edge sequence or operation count. Returned API cells exactly match flexible and pass independent plain-Dijkstra thresholds. No candidate-only exactness or optimality claim is made.",
            "original_evidence_sha256": {name: sha256(directory / name) for name in names},
            "raw_flagged_cells": len(flagged), "rejected_indexed_cases": cases, "remaining_unhandled_raw_flags": 0}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--witness-subset", type=Path)
    parser.add_argument("--fresh-candidate-paths", action="store_true")
    args = parser.parse_args()
    result = build(args.directory, args.witness_subset, args.fresh_candidate_paths)
    output = args.directory / "raw-candidate-reconciliation.json"
    encoded = json.dumps(result, indent=2) + "\n"
    assert not output.exists() or output.read_text() == encoded, "Preserve existing derivative evidence; do not overwrite a different ledger"
    output.write_text(encoded)
    print(json.dumps({"status": result["status"], "raw_flagged_cells": result["raw_flagged_cells"], "sha256": sha256(output), "output": str(output)}))


if __name__ == "__main__":
    main()
