#!/usr/bin/env python3
r"""Call the local matrix endpoint with Python 3.10+ standard library only.

Coordinates are [longitude, latitude]; rows are origins and columns destinations.
Optional pandas in an isolated Conda environment (cmd.exe):
    conda create -n graphhopper-matrix-client python=3.12 pandas
    conda run -n graphhopper-matrix-client python scripts\client.py --output matrix.json
Then in that environment:
    import json, pandas as pd
    body = json.load(open("matrix.json", encoding="utf-8"))
    frame = pd.DataFrame(body["times_ms"])
JSON nulls remain missing values; do not fill them with zero.
"""
from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path
import sys
import urllib.error
import urllib.request


def reject_constant(value):
    raise ValueError(f"Non-finite JSON number: {value}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://localhost:8997")
    parser.add_argument("--profile", choices=("foot", "car"), default="foot")
    parser.add_argument("--algorithm", choices=("auto", "reference", "flexible", "ch_experimental"), default="flexible")
    parser.add_argument("--request", type=Path, help="JSON request; overrides built-in example")
    parser.add_argument("--output", type=Path, default=Path("matrix.json"))
    parser.add_argument("--csv", type=Path, help="Optional long-format metrics CSV")
    parser.add_argument("--timeout", type=float, default=90)
    args = parser.parse_args()
    if args.timeout <= 0:
        parser.error("timeout must be positive")
    if args.request:
        payload = json.loads(args.request.read_text(encoding="utf-8-sig"), parse_constant=reject_constant)
    else:
        payload = {
            "profile": args.profile,
            "from_points": [[7.418737, 43.727687], [7.420771, 43.730864]],
            "to_points": [[7.436566, 43.74958], [7.424355, 43.739662], [7.418737, 43.727687]],
            "out_arrays": ["times", "times_ms", "distances", "weights"],
            "fail_fast": False,
            "local": {"algorithm": args.algorithm},
        }
    req = urllib.request.Request(args.url.rstrip("/") + "/matrix",
                                 data=json.dumps(payload, allow_nan=False).encode("utf-8"),
                                 method="POST", headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=args.timeout) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as exc:
        status, raw = exc.code, exc.read()
    body = json.loads(raw, parse_constant=reject_constant)
    if not isinstance(body, dict):
        raise ValueError("Expected JSON object response")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(body, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    print(f"HTTP {status}; response saved to {args.output}")
    if status != 200:
        print(body.get("message", "Matrix request failed"), file=sys.stderr)
        return 1
    metrics = [key for key in ("times", "times_ms", "distances", "weights") if key in body]
    if not metrics:
        raise ValueError("No metric arrays returned")
    rows = len(body[metrics[0]])
    columns = len(body[metrics[0]][0]) if rows else 0
    for key in metrics:
        if len(body[key]) != rows or any(len(row) != columns for row in body[key]):
            raise ValueError(f"Inconsistent array dimensions: {key}")
    if args.csv:
        args.csv.parent.mkdir(parents=True, exist_ok=True)
        with args.csv.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow(["from_index", "to_index", *metrics])
            for i in range(rows):
                for j in range(columns):
                    writer.writerow([i, j, *[body[key][i][j] for key in metrics]])
    print(f"{rows} x {columns}; algorithm={body.get('info', {}).get('local_matrix', {}).get('algorithm')}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError) as exc:
        print(f"Client failed: {exc}", file=sys.stderr)
        raise SystemExit(2)
