# Matrix validation evidence

Full Maven verification passes. Fixture and regional comparisons against independent flexible-routing oracles have no remaining unexplained discrepancies in their tested coverage. The recorded final release review passes with the scoped limitations below. Raw HTTP/CH comparisons retain **4440** flagged cell/repetition events and their original comparison statuses; timings do not classify them. Any accepted case classification requires explicit retained reconciliation evidence.

`mvn -B verify` exited 0 after 04:50 min, finishing 2026-10-19T19:37:30+01:00. [full-verification.json](full-verification.json) freezes per-module, phase, suite and testcase results with XML/log hashes before opt-in benchmarks overwrite live Surefire reports. Source is based on `ebb578f4e94db72e82f2f8d3bc69417758e16e1c` with intentional uncommitted changes.

| Module | Reported | Passed | Skipped | Failures/errors |
| --- | ---: | ---: | ---: | ---: |
| client-hc | 24 | 24 | 0 | 0/0 |
| core | 2585 | 2563 | 22 | 0/0 |
| example | 4 | 4 | 0 | 0/0 |
| map-matching | 0 | 0 | 0 | 0/0 |
| navigation | 31 | 30 | 1 | 0/0 |
| reader-gtfs | 305 | 100 | 205 | 0/0 |
| tools | 0 | 0 | 0 | 0/0 |
| web | 273 | 272 | 1 | 0/0 |
| web-api | 45 | 45 | 0 | 0/0 |
| web-bundle | 9 | 9 | 0 | 0/0 |
| **Total** | **3276** | **3047** | **229** | **0/0** |

The default full gate includes 18 matrix core cases, 2 DTO cases and 62 HTTP resource cases. Three opt-in matrix classes are skipped in this run: benchmarks, the regional full comparison, and the targeted regional replay. Their separate results must not be inferred from Maven's green default run.

## Baseline and fixture lifecycle

The pristine `mvn -B -pl web -am verify` run remains **FAIL**: 3,177 reported cases, 2,950 passed, 226 skipped and one `PtRouteResourceTripBasedTest` startup error from a partially removed shared MapDB cache. A cache-cleared isolated unmodified replay passed all 11 methods. The feature tests now use distinct per-run UUID caches, start after checked preparation, and stop before best-effort cleanup. Windows can retain mapped files until the JVM exits; no immediate-unmapping claim is made. See [baseline-report.md](baseline-report.md).

## Independent routing checks

The target-set search passed 20,000 differential OD comparisons over 50 deterministic seeds (730201–730250), 20–80-node directed graphs, node/edge traversal, and base/QueryGraphs with multiple virtual points. Returned paths are independently reconstructed and their edge/turn metrics recomputed. Tests also cover hand-computed metrics, turn penalties/U-turns, directed speeds, deterministic ties, zero costs, overflow, stale labels, deadlines, in-flight interruption, concurrent requests and state isolation.

The Monaco fixture compared **2,312 cells** (1,156/profile): foot 1,090 reachable/66 disconnected; car 998 reachable/158 disconnected. Its **327** out-of-threshold car differences are individually explained by geometry-derived virtual splits, with zero unexplained cases. The tight original thresholds remain unchanged. [fixture-validation.json](fixture-validation.json) retains actual paths, snaps, contributions and fingerprints; [precision-notes.md](precision-notes.md) explains the proof requirements.

## Greater Manchester coverage

The independently acquired 51,632,590-byte Geofabrik extract is pinned to SHA-256 `11f9b3f5b1d63cdf107f5804bd46131929dc2f13a1bd2837c6611429d8b9e4d3` in [regional-data.json](regional-data.json). Import produced 500,226 nodes and 636,908 edges, with static foot and car profiles, car U-turn cost 60 seconds, one matrix worker and no CH/LM preparations for the correctness run.

The original run compared **1,296 stratified cells/profile** (1,156 unique coordinate pairs/profile), plus **24 supplied-example cells**: 2,616 total. Foot had 1,091 reachable/205 null cells; car had 936 reachable/360 null cells. Of 51 differences above fixed thresholds, 41 were explained initially and ten car cases remained unexplained, so [regional-validation.json](regional-validation.json) correctly remains **FAIL**.

