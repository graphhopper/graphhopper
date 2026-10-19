# HTTP CH weight reconciliation

The original HTTP benchmark reports, requests and sample CSVs are unchanged. Their reports have `status: MEASURED` and retain all original weight mismatch counts. Separate `ch-weight-reconciliation.json` ledgers explain those differences using the already prepared server graphs; they do not turn the original timing run into a new run.

| Prepared graph | Measured samples | Aggregate weight flags | Flagged indexed cells | Unique profile/coordinate pairs replayed | Retained original issue records checked | Unexplained indexed cells |
|---|---:|---:|---:|---:|---:|---:|
| Manchester | 240 | 760 | 38 | 20 | 520 | 0 |
| Monaco | 160 | 3,680 | 184 | 184 | 300 | 0 |

The Manchester ledger is [regional-http-benchmark/ch-weight-reconciliation.json](regional-http-benchmark/ch-weight-reconciliation.json); the Monaco ledger is [ch-weight-reconciliation.json](ch-weight-reconciliation.json). Each indexed cell links its profile, workload, indices, original reported CH/matrix weights and original report SHA-256 to its complete fresh path proof. Loaded graph, profile and build fingerprints must equal the original HTTP metadata. The helper loads each prepared graph with writes disabled, without reimporting or preparing it.

## Actual paths and exact metrics

The replay uses the default CH factory and captures the exact forward/backward search entries handed to the normal upstream path extractor. The actual algorithms are `dijkstrabi|ch` for foot and `astarbi|ch|edge_based|no_sod` for car. It retains both the fully expanded CH path and a plain Dijkstra path on the identical two-point QueryGraph, with directed edge keys, directed endpoints, edge weights, actual predecessor turns, milliseconds and distances. Every edge/turn is legal and finite, and both complete paths have the requested endpoints.

For all **204 unique pairs**, summing the actual double edge and turn terms as exact `BigDecimal(double)` values gives exactly equal CH-expanded and plain-Dijkstra objective sums. The distance-term sums are also exactly equal, the integer millisecond totals are exactly equal, and each aligned edge has equal endpoints and actual edge/turn metrics. Thus no weight tolerance establishes an alternative path's equivalence. The matrix's actual combined-QueryGraph result is separately reconstructed and checked through `MatrixPrecisionDiagnostics`; any split effects retain their own path evidence.

Manchester has three distinct foot pairs, linked to nine indexed cells, for which CH and Dijkstra select different parallel edges. The diverging directed key choices are **738999 / 738997**, **499926 / 499925**, and **738998 / 738996**. Both full paths and the differing segments are retained. Every changed segment has exactly equal directed endpoints, edge weight, milliseconds and distance; its actual incoming and following turn contributions are also equal. Exact whole-path sums prove the alternatives have the same objective as plain Dijkstra. These roads are not classified as upstream restriction copies. The earlier canonical-only rejection ledger is retained as `regional-http-benchmark/ch-weight-reconciliation-canonical-only-failure.json`. All Monaco expanded canonical paths match plain Dijkstra.

## Specific shortcut storage accounting

The upstream CH preparation graph caches directional base weights as `float`, while prepare-shortcut weights remain `double`. `CHStorage` stores finite shortcut weights in 0.001-weight-unit increments, using `Math.round(max(0.001, weight) * 1000) / 1000d`. Stored quantized child weights are not fed back into preparation. The proof therefore accounts for the float conversion of each actual leaf, preparation arithmetic, and the one storage conversion of each shortcut selected by the query.

For each selected shortcut, `CHWeightAudit` uses upstream `ShortcutUnpacker` to retain all actual directional base-edge occurrences and internal turns. It computes exact sums of the original terms and of the float-converted preparation terms. It derives an outward interval for preparation arithmetic from that particular expansion: `n-1` additions for a node-based binary expansion, or the source-accounted conservative `8*n*n` operation count for an edge-based expansion, including initial source-turn addition/subtraction. Its error magnitude includes those possible source turns. Saturation, illegal terms and nonfinite values fail the proof.

