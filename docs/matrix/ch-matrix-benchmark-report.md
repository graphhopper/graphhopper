# Certified experimental CH matrix comparisons

The certified experimental CH backend was slower than flexible in 16 of 16 measured workloads; `auto` stays flexible. Manchester's 100x100 took 41.777 seconds versus 40.848 seconds for flexible and 10.214 seconds for ordinary CH calls. It beat ordinary per-cell CH calls in some Monaco and duplicate workloads. The matrix measurements used the larger experiment limits described below.

`CH` means **Contraction Hierarchies**. Preprocessing contracts graph nodes and adds shortcuts so later searches can use a much smaller upward graph. This experiment adds target buckets shared across origin searches for static node-based profiles.

The experimental API backend also runs a complete flexible verification row and corrects its candidate results. The timings below include this correctness check. Candidate-only phase timings are retained in the CSV/JSON and do not describe an independently exact API backend.

The benchmark uses one serial request stream, one matrix worker and one admission slot, with a 600-second deadline and 2,147,483,647-state limit. These are explicit experiment limits in the hash-bound source, rather than the shipped defaults of 30 seconds, 1,000,000 states and two admission slots. A completed measurement does not show that the same request completes under the shipped defaults.

| Dataset | Workload | Certified CH median / p95 ms | Flexible median ms | Ordinary CH calls median ms | Flexible / certified CH | CH calls / certified CH |
|---|---:|---:|---:|---:|---:|---:|
| monaco | 100x10 | 84.954 / 92.789 | 75.273 | 197.222 | 0.89x | 2.32x |
| monaco | 100x100 | 108.053 / 126.660 | 76.456 | 1893.535 | 0.71x | 17.52x |
| monaco | 10x10 | 11.848 / 14.252 | 8.311 | 21.632 | 0.70x | 1.83x |
| monaco | 10x100 | 24.034 / 37.597 | 12.182 | 200.324 | 0.51x | 8.34x |
| monaco | 1x10 | 5.389 / 7.850 | 1.935 | 5.009 | 0.36x | 0.93x |
| monaco | 1x100 | 21.487 / 31.350 | 7.706 | 29.577 | 0.36x | 1.38x |
| monaco | 1x3 | 5.911 / 9.626 | 2.519 | 2.184 | 0.43x | 0.37x |
| monaco | duplicate_100x100 | 5.016 / 5.957 | 3.640 | 1543.224 | 0.73x | 307.64x |
| manchester | 100x10 | 846.116 / 895.728 | 78.847 | 985.641 | 0.09x | 1.16x |
| manchester | 100x100 | 41776.722 / 46885.145 | 40848.204 | 10213.587 | 0.98x | 0.24x |
| manchester | 10x10 | 649.603 / 668.972 | 15.603 | 98.124 | 0.02x | 0.15x |
| manchester | 10x100 | 5395.760 / 5629.043 | 4779.610 | 1048.505 | 0.89x | 0.19x |
| manchester | 1x10 | 540.329 / 569.487 | 4.506 | 9.263 | 0.01x | 0.02x |
| manchester | 1x100 | 1208.647 / 1287.234 | 513.531 | 110.200 | 0.42x | 0.09x |
| manchester | 1x3 | 611.567 / 635.821 | 23.622 | 4.899 | 0.04x | 0.01x |
| manchester | duplicate_100x100 | 572.134 / 591.717 | 4.976 | 9790.477 | 0.01x | 17.11x |

A ratio above one means the certified experimental backend was faster for that comparison. These are complete serial in-process request medians; JSON serialization is measured separately. Ordinary CH runs one routing call and extracts a path for each cell, so its cost includes more than its core search algorithm.

| Dataset | Workload | Target buckets ms | Candidate rows ms | Flexible certificate ms | Original-edge metric expansion ms (overlaps CH phases) | Binary-triple corrections |
|---|---:|---:|---:|---:|---:|---:|
| monaco | 1x3 | 2.489 | 1.171 | 1.607 | 1.610 | 2 |
| monaco | 100x100 | 6.272 | 21.343 | 73.023 | 1.883 | 8322 |
| monaco | duplicate_100x100 | 0.715 | 0.686 | 2.961 | 0.733 | 360 |
| manchester | 1x3 | 291.237 | 262.521 | 20.975 | 565.673 | 3 |
| manchester | 100x100 | 444.591 | 769.377 | 40564.844 | 551.347 | 4832 |
| manchester | duplicate_100x100 | 282.104 | 289.525 | 1.929 | 539.401 | 240 |

