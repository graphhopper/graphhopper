#!/usr/bin/env python3
r"""Map the scale workload without permanently labelling every point.

Input JSON: {title, bounds:{west,south,east,north}, points:[{id,lon,lat}]}.
Optional snaps JSON: {profile, points:[{id,snapped:[lon,lat] | null}]}.
Outputs are written beside the input by default, outside the repository:
    python scripts\render-manchester-scale.py --input ..\audit\manchester-1000-points\points.json
"""
from __future__ import annotations

import argparse
import hashlib
import html
import json
import math
from pathlib import Path
import xml.etree.ElementTree as ET


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def finite(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)


def read_points(source, snaps_path=None):
    data = json.loads(source.read_text(encoding="utf-8-sig"))
    bounds, points = data["bounds"], data["points"]
    require(set(bounds) == {"west", "south", "east", "north"}, "Bounds must contain west, south, east and north")
    require(all(finite(value) for value in bounds.values()), "Bounds must be finite numbers")
    west, south, east, north = (bounds[key] for key in ("west", "south", "east", "north"))
    require(-180 <= west < east <= 180 and -85 < south < north < 85, "Bounds are not a valid local geographic rectangle")
    require(points and len({str(point["id"]) for point in points}) == len(points), "Point IDs must be distinct")
    requested = []
    for point in points:
        lon, lat = point["lon"], point["lat"]
        require(finite(lon) and finite(lat) and west <= lon <= east and south <= lat <= north,
                "Every requested point must lie inside the selected rectangle")
        requested.append((lon, lat))
    require(len(set(requested)) == len(points), "Requested point coordinates must be distinct")
    if snaps_path:
        snaps = json.loads(snaps_path.read_text(encoding="utf-8-sig"))
        by_id = {str(point["id"]): point["snapped"] for point in snaps["points"]}
        require(len(by_id) == len(snaps["points"]) == len(points) and set(by_id) == {str(point["id"]) for point in points},
                "Snap IDs must match every requested point exactly once")
        require(isinstance(snaps["profile"], str) and snaps["profile"], "Snaps must identify their routing profile")
        data["snap_profile"] = snaps["profile"]
        for point in points:
            snap = by_id[str(point["id"])]
            if snap is not None:
                require(len(snap) == 2 and all(finite(value) for value in snap)
                        and -180 <= snap[0] <= 180 and -85 < snap[1] < 85, "Invalid snapped coordinate")
                point["snapped"] = snap
    return data


