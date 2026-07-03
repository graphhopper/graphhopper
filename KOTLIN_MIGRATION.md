# GraphHopper Core → Kotlin Migration Plan

Branch: `kotlin` (based on master @ 18c40bfa9). Started 2026-07-02.

## Goal

Incrementally rewrite the `core` module (~61k LOC, 466 main + 200 test files) in Kotlin with
**identical behavior** (existing tests as the lock), **unchanged integration** into `web`, and
**performance parity** verified with `tools/.../Measurement.java` on real maps from `/home/peter/maps/`.

## Constraints (from Peter)

- Do NOT rewrite other modules (web, web-api, tools, map-matching, reader-gtfs, ...) — only
  integrate the Kotlin core into web. Small mechanical edits in consumers (imports, build wiring)
  are allowed only where interop demands it.
- Never change a test unless really necessary for Kotlin; the meaning must stay identical.
- Apply Kotlin-native coding practice where possible.
- HPPC: vendor only the classes we need, with tests and proper attribution (NOTICE.md already
  credits HPPC, Apache 2.0).
- Git: small incremental commits for safe rollback, one logical conversion per commit.
- Geometry: evaluate Spatial K (https://github.com/dellisd/spatial-k) where JTS is used.
- **KMP-readiness**: produce code usable in a Kotlin Multiplatform project where feasible —
  prefer kotlin stdlib (`kotlin.math`, kotlin collections, `expect/actual`-friendly structure)
  over `java.*`; isolate inherently JVM-only pieces (mmap/DataAccess, Janino codegen, StAX XML,
  Jackson annotations, java.io) behind existing interfaces. No module restructure into
  commonMain/jvmMain now — just avoid gratuitous JVM-isms so a later split is cheap.

## Key research findings (2026-07-02)

### Build / module structure
- No Kotlin sources or kotlin-maven-plugin anywhere yet. `kotlin-stdlib` is already on core's
  classpath (pinned; pulled transitively by `osm-legal-default-speeds-jvm`, itself a Kotlin lib).
- **`core` depends on `web-api`** (GHRequest/GHResponse/ResponsePath/Helper/GHPoint/PMap live in
  web-api!). Layering: web-api → core → web-bundle/map-matching/reader-gtfs/navigation/tools → web.
  So "integrate kotlin core into web" is automatic: core remains a plain JVM jar.
- Consumers extending core classes (must stay `open` in Kotlin): `GraphHopper` (GraphHopperGtfs,
  GraphHopperServerTestConfiguration), `DijkstraBidirectionRef` (tools DebugDijkstraBidirection),
  `GHLocation`, and the `Graph`/`EdgeIterator`/`EdgeExplorer` interface family.
- No ServiceLoader/SPI; no consumer reflection on core classes.

### Jackson
- Core classes with Jackson annotations: `GraphHopperConfig` (@JsonAnySetter), `config/Profile`,
  `config/LMProfile`, `routing/ev/EncodedValue` (**@JsonTypeInfo Id.CLASS — FQCNs of
  IntEncodedValueImpl, DecimalEncodedValueImpl, EnumEncodedValue, StringEncodedValue,
  SimpleBooleanEncodedValue, ExternalBooleanEncodedValue are persisted in stored graphs; package
  and class names MUST NOT change**), the 6 EV impls (@JsonCreator mode=PROPERTIES),
  `MaxSpeedCalculator`.
- `jackson-module-kotlin` is registered nowhere. Strategy: keep explicit
  @JsonCreator/@JsonProperty on converted constructors (no new runtime deps, KMP-friendlier than
  relying on kotlin module magic).

### HPPC (0.8.1, Apache 2.0, root pom dependencyManagement, direct dep only in core)
- 116 core main files + 40 core test files import it; also tools (2), map-matching (1),
  reader-gtfs (5). web/web-api/web-bundle/navigation: none.
- Concrete types used by core: Int/Long/DoubleArrayList, Int/LongArrayDeque, Int/LongHashSet,
  Int/LongScatterSet, IntObject/LongObject/IntInt/IntLong/LongInt/LongLong/IntDouble/ObjectInt
  hash+scatter maps, BitSet; interfaces (IntContainer, IntIndexedContainer, IntSet, ...); cursors,
  procedures, predicates; IndirectSort/IndirectComparator; HashOrderMixing internals.
- `com.graphhopper.coll.GH*` wrappers exist ONLY to pin **deterministic hash order**
  (`HashOrderMixing.constant(123321123321123312L)`) — determinism must be preserved.
- HPPC leaks into public API: `Path.getEdges()` → IntArrayList, `Path.calcNodes()` →
  IntIndexedContainer, `ReaderWay.getNodes()` → LongArrayList, `ArrayUtil`, SCC/CH returns.
  Vendoring under a new package therefore touches a handful of import sites in
  tools/map-matching/reader-gtfs (allowed mechanical edits).
- Vendoring estimate: ~45–55 classes if scatter variants are folded into the deterministic GH
  hash maps; ~70–95 if copied 1:1. Precedent: `com.graphhopper.apache.commons.collections.IntFloatBinaryHeap`.
- **DECISION (2026-07-02, research verified at source level): androidx.collection 1.6.0**
  (`androidx.collection:collection`, pure-Kotlin KMP, Apache 2.0, Google-maintained) replaces
  HPPC in core, plus ~4 small Kotlin gap-fillers ported from HPPC with attribution:
  growable BitSet (~150 lines, 8 call sites), IndirectSort (~80 lines, 5 call sites),
  IntDoubleMap via Double.toRawBits over MutableIntLongMap (2 call sites), LongArrayDeque
  mirroring CircularIntArray (1 call site). IntArrayDeque → CircularIntArray.
  - Determinism verified in ScatterMap.kt source: fixed MurmurHash constant, no per-instance/
    per-run seed → iteration reproducible across runs (like HashOrderMixing.constant today).
    It is an implementation property, NOT an API guarantee → pin the version and add a canary
    test asserting a known iteration order, so upgrades that permute order are caught.
  - Cursors → inline forEach lambdas (zero allocation). Object-keyed maps need stable hashCode
    (String — same constraint as HPPC).
  - Perf gate: benchmark CH preparation + routing (Measurement) before/after the switch.
  - Fallback if blocked: HPPC 0.10 upgrade + override nextIterationSeed() in GH* wrappers
    (JVM-only bridge; HPPC 0.9 removed HashOrderMixing and randomizes iteration per-iterator).
  - Rejected: fastutil/Eclipse Collections/Agrona (Java-only), koloboke (dead),
    korlibs-datastructure (single-maintainer risk), kotlinx (no primitive collections exist).
  - Migration is gradual: HPPC stays as a dependency until the last core usage is converted;
    reader-gtfs keeps using HPPC (declare its own dep when core drops it).

### Package migration order (leaf → hub); cross-package cycles noted
1. `apache.commons.*`, `debatty` (vendored, self-contained) — warm-up.
2. `util.shapes`, `geohash`
3. `coll` (+ begin HPPC Kotlin port here)
4. `config`
5. `util` — EXCEPT graph-aware files (`EdgeIteratorState`, `GHUtility`, `PathMerger`,
   `RandomGraph`, `BaseGraphVisualizer`) which migrate with storage/routing.
6. `routing.ev` (77 files, mostly enum-like — Kotlin enum class/object; defer its ~6
   graph-aware files) 
7. `routing.weighting`, then `routing.weighting.custom` (Janino — see risks)
8. `search` (KVStorage), `storage`, `storage.index`
9. `routing.util`, `routing.util.parsers` (+helpers/tour)
10. `reader`, `reader.osm.conditional`, `reader.osm.pbf`, `reader.dem`, `reader.osm`
11. `routing.subnetwork`, `routing.querygraph`, `routing`
12. `routing.ch`, `routing.lm`
13. `isochrone.algorithm` (JTS-heavy; Spatial K evaluation point)
14. root `GraphHopper`, `GraphHopperConfig`
Biggest classes: GraphHopper (1799), BaseGraph (1210), CHPreparationGraph (966),
LandmarkStorage (897), OSMReader (691), CustomModelParser (687), GHUtility (675).

### Tests = behavior lock
- Decisive: `core/.../GraphHopperTest.java` (2833 lines, 259 exact numeric asserts, end-to-end),
  `RoutingAlgorithmWithOSMTest`, `OSMReaderTest`, `GraphHopperOSMTest`; randomized differential
  tests with fixed seeds: `DirectedRoutingTest`, `RandomCHRoutingTest`, `CHTurnCostTest`.
- Web HTTP locks: `web/.../RouteResource*Test` (andorra/moscow/leipzig real maps).
- Test maps in `core/files/` (andorra, monaco, moscow, krautsand, north-bayreuth, ...).
- CI: `mvn -B clean test` (JDK 26/27-ea matrix). Local baseline 2026-07-02: core suite green,
  ~3 min. Canonical commands:
  - `mvn -B test -pl core` (per-package loop)
  - `mvn -B clean test` (milestones — catches consumer-module breakage)
  - Single class: `mvn -B test -pl core -Dtest=GraphHopperTest`

### Performance verification
- Baseline BEFORE first conversion (on master/kotlin tip), then after each major phase:
```
mvn -B -DskipTests package -pl tools -am
java -cp tools/target/graphhopper-tools-*-jar-with-dependencies.jar \
  -XX:+UseParallelGC -Xmx20g -Xms20g com.graphhopper.tools.Measurement \
  datareader.file=/home/peter/maps/germany-260621.osm.pbf \
  measurement.name=<label> measurement.folder=measurements/results/ \
  measurement.summaryfile=measurements/summary.dat \
  measurement.clean=true measurement.stop_on_error=true measurement.json=true \
  measurement.count=5000 measurement.repeats=1 measurement.run_slow_routing=true \
  measurement.ch.node=true measurement.lm=true "measurement.lm.active_counts=[4,8,12]" \
  measurement.vehicle=car measurement.turn_costs=true \
  import.osm.ignored_highways=footway,cycleway,path,pedestrian,bridleway \
  prepare.min_network_size=10000 graph.location=measurements/<label>-gh
```
- Quick iteration variant: alsace or sachsen pbf, lower -Xmx, count=2000.
- **Machine constraint (checked 2026-07-02): 7 GB RAM, 4 cores, ~20 GB free disk.** -Xmx20g is
  impossible here. Peter: **germany works via MMAP** — add `graph.dataaccess.default_type=MMAP`
  and lower `-Xmx` to 2–3g. Use that for the germany Measurement runs (slow but fine);
  austria/alsace/sachsen with RAM + -Xmx4500m for quicker iterations.
- Benchmark hygiene on this small box: never run Measurement concurrently with builds/tests;
  compare only like-for-like runs from the same machine state.
- ALWAYS pipe Measurement output through `tee measurements/<label>.log` so the run is
  live-tailable (the 2026-07-02 java-baseline germany run lacks this — logs only via file-size
  progress + final JSON).
- Benchmark cadence + methodology (Peter 2026-07-02, updated): checkpoints (~30%, ~60%) REUSE
  the java-written germany graph copy (measurements/germany-gh-compat) — no import/prep, checks
  1. graph compatibility 2. query speed. Run `measurements/ab-bench.sh` (paired, alternating
  java-jar/kotlin-jar rounds on the same graph, median over >=3 rounds each) to separate VM
  noise from real differences — this small VM is noisy; single runs are not comparable.
  Full import benchmark only at 100% (and on the dedicated machine via benchmark/benchmark.sh,
  which now supports GH_TOOLS_JAR/GH_JAVA_OPTS/GH_CLEAN/GH_COUNT/GH_DATAACCESS env overrides
  with historic defaults). Cheap spot-checks after hot-path phases only; commits gated by
  tests alone.
- Deterministic equivalence beyond timing: CH shortcut counts, LM landmark data, visited-node
  counts, and identical route distances/times for a fixed random seed — compare JSON outputs.
- Real-world spot checks: run web server (`config-example.yml`) on a real pbf, compare a fixed
  set of routes (distance/time/points) between master build and kotlin build.

## Java baseline (germany, MMAP -Xmx3g, 2026-07-02, commit d384d65c8 state, 3h23m total)

Full JSON: `measurements/java-baseline-germany-2026-07-02.json` (+summary.dat). Graph kept at
`/home/peter/gh-java-baseline/measurements/germany-gh` for load-only + format-compat checks.

DETERMINISTIC anchors (must be bit-identical for the Kotlin build, seed 123, count 5000):
- graph.nodes = 17,902,936 · graph.edges = 20,878,050 · graph.size_in_MB = 1250
- prepare.ch.node.shortcuts = 9,252,034 · prepare.ch.edge.shortcuts = 28,589,795
- routingCH.visited_nodes_mean = 430.8604 (max 713) · routingCH_edge.visited_nodes_mean = 1527.9792
- routingLM8.visited_nodes_mean = 97,846.28 · routing.visited_nodes_mean = 1,272,633.6
- routing.distance_mean = 403,103.84 m

Timing references (MMAP on 7GB/4-core box — compare like-for-like only):
- import 354 s · prepare CH node 171 s · prepare CH edge 2667 s · prepare LM 2405 s
- routingCH 3.77 ms · routingCH_no_instr 1.96 ms · routingCH_full 6.17 ms · routingCH_edge 7.91 ms
- routingLM8 146 ms · flexible routing 1746 ms · routing_edge 6943 ms
- unit_testsCH.out_edge_next 0.867 µs · unit_tests.get_edge_state 0.277 µs

## Per-package conversion protocol

1. `git switch kotlin`; one package (or coherent class cluster) at a time.
2. Convert: delete `X.java`, create `X.kt` in `core/src/main/kotlin/<same package>/` with the
   SAME FQCN. Kotlin-native inside (val, data classes where safe, when, null-safety, kotlin.math,
   extension fns internal to core); Java-compatible surface where consumed by Java
   (`open` where extended, `@JvmStatic`/`@JvmField`/`@JvmOverloads` where Java callers need them,
   `@Throws` for checked exceptions Java callers catch, keep getters as properties — Java sees
   `getX()`).
3. `mvn -q -pl core test` must be green. At phase milestones: `mvn -B clean test` (full repo).
   Batch-final verification must use `clean` — incremental compilation can mask missing `open`
   modifiers against stale subclass classfiles (bit us with Downloader's anonymous test
   subclasses: first test-compile passed, clean run threw IncompatibleClassChangeError).
   NEVER run two maven builds concurrently on this checkout: a delegated agent's verification
   run overlapping the reviewer's produced phantom failures (clean wiped resources mid-test,
   "No input stream found in class path"). Check `ps aux | grep maven` before building;
   delegated agents must finish their gate in the foreground before returning.
