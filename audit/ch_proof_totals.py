"""Summarize the separate CH reconciliation ledgers without large path arrays."""
import json
from collections import Counter
from pathlib import Path

for folder in (Path("docs/matrix/regional-http-benchmark"), Path("docs/matrix")):
    ledger = json.loads((folder / "ch-weight-reconciliation.json").read_text())
    pairs = ledger["unique_pair_proofs"]
    cases = ledger["indexed_cases"]
    print(folder)
    for key in ("status", "original_report_status", "original_report_sha256", "original_requests_sha256", "original_samples_sha256",
                "replay_graph_fingerprint", "replay_profile_fingerprints", "retained_original_issue_records_checked",
                "aggregate_original_flag_instances", "indexed_flagged_cases", "unique_profile_coordinate_pairs", "remaining_unexplained_cases"):
        print(key, ledger[key])
    print("profile_indexed_cases", dict(Counter(p["profile"] for p in cases)))
    print("profile_unique_pairs", dict(Counter(p["profile"] for p in pairs)))
    print("exact_metric_alternatives", sum(p["explicit_exact_metric_optimal_alternative"] for p in pairs))
    ties = [p for p in pairs if not p["same_copy_canonical_expanded_path"]]
    print("differing_paths", len(ties), [p["id"] for p in ties])
    print("indexed_differing_paths", sum(c["unique_pair_proof_id"] in {p["id"] for p in ties} for c in cases))
    print("selected_shortcuts", sum(len(p["quantization_audit"]["selected_shortcuts"]) for p in pairs))
    print("all_single_quantized_predictions", all(s["unique_predicted_stored_weight"] for p in pairs for s in p["quantization_audit"]["selected_shortcuts"]))
    print("max_CH_unrounded_delta", max(abs(p["selected_ch_reported_weight"] - p["unrounded_expanded_base_weight"]) for p in pairs))
    print("max_original_scalar_delta", max(abs(p["observed_ch_weight"] - p["observed_matrix_weight"]) for p in cases))
