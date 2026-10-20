# QueryGraph precision evidence

The fixed Monaco fixture passed with **zero unexplained discrepancies**. This is fixture evidence; the observed maxima below are not universal tolerances.

The 34 points selected with seed `20261019` produced 1,156 cells per profile:

| Profile | Reachable | Disconnected | Within original tolerances | Individually explained split effects |
|---|---:|---:|---:|---:|
| foot | 1,090 | 66 | 1,090 | 0 |
| car | 998 | 158 | 671 | 327 |
| Total | 2,088 | 224 | 1,761 | 327 |

Across the 327 diagnosed pairs, the largest absolute independent-route minus shared-result differences were **0.0001313149885504572 weight units**, **0.0007566120766568929 metres**, and **0 milliseconds**. Every diagnosed case and its actual paths, snap coordinates, directed original edge keys, edge/turn contributions, fingerprints and observed outputs are retained in `fixture-validation.json`. `precision-diagnostics.json` retains the first case separately.

For car origin 0 → destination 1, both methods snapped to the same endpoints. The first raw point `[7.4237518,43.7279317]` snapped 70.27734359956742 m to `[7.4229946,43.7276154]`; the destination `[7.4171931,43.7314215]` was already a tower point. Both routes used the identical collapsed sequence of original directed edges.

The complete difference came from original directed edge key **1105**. The independent route used one stored edge of **39.558 m**. An additional matrix point split that edge into two virtual pieces totalling **39.557702947048 m**. The resulting distance change was **0.000297052952 m**, with unchanged turn costs and weight per metre. It explains the weight change from **448.03853181818187** to **448.03846943706196**. Both durations were **228485 ms**. Total route distance changed from **2439.465 m** to **2439.464702947048 m**.

Upstream `BaseGraphNodesAndEdges` stores distance as an integer with a factor of 1,000, rounding to millimetres. `QueryOverlayBuilder.createEdges` recalculates virtual-edge distance from its geometry using `DistancePlaneProjection`. Thus even a route whose own endpoints are tower nodes can encounter newly split interior edges in a combined QueryGraph. Per-edge duration truncation can also matter in other requests; this fixture showed no millisecond difference among the diagnosed pairs.

No global tolerance was widened. The ordinary comparison remains absolute `1e-7` plus relative `1e-10` for weight, `0.002 m` for distance and `1 ms` for independent-route duration. A case outside those bounds passes only after independently proving identical snapped endpoints and canonical directed route, unchanged turn costs and edge weight per metre, agreement of observed matrix/reference outputs with their reconstructed paths, and strict same-QueryGraph comparison against unmodified Dijkstra. All unclassified differences fail.

The separate core suite passed **20,000 differential OD comparisons**: seeds `730201`–`730250`, both node- and directed-edge-based traversal, base graphs and QueryGraphs with four virtual points, including three points on one edge. Every comparison requires exact milliseconds, tight weight tolerance, and distance within floating-point summation bounds. Every returned diagnostic path is independently recomputed. Its tests also cover the complete hand-computed toy matrix, real turn storage, finite/forbidden/penalized U-turns, stale labels, zero costs, deterministic ties, overflow, limits, and proven in-flight interruption. The latest focused run passed all 16 tests: ten core tests, five engine tests and one standalone split diagnostic.

## Regional restriction-edge copies

The initial regional run compared 1,296 cells for each of foot and car. It recorded 22 foot split effects and 29 car differences; ten car cases initially remained unexplained because the diagnostic required identical raw original-edge keys. That initial **FAIL** report and its JSONL evidence remain unchanged. Their SHA-256 values are pinned by `regional-reconciliation.json`.

Every one of those ten cases differed at exactly one edge-copy choice: directed key **864273 versus 864271** in two cases, **71756 versus 71754** in two cases, and **493848 versus 493842** in six cases. The actual upstream `BaseGraph.forEdgeAndCopiesOfEdge` relation proved that each pair shares reused geometry. These are restriction-transformation copies, rather than unrelated parallel roads. The selected copies had identical directed endpoints, distance, edge weight and milliseconds; both actual predecessor/successor turns were legal with identical costs. The helper retains raw keys and the upstream copy-family members in its exported evidence.

The metric difference came from a separate split edge on each route. Across these ten cases its absolute effect was approximately **0.0001281027 m**, **0.0000279996 weight units** and **0 ms**. Copy identity does not supply a tolerance: the refinement also requires equal recomputed metrics for the changed copy choice, independently legal complete paths, unchanged turn costs and weight per metre, strict same-QueryGraph comparison with unmodified Dijkstra, and agreement with the originally observed matrix/reference outputs. An explicit test rejects unrelated identical parallel edges and copies created without reused geometry.

The targeted regional replay passed all **ten** previously unexplained cells, with `changed_copy_edges_have_equal_metrics=true` in every case and **zero remaining unexplained differences**. It verified the original PBF/model checksums and graph node/edge counts and recomputed the actual paths on a fresh import. No production routing algorithm changed and the entire regional matrix was not repeated. Together, the original 2,592-cell run and targeted replay account for all 51 observed out-of-threshold regional differences.

Two earlier diagnostic replay attempts exhausted the default **190 MiB** forked test heap. Their incomplete JSON snapshots and OOM logs are retained; they are not counted as passing tests. Diagnostic helpers now have source-row scope so predecessor caches are released between sources. The successful targeted replay used a **2 GiB** fork, confirmed by captured `jcmd` VM flags, for the **500,226-node / 636,908-edge** regional import. The replay took **22.987 seconds** in its JUnit report; this is validation runtime, not a matrix latency benchmark. The encompassing focused run passed **18 tests**: ten search tests, six engine tests, one copy-identity test and one regional replay.

The replay pinned the source PBF SHA-256 `11f9b3f5b1d63cdf107f5804bd46131929dc2f13a1bd2837c6611429d8b9e4d3`, the unchanged `car.json` and `foot.json` model checksums, the original FAIL report SHA-256 `9541227d34228415d92cb53578a482b7ef4ee29724e0589c9be72f11ed9f8ed3`, and the original JSONL SHA-256 `a405b29275034ee81169f5026854a5cf061b39822e5eeffc91b4c0ddc034e457`. Fresh-import graph/profile fingerprints are recorded separately in the reconciliation. Each replay requires all legality, copy identity, turn/unit-weight stability, equal changed-copy metrics, same-QueryGraph oracle and observed-output checks to pass; these ten cases passed every check.

The later HTTP comparison against default CH routing has its own [path and shortcut-storage proof](ch-weight-notes.md), separate from these QueryGraph split/copy diagnostics. It accounts for all 38 Manchester and 184 Monaco flagged indexed cells with no unexplained case, retaining the original HTTP reports and their capped historical evidence scope. It uses exact whole-path metric equalities and predicts every selected shortcut's specific stored weight; it does not widen a global tolerance.