4. Commit: `kotlin: convert <package or classes>` — one conversion per commit for rollback.
5. Never edit a test's assertions; if a test won't compile against Kotlin (e.g. SAM/overload
   resolution), adapt syntax minimally, preserving meaning — and note it in the commit message.
5b. (Peter 2026-07-02) When conversion work uncovers additional behavior that must be pinned
   but is NOT covered by existing tests (subtle numeric semantics, iteration order, overflow/
   sign behavior, initialization order, ...): write a NEW test that locks it in, and record the
   finding in the "Pinned behavior discovered during conversion" section below.
6. Test conversion to Kotlin is OPTIONAL/later; Java tests keep working against Kotlin classes
   and are a stronger interop check. (Open question Q4.)

## Risks / tricky spots

- **Janino** (`CustomModelParser` + Expression visitors): generates Java source subclassing
  `CustomWeightingHelper` and compiles it at runtime. Generated Java must still compile against
  the Kotlin-compiled classes — keep method signatures/visibility Java-friendly (no default-arg
  only methods on that surface). Convert this package late; verify with
  RouteResourceCustomModel(LM)Test + Measurement custom-model scenario. Inherently JVM-only.
- **MMapDataAccess**: sun.misc.Unsafe + invokeCleaner reflection — port carefully, JVM-only by
  design (fine re KMP).
