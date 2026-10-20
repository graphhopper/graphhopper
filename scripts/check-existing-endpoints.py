"""Record a small live regression check of unmodified street endpoints (stdlib only)."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import urllib.parse
import urllib.request


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="http://localhost:8999")
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "docs/matrix/live-endpoint-checks.json")
    args = parser.parse_args()
    rows = []
    for profile in ("foot", "car"):
        for endpoint in ("route", "isochrone", "spt"):
            params = [("profile", profile), ("point", "53.4774,-2.2309")]
            if endpoint == "route":
                params += [("point", "53.4875,-2.242"), ("calc_points", "false"), ("instructions", "false")]
            else:
                params += [("time_limit", "60")]
            url = args.url.rstrip("/") + "/" + endpoint + "?" + urllib.parse.urlencode(params)
            with urllib.request.urlopen(url, timeout=60) as response:
                body = response.read()
                status = response.status
                content_type = response.headers.get("Content-Type")
            text = body.decode("utf-8")
            if endpoint == "spt":
                lines = text.strip().splitlines()
                assert len(lines) > 1 and all(name in lines[0] for name in ("longitude", "latitude", "time", "distance")), text[:500]
                details = {"csv_rows_including_header": len(lines), "csv_header": lines[0]}
            else:
                data = json.loads(text)
                if endpoint == "route":
                    path = data["paths"][0]
                    assert path["time"] >= 0 and path["distance"] >= 0
                    details = {key: path[key] for key in ("time", "distance", "weight")}
                else:
                    assert data["polygons"] and data["polygons"][0]["geometry"]["coordinates"]
                    details = {"polygons": len(data["polygons"])}
            rows.append({"profile": profile, "endpoint": endpoint, "url": url, "http_status": status,
                         "content_type": content_type, "body_bytes": len(body), "body_sha256": hashlib.sha256(body).hexdigest(), **details})
    report = {"status": "PASS", "scope": "Small live endpoint regression sample; full Maven tests supply broader coverage.",
              "created_utc": datetime.now(timezone.utc).isoformat(), "checks": rows}
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"PASS: {len(rows)} live /route, /isochrone and /spt checks; {args.output}")


if __name__ == "__main__":
    main()
