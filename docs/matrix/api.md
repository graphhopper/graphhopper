# Local matrix API, contract 1.0

Send JSON to `POST /matrix`. Coordinates are `[longitude, latitude]`.
Set `profile` to a configured static street profile. The example deployment provides
`foot` and turn-aware `car`. Either supply `points` for a square matrix, or both
`from_points` and `to_points` for a rectangular matrix. Axes must be nonempty.
Rows follow origins and columns follow destinations; order and duplicates are preserved.

```json
{"profile":"car","from_points":[[7.42135,43.730729]],"to_points":[[7.419199,43.727697]],"out_arrays":["times","times_ms","distances","weights"],"fail_fast":false,"local":{"algorithm":"flexible"}}
```

`out_arrays` defaults to `times,distances`. Choose a nonempty unique subset of
`times`, `times_ms`, `distances`, `weights`. Outputs always describe the same route,
which minimizes the configured profile's weight. Asking for distances does not change
the objective to shortest distance. Times are nonnegative integer seconds:
`times = times_ms / 1000` rounded down after summing all millisecond durations.
Distances are double precision metres; weights have profile-dependent units.

The matrix reports the configured base-edge and turn-cost objective. An ordinary
prepared-CH `/route` can report a slightly different weight because preparation and
shortcut storage use reduced precision. The recorded HTTP comparisons retain those
differences as `MISMATCH`; separate [Monaco](ch-weight-reconciliation.json) and
[Manchester](regional-http-benchmark/ch-weight-reconciliation.json) proofs reconstruct
the selected CH paths and explain each accepted difference. This does not establish
a general weight tolerance or excuse a time, distance, reachability or path error.
The underlying routing algorithms and API units are unchanged.

`local.algorithm` accepts `reference`, `flexible`, `ch_experimental`, or `auto`
(which continues to select flexible).
Reference calls unmodified in-process two-point GraphHopper Dijkstra with CH and LM
disabled, independent snapping, no geometry and no instructions. Flexible constructs
one request QueryGraph using both axes, then searches once per distinct valid raw origin.
Its directed edge states retain turn history when the selected profile has turn costs.
The response reports the actual backend and measured searches; no symmetry is assumed.

`ch_experimental` is an opt-in node-based fixed-profile bucket CH experiment. It
requires a matching loaded CH preparation. Each distinct snapped destination builds
a reverse upward bucket search, and each distinct valid origin coordinate has a
forward upward search. Directed shortcuts are expanded for actual profile weight,
milliseconds and distance. A stored shortcut weight is never substituted for time.

Existing upstream CH preparation uses reduced-precision costs and can omit a shortcut
needed for an original-double near tie. For that reason every experimental row also
runs the original-cost flexible search on the identical complete QueryGraph. The API
returns those certified metrics/nulls exactly. Candidate differences are counted and
corrected; there is no tolerance waiver. This additional verification is included in
all end-to-end timings. The experiment is not selected automatically.

The response has `requested_algorithm`, actual `algorithm`, and an explicit
`fallback_reason` when the requested CH experiment uses flexible routing instead:
`TURN_COST_PROFILE_UNSUPPORTED`, `NO_PREPARED_CH`, or `EDGE_BASED_CH_UNSUPPORTED`.
For a selected experiment it includes `experimental:true`,
`optimality_verification:"flexible_each_origin"`, loaded `ch_preparation` identity,
and `ch_matrix` backward/forward/certificate search counts, states, bucket/cache entry
counts and metric extraction time. `corrected_cells` counts distinct origin
coordinates by raw destination index before duplicate-origin scatter; the response
states that basis explicitly. Raw CH candidate optimality is not guaranteed.

CH preparation, candidate search and certificate exploration all consume the same
whole-request state budget and deadline. Bucket entries are bounded by the reverse
settled-state budget, and the directed-arc metric cache is capped at the initial
remaining state budget. Cache exhaustion is `SEARCH_LIMIT_EXCEEDED`, not disconnection.
All experiment storage is request-local; the imported CH graph remains immutable.
Read [ch-matrix-design.md](ch-matrix-design.md) for the proof and counterexample.

