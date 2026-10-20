# Source and local-data audit

The source checkout is a fresh shallow clone of the official repository at
`ebb578f4e94db72e82f2f8d3bc69417758e16e1c`, on `feature/local-matrix`.
Its only remote is `origin=https://github.com/graphhopper/graphhopper.git`.
No earlier user changes, graph cache, startup configuration, running-service
details or regional PBF were supplied in the workspace. Unrelated personal
directories were not searched. GitHub publication is not required for local work.

The pack calls this the 11.1 release baseline. The checked-out root and web POMs
declare **11.0-SNAPSHOT**, so build/run examples use the actual artifact name
`web/target/graphhopper-web-11.0-SNAPSHOT.jar`. Java/Maven versions and hashes are
recorded in [provenance.json](provenance.json). Baseline and release results are
recorded separately by the implementation owner; this audit does not mark any
unexecuted Java gate as passing.

## Audited upstream integration points

| Source | Finding and consequence |
| --- | --- |
| `config-example.yml` | Uses `graphhopper.profiles`, `profiles_ch`, and Dropwizard application/admin connectors. The example's ignored pedestrian ways must not be copied for a mixed foot/car import. |
| `core/src/main/java/com/graphhopper/config/Profile.java` | `hasTurnCosts()` is determined by a non-null `TurnCostsConfig`; runtime vehicle hints are obsolete. |
| `web-api/src/main/java/com/graphhopper/util/TurnCostsConfig.java` | `car()` selects motorcar/motor_vehicle. Default U-turn cost is infinite (`-1`); this example explicitly chooses a finite 60 seconds. |
| `core/src/main/resources/com/graphhopper/custom_models/car.json` | Uses car_access and car_average_speed with distance influence 90. |
| `core/src/main/resources/com/graphhopper/custom_models/foot.json` | Uses foot access/priority/speed, hike_rating, mtb_rating, country, road_class and foot_road_access. This example does not enable the separate elevation model. |
| `core/src/main/java/com/graphhopper/util/GHUtility.java` | `loadCustomModelFromJar` loads packaged models and removes comments before JSON parsing. Model files are supplied by the build, not an external service. |
| `core/src/test/java/com/graphhopper/routing/RoutingAlgorithmWithOSMTest.java` | Imports Monaco with `setOSMFile`, `setProfiles`, encoded values and `importOrLoad`; tests use min-network-size 0 and independently configure CH/LM. Includes simple turn-cost and p-turn OSM fixtures. |
| `core/src/main/java/com/graphhopper/routing/Dijkstra.java` | Uses traversal IDs for labels. Edge-based state must retain incoming-edge history. |
| `core/src/main/java/com/graphhopper/routing/DijkstraOneToMany.java` | Indexes weights by nodes and explicitly omits turn times from path extraction; unsuitable as an unmodified general edge-based matrix engine. |
| `core/src/main/java/com/graphhopper/routing/RoutingAlgorithmFactorySimple.java` | Wraps weighting through the supplied graph; avoid a second QueryGraph wrapping. |
| `web-bundle/src/main/java/com/graphhopper/http/GraphHopperBundle.java` | Registers Jersey resources and GraphHopper injection; local matrix registration belongs here. |
| `client-hc/src/main/java/com/graphhopper/api/GHMatrixSyncRequester.java` | Remote HTTP matrix client; it does not supply a local matrix implementation. |

## Isolated fixture configuration

[config-matrix.yml](config-matrix.yml) imports the checksum-recorded upstream
`core/files/monaco.osm.gz` fixture into `target/matrix-monaco-server`, with
node-based `foot` and edge-based `car` profiles and prepared CH for both. It binds
only localhost on application port 8997 and admin port 8998. It uses empty snap
preventions for parity with matrix/reference requests. Small disconnected
components are retained by `prepare.min_network_size: 0`.

This example configuration uses Monaco; its original OSM acquisition/map date is
unknown and the fixture is pinned by source commit and SHA-256. After the initial
audit, a Geofabrik Greater Manchester extract was independently acquired and
verified; [regional-data.json](regional-data.json) records the exact source/date/hash.
The regional comparison and targeted reconciliation have separate retained
evidence in [validation-report.md](validation-report.md). User deployment
configuration, an existing production graph and user-specific OD files remain
unavailable.

## Build and run from cmd.exe

Run these from the checkout root after the recorded baseline has completed:

```bat
mvn -pl web -am package -DskipTests
java -Xms512m -Xmx2g -jar web\target\graphhopper-web-11.0-SNAPSHOT.jar server docs\matrix\config-matrix.yml
```

Use another cmd.exe window for the stdlib Python example:

