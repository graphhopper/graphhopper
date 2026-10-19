# Progress

The user has authorized the optional CH follow-on and a review PR in their fork only.
The original G0–G4 evidence below is historical. The CH implementation has focused
coverage (8 bucket, 5 integration, 4 baseline-audit guard and 69 HTTP cases pass),
plus 600 fresh measured requests across Monaco and Manchester. Follow-on gates
are recorded in [ch-matrix-validation.md](ch-matrix-validation.md) and the
[comparison report](ch-matrix-benchmark-report.md). `auto` remains flexible;
`ch_experimental` always includes original-cost flexible certification/correction.
The fork is `hulsiejames/graphhopper`, PR base `matrix-base-11.1`; official upstream pushes
are disabled. The review PR is intended for the user to review and merge.

- G0–G3: **PASS**. G4: **PASS_WITH_SCOPED_LIMITATIONS**, following the explicit SHA-bound [final review](final-review.json).
- Pack: all files read; 26 checksum entries, 7 JSON files, 25 toy cells, 4 turn cases and 10 starter-verifier tests passed.
- The untouched wider upstream baseline retains its PT cache-startup failure. Its cache-cleared isolated replay passed 11 methods; feature PT fixtures use owned per-run UUID caches and stop before best-effort cleanup. Mapped files may linger until JVM exit.
- Full feature `mvn -B verify`: 3276 reported = 3047 passed + 229 skipped, zero failures/errors. The per-case snapshot is frozen in [full-verification.json](full-verification.json); opt-in runs do not replace it.
- Matrix coverage: 18 core, 2 DTO and 62 HTTP resource cases in the full gate; 20000 deterministic independent Dijkstra comparisons; 2312 Monaco cells with individually explained split precision cases.
- Regional correctness: 2616 original cells, including supplied examples. Initial FAIL and two incomplete low-heap replays remain preserved; ten targeted car replays explained all remaining cases through proven upstream copy relationships. No global tolerance or production routing algorithm changed.
- Benchmarks: 960 complete in-process Monaco measurements and 400 HTTP measurements (160 Monaco, 240 Manchester), with five warm-ups and twenty repetitions per group. The interrupted first recorder attempt is excluded. [benchmark-report.md](benchmark-report.md) states both gains and losses: prepared CH was faster for five of six small Manchester groups.
- HTTP reports retain all 4440 weight-only flagged events: 184 Monaco and 38 regional indexed cases. Separate approved ledgers contain 204 unique expanded-path proofs, exact objective/distance sums and milliseconds, and unique predictions for every selected shortcut. Three regional unique pairs use exactly tied alternative parallel-edge paths. Capped historical records remain a stated limitation.
- The later opt-in CH audits passed one regional and one Monaco test; their default control skipped exactly one test. This diagnostic class is separate from the earlier full-reactor snapshot.
- Live checks: 28 matrix smoke cells, six foot/car route/isochrone/SPT checks, JSON/CSV client checks and five manually reviewed geometry categories passed. Original zero-snapped carriageway candidates remain recorded and do not count as coverage.
- Both isolated servers stopped gracefully; their application/admin ports were checked closed. Actual JVM flags, configuration, graph paths and JAR identities are in [runtime-manifest.json](runtime-manifest.json).
- The earlier core JAR's SHA-256 is `acee903f0fe6514592283d0f8c2f497875ed27afb897ecc8e3201b5e28463e12`; ZIP CRC passed. Forty compiled production classes and both configured models matched its bytes in [binary-source-binding.json](binary-source-binding.json). Its build was intentionally uncommitted; the CH release has its own committed source and artifact identity.
- User deployment/configuration and user OD files were not supplied. The independently acquired Greater Manchester extract is pinned separately. These static street-profile checks do not establish real-world legality or national throughput.
- That core release justified the dedicated CH experiment now implemented. Removing its mandatory flexible verification would require separately proven preparation/query correctness for the original objective, including virtual splits. Production concurrency and national-scale workloads remain unmeasured; PT matrices are outside this milestone.