Snapping uses the profile weighting and subnetwork filter with empty snap preventions,
matching an ordinary `/route` with `snap_preventions:[]`. Out-of-extract points are
invalid, matching ordinary routing's bounds check. There is no extra maximum snap distance.
Partial edges are retained. Exact coordinate duplicates share a snap; nearby distinct
coordinates are never merged by a tolerance.

`fail_fast` defaults to true. With false, invalid origin rows/destination columns and
genuinely disconnected cells are null in every requested output. `hints` distinguishes
`INVALID_SNAP` using `invalid_from_points`/`invalid_to_points` from
`CONNECTION_NOT_FOUND` using `point_pairs`. A valid diagonal is zero; an invalid
diagonal is null. Timeouts, limits and internal errors fail the entire request.

| HTTP status | Stable details code |
| --- | --- |
| 400 | INVALID_REQUEST, INVALID_JSON, UNKNOWN_PROFILE, INVALID_SNAP, CONNECTION_NOT_FOUND |
| 413 | REQUEST_LIMIT_EXCEEDED |
| 422 | SEARCH_LIMIT_EXCEEDED |
| 503 | SERVICE_BUSY |
| 504 | DEADLINE_EXCEEDED |
| 500 | CANCELLED, INVALID_COST, METRIC_OVERFLOW, REFERENCE_ROUTING_FAILED, INTERNAL_ERROR |

Errors contain `message` and `hints:[{message,details}]`, without partial metric arrays.
Malformed JSON, duplicate object fields, trailing JSON values, unknown fields,
explicit null/type coercions, boolean/nonfinite/out-of-range coordinates and unsupported
restrictions are rejected, including options supplied as URL query parameters.
Requests must use `application/json` (otherwise HTTP415).

`info.local_matrix` identifies the contract, backend, profile/turn mode, dimensions,
unique raw origin count, actual searches, settled states, units, graph/profile SHA256,
build commit, one worker per request, and request timings. Each snapped-point entry is
`{coordinates:[lon,lat],snap_distance_m:metres}`; both values are null for an invalid point.
`visited_states_known`/`visited_states_complete` also identify counter completeness.
If an upstream reference response omits search counters, the whole request fails
with `REFERENCE_ROUTING_FAILED`, since its aggregate state budget cannot be enforced.
The graph identity hashes loaded storage contents, including geometry/turn data,
import properties, encoding, engine version and build identity once at engine creation.
Profile identity additionally hashes the resolved configured model and turn settings.

Default bounds are one MiB body, 1000 points per axis, 100000 cells, two admitted
requests, one serial row worker per request, a 30-second request deadline and one million
settled states across each request. Override through JVM properties
`matrix.max_axis_points`, `matrix.max_cells`, `matrix.max_concurrent_requests`,
`matrix.timeout_ms`, `matrix.max_visited_states`. Bounds are checked before output
allocation. Deadline/interruption checks stop shared exploration; reference routes receive
the remaining timeout and state budget. A reference search already inside upstream
Dijkstra handles an interruption after that route returns; its timeout/state cap still applies.
The synchronous API has no remote cancellation
job endpoint; a disconnected HTTP client may leave its calculation running until its deadline.

Excluded: PT/GTFS matrices, departure-time traffic, runtime custom models, headings,
curbsides, point hints, pass-through, alternatives, per-cell geometries, arbitrary routing
hints, asynchronous jobs and an uncertified CH backend selected by auto. The dedicated
CH experiment currently supports node-based fixed profiles only. Existing configured
custom profiles are supported through their imported model.

Combined QueryGraphs can split additional edges compared with independent two-point
requests. Their geometric lengths and per-edge millisecond rounding can differ slightly.
The validation reports retain per-path diagnoses; a matching weight alone never waives
unexplained time/distance discrepancies. Queue ties are deterministic within identical
requests; there is no secondary minimum-time/distance objective.
