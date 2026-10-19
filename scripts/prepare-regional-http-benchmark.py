"""Export both static profiles over the three supplied Manchester example axes."""
from copy import deepcopy
from hashlib import sha256
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
output = root / "docs/matrix/regional-http-benchmark"
output.mkdir(exist_ok=True)
sources = {}
workloads = []
for name in ("manchester_one_to_many", "manchester_rectangular", "manchester_square"):
    path = root / "docs/matrix/examples" / (name + ".json")
    raw = path.read_bytes()
    sources[str(path.relative_to(root))] = sha256(raw).hexdigest()
    base = json.loads(raw)
    if "points" in base:
        base["from_points"] = base["points"]
        base["to_points"] = base.pop("points")
    for profile in ("foot", "car"):
        request = deepcopy(base)
        request["profile"] = profile
        request["fail_fast"] = False
        request["out_arrays"] = ["times", "times_ms", "distances", "weights"]
        request["local"] = {"algorithm": "flexible"}
        workloads.append({"name": name, "profile": profile, "request": request})
report = {"seed": None, "coordinate_order": "longitude,latitude", "coordinate_policy": "Unchanged axes from the supplied pack examples; each run with both configured profiles.",
          "sources_sha256": sources, "workloads": workloads}
path = output / "requests.json"
path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(path)