The later [regional-reconciliation.json](regional-reconciliation.json) replayed exactly those ten cells and passed every independent path/turn/copy/snap/output check, leaving **zero unexplained discrepancies**. It proves the actual upstream shared-geometry restriction-copy relationship and equal metrics for each changed copy; unrelated equal-looking parallel edges are rejected. No production search algorithm changed, no global tolerance was enlarged, and the complete regional matrix was not repeated. Original FAIL report/JSONL hashes are pinned by the reconciliation.

Two earlier diagnostic replays exhausted the default 190 MiB fork heap and remain incomplete evidence. The successful targeted replay used a measured 2 GiB maximum heap (captured jcmd flags) and passed one regional test within an 18-test focused run. Its 22.987-second validation runtime is not a matrix latency benchmark.

Thresholds: weight absolute 1e-7 plus relative 1e-10; independent-route distance 0.002 m and duration 1 ms. Same-QueryGraph comparisons require exact milliseconds and tighter summation bounds. Each out-of-threshold case requires legal reconstructed paths and matching observed metrics. Imported OSM warnings and restriction/access limits remain in [osm-data-limitations.md](osm-data-limitations.md).

## Release evidence and reproducibility

In-process benchmark report: **MEASURED** (960 measured samples). Recorded HTTP benchmark runs: **2**. Live HTTP smoke reports: **7** recorded. Existing endpoint checks: **PASS** (6 checks). Geometry export: **PASS**; manual geometry review: **PASS**. Actual measurements remain separate from correctness claims. The in-process benchmark scope is Monaco even though regional data is now available; request timing includes production validation and excludes post-timer harness checks. [benchmark-report.md](benchmark-report.md) provides named baseline timings, allocation volumes and any completed HTTP comparisons.

## CH reported-weight classifications

The raw HTTP reports keep every original `MISMATCH`: 184 indexed Monaco cases/3,680 repeated events and 38 indexed regional cases/760 repeated events. The [Monaco](ch-weight-reconciliation.json) and [regional](regional-http-benchmark/ch-weight-reconciliation.json) expanded-path ledgers provide 204 unique path proofs, including three regional alternative parallel-edge paths with exactly equal objectives, distance sums and milliseconds. The Java audit checks actual graph legality, selected search-tree/shortcut storage and per-index observed output. The Python adapter independently sums recorded path terms and checks exact shortcut coverage, identities and original scalar/hash bindings; it does not replace the Java graph/storage checks. Exact SHA-bound root approval is recorded separately in the final review and `http_reconciliation_reviews`.

The ledgers check 300 retained Monaco scalar records and 520 regional records. Capped diagnostics do not retain every repetition's paths/scalars. Stable aggregate CSV checksums cannot exclude compensating changes in omitted records; those historical records are not directly reverified. The absence of original time/distance/null flags establishes the original HTTP thresholds, while fresh replay checks full-precision path metrics. No production algorithm or global threshold changed.

The CH diagnostic class was added after the frozen full Maven snapshot. Separate final regional and Monaco runs each passed one test; the default control skipped exactly one conditional test with a successful build. XML/log hashes are retained in `post_snapshot_ch_precision_diagnostics`. [binary-source-binding.json](binary-source-binding.json) rechecks 40 current compiled production classes and both configured model resources against the live-tested JAR bytes; diagnostics remain outside the packaged artifact.

The [manual geometry ledger](manual-geometry-review.json) records actual browser review, candidate failures and accepted category coverage. [Live endpoint checks](live-endpoint-checks.json) retain the six foot/car route, isochrone and SPT responses. [final-review.json](final-review.json), when present, records the root reviewer's decision and reviewed artifact hash. Final-gate booleans in the JSON summary make unmet requirements explicit; no file is promoted to PASS merely because measurements exist.

Use cmd.exe and the commands in [README.md](README.md) and [audit.md](audit.md). Python tools require only the standard library; optional pandas belongs in a separate Conda environment. [validation-summary.json](validation-summary.json) contains seeds, counts, statuses, coverage, artifact hashes and pending gates. Final build artifact/configuration/runtime identity is recorded in [provenance.json](provenance.json). No existing user deployment configuration or OD files were available.
