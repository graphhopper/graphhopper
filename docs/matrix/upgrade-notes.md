# Maintenance and upgrades

The only existing production source modified is
`web-bundle/src/main/java/com/graphhopper/http/GraphHopperBundle.java`, adding
the lazy singleton matrix engine factory/binding and resource registration.
Ordinary routing, SPT, isochrones, CH query code and profile weighting factories retain
their upstream implementations. New production classes live in the matrix request,
routing matrix, and resource packages; new tests and documentation are additive.

`MatrixEngine` contains a small row backend interface separating ordinary reference
routing, shared target-set exploration and the certified experimental CH backend.
Keep the force-backend option and explicit response identity. `auto` remains
flexible: measured certified CH latency includes substantial verification work.

## Experimental CH matrix maintenance

`BucketCHMatrix` uses request-local reverse upward target buckets and forward
upward source searches. Review incoming/reverse edge direction, node ranks,
virtual-node transitions and directional shortcut unpacking after an upstream
change. Existing float-prepared topology can omit an original-double optimum;
expanding shortcut metrics cannot restore an omitted path. The mandatory
same-QueryGraph flexible certificate in `MatrixEngine` must remain in place until
a replacement preparation and query design independently proves the required
objective. Turn-cost profiles currently report an explicit flexible fallback.

Read [ch-matrix-design.md](ch-matrix-design.md),
[ch-matrix-validation.md](ch-matrix-validation.md) and
[ch-matrix-benchmark-report.md](ch-matrix-benchmark-report.md) before changing
selection, budgets or precision handling. Fresh ordinary-CH comparisons include
sixteen Monaco cases with a nonzero exact objective difference; their original
FAIL evidence remains preserved. Their case-bound review does not authorize a
global tolerance change or an original-rational optimality claim.

Run a new full gate and freeze its reports before opt-in tests can replace
Surefire XML. Choose new output names to preserve approved evidence:

```bat
mvn -B verify -Dmatrix.fixture.report=../docs/matrix/ch-matrix-fixture-recheck.json
python scripts\snapshot-matrix-verification.py --help
mvn -B -pl core -am test -Dtest=MatrixCHMatrixBenchmarkTest -Dsurefire.failIfNoSpecifiedTests=false -Dmatrix.ch.benchmark=true -Dmatrix.ch.benchmark.dataset=monaco -Dmatrix.ch.benchmark.output=target/ch-monaco-recheck -Dtest.heap.max=2g
```

The opt-in benchmark borrows an existing prepared cache with writes disabled.
Its extended 600-second / 2,147,483,647-state limits are for measurement; shipped
matrix limits remain 30 seconds / 1,000,000 states. The Manchester 100x100 stress
measurement exceeds those defaults. Current-thread allocated bytes measure
allocation volume, not retained heap or process RSS. Recheck source/class/model
hashes, actual JVM flags, unchanged cache bytes, raw candidates, fresh expanded
path proofs and final-JAR HTTP behavior before issuing another release record.

Rebase from the recorded commit after preserving local changes. Reinspect Dijkstra,
AbstractRoutingAlgorithm, TraversalMode, QueryGraph/QueryGraphWeighting, ViaRouting,
DefaultSnapFilter, GHUtility edge/turn helpers and server injection/lifecycle APIs.
Recreate imported graphs in a fresh directory when engine/config compatibility is not
established; never overwrite a running service's graph or assume cache compatibility.

Rerun the fixture oracle, focused matrix tests, full Maven verification, regional
comparisons, HTTP smoke verifier and named benchmark baselines. Replay precision
diagnoses and failed seeds. Build on JDK17-compatible APIs; the current environment
uses Oracle JDK22 with Maven's `release=17` setting. Preserve Apache2.0 notices.

The root POM also exposes `test.heap.max`/`test.heap.min`, retaining the upstream
190MiB defaults. Use `-Dtest.heap.max=2g` for opt-in regional diagnostics and
benchmarks. `-DargLine=-Xmx2g` does not replace the original literal Surefire
configuration and must not be treated as evidence of the forked JVM's heap size.
The first full regional sample completed under the original 190MiB configuration;
subsequent path replay needed the explicit larger setting.

## Prepared CH weight compatibility

