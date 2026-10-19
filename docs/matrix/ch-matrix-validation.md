# Experimental CH matrix validation

The public `ch_experimental` backend preserves the validated flexible matrix contract by running an independent flexible certificate for every searched origin. It always returns the certificate's weight, elapsed time, distance and null mask. The raw bucket result is a candidate, and its optimality is explicitly unguaranteed. `auto` remains `flexible`; turn-cost car requests explicitly fall back to flexible routing.

The [design note](ch-matrix-design.md) explains directed composition, virtual points, shortcut metric expansion, precision limits, resource bounds and metadata. Existing stage 1–5 reports and frozen proofs remain historical evidence; they have not been overwritten or silently relabelled as CH milestone tests.

## Current gates

| Gate | Status | Evidence |
|---|---|---|
| C1: candidate algorithm and precision failure case | PASS: 8 focused tests, repeated in the initial full reactor | `BucketCHMatrixTest` in the [initial snapshot](ch-matrix-full-verification-initial.json) |
| C2: public certificate, selection, limits and request isolation | PASS: 5 focused tests, repeated in the initial full reactor | `MatrixCHIntegrationTest` in the [initial snapshot](ch-matrix-full-verification-initial.json) |
| C3: HTTP contract and fallback | PASS: 69 focused tests, repeated in the initial full reactor | `MatrixResourceTest` in the [initial snapshot](ch-matrix-full-verification-initial.json) |
| C4: complete Maven reactor regression | Initial run PASS; final run still required after diagnostic tests freeze | [Initial full snapshot](ch-matrix-full-verification-initial.json): 3,298 cases, 3,067 passed, 231 skipped, zero failures/errors |
| C5a: certified public matrices and strong in-process comparisons | Final analysis pending | Exact flexible equality, all measured repetitions, fixed workload/sample coverage and independent plain-Dijkstra checks |
| C5b: ordinary CH baseline accuracy differences | Original failures preserved; scoped review in progress | Fresh case-bound path/objective evidence; no exact-rational CH optimum claim |
| C5c: raw bucket candidate discrepancies | Fresh actual-path replay passed; final analyzer/review binding pending | Original timed paths were not captured; actual unchanged-search replays and individual raw outcomes remain visible |
| C6: final packaged JAR smoke | NOT YET RUN | Existing isolated Monaco configuration, actual captured HTTP responses, then verified server shutdown |
| C7: source, artifact and evidence binding | NOT YET COMPLETE | Final source commit, fresh full verification, JAR/class/model checks and release provenance |
| Automatic backend promotion | DEFERRED | `auto` selects flexible; no raw-candidate exactness claim |

The focused runs completed with zero failures, errors or skips in those three suites. Their local build logs are `audit/matrix-ch-buckets-tests-final.log`, `audit/matrix-ch-integration.log` and `audit/matrix-ch-http-tests.log` in the workspace outside this repository. The same 82 cases passed again in the initial full reactor run.

The initial full `mvn -B verify -Dmatrix.fixture.report=../docs/matrix/ch-matrix-fixture-validation.json` finished at `2026-10-19T23:27:36+01:00` in `04:33 min`; the execution tool returned exit code 0. All 11 declared reactor entries succeeded. The freezer checked all 256 current XML suites against the exact class-level results in that log and the derived run interval. Its output SHA-256 is `08d64b2aae068c267f1b3d72b19a979219e774ba9938086301a4f076ad781238`; the corresponding log SHA-256 is `3e1f63fa16cfea7d2944df31247326980d5400405c7ab530af279db13e61fea1`.

The initial snapshot records the new `MatrixCHHttpAuditTest` and `MatrixCHMatrixBenchmarkTest` suites as one skipped case each. This is the actual default-run representation, not an assumption based on their parameterized opt-in execution counts. Historical opt-in audits and upcoming comparison diagnostics remain separate evidence.

## Candidate tests and the reason for certification