```bat
C:\Users\hulse\anaconda3\python.exe scripts\client.py --url http://localhost:8997 --profile foot --algorithm flexible --output docs\matrix\example-foot-response.json
C:\Users\hulse\anaconda3\python.exe scripts\client.py --url http://localhost:8997 --profile car --algorithm reference --output docs\matrix\example-car-reference-response.json
```

The client sends a rectangular 2×3 request, retains duplicates/order, and writes
the complete response plus optional long-format CSV. To use one's own inputs,
pass `--request request.json`; coordinates are `[longitude, latitude]`.
The examples require the implemented `/matrix` server; the audit itself did not
start a server. Python needs no package installation. Optional DataFrame usage
is documented inside `scripts/client.py`; install pandas in a dedicated Conda
environment if desired.

## Benchmark protocol

The opt-in Monaco harness compares MatrixEngine `reference` and `flexible` with
ordinary in-process prepared CH, using the same profiles and deterministic
coordinates. Planned dimensions are 1×10, 1×100, 1×1,000, 10×10, 10×100, 100×10, 100×100, and
duplicate-heavy 100×100. Each backend receives 5 warm-ups and 20 measured runs,
with backend order rotated. Import/CH preparation is outside warm request timing.
Capture median/p95 wall time, cells/s, searches/visited states when available,
heap/GC observations, Java/OS identity, seed and file hashes. These are fixture
measurements; regional data needs separate coverage. The harness exports its exact
profile/workload coordinates to `benchmark-requests.json` for the HTTP comparison.
CH counters distinguish actual pairwise calls from unavailable core-search counts.
Unknown visited-state totals remain null. Every sample separates completed,
reachable, and null cells; nulls are not counted as successful routes.

The Java harness times `elapsed_request_ms` and `serialization_ms` independently
for matrix responses, then records their per-sample sum as
`request_plus_serialization_ms`. It reuses one Jackson mapper. Both medians and
p95 values are exported for all three timings, and matrix throughput is available
with serialization included. The compatibility columns `elapsed_ms`, `median_ms`
and `p95_ms` retain the request-only scope. The CH baseline measures ordinary
minimal-output routing calls; its matrix-serialization timing is unavailable and
left null. These timings exclude HTTP transport and client parsing.

The raw and aggregate CSV also retain measured engine phase timings for
snapping, QueryGraph creation, backend calls, flexible core search, scatter/output
and engine total when the response exposes them. Reference backend internals and
other missing phases remain null. Independent phase medians need not sum to the
median total. Request heap/GC measurements precede serialization; serialization
GC counts are recorded separately.

When the JVM supports and already enables `com.sun.management.ThreadMXBean`
allocation tracking, the Java harness also records current-thread allocated-byte
deltas around each request and around matrix JSON serialization, with median/p95
values in the summary. The serial matrix worker runs on the measured thread.
Unsupported, disabled or unavailable counters remain null; the harness does not
enable tracking or infer RSS. These deltas measure allocation volume rather than
retained or peak memory and exclude allocations on other threads.

The standalone stdlib HTTP harness uses persistent `http.client` connections,
rotates backend order, and compares `/matrix` flexible with repeated ordinary
`/route` requests under this CH-prepared configuration. Its default subset is
1×10 and 1×100, with 5 warm-ups and 20 measured repetitions per backend/profile.
It records response-body bytes, reconnects, median/p95 latency, completed and
reachable cells/s, fingerprints and per-cell discrepancies. It requests empty
snap preventions and minimal `/route` output without an algorithm override or
CH-disabling hints. Upstream JSON does not identify CH selection, so retain the
actual server startup configuration as evidence. Example commands from cmd.exe:

```bat
mvn -pl core -am test -Dtest=MatrixBenchmarkTest -Dsurefire.failIfNoSpecifiedTests=false -Dmatrix.benchmark=true -Dtest.heap.max=2g
C:\Users\hulse\anaconda3\python.exe scripts\benchmark-http.py
C:\Users\hulse\anaconda3\python.exe scripts\benchmark-http.py --workloads 100x100 --include-large --repeats 5
```

The last command explicitly opts into a larger HTTP workload with fewer measured
repetitions; the selected count and limited percentile resolution are recorded.
More than 1,000 cells requires `--include-large`. HTTP timings include response
parsing/validation and exclude pre-encoding/import/CH preparation. Body bytes
exclude headers. Network, unknown server and search-limit failures mark a run
incomplete; they never become disconnected cells. An HTTP timing run marked
`MEASURED` is separate from the recorded metric-comparison status and is not a
release correctness certificate. No result is claimed before execution.
