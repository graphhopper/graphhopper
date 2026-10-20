"""Summarize recorded upstream OSM restriction import warnings without guessing their cause."""
import argparse
from collections import Counter, defaultdict
import datetime
import hashlib
import json
from pathlib import Path
import re


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("log", type=Path)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "docs/matrix")
    args = parser.parse_args()
    log = args.log.resolve()
    raw = log.read_bytes()
    lines = raw.decode("utf-8", errors="replace").splitlines()
    restrictions = []
    categories = defaultdict(list)
    other_warnings = []
    pattern = re.compile(r"Restriction relation (\d+) (.*?)\. tags: (.*), members: (.*)\. Relation ignored\.")
    for number, line in enumerate(lines, 1):
        if " WARN " not in line:
            continue
        match = pattern.search(line)
        if match:
            relation, reason, tags, members = match.groups()
            item = {"relation_id": int(relation), "reason": reason, "tags": tags,
                    "members": members, "log_line": number, "raw": line}
            restrictions.append(item)
            categories[reason].append(int(relation))
        else:
            other_warnings.append({"log_line": number, "raw": line})
    args.output.mkdir(parents=True, exist_ok=True)
    data_manifest = json.loads((args.output / "regional-data.json").read_text(encoding="utf-8"))
    summary = {
        "generated_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "input_log": str(log),
        "input_log_snapshot_sha256": hashlib.sha256(raw).hexdigest(),
        "input_log_snapshot_bytes": len(raw),
        "input_log_snapshot_lines": len(lines),
        "source_pbf_sha256": data_manifest["sha256"],
        "source_pbf_url": data_manifest["resolved_url"],
        "restriction_warning_count": len(restrictions),
        "distinct_ignored_relation_count": len({item["relation_id"] for item in restrictions}),
        "other_upstream_warning_count": len(other_warnings),
        "maven_warning_count": sum("[WARNING]" in line for line in lines),
        "categories": [{"reason": reason, "count": len(ids), "relation_ids": ids}
                       for reason, ids in sorted(categories.items(), key=lambda item: (-len(item[1]), item[0]))],
        "restriction_warnings": restrictions,
        "other_upstream_warnings": other_warnings,
    }
    (args.output / "osm-import-warnings.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    table = ["| Recorded importer category | Count | Representative OSM relation IDs |",
             "|---|---:|---|"]
    for item in summary["categories"]:
        representative = ", ".join(f"[{relation}](https://www.openstreetmap.org/relation/{relation})" for relation in item["relation_ids"][:3])
        table.append(f"| {item['reason']} | {item['count']} | {representative} |")
    document = f"""# Regional OSM import limitations

The recorded Greater Manchester import emitted **{len(restrictions)} restriction warnings covering {summary['distinct_ignored_relation_count']} distinct relations**. Each ends with `Relation ignored.` These warnings come from GraphHopper's upstream OSM import, before matrix searches. The snapshot contained {len(other_warnings)} other upstream `WARN` messages and {summary['maven_warning_count']} Maven warning messages; the latter are build-tool messages, not OSM restrictions.

The pinned input is [Geofabrik greater-manchester-261018.osm.pbf]({data_manifest['resolved_url']}), acquired {data_manifest['acquired_utc']}, {data_manifest['size_bytes']:,} bytes, SHA-256 `{data_manifest['sha256']}`. Acquisition headers, verified MD5 and the saved regional boundary are in [regional-data.json](regional-data.json). The boundary bbox is longitude/latitude `[-2.732097, 53.32613, -1.90804, 53.68742]`.

{chr(10).join(table)}

The counts describe the input as interpreted by this importer. Missing members can arise from extract cropping, missing source references, configured import filtering or malformed relations. Disconnected/ambiguous member paths, unsplit ways and unsupported member combinations are separate reported conditions. **The log alone does not establish which individual relation was cropped or malformed in the full OSM dataset.** No relation has been individually checked against the uncropped source in this audit.

The upstream source [OSMReader.java](../../core/src/main/java/com/graphhopper/reader/osm/OSMReader.java) catches restriction import exceptions and logs these messages in `warnOfRestriction`; the normal route engine and matrix engine use the resulting imported graph. An ignored real-world restriction can therefore affect both results while their metrics still agree. These input/import limitations do not demonstrate a matrix state, scatter or turn-cost defect. Any matrix-versus-independent-route discrepancy remains a separate failure requiring path evidence; importer warnings do not excuse it.

Regional clipping can also remove routes that leave the extract and return. Boundary comparisons establish agreement on this imported network, not equivalence with a larger UK import. This run keeps small subnetworks (`prepare.min_network_size=0`) to retain disconnected cases; that choice is recorded in the harness and can differ from a production import.

All warning categories, relation IDs, source line numbers, complete messages and the SHA-256 of the log snapshot are retained in [osm-import-warnings.json](osm-import-warnings.json). The source log is [matrix-regional.log](../../../audit/matrix-regional.log). If that running log grows, its final checksum will differ from the documented snapshot; the import warning records remain tied to their line numbers and pinned PBF.

Geometry/map inspection is **NOT RUN** here. This audit does not certify real-world street legality, specific bridge crossings, opposite carriageway snapping or pedestrian-only access. Route geometry exports and manual inspection remain separate evidence.
"""
    (args.output / "osm-data-limitations.md").write_text(document, encoding="utf-8")
    print(json.dumps({key: summary[key] for key in ("restriction_warning_count", "distinct_ignored_relation_count", "other_upstream_warning_count", "maven_warning_count")}))
    print(json.dumps({item["reason"]: item["count"] for item in summary["categories"]}, indent=2))


if __name__ == "__main__":
    main()