Both endpoints of this pre-storage interval must encode to **one identical stored value**, and that predicted value must exactly equal the actual selected shortcut's stored weight. Every selected shortcut passes: **157 selected shortcut occurrences** across the 20 Manchester pairs and **649** across the 184 Monaco pairs. This is a prediction of each particular stored value, rather than acceptance because an observed discrepancy falls below a common bound.

The captured query chains must also reproduce every visited entry and the final reported CH weight exactly with the upstream addition order and actual connecting/meeting turns. An exact decimal ledger then telescopes:

`reported CH weight - exact expanded terms = sum(selected stored shortcut - its exact expanded terms) + exact query-addition residual`.

For Manchester foot pair 0, the expanded/plain objective is **644.8518299683183** and CH reports **644.8512299683184**. The exact selected-shortcut residual sum begins **-0.00059999999992300012598839265**; the query-addition residual is **3.9968028886505635e-15**. Their exact sum accounts for the CH discrepancy. The normal route serializer's `Helper.round6` gives **644.85123**, exactly matching the retained original issue value. Every retained original CH issue scalar matches this serializer rule exactly; every retained matrix scalar matches the fresh matrix output.

The largest observed CH-reported minus unrounded-expanded absolute differences in these fresh path proofs are **0.0011815584415444391** for Manchester and **0.0016390909091228423** for Monaco. These are dataset observations, not universal tolerances or permission to accept other CH discrepancies.

## Evidence scope and reproduction

The original HTTP benchmark retained at most 100 individual issue records per workload/profile group. Repeat zero contains all flagged indexed cells. All **520 Manchester** and **300 Monaco** retained issue records are checked, and all aggregate counts are accounted for by the indexed cases times 20 repeats. The recorded per-group weight checksums are stable across all 20 samples. A stable aggregate checksum cannot exclude compensating per-cell changes in omitted records: those omitted original per-repeat scalars and paths were not captured and are not claimed to have been directly replayed. Fresh independent paths were reconstructed for the 204 unique coordinate/profile pairs.

The original absence of time, distance or null flags establishes only the original HTTP thresholds (1 ms and 0.002 m), not exact equality of uncaptured raw responses. Exact path metric equality is a result of the separate offline replay. The replay does not measure route or matrix latency and does not change production routing code, search tolerances, or the original benchmark samples.

From `cmd.exe` in the checkout, the opt-in audit can be repeated against the retained prepared graphs:

```bat
mvn -B -pl core -am -Dtest=MatrixCHHttpAuditTest -Dsurefire.failIfNoSpecifiedTests=false -Dmatrix.ch.audit=true -Dtest.heap.max=2g test
mvn -B -pl core -am -Dtest=MatrixCHHttpAuditTest -Dsurefire.failIfNoSpecifiedTests=false -Dmatrix.ch.audit=true -Dmatrix.ch.audit.directory=docs/matrix -Dmatrix.ch.audit.graph=target/matrix-monaco-server -Dtest.heap.max=2g test
```

The report directory, prepared graph, requests and ledger destination can be configured with `matrix.ch.audit.directory`, `matrix.ch.audit.graph`, `matrix.ch.audit.requests` and `matrix.ch.audit.output`. Request-file selection otherwise uses `requests.json`, falling back to `benchmark-requests.json`. Original artifact SHA-256 values are checked again before writing the separate ledger.

The final Manchester and Monaco proof runs each passed one test with no failures, errors or skips (JUnit durations **5.453 s** and **3.683 s**). Their XML reports and complete Maven logs are retained in the outer workspace `audit` directory as `matrix-ch-http-regional-final.*` and `matrix-ch-http-monaco-final.*`. These durations describe offline proof execution, not routing benchmarks.

The full 3,276-test Maven verification gate predates only the later test-only CH diagnostics and benchmark-recorder changes. Production routing source and the fat JAR used for HTTP timing were unchanged by this reconciliation. A final portable control ran the exact diagnostic test selection without `matrix.ch.audit`: **BUILD SUCCESS, one test skipped**, zero failures/errors. Its log and XML are retained as `audit/matrix-ch-http-default-control.*` in the outer workspace. Thus a normal build does not try to load external prepared graphs for this opt-in class.
