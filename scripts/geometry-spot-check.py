"""Export ordinary /route geometry for manual map review. Requires an already running local server."""
import argparse
import datetime
import html
import json
import math
from pathlib import Path
import sys
import urllib.error
import urllib.request


PICCADILLY = [-2.2309, 53.4774]
VICTORIA = [-2.242, 53.4875]
BLACKFRIARS_CENTRE = [-2.247842, 53.48381]
BLACKFRIARS_SOURCE = "https://www.planning.data.gov.uk/entity/31735750"
DETAILS = ["street_name", "road_class", "edge_key", "time", "distance", "weight", "car_access", "foot_access"]


def route(base_url, points, profile, timeout):
    payload = {"profile": profile, "points": points, "algorithm": "dijkstra", "ch.disable": True,
               "lm.disable": True, "snap_preventions": [], "instructions": True, "calc_points": True,
               "points_encoded": False, "details": DETAILS}
    request = urllib.request.Request(base_url.rstrip("/") + "/route", json.dumps(payload).encode("utf-8"),
                                     {"Content-Type": "application/json", "Accept": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return payload, response.status, json.load(response)
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8", errors="replace")
        try:
            body = json.loads(raw)
        except ValueError:
            body = {"message": raw}
        return payload, error.code, body


def validate(status, response):
    if status != 200 or not response.get("paths"):
        raise ValueError(f"Ordinary route failed: HTTP {status}: {response.get('message', response)}")
    path = response["paths"][0]
    for metric in ("distance", "weight", "time"):
        if isinstance(path.get(metric), bool) or not isinstance(path.get(metric), (int, float)) or not math.isfinite(path[metric]) or path[metric] < 0:
            raise ValueError(f"Invalid route metric: {metric}")
    geometry = path.get("points", {})
    if geometry.get("type") != "LineString" or not geometry.get("coordinates"):
        raise ValueError("Missing unencoded route geometry")
    for coordinate in geometry["coordinates"]:
        if len(coordinate) < 2 or not all(isinstance(value, (int, float)) and math.isfinite(value) for value in coordinate[:2]):
            raise ValueError("Invalid route coordinate")
    if path.get("points_encoded") is True:
        raise ValueError("Expected unencoded geometry")
    if not isinstance(path.get("instructions"), list) or not path["instructions"]:
        raise ValueError("Missing street instructions")
    for detail in DETAILS:
        if detail not in path.get("details", {}):
            raise ValueError(f"Missing path detail {detail}")
    return path


def feature_collection(name, profile, raw_points, path, restriction_context=None):
    features = [{"type": "Feature", "geometry": path["points"], "properties": {
        "case": name, "profile": profile, "distance_m": path["distance"], "time_ms": path["time"],
        "weight": path["weight"], "manual_review_status": "PENDING"}}]
    for kind, coordinates in (("raw", raw_points), ("snapped", path.get("snapped_waypoints", {}).get("coordinates", []))):
        for index, point in enumerate(coordinates):
            features.append({"type": "Feature", "geometry": {"type": "Point", "coordinates": point},
                             "properties": {"kind": kind, "index": index, "case": name, "profile": profile}})
    if restriction_context is not None:
        features[0]["properties"]["restriction_context"] = restriction_context
        features.append({"type": "Feature", "geometry": {"type": "Point", "coordinates": restriction_context["via_coordinate_lon_lat"]},
                         "properties": {"kind": "via", "index": restriction_context["relation_id"], "case": name, "profile": profile}})
    return {"type": "FeatureCollection", "features": features}


def svg_map(collection):
    route_coordinates = collection["features"][0]["geometry"]["coordinates"]
    all_points = route_coordinates + [feature["geometry"]["coordinates"] for feature in collection["features"][1:]]
    middle_latitude = sum(point[1] for point in all_points) / len(all_points)
    scale_lon = math.cos(math.radians(middle_latitude))
    projected = [(point[0] * scale_lon, -point[1]) for point in all_points]
    min_x, max_x = min(point[0] for point in projected), max(point[0] for point in projected)
    min_y, max_y = min(point[1] for point in projected), max(point[1] for point in projected)
    width, height, padding = 860, 480, 35
    scale = min((width - 2 * padding) / max(max_x - min_x, 1e-6), (height - 2 * padding) / max(max_y - min_y, 1e-6))

    def project(point):
        return padding + (point[0] * scale_lon - min_x) * scale, padding + (-point[1] - min_y) * scale

    line = " ".join(f"{x:.2f},{y:.2f}" for x, y in map(project, route_coordinates))
    drawing = [f'<svg viewBox="0 0 {width} {height}" role="img" aria-label="Actual exported route geometry">',
               '<rect width="100%" height="100%" fill="#f5f7fa"/>',
               f'<polyline points="{line}" fill="none" stroke="#2563eb" stroke-width="4"/>']
    for feature in collection["features"][1:]:
        x, y = project(feature["geometry"]["coordinates"])
        props = feature["properties"]
        color = {"raw": "#b42318", "snapped": "#07805c", "via": "#b45309"}[props["kind"]]
        title = html.escape(f"{props['kind']} {props['index']}: {feature['geometry']['coordinates']}")
        drawing.append(f'<circle cx="{x:.2f}" cy="{y:.2f}" r="6" fill="{color}" stroke="white"><title>{title}</title></circle>')
        label = "via" if props["kind"] == "via" else f'{props["kind"]} {props["index"]}'
        drawing.append(f'<text x="{x + 9:.2f}" y="{y - 8:.2f}" font-size="13" fill="{color}">{label}</text>')
    drawing.append('</svg>')
    return "".join(drawing)


def same_snap_html(observations):
    if not observations:
        return ''
    return '<aside><h2>Preserved same-snap observations</h2><p>The initial offset candidates both snapped to one point and returned zero. They did not establish opposing-carriageway coverage.</p><ul>' \
           + ''.join(f'<li><a href="{html.escape(item["response_file"])}">{html.escape(item["response_file"])}</a>: SAME SNAPPED POINT, distance {item["distance_m"]} m</li>' for item in observations) \
           + '</ul></aside>'


def make_html(records, same_snap_observations=()):
    cards = []
    for record in records:
        name = html.escape(record["case"])
        if record["status"] != "EXPORTED":
            cards.append(f'<section><h2>{name}</h2><p>{html.escape(record.get("error", "No route"))}</p></section>')
            continue
        path, collection = record["path"], record["geojson"]
        instructions = "".join(f'<tr><td>{html.escape(str(item.get("text", "")))}</td><td>{item.get("distance", "")}</td><td>{item.get("time", "")}</td></tr>'
                               for item in path["instructions"])
        centre = path["points"]["coordinates"][len(path["points"]["coordinates"]) // 2]
        osm = f"https://www.openstreetmap.org/?mlat={centre[1]}&mlon={centre[0]}#map=17/{centre[1]}/{centre[0]}"
        context = record.get("restriction_context")
        context_html = '' if context is None else '<p>Restriction candidate: confirmation PENDING.</p><pre>' + html.escape(json.dumps(context, indent=2)) + '</pre>'
        cards.append(f'<section id="{html.escape(record["file_prefix"])}"><h2>{name} · {html.escape(record["profile"])}</h2><p>{html.escape(record["purpose"])}</p>'
                     + context_html
                     + f'<p>{path["distance"]} m · {path["time"]} ms · weight {path["weight"]}. Manual review: PENDING.</p>'
                     + svg_map(collection)
                     + f'<p>Blue: route. Red: requested points. Green: snapped points. <a href="{osm}">Open corresponding OpenStreetMap area</a>. '
                     + 'The drawing has no basemap; use the exported GeoJSON and street details for review.</p>'
                     + f'<p><a href="{html.escape(record["file_prefix"])}.geojson">GeoJSON</a> · <a href="{html.escape(record["file_prefix"])}.response.json">Complete upstream response</a></p>'
                     + '<table><thead><tr><th>Instruction</th><th>Metres</th><th>Milliseconds</th></tr></thead><tbody>' + instructions + '</tbody></table>'
                     + '<details><summary>Actual path details</summary><pre>' + html.escape(json.dumps(path["details"], indent=2)) + '</pre></details></section>')
    return '<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">' \
           '<title>Manchester route geometry spot checks</title><style>body{font:16px system-ui;max-width:1100px;margin:2rem auto;padding:0 1rem;color:#172033;background:#edf1f7}section{background:white;padding:1.5rem;margin:1rem 0;border-radius:12px}svg{width:100%;border:1px solid #d8dfe9}table{border-collapse:collapse;width:100%}th,td{padding:.4rem;text-align:left;border-bottom:1px solid #ddd}pre{white-space:pre-wrap;font-size:12px}h1,h2{line-height:1.2}</style>' \
           '<h1>Manchester route geometry spot checks</h1><p>Actual independent local /route outputs. Automated validity checks and geometry export do not establish real-world street legality. Manual inspection is pending.</p>' \
           + "".join(cards) + same_snap_html(same_snap_observations) + '</html>'


def overview_html(records, same_snap_observations=()):
    cards = []
    for record in records:
        title = html.escape(record["case"] + " · " + record["profile"])
        if record["status"] != "EXPORTED":
            cards.append('<article><h2>' + title + '</h2><p>Export failed: ' + html.escape(record.get("error", "Unknown error")) + '</p></article>')
            continue
        path = record["path"]
        context = record.get("restriction_context")
        note = '' if context is None else f'<p>Relation {context["relation_id"]}: {html.escape(context["restriction"])}. Import/detour confirmation PENDING.</p>'
        cards.append('<article><h2>' + title + '</h2>' + svg_map(record["geojson"])
                     + f'<p>{path["distance"]:.3f} m · {path["time"]} ms · weight {path["weight"]}</p>' + note
                     + f'<a href="index.html#{html.escape(record["file_prefix"])}">Full instructions, path details and GeoJSON</a></article>')
    copyrights = sorted({str(value) for record in records for value in record.get("copyrights", [])})
    return '<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">' \
           '<title>Geometry overview</title><style>body{font:14px system-ui;max-width:1240px;margin:1.5rem auto;padding:0 1rem;background:#edf1f7;color:#172033}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1rem}article{background:white;padding:1rem;border-radius:10px}h2{font-size:16px;margin:.2rem 0 .8rem}svg{width:100%;border:1px solid #d8dfe9}p{margin:.5rem 0}a{color:#124cb1}@media(max-width:760px){.grid{grid-template-columns:1fr}}</style>' \
           '<h1>Actual route geometry overview</h1><p>Blue: route · red: raw points · green: snapped points · amber: restriction via node where present. ' \
           'No basemap. Manual map and street-legality inspection remain PENDING; candidate labels do not establish a restriction or opposing carriageway.</p><div class="grid">' \
           + ''.join(cards) + '</div>' + same_snap_html(same_snap_observations) + '<p>' + html.escape('; '.join(copyrights)) + '</p></html>'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8999")
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "docs/matrix/geometry-spot-checks")
    parser.add_argument("--timeout", type=float, default=60)
    parser.add_argument("--restriction-candidates", type=Path, help="Optional metadata-only RestrictionCandidates.java report")
    parser.add_argument("--restriction-index", type=int, default=0, help="Zero-based candidate index; default 0")
    parser.add_argument("--geometry-candidates", type=Path, help="Optional GeometryCandidates.java report; uses actual opposing pair[0] and footway[0]")
    args = parser.parse_args()
    restriction = None
    if args.restriction_candidates is not None:
        candidate_report = json.loads(args.restriction_candidates.read_text(encoding="utf-8"))
        candidates = candidate_report.get("candidates", [])
        if not 0 <= args.restriction_index < len(candidates):
            parser.error("restriction-index is outside the supplied candidates")
        restriction = candidates[args.restriction_index]
    geometry_report = None
    if args.geometry_candidates is not None:
        geometry_report = json.loads(args.geometry_candidates.read_text(encoding="utf-8"))
        if not geometry_report.get("opposing_carriageways") or not geometry_report.get("footways"):
            parser.error("geometry-candidates must contain an opposing pair and a footway")
    args.output.mkdir(parents=True, exist_ok=True)
    records = []
    run_stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")

    def run(name, profile, points, purpose, provenance, restriction_context=None):
        base_prefix = name + "-" + profile
        prefix = base_prefix
        version = 2
        while any((args.output / (prefix + extension)).exists() for extension in (".request.json", ".response.json", ".geojson")):
            prefix = base_prefix + "-run" + str(version)
            version += 1
        record = {"case": name, "profile": profile, "points": points, "purpose": purpose,
                  "coordinate_provenance": provenance, "file_prefix": prefix, "manual_review": "PENDING"}
        if restriction_context is not None:
            record["restriction_context"] = restriction_context
        try:
            request, status, response = route(args.base_url, points, profile, args.timeout)
            record["http_status"] = status
            (args.output / (prefix + ".request.json")).write_text(json.dumps(request, indent=2) + "\n", encoding="utf-8")
            (args.output / (prefix + ".response.json")).write_text(json.dumps(response, indent=2) + "\n", encoding="utf-8")
            path = validate(status, response)
            snaps = path.get("snapped_waypoints", {}).get("coordinates", [])
            record["snapped_points_identical"] = len(snaps) == 2 and snaps[0] == snaps[1]
            collection = feature_collection(name, profile, points, path, restriction_context)
            (args.output / (prefix + ".geojson")).write_text(json.dumps(collection, indent=2) + "\n", encoding="utf-8")
            record.update(status="EXPORTED", path=path, geojson=collection, copyrights=response.get("info", {}).get("copyrights", []))
        except (ValueError, OSError, urllib.error.URLError) as error:
            record.update(status="FAIL", error=str(error))
        records.append(record)
        print(f"{name} {profile}: {record['status']}", flush=True)
        return record

    for profile in ("car", "foot"):
        run("piccadilly-to-victoria", profile, [PICCADILLY, VICTORIA],
            "Station-area route; inspect street sequence and endpoint snaps.", "Coordinates copied from the supplied Manchester examples.")
        # Offsets span the official bridge marker; the actual returned route must be reviewed to confirm its crossing.
        west = [BLACKFRIARS_CENTRE[0] - .0012, BLACKFRIARS_CENTRE[1] + .0006]
        east = [BLACKFRIARS_CENTRE[0] + .0012, BLACKFRIARS_CENTRE[1] - .0006]
        run("irwell-blackfriars-approaches", profile, [west, east],
            "Bridge-crossing candidate. Inspect whether geometry actually crosses the River Irwell on the expected bridge.",
            {"official_bridge_marker_lon_lat": BLACKFRIARS_CENTRE, "source": BLACKFRIARS_SOURCE,
             "method": "Approach candidates offset from the official marker; these raw points are not assertions of road location."})

    baseline_car = next(record for record in records if record["case"] == "piccadilly-to-victoria" and record["profile"] == "car")
    if geometry_report is not None:
        pair = geometry_report["opposing_carriageways"][0]
        for suffix, points in (("forward", pair["points_lon_lat"]), ("reverse", list(reversed(pair["points_lon_lat"])))):
            run("opposite-carriageways-source-" + suffix, "car", points,
                "Actual separate opposing motorway carriageways from pinned OSM source geometry. Check distinct imported snaps and the permitted-direction detour.",
                {"source_sha256": geometry_report["source_sha256"], "metadata_report": str(args.geometry_candidates.resolve()), "source_pair": pair})
    elif baseline_car["status"] == "EXPORTED":
        coordinates = baseline_car["path"]["points"]["coordinates"]
        segments = [(a, b) for a, b in zip(coordinates, coordinates[1:]) if a[:2] != b[:2]]
        if segments:
            a, b = segments[len(segments) // 2]
            latitude = (a[1] + b[1]) / 2
            dx = (b[0] - a[0]) * math.cos(math.radians(latitude))
            dy = b[1] - a[1]
            norm = math.hypot(dx, dy)
            offset = 15 / 111320
            centre = [(a[0] + b[0]) / 2, latitude]
            shift = [-dy / norm * offset / math.cos(math.radians(latitude)), dx / norm * offset]
            left = [centre[0] + shift[0], centre[1] + shift[1]]
            right = [centre[0] - shift[0], centre[1] - shift[1]]
            for suffix, points in (("forward", [left, right]), ("reverse", [right, left])):
                run("opposite-carriageway-candidate-" + suffix, "car", points,
                    "Inspect opposing-carriageway snapping. Offsets can land on one road; the case remains a candidate until map review.",
                    {"method": "15 m normal offsets from an actual baseline route segment", "actual_segment": [a, b]})

    baseline_foot = next(record for record in records if record["case"] == "piccadilly-to-victoria" and record["profile"] == "foot")
    foot_only_found = False
    if geometry_report is not None:
        footway = geometry_report["footways"][0]
        foot_record = run("foot-only-source-way-" + str(footway["osm_way_id"]), "foot", footway["points_lon_lat"],
                          "Actual OSM footway geometry. Confirm imported foot_access=true and car_access=false; this does not certify real-world access.",
                          {"source_sha256": geometry_report["source_sha256"], "metadata_report": str(args.geometry_candidates.resolve()), "source_way": footway})
        if foot_record["status"] == "EXPORTED":
            foot_only_found = any(access is False and first < last for first, last, access in foot_record["path"]["details"]["car_access"])
    elif baseline_foot["status"] == "EXPORTED":
        path = baseline_foot["path"]
        for first, last, accessible in path["details"]["car_access"]:
            if accessible is False and first < last:
                points = [path["points"]["coordinates"][first], path["points"]["coordinates"][last]]
                if points[0][:2] == points[1][:2]:
                    continue
                run("foot-only-encoded-segment", "foot", points,
                    "Inspect a pedestrian route whose original path detail has car_access=false; this is an imported-profile fact, not a legal-access certificate.",
                    {"method": "Endpoints from an actual foot route interval with car_access=false", "interval": [first, last]})
                foot_only_found = True
                break
    if restriction is not None:
        context = {key: restriction[key] for key in ("relation_id", "restriction", "relation_tags", "via_osm_node",
                   "via_coordinate_lon_lat", "from_osm_way", "from_way_tags", "to_osm_way", "to_way_tags")}
        context["confirmation"] = "PENDING"
        for profile in ("car", "foot"):
            run("restriction-" + str(restriction["relation_id"]), profile,
                [restriction["origin_coordinate_lon_lat"], restriction["destination_coordinate_lon_lat"]],
                "Actual OSM relation candidate. Inspect whether car detours around the prohibited approach turn; compare foot geometry without claiming street legality.",
                {"candidate_report": str(args.restriction_candidates.resolve()), "candidate_index": args.restriction_index,
                 "source_sha256": candidate_report.get("source_sha256")}, context)
    same_snap_observations = []
    for previous in sorted(args.output.glob("opposite-carriageway-candidate-*.response.json")):
        previous_body = json.loads(previous.read_text(encoding="utf-8"))
        for path in previous_body.get("paths", [])[:1]:
            snaps = path.get("snapped_waypoints", {}).get("coordinates", [])
            if len(snaps) == 2 and snaps[0] == snaps[1]:
                same_snap_observations.append({"response_file": previous.name, "distance_m": path["distance"], "snapped_coordinates": snaps})
    (args.output / "index.html").write_text(make_html(records, same_snap_observations), encoding="utf-8")
    (args.output / "overview.html").write_text(overview_html(records, same_snap_observations), encoding="utf-8")
    opposing_records = [record for record in records if record["case"].startswith(("opposite-carriageway-candidate-", "opposite-carriageways-source-"))]
    opposite_candidates = len(opposing_records)
    export_passed = all(record["status"] == "EXPORTED" for record in records) and foot_only_found and opposite_candidates == 2
    summary = {"generated_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(), "base_url": args.base_url,
               "automated_export_status": "PASS" if export_passed else "FAIL",
               "manual_geometry_review": "NOT_RUN", "foot_only_encoded_segment_found": foot_only_found,
               "opposite_carriageway_candidates_exported": opposite_candidates,
               "opposite_carriageway_distinct_snaps": all(record.get("snapped_points_identical") is False for record in opposing_records),
               "preserved_same_snap_observations": same_snap_observations,
               "cases": [{key: value for key, value in record.items() if key not in ("path", "geojson")} for record in records]}
    encoded_summary = json.dumps(summary, indent=2) + "\n"
    previous_summary = args.output / "summary.json"
    if previous_summary.exists():
        (args.output / ("summary-before-" + run_stamp + ".json")).write_bytes(previous_summary.read_bytes())
    (args.output / ("summary-" + run_stamp + ".json")).write_text(encoded_summary, encoding="utf-8")
    previous_summary.write_text(encoded_summary, encoding="utf-8")
    print("Manual geometry/map review: NOT RUN; open " + str(args.output / "index.html"), flush=True)
    return 0 if export_passed else 1


if __name__ == "__main__":
    sys.exit(main())
