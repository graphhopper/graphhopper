#!/usr/bin/env python3
"""CLI tool to post a GPX file to the GraphHopper map-matching API and visualize the result."""

import argparse
import http.server
import json
import os
import sys
import threading
import urllib.request
import webbrowser
import xml.etree.ElementTree as ET

NS = {"gpx": "http://www.topografix.com/GPX/1/1"}
MAX_POINTS = 3000  # chunk size threshold
DEBUG_TEMPLATE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "debug-visualizer-template.html")


def main():
    parser = argparse.ArgumentParser(description="Map-match a GPX file via GraphHopper and visualize the result")
    parser.add_argument("gpx_file", help="Path to GPX file")
    parser.add_argument("--profile", default="car", help="Routing profile (default: car)")
    parser.add_argument("--url", default="https://graphhopper.com/api/1", help="GraphHopper base URL")
    parser.add_argument("--key", default="7830772c-f1a9-4b29-8c27-b9a82f86ee7f", help="GraphHopper API key")
    parser.add_argument("--port", type=int, default=8899, help="Local server port for visualization")
    parser.add_argument("--chunk-size", type=int, default=MAX_POINTS, help=f"Max trackpoints per chunk (default: {MAX_POINTS})")
    parser.add_argument("--chunk", type=int, default=None, help="Only match and show this chunk (1-based)")
    parser.add_argument("--start", type=int, default=None, help="First trackpoint index (0-based, before chunking)")
    parser.add_argument("--end", type=int, default=None, help="Last trackpoint index (0-based, inclusive, before chunking)")
    parser.add_argument("--gps-accuracy", type=int, default=None, help="GPS accuracy in meters (GraphHopper gps_accuracy parameter)")
    parser.add_argument("--debug", action="store_true", help="Request debug output and open the debug visualizer")
    parser.add_argument("--debug-template", default=DEBUG_TEMPLATE, help="Path to debug-visualizer-template.html")
    args = parser.parse_args()

    with open(args.gpx_file, "rb") as f:
        gpx_text = f.read().decode()

    root = ET.fromstring(gpx_text)
    trkpts = root.findall(".//gpx:trkpt", NS)

    print(f"{len(trkpts)} trackpoints in GPX file")

    if args.start is not None or args.end is not None:
        s = args.start if args.start is not None else 0
        e = (args.end + 1) if args.end is not None else len(trkpts)
        trkpts = trkpts[s:e]
        print(f"Using points {s}-{e-1} ({len(trkpts)} points)")

    if len(trkpts) <= args.chunk_size:
        chunks = [trkpts]
    else:
        chunks = []
        for i in range(0, len(trkpts), args.chunk_size):
            chunks.append(trkpts[i:i + args.chunk_size])
        print(f"Splitting into {len(chunks)} chunks of up to {args.chunk_size} points")

    if args.chunk is not None:
        if args.chunk < 1 or args.chunk > len(chunks):
            print(f"Error: --chunk must be between 1 and {len(chunks)}", file=sys.stderr)
            sys.exit(1)
        idx = args.chunk - 1
        chunks = [chunks[idx]]
        # narrow original coords to just this chunk's range
        start = idx * args.chunk_size
        end = min(start + args.chunk_size, len(trkpts))
        original_coords = [[float(tp.get("lon")), float(tp.get("lat"))] for tp in trkpts[start:end]]
        print(f"Selected chunk {args.chunk} (points {start}-{end-1})")
    else:
        original_coords = [[float(tp.get("lon")), float(tp.get("lat"))] for tp in trkpts]

    if args.debug:
        # Debug mode: single request, no chunking, use debug visualizer
        all_pts = [tp for chunk in chunks for tp in chunk]
        gpx_data = build_gpx(all_pts)
        print(f"Matching {len(all_pts)} points in debug mode...", end=" ", flush=True)
        debug_json = post_match(gpx_data, args.url, args.profile, args.key, args.gps_accuracy, debug=True)
        print("done")
        html = build_debug_html(debug_json, args.debug_template)
        serve_and_open(html, args.port)
    else:
        matched_chunks = []
        for i, chunk in enumerate(chunks):
            gpx_chunk = build_gpx(chunk)
            label = f"chunk {i+1}/{len(chunks)}" if len(chunks) > 1 else "GPX"
            if args.chunk is not None:
                label = f"chunk {args.chunk}"
            print(f"Matching {label} ({len(chunk)} points)...", end=" ", flush=True)
            result = post_match(gpx_chunk, args.url, args.profile, args.key, args.gps_accuracy)
            coords = decode_points(result["paths"][0]["points"])
            matched_chunks.append(coords)
            print(f"-> {len(coords)} matched points")

        html = build_html(matched_chunks, original_coords)
        serve_and_open(html, args.port)


