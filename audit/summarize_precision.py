import json
from pathlib import Path

root = Path(__file__).resolve().parent.parent
report = json.loads((root / "docs/matrix/fixture-validation.json").read_text())
summary = {key: report[key] for key in ("status", "seed", "profile_counts", "diagnosed_case_count", "unexplained_cases")}
cases = report["diagnosed_cases"]
summary["max_absolute_independent_minus_shared"] = {
    metric: max((abs(case["independent_minus_shared"][metric]) for case in cases), default=0)
    for metric in ("weight", "time_ms", "distance_m")
}
summary["first_case"] = {
    key: cases[0][key]
    for key in ("profile", "from_index", "to_index", "from", "to", "independent_snaps", "combined_snaps",
                "upstream_route_metrics", "reported_shared_metrics", "independent_minus_shared", "per_original_edge_deltas")
} if cases else None
print(json.dumps(summary, indent=2))