[BucketCHMatrixTest](../../core/src/test/java/com/graphhopper/routing/matrix/BucketCHMatrixTest.java) checks directed independent metrics, disconnected cells, twenty seeded directed base graphs, same-edge virtual points, batching, twenty seeded directed QueryGraph chains, equal-objective alternatives, interruption, deadlines, aggregate preparation limits and the high-degree metric-cache cap. QueryGraph chain cases deliberately isolate virtual-overlay behavior without shortcuts; they do not claim exhaustive randomized testing of every shortcut/virtual combination.

The executed `floatWitnessCanLoseOriginalObjectiveAndCandidateMustBeCertified` test uses two directed alternatives. The shorter route has two edges weighted `10000.0001`; the longer has two weighted `10000.0002`. Existing CH preparation casts all four to float `10000.0`, allowing the longer route to witness away the shortcut needed for the original optimum. Even though candidate search expands shortcuts and uses their original objective, its raw result is weight `20000.0004`, time `300 ms`, distance `300 m`; independent Dijkstra obtains `20000.0002`, `30 ms`, `30 m`. This is a demonstrated topology failure, not just a difference in printed precision. It prevents an uncertified or automatically selected CH-only backend in this milestone.

Equal objective does not imply equal time or distance. The candidate tie test requires one internally consistent legal metric triple rather than mixing metrics from alternative paths. The public backend's independent certificate always determines the returned triple and therefore preserves flexible tie behavior.

## Public integration and HTTP checks

[MatrixCHIntegrationTest](../../core/src/test/java/com/graphhopper/routing/matrix/MatrixCHIntegrationTest.java) checks real prepared Monaco data, exact equality with flexible metrics/hints, genuine reverse/forward bucket work, origin/target deduplication counters, certificate counters, combined visited-state accounting and unchanged `auto` selection. It also checks explicit turn-cost/no-preparation fallbacks, invalid-axis nulls and `fail_fast`, whole-request search-limit failures, interruption recovery, concurrent request-local state and an unchanged loaded-graph fingerprint.

[MatrixResourceTest](../../web/src/test/java/com/graphhopper/application/resources/MatrixResourceTest.java) runs the strict raw-JSON HTTP contract against the new algorithm option as well as the existing backends. Its 69-case focused run includes actual experimental node-profile selection, car fallback, output shape/order/duplicates, selective arrays, invalid snaps, nulls and input restrictions. A successful fallback is recorded as `algorithm: flexible` with the requested backend and reason retained; it is not counted as an accelerated CH matrix measurement.

`corrected_cells` counts every candidate-versus-certificate binary triple/reachability difference by distinct raw origin coordinate and destination column, before duplicate-origin scatter. It includes floating-point association and equal-cost alternatives, so the count alone does not establish how many raw paths were suboptimal.

## Full regression evidence freezer

Run the following from the repository root in cmd.exe after the corresponding full Maven build finishes, before any later Maven command replaces its XML:

```bat
python scripts\snapshot-matrix-verification.py --log ..\audit\matrix-ch-full-verify.log --output docs\matrix\ch-matrix-full-verification-initial.json --command mvn -B verify -Dmatrix.fixture.report=../docs/matrix/ch-matrix-fixture-validation.json
```

The [freezer](../../scripts/snapshot-matrix-verification.py) uses Python's standard library. It rejects an existing output path, an incomplete/nonfull reactor log, failed/skipped reactor modules, XML suites absent from the exact log, disagreement between log/XML counts or actual testcase outcomes, and report timestamps outside the logged build interval. It rechecks report/log bytes during collection. Conditional tests count as skipped exactly as recorded in XML, not as passed. The snapshot records exact caller-supplied Maven argv, suite/report hashes, timestamps, every testcase outcome, totals and worktree state. A process exit code is not invented from a log; the Maven success marker, complete reactor summary and matching current XML provide the recorded success evidence.