The phase table shows medians; separate phase medians need not sum to the median request. Original-edge metric expansion is nested within the bucket/candidate phases and must not be added to them. Corrections count any candidate-versus-certificate binary weight/time/distance difference (including harmless floating-point association and equal-cost route differences), plus reachability differences; they do not by themselves count suboptimal routes. Counts apply to each searched distinct origin and raw destination column before duplicate-origin rows are scattered.

| Dataset, 100x100 | Flexible calling-thread allocation MiB | Certified CH calling-thread allocation MiB | Ordinary CH calls allocation MiB | CH request bucket entries | CH request metric-cache entries |
|---|---:|---:|---:|---:|---:|
| monaco | 38.690 | 61.020 | 805.199 | 4978 | 2397 |
| manchester | 10412.277 | 11339.208 | 5692.645 | 49619 | 19446 |

Allocation medians count cumulative bytes allocated by the calling thread during the complete request. They exclude JSON serialization and other threads, and do not measure retained heap, peak process memory or RSS. Buckets and exact edge-metric caches are rebuilt for each request; their entries are counts rather than byte-size estimates.

Every certified experimental matrix metric cell exactly matched the shared flexible matrix on the same overlay, and also passed the fixed original thresholds (weight 1e-7 + 1e-10 relative, distance 0.002 m, duration 1 ms). The harness checks every metric cell for exact stability across repetitions and checks turn-aware car fallback separately. This measurement gate supplements the dedicated algorithm tests; it does not prove all possible paths.

Monaco: 1,217 nodes, 1,619 edges; 5 warmups and 20 measured repetitions per backend/workload, 480 samples. The 100x100 axes contain 100 distinct raw origin coordinates and 100 distinct raw destination coordinates; the explicit duplicate workload contains five of each. The existing prepared cache loaded in 753.4 ms and its file bytes were verified unchanged. Cache loading is not CH preprocessing: no new global import or CH preparation occurred, and cold CH preparation time was not remeasured. Existing foot CH node/shortcut files occupy 2.000 MiB on disk; this is disk storage, not memory use. Request bucket construction is separately measured in the phase table. Seed-shuffled imported tower-node coordinates; first 100 are used, plus a nearest-ten local axis and exact duplicates. JVM arguments: `['-Xmx2g', '-Xms190m', '-Duser.language=en']`. [Raw evidence](ch-matrix-benchmark-monaco/metadata.json).

Manchester: 500,226 nodes, 636,908 edges; 2 warmups and 5 measured repetitions per backend/workload, 120 samples. The 100x100 axes contain 91 distinct raw origin coordinates and 91 distinct raw destination coordinates; the explicit duplicate workload contains five of each. The existing prepared cache loaded in 855.7 ms and its file bytes were verified unchanged. Cache loading is not CH preprocessing: no new global import or CH preparation occurred, and cold CH preparation time was not remeasured. Existing foot CH node/shortcut files occupy 14.000 MiB on disk; this is disk storage, not memory use. Request bucket construction is separately measured in the phase table. 100 imported tower nodes nearest Manchester town-hall coordinates, shuffled with the fixed seed. 1x3 uses supplied city coordinates. Local-node workloads do not represent the entire extract's route-length distribution. JVM arguments: `['-Xmx2g', '-Xms190m', '-Duser.language=en']`. [Raw evidence](ch-matrix-benchmark-manchester/metadata.json).

| Workloads exceeding a shipped default | Maximum measured settled states | Maximum engine total ms | Exceeded default |
|---|---:|---:|---|
| manchester / 10x100 / flexible | 4,477,250 | 4798.2 | 1,000,000-state budget |
| manchester / 10x100 / ch_experimental | 4,532,689 | 5628.9 | 1,000,000-state budget |
| manchester / 100x100 / flexible | 38,056,637 | 44970.2 | 1,000,000-state budget, 30-second deadline |
| manchester / 100x100 / ch_experimental | 38,155,878 | 46885.0 | 1,000,000-state budget, 30-second deadline |

These rows completed only with the experiment's larger budgets. The limits are enforced by the API; increasing them is an explicit operator choice and does not remove their compute cost.

Manchester's 100x100 contained 1,138 null cells and visited 38,056,637 states for flexible and 38,155,878 for certified CH. Inference from the target-set search termination: unreachable targets can keep a row searching until its reachable component is exhausted, contributing to this cost. This local sample does not establish a regional or national workload distribution.