def build_gpx(trkpts):
    """Build a minimal GPX XML string from a list of trkpt Elements."""
    lines = ['<?xml version="1.0" encoding="UTF-8"?>',
             '<gpx xmlns="http://www.topografix.com/GPX/1/1" version="1.1">',
             '<trk><trkseg>']
    for tp in trkpts:
        lat, lon = tp.get("lat"), tp.get("lon")
        time_el = tp.find("gpx:time", NS)
        if time_el is not None and time_el.text:
            lines.append(f'<trkpt lat="{lat}" lon="{lon}"><time>{time_el.text}</time></trkpt>')
        else:
            lines.append(f'<trkpt lat="{lat}" lon="{lon}"/>')
    lines.append('</trkseg></trk></gpx>')
    return "\n".join(lines).encode()


def post_match(gpx_data, base_url, profile, key, gps_accuracy=None, debug=False):
    out_type = "debug" if debug else "json"
    api_url = f"{base_url}/match?profile={profile}&type={out_type}&key={key}"
    if gps_accuracy is not None:
        api_url += f"&gps_accuracy={gps_accuracy}"
    req = urllib.request.Request(api_url, data=gpx_data, headers={"Content-Type": "application/xml"})
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        print(f"\nAPI error {e.code}: {body}", file=sys.stderr)
        sys.exit(1)


def decode_points(points):
    """Decode points from the API response — either encoded polyline string or GeoJSON-like object."""
    if isinstance(points, str):
        # Decode Google encoded polyline
        coords = []
        index, lat, lng = 0, 0, 0
        while index < len(points):
            for is_lng in (False, True):
                shift, result = 0, 0
                while True:
                    b = ord(points[index]) - 63
                    index += 1
                    result |= (b & 0x1F) << shift
                    shift += 5
                    if b < 0x20:
                        break
                delta = ~(result >> 1) if (result & 1) else (result >> 1)
                if is_lng:
                    lng += delta
                else:
                    lat += delta
            coords.append([lng / 1e5, lat / 1e5])
        return coords
    else:
        return points["coordinates"]


def snake_to_camel(obj):
    """Recursively convert snake_case keys to camelCase."""
    if isinstance(obj, dict):
        return {_to_camel(k): snake_to_camel(v) for k, v in obj.items()}
    if isinstance(obj, list):
        return [snake_to_camel(item) for item in obj]
    return obj


def _to_camel(s):
    parts = s.split("_")
    return parts[0] + "".join(p.capitalize() for p in parts[1:])


def build_debug_html(debug_json, template_path):
    """Read the debug visualizer template and inject the debug JSON data."""
    template_path = os.path.normpath(template_path)
    if not os.path.exists(template_path):
        print(f"Error: debug template not found at {template_path}", file=sys.stderr)
        sys.exit(1)
    with open(template_path) as f:
        template = f.read()
    camel_json = snake_to_camel(debug_json)
    return template.replace("/*DEBUG_DATA_PLACEHOLDER*/", json.dumps(camel_json))


COLORS = [
    '#2196F3', '#4CAF50', '#FF9800', '#9C27B0', '#00BCD4',
    '#E91E63', '#3F51B5', '#009688', '#FF5722', '#607D8B',
]