def render(source, output, snaps_path=None):
    data = read_points(source, snaps_path)
    bounds, points = data["bounds"], data["points"]
    title = str(data.get("title", "Manchester: 1,000-point matrix workload"))
    if data.get("snap_profile"):
        title += " (" + data["snap_profile"] + " snaps)"
    west, south, east, north = (bounds[key] for key in ("west", "south", "east", "north"))
    all_coordinates = [(point["lon"], point["lat"]) for point in points]
    all_coordinates += [tuple(point["snapped"]) for point in points if "snapped" in point]
    draw_west, draw_south = min(west, *(p[0] for p in all_coordinates)), min(south, *(p[1] for p in all_coordinates))
    draw_east, draw_north = max(east, *(p[0] for p in all_coordinates)), max(north, *(p[1] for p in all_coordinates))
    mercator = lambda lat: math.log(math.tan(math.pi / 4 + math.radians(lat) / 2))
    width, height, margin = 1000, 760, 60
    x_span, y_span = math.radians(draw_east - draw_west), mercator(draw_north) - mercator(draw_south)
    scale = min((width - 2 * margin) / x_span, (height - 2 * margin) / y_span)
    x_pad, y_pad = (width - scale * x_span) / 2, (height - scale * y_span) / 2
    project = lambda lon, lat: (x_pad + math.radians(lon - draw_west) * scale,
                               y_pad + (mercator(draw_north) - mercator(lat)) * scale)
    left, top = project(west, north)
    right, bottom = project(east, south)
    km_width = math.radians(east - west) * 6371 * math.cos(math.radians((south + north) / 2))
    km_height = math.radians(north - south) * 6371
    count = len(points)
    svg = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" role="img" aria-labelledby="map-title">',
           f'<title id="map-title">{html.escape(title)}</title>', '<rect width="1000" height="760" fill="#f6f8fb"/>',
           f'<rect x="{left:.3f}" y="{top:.3f}" width="{right-left:.3f}" height="{bottom-top:.3f}" fill="#0d9488" fill-opacity=".07" stroke="#0f766e"/>',
           '<g font-family="system-ui,sans-serif" font-size="16" fill="#334155"><text x="24" y="28">N ↑</text>',
           f'<text x="500" y="28" text-anchor="middle">{km_width:.2f} km east–west × {km_height:.2f} km north–south</text>',
           f'<text x="500" y="742" text-anchor="middle">{count:,} points · {count**2:,} ordered pairs · {count*(count-1):,} off-diagonal pairs</text></g>']
    features = [{"type": "Feature", "properties": {"kind": "selected_area", "title": title}, "geometry": {"type": "Polygon",
                "coordinates": [[[west,south],[east,south],[east,north],[west,north],[west,south]]]}}]
    for point in points:
        lon, lat = point["lon"], point["lat"]
        x, y = project(lon, lat)
        if "snapped" in point:
            sx, sy = project(*point["snapped"])
            svg.append(f'<line x1="{x:.3f}" y1="{y:.3f}" x2="{sx:.3f}" y2="{sy:.3f}" stroke="#d97706" stroke-opacity=".5"/>')
            svg.append(f'<circle cx="{sx:.3f}" cy="{sy:.3f}" r="3.8" fill="none" stroke="#d97706"/>')
            features.append({"type": "Feature", "properties": {"kind": "profile_snap", "id": point["id"], "profile": data["snap_profile"]},
                             "geometry": {"type": "Point", "coordinates": point["snapped"]}})
        svg.append(f'<circle cx="{x:.3f}" cy="{y:.3f}" r="2.7" fill="#1d4ed8"><title>{html.escape(str(point["id"]))}: {lon:.8f}, {lat:.8f}</title></circle>')
        features.append({"type": "Feature", "properties": {"kind": "requested_point", "id": point["id"]},
                         "geometry": {"type": "Point", "coordinates": [lon,lat]}})
    svg = "\n".join(svg + ["</svg>"]) + "\n"
    ET.fromstring(svg)
    embedded = json.dumps(data, ensure_ascii=True, separators=(",", ":")).replace("<", "\\u003c")
    page = """<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>__TITLE__</title><link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<style>body{font:16px/1.5 system-ui,sans-serif;margin:0;color:#0f172a;background:#f6f8fb}main{max-width:1180px;margin:auto;padding:24px}h1{font-size:27px;margin:0 0 12px}#map{height:690px;border:1px solid #cbd5e1;border-radius:10px;background:#e2e8f0}a{color:#1d4ed8}svg{width:100%;height:auto}code{overflow-wrap:anywhere}.blue{color:#1d4ed8}.orange{color:#b45309}details{margin-top:16px}</style>
<main><h1>__TITLE__</h1><p><strong>__N__ points</strong> are each used as origin and destination: <strong>__PAIRS__ ordered pairs</strong>, including __N__ diagonal pairs; __OFFDIAGONAL__ directed pairs exclude the diagonals. Hover or tap a point to inspect its ID and exact coordinates.</p>
<p><span class="blue">● Requested coordinate</span> · <span class="orange">○ Profile snap, where supplied</span> · <a href="area-points.geojson">GeoJSON</a> · <a href="area-map.svg">Offline point layout</a></p>
<div id="map" aria-label="Interactive Manchester area with benchmark points"></div><p>The street basemap needs internet access. The offline layout below contains every requested point, with IDs available on hover.</p>
<details><summary>Offline point layout</summary>__SVG__</details><p>Point input: <code>__INPUT__</code>, SHA-256 <code>__SHA__</code>. Dimensions use a local spherical approximation. Map: © <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>, rendered with <a href="https://leafletjs.com/">Leaflet 1.9.4</a>.</p></main>
<script id="area-data" type="application/json">__DATA__</script><script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script><script>
const data=JSON.parse(document.getElementById('area-data').textContent);
function label(p,snap=false){const e=document.createElement('span');e.textContent=String(p.id)+(snap?' ('+data.snap_profile+' snap)':'')+': '+(snap?p.snapped:[p.lon,p.lat]).map(v=>v.toFixed(8)).join(', ');return e}
if(window.L){const b=data.bounds,map=L.map('map',{preferCanvas:true});L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>'}).addTo(map);L.rectangle([[b.south,b.west],[b.north,b.east]],{color:'#0f766e',fillOpacity:.05,weight:2}).addTo(map);const bounds=L.latLngBounds([[b.south,b.west],[b.north,b.east]]);for(const p of data.points){const ll=[p.lat,p.lon];bounds.extend(ll);L.circleMarker(ll,{radius:3,color:'#fff',weight:.6,fillColor:'#1d4ed8',fillOpacity:.9}).addTo(map).bindTooltip(label(p));if(p.snapped){const s=[p.snapped[1],p.snapped[0]];bounds.extend(s);L.polyline([ll,s],{color:'#d97706',weight:1,opacity:.4}).addTo(map);L.circleMarker(s,{radius:4.5,color:'#d97706',weight:1,fillOpacity:0}).addTo(map).bindTooltip(label(p,true))}}map.fitBounds(bounds.pad(.06));L.control.scale({imperial:false}).addTo(map)}else{document.getElementById('map').innerHTML=document.querySelector('details svg').outerHTML;document.getElementById('map').style.height='auto'}
</script></html>"""
    substitutions = {"__TITLE__": html.escape(title), "__N__": f"{count:,}", "__PAIRS__": f"{count**2:,}",
                     "__OFFDIAGONAL__": f"{count*(count-1):,}", "__SVG__": svg, "__INPUT__": html.escape(source.name),
                     "__SHA__": digest(source), "__DATA__": embedded}
    for key, value in substitutions.items():
        page = page.replace(key, value)
    output.mkdir(parents=True, exist_ok=True)
    contents = {"area-map.svg": svg, "area-map.html": page + "\n", "area-points.geojson": json.dumps({
        "type": "FeatureCollection", "bbox": [draw_west,draw_south,draw_east,draw_north], "features": features}, separators=(",", ":")) + "\n"}
    for name, content in contents.items():
        (output / name).write_text(content, encoding="utf-8")
    manifest = {"input": source.name, "input_sha256": digest(source), "generator_sha256": digest(Path(__file__)),
                "snaps_sha256": digest(snaps_path) if snaps_path else None, "snap_profile": data.get("snap_profile"),
                "requested_points": count, "ordered_pairs": count**2, "off_diagonal_pairs": count*(count-1),
                "diagonal_pairs": count, "valid_profile_snaps": sum("snapped" in point for point in points),
                "distinct_profile_snaps": len({tuple(point["snapped"]) for point in points if "snapped" in point}),
                "approximate_area_km": {"east_west": km_width, "north_south": km_height},
                "artifacts_sha256": {name: digest(output / name) for name in contents}}
    (output / "area-map-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--snaps", type=Path)
    args = parser.parse_args()
    render(args.input, args.output or args.input.parent, args.snaps)