The untimed raw bucket-CH candidate pass produced 296 flagged cells versus independent upstream Dijkstra on the identical complete QueryGraph. Each flagged raw triple is rejected by the mandatory certificate; returned API cells exactly match flexible and pass the independent oracle thresholds. Full candidate and independent outcomes, every flag, and raw statistics are preserved in each `raw-candidates.json`, with individually bound `raw-candidate-reconciliation.json` records. The original timing and initial oracle passes did not capture candidate edge paths. A later read-only replay of the unchanged production algorithm captured 250 actual selected candidate paths covering all 296 indexed flags; `candidate-path-reconciliation.json` and `candidate-path-witnesses.jsonl.gz` bind every replay to the original scalars, source/class/cache identities and exact root approvals. This is fresh replay evidence, rather than a capture of every timed path. Candidate-only exactness is not claimed.

Manchester's 234 fresh candidate paths follow the same physical routes as the independent oracle. Explicitly replacing removed original whole-edge shortcut leaves with the actual shared-overlay virtual chains reproduces the oracle's exact weight, distance and time sums. The 457 recorded leaf replacements show the individual stored-distance/geometry-length and per-piece rounding differences. Monaco retains the separately described alternative-route cases. These are scoped candidate accounting and rejection records; every returned API cell still comes from the mandatory flexible certificate.

Ordinary pairwise CH versus flexible produced 32,337 flagged first-outcome cells across these workloads. Every flag and full outcome is preserved and linked to 18,827 fresh selected-path proofs in `pairwise-ch-reconciliation.json` and `pairwise-ch-witnesses.jsonl.gz`. The fresh proof reconstructs the actual default CH search trees, original-edge expansion, independent pair/shared Dijkstra paths and the specific shortcut arithmetic. The independent Python analyzer checks exact rational path sums and source-derived float/shortcut rounding arithmetic, all indexed coverage and scalar/coordinate bindings. Earlier CH precision reports were not reused as waivers. Timed repetitions were checked for exact metric-cell stability; the proof paths themselves are fresh replays, not captures of every timed repetition.

The original proof gate remains FAIL for 16 cases with nonzero exact rational CH-versus-Dijkstra objective differences. 15 have identical recomputed sequential IEEE-754 double labels despite their nonzero exact deltas; 1 case has a one-ULP larger recomputed ordinary-CH label. The independently legal alternatives also have different actual time and distance. New case-bound posthoc ledgers and explicit root SHA approvals permit a scoped performance comparison while preserving those accuracy differences; they do not turn the original FAIL into an exact-optimality pass. Float32 branch-cost ranking is recorded as context, and the particular historical contraction/witness decision causing selection was not replayed or proved.

The samples retain all production counters and phase timings, current-thread allocation deltas, serialization costs, reachable/null counts and heap-pool peak sums. Allocation counts measure cumulative bytes allocated by the calling thread; they are not retained memory, peak process memory or RSS. Dataset source fingerprints, prepared-cache hashes, source hashes, JVM arguments and evidence hashes are recorded in each metadata file.

The experiment is limited to a serial request stream on the bundled Monaco fixture and a local sample within the checksum-pinned Greater Manchester extract. It provides no concurrent-throughput, national-scale, edge-based CH matrix, or unverified CH-only exactness claim.

To replay the measurements in cmd.exe, first prepare the existing `target/matrix-monaco-server` and `target/matrix-manchester-server` caches using the documented run configurations, then stop both servers. The harness loads these caches with writes disabled and refuses to overwrite a prior experiment directory. These commands write separate replay artifacts:

```bat
mvn -B -pl core -am test -Dtest=MatrixCHMatrixBenchmarkTest -Dsurefire.failIfNoSpecifiedTests=false -Dtest.heap.max=2g -Dmatrix.ch.benchmark=true -Dmatrix.ch.benchmark.dataset=monaco -Dmatrix.ch.benchmark.output=docs/matrix/ch-matrix-benchmark-monaco-replay
mvn -B -pl core -am test -Dtest=MatrixCHMatrixBenchmarkTest -Dsurefire.failIfNoSpecifiedTests=false -Dtest.heap.max=2g -Dmatrix.ch.benchmark=true -Dmatrix.ch.benchmark.dataset=manchester -Dmatrix.ch.benchmark.output=docs/matrix/ch-matrix-benchmark-manchester-replay
```

A measurement replay can retain a FAIL for the known ordinary-CH objective cases. New output directories require fresh case-bound diagnostic traces, rejection/posthoc ledgers and checksum-bound reviews before the strict analyzer accepts them; this report's approvals do not transfer to a new run. To recheck the shipped frozen evidence and regenerate its derivative tables:

```bat
python scripts\summarize-ch-matrix-benchmarks.py
```

[Machine-readable analysis](ch-matrix-benchmark-analysis.json) · [Summary CSV](ch-matrix-benchmark-results.csv)
