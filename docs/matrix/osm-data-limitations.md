# Regional OSM import limitations

The recorded Greater Manchester import emitted **137 restriction warnings covering 137 distinct relations**. Each ends with `Relation ignored.` These warnings come from GraphHopper's upstream OSM import, before matrix searches. The snapshot contained 0 other upstream `WARN` messages and 4 Maven warning messages; the latter are build-tool messages, not OSM restrictions.

The pinned input is [Geofabrik greater-manchester-261018.osm.pbf](https://download.geofabrik.de/europe/united-kingdom/england/greater-manchester-261018.osm.pbf), acquired 2026-10-19T18:06:41.557727+00:00, 51,632,590 bytes, SHA-256 `11f9b3f5b1d63cdf107f5804bd46131929dc2f13a1bd2837c6611429d8b9e4d3`. Acquisition headers, verified MD5 and the saved regional boundary are in [regional-data.json](regional-data.json). The boundary bbox is longitude/latitude `[-2.732097, 53.32613, -1.90804, 53.68742]`.

| Recorded importer category | Count | Representative OSM relation IDs |
|---|---:|---|
| has no member with role 'to' | 40 | [4853649](https://www.openstreetmap.org/relation/4853649), [4853650](https://www.openstreetmap.org/relation/4853650), [4853651](https://www.openstreetmap.org/relation/4853651) |
| has no member with role 'from' | 32 | [7422845](https://www.openstreetmap.org/relation/7422845), [7423368](https://www.openstreetmap.org/relation/7423368), [7902165](https://www.openstreetmap.org/relation/7902165) |
| has no member with role 'from' and 'to' | 32 | [2087629](https://www.openstreetmap.org/relation/2087629), [8001488](https://www.openstreetmap.org/relation/8001488), [8001489](https://www.openstreetmap.org/relation/8001489) |
| has no member with role 'via' | 10 | [7833466](https://www.openstreetmap.org/relation/7833466), [7833467](https://www.openstreetmap.org/relation/7833467), [7833468](https://www.openstreetmap.org/relation/7833468) |
| has multiple members with role 'via' and type 'node', but multiple via-members are only allowed when they are of type: 'way' | 8 | [13131244](https://www.openstreetmap.org/relation/13131244), [13347980](https://www.openstreetmap.org/relation/13347980), [15525534](https://www.openstreetmap.org/relation/15525534) |
| has disconnected member ways | 4 | [3821792](https://www.openstreetmap.org/relation/3821792), [8908883](https://www.openstreetmap.org/relation/8908883), [14713987](https://www.openstreetmap.org/relation/14713987) |
| has to-member ways that aren't split at the via-member node | 3 | [14222994](https://www.openstreetmap.org/relation/14222994), [19417734](https://www.openstreetmap.org/relation/19417734), [19976136](https://www.openstreetmap.org/relation/19976136) |
| has from-member ways that aren't split at the via-member node | 2 | [13258979](https://www.openstreetmap.org/relation/13258979), [19976135](https://www.openstreetmap.org/relation/19976135) |
| has multiple members with role 'from' even though it is not a 'no_entry' restriction | 2 | [9038615](https://www.openstreetmap.org/relation/9038615), [12947073](https://www.openstreetmap.org/relation/12947073) |
| has from-member ways that aren't adjacent to the via-member node | 1 | [11878414](https://www.openstreetmap.org/relation/11878414) |
| has member ways that do not form a unique path | 1 | [3821793](https://www.openstreetmap.org/relation/3821793) |
| has multiple members with role 'to' even though it is not a 'no_exit' restriction | 1 | [8462058](https://www.openstreetmap.org/relation/8462058) |
| has to-member ways that aren't adjacent to the via-member node | 1 | [13009586](https://www.openstreetmap.org/relation/13009586) |

The counts describe the input as interpreted by this importer. Missing members can arise from extract cropping, missing source references, configured import filtering or malformed relations. Disconnected/ambiguous member paths, unsplit ways and unsupported member combinations are separate reported conditions. **The log alone does not establish which individual relation was cropped or malformed in the full OSM dataset.** No relation has been individually checked against the uncropped source in this audit.

The upstream source [OSMReader.java](../../core/src/main/java/com/graphhopper/reader/osm/OSMReader.java) catches restriction import exceptions and logs these messages in `warnOfRestriction`; the normal route engine and matrix engine use the resulting imported graph. An ignored real-world restriction can therefore affect both results while their metrics still agree. These input/import limitations do not demonstrate a matrix state, scatter or turn-cost defect. Any matrix-versus-independent-route discrepancy remains a separate failure requiring path evidence; importer warnings do not excuse it.

Regional clipping can also remove routes that leave the extract and return. Boundary comparisons establish agreement on this imported network, not equivalence with a larger UK import. This run keeps small subnetworks (`prepare.min_network_size=0`) to retain disconnected cases; that choice is recorded in the harness and can differ from a production import.

All warning categories, relation IDs, source line numbers, complete messages and the SHA-256 of the log snapshot are retained in [osm-import-warnings.json](osm-import-warnings.json). The source log is [matrix-regional.log](../../../audit/matrix-regional.log). If that running log grows, its final checksum will differ from the documented snapshot; the import warning records remain tied to their line numbers and pinned PBF.

Geometry/map inspection is **NOT RUN** here. This audit does not certify real-world street legality, specific bridge crossings, opposite carriageway snapping or pedestrian-only access. Route geometry exports and manual inspection remain separate evidence.