- **Hot-path boxing**: Kotlin `Int?`, non-inlined lambdas, or `for (i in list)` over boxed
  collections can regress performance. In routing/CH/LM/storage hot loops: primitives, plain
  `while`/indexed loops, no closures capturing boxed values. Measurement guards this.
- **Deterministic hash order** of GH*/HPPC maps — bit-identical iteration order expected by
  tests and stored-graph reproducibility.
- **EncodedValue FQCNs** persisted via @JsonTypeInfo(Id.CLASS) — never rename/move those classes.
- **Static state**: Constants, Country/State/Surface enums, PriorityCode → companion objects /
  top-level; watch initialization order changes.
- Kotlin/JDK: Java 25 toolchain locally, CI on 26/27-ea — use latest stable Kotlin (2.2.x),
  jvmTarget matching core's maven.compiler.release.

## Platform seams (from discussion with Peter 2026-07-02)

- **DataAccess/mmap**: there is an iOS GraphHopper port with its own DataAccess implementations.
  Plan: DataAccess interface + RAM implementations converted platform-neutral (ByteArray-backed,
  no java.nio in common logic); MMapDataAccess stays explicitly JVM-only; GHDirectory remains the
  single DAType→implementation factory = future expect/actual point. Preserve today's DataAccess
  contract exactly so existing ports keep working.