Ordinary prepared-CH routing remains an independent point-to-point baseline.
Its reported weight can include float preparation costs and rounded/clamped
shortcut storage, while the matrix accumulates the profile's base-edge and turn
costs. The original HTTP comparisons remain `MISMATCH`: 184 indexed Monaco cases
and 38 indexed Manchester cases, repeated 20 times. Their separate
[Monaco](ch-weight-reconciliation.json) and
[Manchester](regional-http-benchmark/ch-weight-reconciliation.json) ledgers bind
the original report, request set and sample CSV hashes to fresh expanded paths.

The opt-in `MatrixCHHttpAuditTest` and `CHWeightAudit` check actual selected CH
search-tree edges, shortcut unpacking, unique stored-weight predictions, path
legality/endpoints and exact objective/distance sums and milliseconds. Three
regional unique OD pairs have different parallel-edge paths with exactly equal
metrics; they require explicit tie proofs. Capped historical diagnostics do not
retain every repetition's path/scalar, and stable aggregate checksums alone cannot
exclude compensating changes in omitted records. Fresh replay establishes the
recorded indexed-case classifications within that limit.

Review these helpers against `CHStorage`, CH preparation, shortcut unpacking,
default algorithm selection and route-weight serialization after any upgrade.
Preserve the original raw flags and tight thresholds. Recheck into a new output
file so existing approved proof hashes stay intact:

```bat
mvn -B -pl core -am test -Dtest=MatrixCHHttpAuditTest -Dsurefire.failIfNoSpecifiedTests=false -Dmatrix.ch.audit=true -Dtest.heap.max=2g -Dmatrix.ch.audit.output=target/ch-regional-recheck.json
mvn -B -pl core -am test -Dtest=MatrixCHHttpAuditTest -Dsurefire.failIfNoSpecifiedTests=false -Dmatrix.ch.audit=true -Dtest.heap.max=2g -Dmatrix.ch.audit.directory=docs/matrix -Dmatrix.ch.audit.graph=target/matrix-monaco-server -Dmatrix.ch.audit.output=target/ch-monaco-recheck.json
```

The prepared graph must be stopped and available; the audit disables graph writes
and does not import or prepare it. This diagnostic class was added after the
frozen full-reactor snapshot and has separate passing replay/default-skip evidence.

## Existing PT test fixture lifecycle

Two existing web test classes have a narrow fixture-only correction:
`PtRouteResourceTest` and `PtRouteResourceTripBasedTest`. The pristine full reactor
failed when both reused `target/gtfs-app-gh` and attempted cleanup while the
Dropwizard application still held memory-mapped MapDB files. The failed cache
retained `transit_schedule` but lost `pt_to_street`; the next class attempted to
load it. A cache-cleared isolated replay of the unmodified TripBased class passed
all eleven tests. Exact counts and evidence are in [baseline-report.md](baseline-report.md).

The feature tests use distinct class-specific, UUID-suffixed directories for each
JVM run, check any existing owned directory before explicit `app.before()` in
`@BeforeAll`, then call `app.after()` once before best-effort removal in
`@AfterAll`. Automatic `DropwizardExtensionsSupport` registration is removed
from those classes to avoid duplicate shutdown callbacks: the dependency's
extension lifecycle is reference counted. If startup/shutdown fails, cleanup does
not attempt deletion after a failed shutdown. Existing routing inputs, expected
values and assertions remain intact; no production PT routing changes are made.

On this Windows/JDK22 environment, a follow-up run passed every PT route assertion
but could not remove all mapped files even after the application stopped. Cleanup
therefore logs retained generated caches without asserting immediate unmapping.
UUID ownership prevents any subsequent run from loading a partially removed cache.
After Maven and its test JVM exit, optional checked cleanup is available:

```bat
C:\Users\hulse\anaconda3\python.exe scripts\cleanup-pt-test-caches.py --dry-run
C:\Users\hulse\anaconda3\python.exe scripts\cleanup-pt-test-caches.py
```

The script resolves and validates every target beneath this checkout's
`web/target`, recognizes only the two fixture prefixes and UUIDs, rejects redirected
paths, and reports files it cannot remove. It does not remove arbitrary graphs.

When upgrading Dropwizard, review this ordering against its pinned lifecycle API.
Do not restore deletion of a live graph cache, shared cache names or claims that
stopping the application guarantees immediate filesystem unmapping.
