#!/usr/bin/env python3
r"""Render the exact benchmark area/points as HTML, offline SVG and GeoJSON.

Input: {title, bounds:{west,south,east,north}, points:[{id,lon,lat}]}
Optional results.json: {snaps:{profile,points:[{id,snapped:[lon,lat]}]}}.
Coordinates are never resnapped here. Run from the repo root in cmd.exe:
    python scripts\render-manchester-area.py
"""
from __future__ import annotations

import argparse
import hashlib
import html
import json
import math
from pathlib import Path
import xml.etree.ElementTree as ET


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def finite(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)


def render(source: Path, output: Path, results: Path | None, snaps_path: Path | None, profile: str | None):
    data = json.loads(source.read_text(encoding="utf-8"))
    title = str(data.get("title", "Manchester area: every ordered point pair"))
    bounds, points = data["bounds"], data["points"]
    evidence = {"points_sha256": sha256(source)}
    for point in points:
        point["longitude"], point["latitude"] = point["lon"], point["lat"]
    snaps = None
    if results is not None:
        report = json.loads(results.read_text(encoding="utf-8"))
        evidence["results_sha256"] = sha256(results)
        snaps = report.get("snaps")
        if profile is not None:
            assert snaps is None, "Use a generic snaps object or profile-specific results metadata"
            metadata = report["profiles"][profile]["matrix_auto_metadata"]
            source_snaps = metadata["from_snapped_points"]
            assert source_snaps == metadata["to_snapped_points"]
            assert len(source_snaps) == len(points)
            snaps = {"profile": profile, "points": [
                {"id": point["id"], "snapped": snap["coordinates"]}
                for point, snap in zip(points, source_snaps)]}
    if snaps_path is not None:
        assert snaps is None, "Supply snaps in results.json or --snaps, not both"
        snaps = json.loads(snaps_path.read_text(encoding="utf-8"))
        evidence["snaps_sha256"] = sha256(snaps_path)
    if snaps is not None:
        assert isinstance(snaps["profile"], str) and snaps["profile"]
        by_id = {point["id"]: point for point in snaps["points"]}
        assert len(by_id) == len(snaps["points"]) == len(points)
        assert set(by_id) == {point["id"] for point in points}
        for point in points:
            if by_id[point["id"]]["snapped"] is not None:
                point["snapped"] = by_id[point["id"]]["snapped"]
        data["snap_profile"] = snaps["profile"]
        title += f" ({snaps['profile']} snaps)"
    assert set(bounds) == {"west", "south", "east", "north"}
    assert all(finite(value) for value in bounds.values())
    west, south, east, north = (bounds[key] for key in ("west", "south", "east", "north"))
    assert -180 <= west < east <= 180 and -85 < south < north < 85
    assert points and len({point["id"] for point in points}) == len(points)
    coordinates = []
    for point in points:
        lon, lat = point["longitude"], point["latitude"]
        assert finite(lon) and finite(lat) and west <= lon <= east and south <= lat <= north
        coordinates.append((lon, lat))
        if "snapped" in point:
            assert len(point["snapped"]) == 2
            assert all(finite(value) for value in point["snapped"])
            assert -180 <= point["snapped"][0] <= 180 and -85 < point["snapped"][1] < 85
    assert len(set(coordinates)) == len(points), "Map source points must be distinct"
    output.mkdir(parents=True, exist_ok=True)
    mercator = lambda lat: math.log(math.tan(math.pi / 4 + math.radians(lat) / 2))
    all_coordinates = coordinates + [tuple(point["snapped"]) for point in points if "snapped" in point]
    draw_west = min(west, *(lon for lon, lat in all_coordinates))
    draw_east = max(east, *(lon for lon, lat in all_coordinates))
    draw_south = min(south, *(lat for lon, lat in all_coordinates))
    draw_north = max(north, *(lat for lon, lat in all_coordinates))
    width, height, margin = 960, 700, 72
    x_span, y_span = math.radians(draw_east - draw_west), mercator(draw_north) - mercator(draw_south)
    scale = min((width - 2 * margin) / x_span, (height - 2 * margin) / y_span)
    x_pad, y_pad = (width - scale * x_span) / 2, (height - scale * y_span) / 2
    project = lambda lon, lat: (x_pad + math.radians(lon - draw_west) * scale,
                                y_pad + (mercator(draw_north) - mercator(lat)) * scale)
    left, top = project(west, north)
    right, bottom = project(east, south)
    mid_lat = (south + north) / 2
    km_width = math.radians(east - west) * 6371 * math.cos(math.radians(mid_lat))
    km_height = math.radians(north - south) * 6371
    svg = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" role="img" aria-labelledby="map-title map-description">',
           f'<title id="map-title">{html.escape(title)}</title>',
           '<desc id="map-description">Geographic point layout, north up. Filled blue circles are requested coordinates; orange rings mark profile snaps where supplied. Street basemap is available in the HTML map.</desc>',
           '<rect width="960" height="700" fill="#f6f8fb"/>',
           f'<rect x="{left:.3f}" y="{top:.3f}" width="{right-left:.3f}" height="{bottom-top:.3f}" fill="#0d9488" fill-opacity="0.09" stroke="#0f766e" stroke-width="2"/>',
           '<g font-family="system-ui,sans-serif" font-size="15" fill="#334155">',
           '<text x="30" y="30">N ↑</text>',
           f'<text x="480" y="28" text-anchor="middle">{km_width:.2f} km east–west × {km_height:.2f} km north–south</text>',
           f'<text x="480" y="686" text-anchor="middle">{len(points)} requested points · {len(points)**2} ordered pairs including {len(points)} diagonal pairs</text></g>']
    features = [{"type": "Feature", "properties": {"kind": "selected_area", "title": title},
                 "geometry": {"type": "Polygon", "coordinates": [[[west,south],[east,south],[east,north],[west,north],[west,south]]]}}]
    for point in points:
        lon, lat = point["longitude"], point["latitude"]
        x, y = project(lon, lat)
        label = html.escape(str(point["id"]))
        if "snapped" in point:
            sx, sy = project(*point["snapped"])
            svg += [f'<line x1="{x:.3f}" y1="{y:.3f}" x2="{sx:.3f}" y2="{sy:.3f}" stroke="#d97706" stroke-width="2"/>',
                    f'<circle cx="{sx:.3f}" cy="{sy:.3f}" r="12" fill="none" stroke="#d97706" stroke-width="2"/>']
            features.append({"type": "Feature", "properties": {"kind": "profile_snap", "id": point["id"], "profile": data.get("snap_profile")},
                             "geometry": {"type": "Point", "coordinates": point["snapped"]}})
        svg += [f'<circle cx="{x:.3f}" cy="{y:.3f}" r="9" fill="#1d4ed8" stroke="white" stroke-width="2"/>',
                f'<text x="{x+14:.3f}" y="{y-12:.3f}" font-family="system-ui,sans-serif" font-size="15" font-weight="700" fill="#0f172a" stroke="#f6f8fb" stroke-width="4" paint-order="stroke">{label}</text>']
        features.append({"type": "Feature", "properties": {"kind": "requested_point", "id": point["id"]},
                         "geometry": {"type": "Point", "coordinates": [lon, lat]}})
    svg.append('</svg>')
    svg = "\n".join(svg) + "\n"
    ET.fromstring(svg)
    geojson = {"type": "FeatureCollection", "bbox": [draw_west, draw_south, draw_east, draw_north], "features": features}
    (output / "area-map.svg").write_text(svg, encoding="utf-8")
    (output / "area-points.geojson").write_text(json.dumps(geojson, indent=2) + "\n", encoding="utf-8")
    embedded = json.dumps(data, ensure_ascii=True).replace("<", "\\u003c")
    page = """<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>__TITLE__</title>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<style>body{font:16px/1.5 system-ui,sans-serif;margin:0;color:#0f172a;background:#f6f8fb}main{max-width:1120px;margin:auto;padding:28px}h1{font-size:26px;margin:0 0 10px}p{max-width:95ch}#map{height:620px;border:1px solid #cbd5e1;border-radius:10px;background:#e2e8f0}a{color:#1d4ed8}table{border-collapse:collapse;width:100%;font-size:14px}td,th{text-align:left;border-bottom:1px solid #cbd5e1;padding:7px}svg{width:100%;height:auto}.legend{display:flex;gap:20px;flex-wrap:wrap}.requested{color:#1d4ed8}.snapped{color:#b45309}.marker-label{font-weight:750;color:#0f172a;text-shadow:0 0 3px white,0 0 4px white;background:transparent;border:0;box-shadow:none}.leaflet-tooltip:before{display:none}</style>
<main><h1>__TITLE__</h1><p>Every requested point is used as both origin and destination. There are <strong>__COUNT__ ordered pairs</strong>, including __N__ same-point diagonal pairs. The shaded rectangle is the selected geographic area; no route lines are shown.</p>
<p class="legend"><span class="requested">● Requested coordinate</span><span class="snapped">○ Profile-snapped coordinate, where supplied</span><a href="area-points.geojson">GeoJSON</a><a href="area-map.svg">Offline point layout</a></p>
<div id="map" aria-label="Interactive OpenStreetMap area with labelled requested points"></div>
<p id="map-note">The street basemap requires internet access. Exact coordinates and the offline diagram remain available below.</p>
<details><summary>Offline geographic point layout</summary>__SVG__</details>
<details><summary>Exact point coordinates (longitude, latitude)</summary><table><thead><tr><th>Point</th><th>Requested</th><th>Profile snap</th></tr></thead><tbody id="coordinates"></tbody></table></details>
<p>Point/area data: <code>__INPUT__</code>, SHA-256 <code>__SHA__</code>. Map: © <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>, rendered with <a href="https://leafletjs.com/">Leaflet 1.9.4</a>. Geographic dimensions use a local spherical approximation; point coordinates define the workload.</p></main>
<script id="area-data" type="application/json">__DATA__</script>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
const data=JSON.parse(document.getElementById('area-data').textContent);
const rows=document.getElementById('coordinates');
for(const p of data.points){const row=document.createElement('tr');for(const value of [p.id,`${p.longitude.toFixed(8)}, ${p.latitude.toFixed(8)}`,p.snapped?p.snapped.map(v=>v.toFixed(8)).join(', '):'not supplied']){const cell=document.createElement('td');cell.textContent=value;row.appendChild(cell)}rows.appendChild(row)}
if(window.L){const b=data.bounds;const map=L.map('map');L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>'}).addTo(map);L.rectangle([[b.south,b.west],[b.north,b.east]],{color:'#0f766e',fillOpacity:.08,weight:2}).addTo(map);const bounds=L.latLngBounds([[b.south,b.west],[b.north,b.east]]);for(const p of data.points){const ll=[p.latitude,p.longitude];bounds.extend(ll);L.circleMarker(ll,{radius:7,color:'#fff',weight:2,fillColor:'#1d4ed8',fillOpacity:1}).addTo(map).bindTooltip(String(p.id),{permanent:true,direction:'top',className:'marker-label'});if(p.snapped){const s=[p.snapped[1],p.snapped[0]];bounds.extend(s);L.polyline([ll,s],{color:'#d97706',weight:2}).addTo(map);L.circleMarker(s,{radius:10,color:'#d97706',weight:2,fillOpacity:0}).addTo(map)}}map.fitBounds(bounds.pad(.13));L.control.scale({imperial:false}).addTo(map)}else{document.getElementById('map').innerHTML=document.querySelector('details svg').outerHTML;document.getElementById('map').style.height='auto'}
</script></html>"""
    replacements = {"__TITLE__": html.escape(title), "__COUNT__": str(len(points)**2), "__N__": str(len(points)),
                    "__SVG__": svg, "__INPUT__": html.escape(source.name), "__SHA__": sha256(source), "__DATA__": embedded}
    for key, value in replacements.items():
        page = page.replace(key, value)
    (output / "area-map.html").write_text(page + "\n", encoding="utf-8")
    manifest = {"input": source.name, "input_sha256": sha256(source), "generator_sha256": sha256(Path(__file__)),
                "evidence_sha256": evidence, "snap_profile": data.get("snap_profile"),
                "requested_points": len(points), "ordered_pairs": len(points)**2, "diagonal_pairs": len(points),
                "valid_profile_snaps": sum("snapped" in point for point in points),
                "distinct_profile_snaps": len({tuple(point["snapped"]) for point in points if "snapped" in point}),
                "approximate_area_km": {"east_west": km_width, "north_south": km_height},
                "artifacts_sha256": {name: sha256(output / name) for name in ("area-map.svg", "area-map.html", "area-points.geojson")}}
    (output / "area-map-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    root = Path(__file__).resolve().parents[1]
    parser.add_argument("--input", type=Path, default=root.parent / "audit/manchester-area-comparison/points.json")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--results", type=Path, help="Optional results JSON containing a snaps object")
    parser.add_argument("--snaps", type=Path, help="Optional standalone {profile,points:[{id,snapped}]} JSON")
    parser.add_argument("--profile", choices=("foot", "car"), help="Read profile-specific matrix_auto_metadata snaps from results JSON")
    args = parser.parse_args()
    results = args.results or (args.input.parent / "results.json")
    if args.results and not results.is_file():
        parser.error("--results must name an existing JSON file")
    if args.profile and not results.exists():
        parser.error("--profile requires a results JSON file")
    render(args.input, args.output or args.input.parent, results if results.exists() else None, args.snaps, args.profile)
