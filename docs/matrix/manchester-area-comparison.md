# Manchester geographic OD comparison

On this workload the implemented matrix endpoint is **slower than all-pairs
`/route` calls using prepared CH**. Its default state budget also rejects the
complete matrix. This experiment does not establish a performance benefit.

Full measurements, literal HTTP requests/responses, maps, GeoJSON, pair controls
and checksums are [release assets](https://github.com/hulsiejames/graphhopper/releases/tag/manchester-matrix-benchmark-2026-10-20).
Generated evidence is deliberately excluded from this PR.

## Area and method

- A fixed 4 x 4 grid over central Greater Manchester, spanning Manchester and
  Salford: west/east -2.270/-2.215, south/north 53.465/53.495.
  Approximately 3.639 x 3.336 km (12.1 square km).
- All 16 points serve as origins and destinations: 256 directed cells per
  profile, including 16 self-pairs and 240 cross-pairs. Both foot and car use the
  same requested coordinates. All points have distinct valid profile snaps;
  maximum snap displacement is 33.69 m for foot and 35.68 m for car.
- Every pair was routed, including reverse directions. All 256 pairs were
  reachable for each profile, all self-pairs returned zero, and all cross-pairs
  returned positive trip metrics.
- One full warmup per method, then five measured whole-workload repetitions,
  with method order rotated. No cached response reuse. Every repetition's
  scalar outcomes were checked for exact repeat stability.
- Persistent HTTP connections on IPv4 loopback, identity encoding, preencoded
  requests. `/route` disables geometry and instructions. Four concurrent callers
  each have their own connection. Timers include HTTP, JSON parsing and worker
  dispatch; archival/comparison occurs outside timing. Import and CH preparation
  are excluded. Both runs use the same prepared graph cache, JAR, profiles and
  JVM heap size.
- The default `/route` baseline uses its prepared CH configuration. Server logs
  record the selected CH algorithms. A separate complete Dijkstra reference
  disables CH/LM explicitly; its sweep is outside the production timing table.

## Runtime

The shipped matrix limit of 1,000,000 visited states failed with HTTP 422
`SEARCH_LIMIT_EXCEEDED` for **both profiles and both matrix modes**, in warmup
and all five measured repetitions. Every `/route` batch completed. Error-return
latencies are retained in the release and are not presented as completed-work
runtime.

The following separate run changed only the matrix state budget to 8,000,000
using `-Dmatrix.max_visited_states=8000000`. The 30-second deadline, admission
limit and coordinates stayed fixed. Values are whole-256-cell wall-clock
milliseconds, median [minimum, maximum] across five measured repetitions.

| Method | Foot, ms | Car, ms |
| --- | ---: | ---: |
| `/route`, sequential 256 calls | 554.23 [493.02, 659.91] | 476.55 [459.20, 512.80] |
| `/route`, four concurrent callers | 199.09 [184.28, 230.42] | 187.19 [181.32, 200.98] |
| `/matrix`, auto, one 16 x 16 call | 895.61 [888.50, 906.84] | 1191.57 [1166.88, 1202.47] |
| `/matrix`, ch_experimental | 1697.45 [1641.42, 1731.22] | 1188.06 [1151.09, 1264.90] |

Auto selects flexible routing here. It is **1.62x slower for foot and 2.50x
slower for car** than sequential prepared-CH `/route`, or 4.50x/6.37x slower than
four concurrent callers. It settles 1,125,464/1,205,633 states respectively,
explaining why the original one-million-state budget failed.

Experimental CH adds candidate work plus its mandatory flexible certificate
for foot, increasing runtime. Car explicitly falls back to flexible with
`TURN_COST_PROFILE_UNSUPPORTED`; the two car rows therefore exercise the same
backend and their small timing difference is run-to-run variation. Both matrix
modes return exactly equal scalar results, cell for cell.

These are local measurements for one fixed small geographic workload. Four
callers use additional concurrency. No production-load, remote-network or
national-scale speedup is claimed.

## Returned trip metrics and the reference

Compared with both default `/route` and the explicit Dijkstra reference:

| Metric | Foot | Car |
| --- | ---: | ---: |
| Reachability agreement | 256/256 | 256/256 |
| Exact travel-time agreement, milliseconds | 256/256 | 256/256 |
| Distance agreement after `/route` rounding to 0.001 m | 254/256 | 239/256 |
| Largest absolute distance difference | 0.0005161 m | 0.0010546 m |

The matrix emits unrounded distances and weights; `/route` rounds distances to
0.001 m and weights to six decimals. Every differing cell and signed delta is
recorded, without truncation or a posthoc tolerance waiver.

Against Dijkstra, four foot cells and 28 car cells differ after HTTP rounding
in weight and/or distance; all still have identical millisecond durations.
Each was rerun as a separate flexible 1 x 1 matrix, with only that pair's snaps.
**All 32 controls match Dijkstra's serialized weight, duration and distance.**
This supports the shared matrix QueryGraph's extra virtual edge splits as the
source of these small differences. It is endpoint-output evidence, rather than
a proof of binary-identical path costs on a common graph overlay.

Default prepared-CH `/route` and Dijkstra also differ slightly in reported
weight for 230 foot cells and 240 car cells, despite identical returned times
and distances. Do not treat the default CH weight as an exact original-weight
oracle.

Dijkstra is the stronger correctness reference for the configured **profile
weight** on its two-point graph overlay. It does not promise the shortest
distance and fastest time simultaneously. This car model includes 90 s/km
distance influence and turn costs; foot uses road priority. See the pinned
[routing modes](https://github.com/graphhopper/graphhopper/blob/11.1/docs/core/routing.md),
[custom weighting](https://github.com/graphhopper/graphhopper/blob/11.1/docs/core/custom-models.md)
and [HTTP serializer](https://github.com/graphhopper/graphhopper/blob/11.1/web-api/src/main/java/com/graphhopper/jackson/ResponsePathSerializer.java).

## Reproduction and artifacts

Measured server JAR SHA-256:
`7bba4abeb278529c9a8d1d52dad6336ca1a14d9532e0462642f14a55c9a84b70`.
Its embedded clean source commit is
`a9102e2039b079a43dcb1494d77619e4e989acaa`; version `11.0-SNAPSHOT`.
The Greater Manchester PBF SHA-256 is
`11f9b3f5b1d63cdf107f5804bd46131929dc2f13a1bd2837c6611429d8b9e4d3`.
The prepared graph has 500,226 nodes and 636,908 edges. The recorded JVM uses
`-Xmx4g`; release runtime records bind the owned listening process to its actual
arguments. Both owned servers were stopped after the experiment.

From the repository root in `cmd.exe`, with the prepared regional graph:

```bat
run-matrix.cmd regional
```

In another terminal (stdlib only; an existing Conda Python is sufficient):

```bat
python scripts\benchmark-manchester-area.py --output-dir ..\audit\manchester-area-comparison\default-limits
```

For the separately labelled expanded-budget run, restart the server:

```bat
java -Xmx4g -Dmatrix.max_visited_states=8000000 -jar web\target\graphhopper-web-11.0-SNAPSHOT.jar server docs\matrix\config-manchester.yml
python scripts\benchmark-manchester-area.py --output-dir ..\audit\manchester-area-comparison\expanded-budget --matrix-max-visited-states 8000000
python scripts\render-manchester-area.py --input ..\audit\manchester-area-comparison\expanded-budget\points.json --results ..\audit\manchester-area-comparison\expanded-budget\results.json --profile car --output ..\audit\manchester-area-comparison\car
```

`--matrix-max-visited-states` records the operator's setting; it does not change
the server. The harness refuses to overwrite a prior run. Output files include
`all-od-results.csv`, timing summaries/samples, complete HTTP response ledgers,
and machine-readable comparisons. The release includes HTML street maps with
an offline point-layout fallback, both profile snaps, GeoJSON, diagnostic pair
controls and a SHA-256 manifest. Graph caches, PBFs and server JARs are excluded.