- **graphhopper-ios findings (2026-07-02, repo researched)**: dormant (tracks GH 1.0 via
  `ios_compatibility` branch, last push Oct 2021); whole-source j2objc 2.5 translation, NOT
  hand-ported. There is NO custom MMapDataAccess — j2objc's libcore-based JRE emulation provides
  java.nio FileChannel.map/MappedByteBuffer over real mmap(2); the app uses `forMobile()` →
  DAType.MMAP with a desktop-prebuilt `.osm-gh` bundle. What they actually had to change
  (only 13 files!) = the true platform-seam list, adopt for KMP-readiness:
  1. Helper's memory introspection (ManagementFactory/Runtime) — make injectable or isolate.
  2. Classpath resources (TranslationMap) — keep constructor-injectable, no
     Class.getResourceAsStream in converted common logic.
  3. Import pipeline (reader.osm, reader.dem) cleanly excludable from the routing runtime —
     preserve that package separation strictly.
  4. DataAccess stays the expect/actual boundary (KMP has no libcore emulation, unlike j2objc).
  5. SLF4J was faked, Jackson reduced to annotations — keep converted code loosely coupled to
     both.
  Their DataAccess contract = the standard interface, nothing iOS-shaped → preserving today's
  contract is sufficient; no signature alignment needed with the (outdated) port.
