# Manchester matrix comparison: 1,000 points

At one million OD cells, the implemented auto matrix is faster than the complete
eight-caller `/route` workload on this local test: **4.72–5.33x for driving and
9.77–10.15x for walking**. This is a measured full-workload comparison with that
client, rather than a claim about maximum server throughput.

Detailed outputs and maps are [linked release assets](https://github.com/hulsiejames/graphhopper/releases/tag/manchester-matrix-1000-points-2026-10-20).
Only scripts and this summary are committed; generated evidence stays outside Git.
The [earlier 16-point comparison](manchester-area-comparison.md) showed a smaller
workload where the matrix lost.

## Workload and measured runtime

There are **1,000 points**, giving **1,000,000 directed cells per profile**, or
999,000 directed pairs excluding the 1,000 self-pairs. Both driving and walking
were tested: two complete million-request `/route` sweeps in total.

The rectangle stays the same as the earlier experiment: longitude -2.270 to
-2.215, latitude 53.465 to 53.495, approximately 12.1 square km across Manchester
and Salford. From 1,500 seeded, jittered grid candidates, selection takes the
first 1,000 that connect both ways to a common hub for both profiles, snap within
100 m, and have distinct snapped coordinates for each profile. These criteria
were fixed before timing. No points were dropped after seeing benchmark results.
The selected points cover all 16 subdivisions of the rectangle with 54–75 points
in each. Both full matrices confirm 1,000 distinct valid snaps; maximum snap
distances are 96.415 m for car and 70.149 m for foot.

All values below are **actual wall-clock seconds for the complete million-cell
workload**, including all diagonal and reverse-direction cells:

| Profile | Matrix run 1 | Matrix run 2 | `/route`, eight callers, one full run | Observed speedup range |
| --- | ---: | ---: | ---: | ---: |
| Car | 107.004 | 94.843 | 505.563 | 4.72–5.33x |
| Foot | 59.908 | 57.686 | 585.320 | 9.77–10.15x |

Each matrix is one HTTP request. Each route sweep makes 1,000,000 real requests
through eight persistent connections, with one worker per connection. Requests
do not disable CH; the prepared profile selects the normal CH `/route` backend.
Matrix `auto` selects `flexible`, performs 1,000 target-set searches, and settles
84,058,859 states for car or 66,613,239 for foot. No experimental-CH million-cell
result is claimed in this test.

Both matrix repetitions return **binary-identical trip metrics**, cell for cell.
Every matrix and route cell is reachable, and every diagonal is zero. All six
full runs completed successfully. The route sweeps were uninterrupted; they did
not resume from checkpoints.

The matrix amortizes one search over all destinations from an origin. At this
density it also avoids 999,999 HTTP requests per workload. These are plausible
reasons for the observed crossover; the benchmark does not isolate their
individual contributions.

## Client-capacity check

A separate, untimed-for-the-main-table pilot used the same 10,000 sampled pairs
with 8/16/32/64 client threads and eight independent client processes. Each
setting was repeated twice, with thread order rotated. Eight processes retain
eight concurrent HTTP connections while removing a shared Python GIL.

| Profile | 8-thread sample median | Best thread sample median | 8-process sample median |
| --- | ---: | ---: | ---: |
| Car | 4.628 s | 4.332 s (64 threads) | 3.968 s |
| Foot | 5.607 s | 5.261 s (64 threads) | 5.353 s |

The largest sampled elapsed-time reduction is 14.26% for car and 6.16% for foot.
No additional million-request run at these settings was performed. These pilot
durations are not substituted for measured full-workload times or presented as
maximum server capacity. The headline speedups apply to the measured eight-
caller baseline; a differently tuned client can narrow them.

## Cell-by-cell accuracy

All one million cells per profile were compared, retaining every outcome and
delta. No numerical tolerance turns differing cells into exact matches.

| Compared with normal `/route` | Car | Foot |
| --- | ---: | ---: |
| Reachability agreement | 1,000,000/1,000,000 | 1,000,000/1,000,000 |
| Exact millisecond-duration agreement | 330,334/1,000,000 | 579,473/1,000,000 |
| Median absolute duration difference | 1 ms | 0 ms |
| 99th percentile absolute duration difference | 4 ms | 2 ms |
| Maximum absolute duration difference | 7 ms | 4 ms |
| Distance agreement after `/route` rounding to 0.001 m | 479,866/1,000,000 | 698,518/1,000,000 |
| Maximum absolute distance difference | 0.003861407 m | 0.002542295 m |

`/route` rounds distance to 0.001 m and weight to six decimals. A full matrix has
one QueryGraph containing all 1,000 snaps, whereas an individual route normally
has two. Additional virtual splits can change distance accumulation and per-edge
millisecond rounding. This provides a plausible explanation for the lower exact
time agreement than in the 16-point test, despite small absolute differences.

To check the explanation against original-weight routing, a separate Dijkstra
sample disables CH and LM explicitly. Per profile it includes one seeded
off-diagonal destination for every origin plus the 20 largest time and 20
largest distance discrepancies: 1,040 unique cells each, 2,080 total. Every
sampled full-matrix disagreement after HTTP rounding is also rerun as a separate
flexible 1 x 1 matrix with only that pair's snaps.

All **1,908 pair controls match Dijkstra's serialized duration, distance and
weight**: 1,018 car and 890 foot. Default CH and Dijkstra have identical reported
distances for all 2,080 samples; their times agree in all car samples and all but
one foot sample, which differs by 1 ms. These checks support overlay/rounding
effects in the sampled cells. They do not establish physical path identity or
prove every unsampled discrepancy has the same cause.

Dijkstra is the stronger reference for minimum configured profile weight on its
two-point overlay. It is not a simultaneous shortest-distance and fastest-time
oracle. Car retains distance influence and turn costs; foot retains road
priority. A million exact Dijkstra pair queries per profile was not performed.

## Limits, timing and reproduction

The unchanged endpoint rejects the million-cell request with **HTTP 413,
`REQUEST_LIMIT_EXCEEDED`**, because its shipped maximum is 100,000 cells. The
successful run is explicitly an operator-configured benchmark:

```bat
java -Xmx4g -Dmatrix.max_cells=1000000 -Dmatrix.max_visited_states=1000000000 -Dmatrix.timeout_ms=600000 -jar web\target\graphhopper-web-11.0-SNAPSHOT.jar server ..\audit\manchester-1000-points\config.yml
```

The graph, profiles and routing implementation are unchanged. The YAML changes
only the `/route` resource logger to WARN to avoid a million informational log
lines. JVM/property and listener ownership records are included in the release.
No measured server workloads overlap. Both app/admin ports were confirmed
closed after stopping the owned test server.

Requests use IPv4 loopback, persistent HTTP and identity encoding. Geometry and
instructions are disabled for `/route`. Matrix responses request millisecond
times, distances and weights. Connection opening and warmups precede the
timers; graph import and CH preparation are excluded. Matrix timing includes
HTTP and JSON parsing, with binary conversion/archival afterwards. Route wall
time also includes payload creation, compact row writes and checkpoints; this
small asymmetry is disclosed rather than subtracted from the measurement.

The accepted server JAR SHA-256 remains
`7bba4abeb278529c9a8d1d52dad6336ca1a14d9532e0462642f14a55c9a84b70`,
built cleanly from `a9102e2039b079a43dcb1494d77619e4e989acaa`.
The cached Greater Manchester graph has 500,226 nodes and 636,908 edges. Dataset
PBF SHA-256:
`11f9b3f5b1d63cdf107f5804bd46131929dc2f13a1bd2837c6611429d8b9e4d3`.
Frozen point-set SHA-256:
`76f19d88f67c24bfeb2c58e067ff6cfa8e811223d003bc513844ebdeb56c65ed`.
The recorded host runs Windows 11 and Java 22.0.2 with eight logical processors
and approximately 48 GiB of physical RAM. The test JVM has a 4 GiB heap cap;
its recorded peak working set was approximately 995 MiB.

Place the release's `benchmark-data` contents at
`..\audit\manchester-1000-points` (or adjust these paths) and prepare the regional
graph. Run the stdlib harness from the repository root in `cmd.exe` using a fresh
output directory; pass the downloaded point file and actual loaded YAML config:

```bat
python scripts\benchmark-manchester-scale.py --phase matrix --output-dir ..\audit\fresh-run --points ..\audit\manchester-1000-points\points.json --server-config ..\audit\manchester-1000-points\config.yml --profile car --label r1 --matrix-max-cells 1000000 --matrix-max-visited-states 1000000000 --matrix-timeout-ms 600000
python scripts\benchmark-manchester-scale.py --phase routes --output-dir ..\audit\fresh-run --points ..\audit\manchester-1000-points\points.json --server-config ..\audit\manchester-1000-points\config.yml --profile car --workers 8 --label r1 --matrix-max-cells 1000000 --matrix-max-visited-states 1000000000 --matrix-timeout-ms 600000
python scripts\benchmark-manchester-scale.py --phase compare --output-dir ..\audit\fresh-run --points ..\audit\manchester-1000-points\points.json --profile car --workers 8 --label r1
```

Repeat with `--profile foot` and a new matrix label for a second repetition.
The limit arguments record the server's settings; they do not change it.
Existing completed outputs are not overwritten. Release reproduction helpers
record the precise sequential schedule, candidate selection, extra concurrency
pilots, Dijkstra sample and exports. The independent audits verify every raw
matrix cell, every route record/row, all comparison counts and all sampled
Dijkstra controls. Full per-profile million-row CSVs, binary vectors, original
matrix responses, maps, GeoJSON and SHA-256 manifests live in the release.