def build_html(matched_chunks, original_coords):
    chunk_data = json.dumps([
        {"type": "Feature", "geometry": {"type": "LineString", "coordinates": coords},
         "properties": {"chunk": i}}
        for i, coords in enumerate(matched_chunks)
    ])

    original_geojson = json.dumps({
        "type": "Feature",
        "geometry": {"type": "LineString", "coordinates": original_coords},
        "properties": {"name": "original"}
    })

    colors_js = json.dumps(COLORS)
    num_chunks = len(matched_chunks)

    return f"""<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8"/>
<title>Map Matching Result</title>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
  body {{ margin: 0; }}
  #map {{ width: 100vw; height: 100vh; }}
  .legend {{ background: white; padding: 10px; border-radius: 5px; font: 14px/1.6 sans-serif; box-shadow: 0 1px 5px rgba(0,0,0,0.3); }}
</style>
</head>
<body>
<div id="map"></div>
<script>
var chunks = {chunk_data};
var originalData = {original_geojson};
var colors = {colors_js};
var numChunks = {num_chunks};

var map = L.map('map');
L.tileLayer('https://{{s}}.basemaps.cartocdn.com/light_all/{{z}}/{{x}}/{{y}}@2x.png', {{
    attribution: '&copy; OpenStreetMap contributors &copy; CARTO',
    maxZoom: 20
}}).addTo(map);

var originalLayer = L.geoJSON(originalData, {{
    style: {{ color: '#cc3333', weight: 4, opacity: 0.6, dashArray: '6,6' }}
}}).addTo(map);

var matchedGroup = L.featureGroup().addTo(map);
var markerGroup = L.featureGroup().addTo(map);
var overlays = {{ 'Original GPS': originalLayer }};

var startIcon = L.divIcon({{
    className: '',
    html: '<div style="background:#22bb33;border:3px solid white;border-radius:50%;width:16px;height:16px;box-shadow:0 2px 6px rgba(0,0,0,0.5);"></div>',
    iconSize: [22, 22],
    iconAnchor: [11, 11]
}});
var endIcon = L.divIcon({{
    className: '',
    html: '<div style="background:#dd2222;border:3px solid white;border-radius:50%;width:16px;height:16px;box-shadow:0 2px 6px rgba(0,0,0,0.5);"></div>',
    iconSize: [22, 22],
    iconAnchor: [11, 11]
}});

chunks.forEach(function(feat, i) {{
    var color = colors[i % colors.length];
    var layer = L.geoJSON(feat, {{
        style: {{ color: color, weight: 6, opacity: 0.9 }}
    }}).addTo(matchedGroup);
    if (numChunks > 1) {{
        overlays['Chunk ' + (i+1)] = layer;
    }}
    // start/end markers for each chunk
    var coords = feat.geometry.coordinates;
    if (coords.length > 0) {{
        var s = coords[0], e = coords[coords.length - 1];
        if (i === 0) {{
            L.marker([s[1], s[0]], {{ icon: startIcon }}).bindTooltip('Start').addTo(markerGroup);
        }}
        if (i === chunks.length - 1) {{
            L.marker([e[1], e[0]], {{ icon: endIcon }}).bindTooltip('End').addTo(markerGroup);
        }}
    }}
}});

if (numChunks === 1) {{
    overlays['Matched route'] = matchedGroup;
}}
overlays['Start/End markers'] = markerGroup;

map.fitBounds(matchedGroup.getBounds().pad(0.1));
L.control.layers(null, overlays).addTo(map);

var legend = L.control({{ position: 'bottomright' }});
legend.onAdd = function() {{
    var div = L.DomUtil.create('div', 'legend');
    var html = '<b>Legend</b><br><span style="color:#cc3333">- - -</span> Original GPS<br>';
    if (numChunks === 1) {{
        html += '<span style="color:' + colors[0] + '">&mdash;&mdash;</span> Matched route<br>';
    }} else {{
        for (var i = 0; i < numChunks; i++) {{
            var c = colors[i % colors.length];
            html += '<span style="color:' + c + '">&mdash;&mdash;</span> Chunk ' + (i+1) + '<br>';
        }}
    }}
    html += '<span style="display:inline-block;width:10px;height:10px;background:#22bb33;border-radius:50;border:2px solid white;box-shadow:0 1px 3px rgba(0,0,0,0.4);"></span> Start<br>';
    html += '<span style="display:inline-block;width:10px;height:10px;background:#dd2222;border-radius:50%;border:2px solid white;box-shadow:0 1px 3px rgba(0,0,0,0.4);"></span> End';
    div.innerHTML = html;
    return div;
}};
legend.addTo(map);
</script>
</body>
</html>"""


def serve_and_open(html, port):
    html_bytes = html.encode()

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            self.send_response(200)
            self.send_header("Content-Type", "text/html")
            self.end_headers()
            self.wfile.write(html_bytes)

        def log_message(self, format, *args):
            pass

    server = http.server.HTTPServer(("127.0.0.1", port), Handler)
    url = f"http://127.0.0.1:{port}"
    print(f"Opening {url} — press Ctrl+C to stop")
    threading.Timer(0.5, lambda: webbrowser.open(url)).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nDone.")


if __name__ == "__main__":
    main()