Eleven reproducible [standard-library guard checks](../../scripts/test_snapshot_matrix_verification.py) passed, covering accepted current evidence, warning-level summaries for skipped suites, full-width reactor names without padding dots, and rejection of old or later XML, mismatched counts/outcomes, incomplete builds, partial reactors, stale extra suites and missing current XML. An attempted write to the existing initial snapshot was also rejected without changing it. These checks exercise the evidence recorder and are separate from Maven product tests. Run them with:

```bat
python scripts\test_snapshot_matrix_verification.py
```

The new `CHMatrixPairwiseAudit` helper and four `CHMatrixPairwiseAuditTest` methods were added after the initial full build's core compilation. The initial snapshot must not be cited as proof that these later diagnostics executed. Their compilation/execution and output require separate evidence, followed by a final full reactor run once the code is frozen. The final run receives its own log and `ch-matrix-full-verification.json`; the initial snapshot remains intact. A later diagnostic run must not overwrite or inflate the earlier frozen full-reactor counts.

The default fixture report destination was also moved into the Maven build output so an ordinary future `mvn verify` does not overwrite historical documentation. The final full build explicitly writes `ch-matrix-fixture-validation-final.json`; the initial `ch-matrix-fixture-validation.json` remains a separate run artifact.

## Known baseline and raw-candidate accuracy differences

The new Monaco comparison found 16 ordinary CH cases that failed the strict original-rational-objective proof. Those failures remain failures in the original experiment and witness files. Fifteen expanded CH paths and their flexible alternatives have the same sequential IEEE-754 double total while their exact rational sums differ; one has a one-ULP larger CH double total. None is an exact rational optimum tie. The observed alternatives also have different actual time and distance.

The case-bound posthoc review records the actual directed paths, sums and original scalar observations. Cached float32 branch sums reverse the ranking for the affected alternative, but the particular historical witness decision or omitted shortcut causing selection has not been replayed or proven. This supports a disclosed ordinary-CH accuracy limitation in the performance comparison; it does not convert the original failed optimum checks into passing checks or supply a universal tolerance waiver.

Raw bucket candidates also require independent treatment. Their actual paths were not captured in the original measurement. The first context ledger linked legal ordinary-CH paths; matching those paths' times or association bounds alone did not establish which edge sequence the bucket search selected. Subsequent diagnostics replayed the actual unchanged `BucketCHMatrix` private search and reflected its final labels, buckets and joins to identify its selected CH arcs. These are fresh replay captures, not captures of the original timed repetitions. Source/class manifests and original scalar/coordinate bindings remain explicit.

The Manchester replay covers 280 indexed flags with 234 unique actual candidate-path proofs. All represent the same physical route as independent Dijkstra. The captured paths contain 457 occurrences of removed original whole-edge shortcut leaves across four directed keys; replacing those leaves with the actual complete-QueryGraph virtual chains makes the represented path's exact objective, distance and time equal the independent oracle. Observed raw-minus-oracle differences range from approximately `-0.000269074` to `+0.000070476` profile-weight units and `-0.000373714` to `+0.000097883` metres; elapsed milliseconds are unchanged. The concrete wrapper-bug hypothesis was ruled out and no production change or benchmark rerun was made on that basis.

The 16 Monaco candidate replays capture the actual alternative branch and its small nonzero rational objective difference, rather than borrowing an ordinary-CH path as a substitute. The public backend's complete certificate rejects every differing candidate triple and returns flexible values. Final analysis acceptance needs exact indexed raw-flag coverage, whole-witness verification and root approval of the specific candidate-reconciliation/witness/rejection hashes. Neither the samples nor their explanations establish candidate-only exactness for all possible inputs; the adversarial float-witness test still rules out that claim.

The complete-QueryGraph representation can also differ slightly from a shortcut's original whole-edge representation. `QueryGraphWeighting.calcEdgeWeight` and `calcEdgeMillis` delegate directly to the underlying node-profile weighting; wrapping alone does not change these costs. Upstream QueryOverlayBuilder recomputes virtual segment distances from geometry, while an original shortcut leaf retains its stored whole-edge distance. Any particular discrepancy must be tied to its actual graph/path/geometry evidence. The certificate preserves the flexible output irrespective of this raw-candidate representation difference.