- **Janino / custom models**: runtime classloading is impossible on Android (no DEX at runtime)
  and iOS (AOT). Janino is BOTH the codegen AND the parser/validator of condition expressions
  (ConditionalExpressionVisitor/ValueExpressionVisitor walk Janino's AST).
  Plan adopted: (1) JVM keeps Janino unchanged (perf parity); (2) during conversion, make
  "CustomModel → CustomWeightingHelper" a small factory seam so backends are pluggable;
  (3) portable closure-composition evaluator (function-object DAG built once per profile,
  ~1.5–2x generated bytecode, KMP-safe); (4) build-time Kotlin source generation for fixed
  mobile profiles (full speed under AOT; kotlinc compiles at app build — we never reimplement a
  compiler, only the ~100-line emit template).
  **Peter 2026-07-02: (3) AND (4) are IN SCOPE.** Architecture: one front-end (small
  recursive-descent parser for the condition grammar → validated AST — this replaces Janino's
  parser role and is the main new shared piece), three back-ends: Janino Java-source emit
  (server JVM, default, unchanged), closure composer (portable runtime), Kotlin source emitter +
  profile-name registry (build-time mobile). Correctness: differential tests — closure backend
  must match Janino backend bit-identically on all custom-model tests + randomized models.
  Perf: Measurement custom-model scenario per backend. Scheduled directly after the
  routing.weighting.custom conversion (phase 7): parser first, then backends, tests throughout.
  Core provides generator + registry + runnable tool; app-side Gradle wiring is out of scope.
  **Attribution (Peter 2026-07-02): if the new parser/back-ends use code OR ideas from Janino**
  (BSD-3-Clause, Arno Unkrig / janino-compiler.github.io), add a BSD attribution header to the
  affected files and an entry in NOTICE.md — same as the HPPC-derived gap-fillers get for
  Apache 2.0. This applies also to code derived from GraphHopper's existing Janino-AST visitors
  (ConditionalExpressionVisitor/ValueExpressionVisitor) insofar as their logic mirrors Janino
  API concepts.

## Geometry / Spatial K evaluation

JTS usage: `util/shapes/{Polygon,BBox}`, `GHUtility`, `isochrone.algorithm` (Delaunay
triangulation/QuadEdge — Spatial K has NO equivalent), `AreaIndex`, `CustomArea`, `SplitArea`,
`InMemConstructionIndex`, `PixelGridTraversal`.
**DECISION (2026-07-03, at the isochrone phase): keep JTS everywhere for this migration.**
Spatial K offers no Delaunay/QuadEdge (isochrone's core need), JTS prepared-geometry types are
part of the public API (Polygon/AreaIndex/CustomArea), and swapping the simple-geometry cases
would be an API break for zero KMP gain while the module remains JVM-only. Spatial K remains
the candidate for a FUTURE true-KMP split (common geometry types in commonMain with JTS as the
JVM actual) — revisit then.

## Pinned behavior discovered during conversion

Moved to the durable, migration-independent document **`docs/pinned-behavior.md`** (Peter: these
findings are "a test for our tests" and must outlive this migration doc). Rule 5b still applies:
new discoveries get a pinning test AND an entry there.

## Progress log

- [x] 2026-07-02 Research + this plan; baseline: full core suite green on `kotlin` branch.
- [x] 2026-07-02 Phase 0: mixed build + StringUtils, JaroWinkler, SpatialKeyAlgo converted; full repo green.
- [x] 2026-07-02 Measurement baseline (germany, MMAP) recorded — see "Java baseline" section.
- [x] 2026-07-03 ALL 14 PHASES COMPLETE — core/src/main/java is EMPTY: 466/466 classes
  Kotlin. Full repo green (mvn -B clean test, all 11 modules; core 3214 tests incl. 259-assert
  GraphHopperTest; web 215 HTTP tests). Ten germany load-gates passed with bit-identical
  checksums; 30% and 60% A/B checkpoints passed (CH faster in kotlin, LM8 within noise/flagged).
  One flagged test edit (GraphHopperTest: 2 anonymous overrides gained 'protected' because
  kotlin cannot emit package-private).
- [x] 2026-07-02 STORAGE-COMPAT GATE PASSED at 24.9%: kotlin core loaded the java-written
  germany graph (nodes/edges match anchors exactly), identical route checksum + visited nodes
  across runs; warm routingCH 4.31ms vs 3.77ms baseline (non-like-for-like, verdict at 30%
  checkpoint). Graph copy kept at measurements/germany-gh-compat for future gates;
  results: measurements/kotlin25-compat*.json
- [x] 2026-07-03 100% CHECKPOINT PASSED — full fresh germany import with the kotlin core:
  EVERY deterministic anchor EXACT vs java baseline (nodes 17,902,936; edges 20,878,050;
  1250MB; shortcuts 9,252,034 node / 28,589,795 edge; all visited-node means; distance mean).
  Final A/B: query parity or better (CH 3.72 vs 4.01ms median, LM8 dead even — watch-flag
  RESOLVED as noise). Import 318s (java 354s). Prep times +5-8% single-run/unpaired —
  INCONCLUSIVE on this VM; recommend one paired GH_CLEAN=true comparison on the dedicated
  machine. Results: measurements/kotlin100-fullimport.json, measurements/ab/ (third stamp).
- [x] 2026-07-03 custom-model STAGE 2 DONE: CustomWeightingBackend seam cut
  (fun interface CustomModel+EncodedValueLookup -> CustomWeighting.Parameters, mirrors
  createWeightingParameters 1:1; JaninoBackend object delegates to the moved-verbatim
  createJaninoWeightingParameters — caches untouched in CustomModelParser;
  CustomWeightingBackends.default @Volatile registry). Public CustomModelParser entry
  points route through the seam; DefaultWeightingFactory wired directly.
  CustomWeightingBackendTest added. Gates green (core 3216, web 215).
- [ ] REMAINING WORK: (1) (2) custom-model stages 4-5 (closure composer + differential tests,
  build-time kotlin source generator — Peter approved scope); (3) HPPC→androidx.collection
  call-site switch + gap-fillers + canary + perf gate, then drop hppc from core (reader-gtfs
  declares its own); (4) NOTICE.md attribution review; (5) final real-route diff report.
- [x] 2026-07-03 custom-model STAGE 3 DONE: shared expression FRONT-END in new KMP-clean
  package core/.../weighting/custom/expression/ (hand-rolled ExpressionLexer + recursive-descent
  ExpressionParser -> ExprNode sealed AST; ExpressionValidator with (a) parse-level walkers
  transcribing the Janino visitors 1:1 and (b) a strict layer mirroring what the janino
  pipeline only rejects at codegen/compile time: enum-constant membership per EV, area refs
  (isValidId mirror), variable declarability per context EDGE/TURN_PENALTY, single-EV +
  non-negativity of values via double eval at EV range endpoints; ExpressionScopes = the only
  JVM-touching adapter from EncodedValueLookup). Janino quirks pinned empirically and
  reproduced: line comment at EOF rejected but newline-terminated accepted, block comments
  skipped anywhere, octal literal digit validation ("09" rejected, "010" ok), "_" separator
  rules, Math.sqrt() (0-arg) guessing "Math", enum-comparison throw for non-==/!=,
  "Infinity" only valid for op add with the exact string. ExpressionParserDifferentialTest:
  case-by-case accept/reject parity (parse-level vs visitors incl. guessedVariables+operators
  order: 134+119 conditions, 66+135 values; full-pipeline vs janino compile: all 23 bundled
  models + ~60 valid/invalid condition/value/area cases with pinned expected verdicts).
  Attribution: BSD-3-Clause idea-derivation headers on the 4 parser-package files + NOTICE.md
  entry (no janino source copied). Janino path untouched. Gates green (core clean test 3223,
  web -am test-compile).
- [x] 2026-07-02 ~30% CHECKPOINT PASSED (at 27.0%, after ev machinery + weighting):
  paired A/B (3 rounds each, java-written germany graph, count=5000): routingCH median
  4.11ms(J)/4.07ms(K), CH_edge 8.07/8.02, LM8 129.2/127.7 — parity within noise; route
  checksum 27749812 and visited-node means bit-identical J vs K and vs baseline anchors.
  Results: measurements/ab/20260702-*.json
- [x] 2026-07-03 ~60% CHECKPOINT PASSED (at 68.2%, after readers): checksums bit-identical
  (27749812, all 6 runs); medians J/K: routingCH 4.05/3.73ms, CH_edge 8.21/7.26 (kotlin
  ~8-12% faster), LM8 119.1/124.1 (+4.2% — borderline, ranges nearly overlap, 30% checkpoint
  had K faster; WATCH LM8 at the 100% checkpoint, profile if it reproduces).
  Results: measurements/ab/ (second stamp).
- [ ] Final: full `mvn -B clean test`, Measurement comparison (full import), real-route diff report.

## Adopted working defaults (2026-07-02, Peter AFK — override anytime)

- Perf budget: within noise (~±3%) on Measurement vs Java baseline; deterministic counts
  (CH shortcuts, visited nodes, routes) must be identical.
- Tests stay in Java permanently — strongest behavior lock + continuous interop check.
- Style: idiomatic-where-safe in one pass; hot paths stay primitive/loop-based; public surface
  Java-friendly.
- Other modules: mechanical edits allowed (imports, build wiring, minimal test syntax), each in
  its own commit, no logic rewrites.

## Open questions for Peter

- Q1 HPPC replacement: androidx.collection (KMP lib, no copying) vs Kotlin port of HPPC subset
  vs hybrid — pending library-coverage research. Either way a few imports change in
  tools/map-matching/reader-gtfs; keep `hppc` for reader-gtfs or let it declare its own?
- Q2 Consumer edits: confirm mechanical edits in other modules are OK when interop demands
  (imports for vendored collections, possibly SAM syntax in their tests).
- Q3 Performance budget: acceptable regression threshold on Measurement (suggest: within noise,
  ±3%, and identical deterministic counts)?
- Q4 Tests: keep all tests in Java permanently (strongest interop lock) or convert them too at
  the very end?
- Q5 Kotlin style depth: mechanical-but-safe conversion with idiomatic internals immediately,
  or a second idiomatic pass per package?
- Q6 Spatial K: adopt where it fits, or JTS-only until a dedicated later decision?
