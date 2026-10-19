# Matrix development context

The complete supplied pack was extracted and read before implementation.
It is preserved under `../prompt_pack/GraphHopper_Local_Matrix_Codex_Pack`.
User instructions override its PowerShell examples: use cmd.exe and Python automation.

Base: official GraphHopper 11.1, `ebb578f4e94db72e82f2f8d3bc69417758e16e1c`.
Branch: `feature/local-matrix`. Fresh shallow checkout; no prior local work.
No user configuration, running service, graph cache or PBF was supplied in this workspace.
Do not scan unrelated personal directories. Use upstream fixtures initially.
Java/Maven: Oracle JDK 22.0.2, Maven 3.9.9; source compiles with release 17.
No repository AGENTS.md was found. CONTRIBUTING.md requires tests and Maven verification.

Contract: POST /matrix, static configured profiles, both traversal modes, exact axes/order,
weight objective with independent time/distance, strict unsupported-field rejection.
The reference uses ordinary GraphHopper.route; flexible uses one target-set search per origin.
The original core milestone excluded CH matrix acceleration. The user subsequently
authorized the optional follow-on and comparisons, plus committing and opening a PR in
their fork only. The fork now exists at `https://github.com/hulsiejames/graphhopper`.
`origin` points there; official upstream is fetch-only (`upstream` push URL is `DISABLED`).
PR base `matrix-base-11.1` pins the inspected release commit, avoiding an implicit master upgrade.

The actual POM/artifact version at this release commit is `11.0-SNAPSHOT`.
The original core-release JAR embedded the base commit plus dirty state and is
separately SHA-256 pinned in its historical provenance. New CH artifact/source
identity belongs to `ch-matrix-release.json`; do not conflate the two builds.

Historical core evidence: full `mvn -B verify` passed with 3276 reported cases, 3047 passed,
229 skipped and no failures/errors. `full-verification.json` freezes per-case
results before benchmark reports replace live Surefire files. The pristine baseline's
one PT cache startup error remains preserved; a cache-cleared unmodified replay
passed all 11 methods. Feature-only PT fixture lifecycle/UUID changes do not alter
routing expected values or production semantics.

Greater Manchester data was independently acquired and checksum-verified after
the initial audit. The initial 2616-cell regional run retained its FAIL status;
ten targeted car replays explain every remaining discrepancy using independently
proven upstream geometry-copy relationships and legal paths. No production routing
change or global tolerance relaxation was used. Default Maven skips opt-in regional
and benchmark classes; read their separate saved reports.

Historical core evidence and root review are complete: G0–G3 PASS; G4
PASS_WITH_SCOPED_LIMITATIONS. The 960 in-process Monaco samples and 400 HTTP
samples are retained separately. Small Manchester HTTP matrices lost to prepared
CH in five of six profile/workload groups; Monaco gains are not regional-scale claims.

Raw HTTP reports retain 4440 weight-only flagged events across 222 indexed cases.
Separate SHA-approved CH path/storage ledgers prove 204 unique pairs, with exact
cost/distance sums and milliseconds, including three regional tied alternative paths.
Capped historical diagnostics do not retain every repetition's path/scalars.
The final review records this limit rather than rewriting MISMATCH to MATCH.

All 28 HTTP smoke cells, six existing endpoint checks and five manual geometry
categories passed. Original zero-snapped carriageway candidates remain preserved
and were replaced with actual OSM-way candidates. Both isolated servers stopped
gracefully and their application/admin ports were checked closed.

The earlier live-tested JAR is SHA-256 pinned, CRC-verified and matched 40 compiled
production classes plus both configured model resources. Diagnostic-only tests
were added after the frozen full gate and have separate passing/default-skip evidence.
Read validation-summary.json, final-review.json, benchmark-report.md,
binary-source-binding.json and runtime-manifest.json before extending the scope.

The CH follow-on implements `ch_experimental` with request-local reverse
upward destination buckets and forward upward source searches, with actual directional
shortcut metric expansion. A reproduced float-witness topology counterexample proves
that existing preparations can omit an original-double optimum. Each API row therefore
has a mandatory same-QueryGraph flexible certificate/correction; `auto` stays flexible.
Turn-cost profiles and missing preparations report explicit flexible fallback reasons.

Focused evidence: eight bucket tests pass, including 10240 seeded base/QueryGraph
comparisons; five integration tests, four fresh baseline-audit guard tests and 69
HTTP tests pass. New Monaco/Manchester comparisons contain 600 measured samples
and retain their original flags. Fresh ordinary-CH witnesses include sixteen
Monaco cases with a nonzero exact rational objective difference: fifteen have
equal sequential double sums, and one differs by one ULP. No original-rational
optimality pass is claimed for those cases. Certified API arrays match flexible
routing exactly. Whole-original-edge versus shared-overlay split precision is
diagnosed separately from CH preparation precision.
Preserve the old core-release JSON and JAR snapshot under outer `audit/core-release-before-ch`.
Do not interpret its older build/proof identity as certification of the new artifact.

Release gates and artifact details are in `ch-matrix-validation.md`,
`ch-matrix-full-verification.json`, `ch-matrix-live-smoke.json` and
`ch-matrix-release.json`. Preserve the old core-release files as separate evidence.
The review PR must target the user's fork, with base `matrix-base-11.1`; leave it
unmerged for the user. Official upstream pushes remain disabled.
Git's existing credential manager successfully verified a push of the unchanged
pinned PR base branch; no further account confirmation is required. The unnecessary
CLI re-authentication flow was cancelled. Production concurrency and national
throughput remain unmeasured.