## Final artifact smoke and release binding

The [live smoke helper](../../scripts/smoke-ch-matrix-release.py) targets an already running `run-matrix.cmd` Monaco server on localhost ports 8997/8998; it never launches or stops Java. It sends the same duplicate 3×3 request through foot experimental, flexible and `auto` backends, then car experimental/flexible requests. Required checks include exact metric arrays/hints, actual selection and fallback reasons, independent certificate/search counters, combined visited states, duplicate scatter, zero-length cells, units and the final build identity. Separate invalid-snap and `fail_fast` requests check a null row and HTTP 400; an unsupported option is also rejected. Optional `/route` capture checks an existing endpoint.

The helper preserves literal request/response bytes and their hashes. Its first passing report still records `server_stopped: false`. Only a later `--confirm-stopped` phase, after external shutdown, may record both application/admin ports refusing connections on every resolved localhost address. It verifies that JAR and configuration bytes stayed unchanged and preserves the original pre-shutdown report.

GraphHopper's actual embedded build identity is `com/graphhopper/gitinfo`, containing a comment followed by five positional fields; dependency `git.properties` files are not its authority. The helpers follow the selected-version Constants/Helper line-reading behavior, require the full final source commit and clean-build flag, and record filtered version/builddate values and resource hashes. Seven [standard-library identity checks](../../scripts/test_ch_release_identity.py) passed, including CR/LF handling, an empty commit-message field, and rejection of dirty, mismatched, unfiltered or malformed identities. These small recorder checks run separately from Maven product tests:

```bat
python scripts\test_ch_release_identity.py
```

The [release recorder](../../scripts/record-ch-matrix-release.py) requires the final full-verification HEAD to match the specified source commit and checks that production/build/test inputs still equal that commit, including rejection of untracked inputs. It checks current production class bytes against both benchmark manifests and the packaged JAR, configured model bytes, archive CRC, the JAR's complete source commit, original evidence hashes, scoped root approvals and actual captured smoke response bodies. It accepts the analyzer status `MEASURED_WITH_SCOPED_BASELINE_ACCURACY_DIFFERENCES` and explicitly rejects an ordinary-CH exact-rational-optimality claim.

Graph/profile fingerprints include build identity. The final clean source commit therefore has different fingerprints from the earlier measured dirty base build even with unchanged graph storage. The recorder preserves both identities and binds actual prepared-cache file SHA manifests, matching production class bytes and configured models; it does not rewrite measured fingerprints or pretend they match. The validation text is finalized before the release recorder hashes it, avoiding a circular document/release checksum dependency.

## Comparison acceptance and limitations

Certified public matrices must equal shared flexible matrices exactly on the same complete QueryGraph, including integer times and null placement, and remain stable across every measured repetition. Raw candidate results require their own full outcome/discrepancy ledger against independent flexible Dijkstra. Strong in-process ordinary prepared-CH routing is a separate baseline with its actual algorithm verified. Every new scalar/path mismatch needs current evidence; the earlier HTTP CH audit does not automatically reconcile new benchmark coordinates.

Timing reports must include mandatory certification, candidate metric expansion, output work and all request-local construction. Raw phase timings must remain labelled as phases of a certified request. Metric-expansion time is nested within bucket/candidate phases and must not be added again. Allocation volume, cache entry counts, retained memory, heap-pool peak sums and RSS are different quantities. Existing prepared-cache loading is measured separately, and its original import/preparation costs remain explicitly outside warm request timings.

This milestone makes no edge-based CH matrix, national-scale, concurrent-throughput or uncertified CH-only exactness claim. Mandatory certification is an operational cost and can make this experiment slower than the existing flexible backend; measured comparisons determine that outcome, not the use of CH alone.
