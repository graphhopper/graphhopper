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
- [x] 2026-07-03 HPPC switch batches H1+H2 DONE (pure additions, no call sites changed):
  H1 gap-fillers (com.graphhopper.coll.GrowableBitSet with the Jackson-pinned bits/wlen field
  layout + GrowableBitSetIterator; coll.primitive IndirectSort/IndirectComparator, LongArrayDeque,
  IntDoubleHashMap androidx shim) with semantic tests + GapFillerHppcParityTest (differential,
  dies in H8). H2 exact-layout hash ports (IntObjectHashMap, LongObjectHashMap, IntLongHashMap,
  LongLongHashMap, ObjectIntHashMap, IntHashSet, LongHashSet + scatter IntScatterSet,
  LongScatterSet, LongIntScatterMap; shared HashPort internals; constant order-mixing inlined as
  `seed` ctor param, default = the GH 123321123321123312L seed). Order identity PROVEN two ways:
  HashPortHppcParityTest (element-by-element vs live hppc, sizes 5..30k across resize boundaries,
  empty keys, removals, ensureCapacity path, GH/default/BridgePathFinder(16,0.5,123) ctor
  variants — dies in H8) and HashPortOrderPinTest (absolute literals harvested from live hppc,
  incl. the forEach-vs-iterator empty-key order asymmetry — survives H8, NEVER regenerate from
  the ports). NOTICE.md updated. Gates green (core clean test, web -am test-compile).
- [x] 2026-07-03 HPPC switch batch H3 DONE (THE order-critical batch): all GH* wrappers rewired
  from hppc supertypes onto the coll.primitive exact-layout ports (GHIntObjectHashMap,
  GHIntHashSet [+temporary hppc-IntContainer bridge ctor for Path.getEdges, dies in H5/H8],
  GHLongObjectHashMap, GHIntLongHashMap, GHLongLongHashMap, GHLongHashSet, GHObjectIntHashMap
  [hppc putAll ctor dropped, no callers]; FQCNs + ctor shapes unchanged, seed = port default);
  BridgePathFinder result map → seeded port(16, 0.5, seed 123). Call-site order decisions per
  hppc code path: cursor/values() iteration → forEachInIteratorOrder (ShortestPathTree,
  EdgeBasedNodeContractor bridge-path loop, GHTBitSet.copyTo, GHSortedCollection via new
  firstInIteratorOrder); forEach(procedure) → forEach [empty key FIRST] (QueryGraph,
  QueryRoutingCHGraph, EdgeChangeBuilder, LandmarkStorage.initLandmarkWeights);
  forEach(predicate) → forEachWhile [empty key FIRST, early exit] (AlternativeRoute/CH/EdgeCH,
  QueryOverlayBuilder, LandmarkStorage.setSubnetworks). Leaked-supertype signatures adapted
  core-internally: bestWeightMap*/fromMap now GHIntObjectHashMap across
  AbstractBidirAlgo/NonCH/CH/DijkstraBidirectionCH/Dijkstra; QueryOverlay.edgeChangesAtRealNodes
  + EdgeChangeBuilder.build; EdgeElevationInterpolator gatherOuterAndInnerNodeIds param
  IntSet→GHIntHashSet (tests pass GHIntHashSet, unaffected). AvoidEdgesWeighting keeps its
  public hppc-IntSet surface (H5); only the default field init became plain hppc IntHashSet
  (empty + membership-only ⇒ behavior identical). Port surface additions: IntHashSet.addAll(
  IntHashSet), IntHashSet.firstInIteratorOrder(). ONE test file edited (flagged per rule 5):
  QueryGraphTest — mechanical import/type swap IntObjectMap→GHIntObjectHashMap forced by the
  supertype leak in QueryOverlay.getEdgeChangesAtRealNodes(); assertions untouched. Gates:
  core clean test green (3289), web/tools/map-matching/reader-gtfs -am test-compile green,
  germany load-gate (count=1000, MMAP, Xmx3g, clean=false) checksums BIT-IDENTICAL:
  routingCH dummy 5603941, routingLM8 dummy 270582 (log: measurements/h3-gate.log).
- [ ] REMAINING WORK: (3) HPPC→androidx.collection
  call-site switch + gap-fillers + canary + perf gate, then drop hppc from core (reader-gtfs
  declares its own) — SCOPED 2026-07-03, full execution plan in section
  "HPPC → androidx.collection switch plan" (hybrid: androidx + hppc-layout ports for 9
  order-critical sites, zero checksum changes, batches H1–H9);
  (4) NOTICE.md attribution review; (5) final real-route diff report.
- [x] 2026-07-03 custom-model STAGE 5 DONE (custom-model platform work COMPLETE): build-time
  Kotlin source generation + registry in core/.../weighting/custom/generate/.
  `CustomWeightingSourceGenerator` unparses the validated stage-3 AST into a Kotlin class
  extending CustomWeightingHelper (init(lookup) EV field binding, getSpeed/getPriority/
  getTurnPenalty if/else chains mirroring createClassTemplate/parseExpressions 1:1) with
  TypedCompiler's promotion rules made EXPLICIT (`.toDouble()` insertions, int/int stays Int,
  literals re-typed via the shared `TypedCompiler.parseJavaNumericLiteral` and folded — Kotlin
  has no octal and types -2147483648 as Long; String== -> `===`, & | ^ -> and/or/xor, shifts
  -> shl/shr/ushr with Int distance, Math.* -> kotlin.math.*). Validation reuses ClosureBackend's
  internal helpers (validateValues/collectVariables/calcMax*) so build-time accept/reject ==
  the differentially-proven backends. `GeneratedWeightingRegistry` keys on
  customModel.toString() (same identity as the janino cache — no profile name needed);
  `RegistryBackend : CustomWeightingBackend` instantiates per request like janino and throws a
  clear registration hint otherwise. `GenerateCustomWeightingMain` (key=value args) builds an
  EncodingManager from a `graph.encoded_values` string via DefaultImportRegistry (NO graph
  needed), writes one .kt per model + GeneratedCustomWeightings.registerAll() snippet with the
  captured keys; exercised end-to-end on car.json (output byte-identical to the checked-in
  golden). Verification (golden + semantic, no runtime kotlinc): SourceGeneratorTest asserts
  generator output == the TWO pre-generated classes checked into core/src/test/kotlin
  (car.json + a kitchen-sink model covering int-division/shift/bitwise/literal-typing/enum/
  area/backward_/change_angle/street-name/Infinity corners) + 10 build-time rejection cases;
  RegistryBackendDifferentialTest compiles those classes in the normal test build, registers
  them and proves janino vs registry EXACT-equal per edge (both directions, weights+millis),
  per incident turn pair, calcMinWeightPerDistance and DI/HP defaults on a seeded random
  graph. Two mutation probes confirmed the harness catches int-division and
  change_angle-direction deviations (an initial probe exposed that the fixture's
  maxORMaxStorable-based random fill degenerated int EVs to 0 — Stage5Fixtures fills the
  STORABLE range instead). Default backend unchanged (Janino). No Janino attribution needed:
  the emitted template mirrors GraphHopper's own janino template, and the generator consumes
  our own AST (idea-level janino attribution stays where it was: expression package +
  NOTICE.md). App-side gradle wiring out of scope (documented in the tool's KDoc). Gates
  green (core clean test, web -am test-compile).
- [x] 2026-07-03 follow-up: ClosureBackendDifferentialTest now reuses Stage5Fixtures'
  createRandomGraph/setRandomValue (Stage5Fixtures made public) so its int/decimal EV fill
  uses the STORABLE range too — the old maxOrMaxStorable-based fill shrank the range to the
  running max, collapsing int EVs toward their minimum over the edge sequence (partial teeth
  only: an int-division mutation probe was caught, but only via early edges). Seeds unchanged.
  Mutation probe re-run after the change (int/int DIV promoted to double in TypedCompiler):
  caught by conditionTypingParity (<hike_rating / 2 == 1>) + randomModels; reverted. Gate
  green (core clean test 3239).
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
- [x] 2026-07-03 custom-model STAGE 4 DONE: closure-composer backend `ClosureBackend`
  (function-object DAG, NO Janino/runtime classloading; default backend UNCHANGED — Janino
  stays production, closure selected only via CustomWeightingBackends.default in tests).
  KMP-clean evaluation core in expression/: Evaluators.kt (primitive-returning typed nodes +
  cells + StatementProgram runtime — zero allocation per evaluated edge) and TypedCompiler.kt
  (stage-3 ExprNode -> typed nodes with exact JAVA semantics: literal typing incl.
  hex/octal/binary/`_`/suffixes and the -2147483648 JLS special case, int/int division stays
  int, promotion int->long->float->double, boolean-only conditions, `& | ^` boolean vs
  bitwise, integral-only shifts, enum==CONSTANT of the LHS type via ordinal, String `==`
  identity with per-program literal pooling, equals/contains, Math.sqrt/abs overload
  resolution — composition-time rejection of everything janino only rejects at compile time).
  JVM glue ClosureBackend.kt mirrors createClazz 1:1 (check order, group structure, area
  geometry checks, backward_/prev_ prefixes, street_name/prev_street_name/change_angle,
  needTwoDirections + inEdgeReverse/outEdgeReverse, per-request fresh mutable cells =
  janino's per-request instance) + janino-free calcMaxSpeed/calcMaxPriority via new
  ExpressionScopes.minMaxScope (FindMinMax's stricter no-"Infinity" validator).
  ClosureBackendDifferentialTest (seeds: graph 123, models 20260703): 23 bundled models,
  ~25 handcrafted, 60/26 accepted/rejected edge conditions, 18/6/2 turn conditions,
  12/9 values, area + DI/heading-penalty defaulting (incl. unfavored virtual edge),
  120 random models — bit-identical (==, no epsilon) calcEdgeWeight+calcEdgeMillis on all
  120 random-graph edges in both directions, all incident turn pairs, and
  calcMinWeightPerDistance (or both throw). Two mutation probes confirmed the harness
  catches int-division and turn-direction deviations. Documented divergence: String
  concatenation `+` is rejected by the closure backend though janino compiles it (useless
  for weights; kept out of the corpus). Stage 5 note: the source generator should reuse
  TypedCompiler's typing rules by emitting per-SemType Kotlin expressions from the same
  typed lowering (or simply unparse the validated ExprNode with the enum-constant
  qualification, mirroring parseExpressions). Gates green (core clean test, web -am
  test-compile).
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

## HPPC → androidx.collection switch plan

**SIGN-OFFS (Peter 2026-07-03, superseding):** iteration-order identity is NOT required —
different order/bytes in newly written graphs are acceptable. Acceptance bar: semantic quality
only — shortcut counts / visited-node means "not completely off" (working tolerance ~±2% vs the
baseline anchors) and the full suite green. Consequence: Option A's exact-layout hash ports and
order-harvest tests are DROPPED (H2 reduced to nothing; H1 gap-fillers remain); androidx is
used directly everywhere, including the 9 order-sensitive sites. Where an existing migration-
created pin test literally pins an order artifact (androidx canary etc.) it gets re-baselined
deliberately; original behavior-lock tests stay untouched unless a tie-dependent expectation
makes an edit strictly necessary (flag each case). Public-API surface switch (batch H5) EXPLICITLY sanctioned
incl. mechanical consumer edits (tools Measurement/MiniGraphUI, map-matching MapMatching, the
API-forced BitSetIterator loop in reader-gtfs Analysis.java) and the 40 core-test mechanical
import/construction updates (assertions untouched). H8 (drop hppc from core; reader-gtfs
declares its own hppc dep for its 5-file internal usage) unblocked. Full hppc removal from
reader-gtfs itself stays DEFERRED ("core first"). (2026-07-03, scoping — no code changed yet)

Execution plan for the remaining item "(3) HPPC→androidx.collection". Sources: full grep
inventory + per-site iteration-order audit of all 75 hppc-using main files, all 40 hppc-using
test files, and the 4 consumer modules. Supersedes the DECISION section's estimate where noted.

### Headline finding — determinism is not enough, ORDER IDENTITY is required

androidx.collection iterates hash containers in a deterministic order, but a DIFFERENT order
than hppc (both scatter layout and HashOrderMixing.constant). Every site where hash-iteration
order leaks into stored graphs or pinned test outputs would produce *different-but-deterministic*
results after a naive switch → germany anchors (turn-cost bytes, elevations, 9,252,034/28,589,795
shortcut counts, QueryGraphTest's pinned virtual node ids) would legitimately differ. The audit
found the order leaks are FEW (9 sites, listed in §4) and all flow through a handful of container
classes. Therefore the plan is a HYBRID:

- androidx.collection for all lists, deques (via CircularIntArray), and every hash container
  whose iteration order is provably unobserved (keyed access / membership / order-independent
  aggregation) — the large majority of the 166 imports.
- a small Kotlin port of hppc's exact hash layout (same bit mixer, load factor, probing,
  iteration = table-slot order) for the ~5 order-critical container classes, with Apache-2.0
  attribution — this achieves the ZERO-checksum goal. Option B (accept androidx order +
  re-baseline germany + edit pinned tests) is listed per site in §4 for Peter's signoff, but
  NOT recommended: it would re-baseline 5 anchors and touch pinned test literals.

### 1. Inventory (core/src/main/kotlin: 166 hppc imports in 75 files)

By type (import count): IntArrayList 30 · IntObjectMap 11 · LongArrayList 9 · IntSet 9 ·
BitSet 9 · IntHashSet 8 · IntContainer 7 · HashOrderMixingStrategy 7 · IntObjectPredicate 5 ·
IntScatterSet 5 · IntObjectHashMap 5 · IntIndexedContainer 5 · LongHashSet 4 · IntArrayDeque 4 ·
IndirectSort/IndirectComparator 6 · IntObjectProcedure 3 · LongSet 3 · IntObjectScatterMap 3 ·
IntCursor 3 · IntProcedure 2 · LongScatterSet 2 · LongIntScatterMap 2 · LongIntMap 2 ·
LongArrayDeque 2 · IntLongMap 2 · IntDoubleMap 2 · IntDoubleHashMap 2 · HashOrderMixing 2 ·
DoubleArrayList 2 · singles: LongIntProcedure, ObjectIntHashMap, ObjectIntAssociativeContainer,
LongObjectHashMap, LongLongHashMap, LongIndexedContainer, IntLongScatterMap, IntLongHashMap,
IntIntScatterMap, IntDoubleScatterMap.

By package (files): routing 15 · routing/ch 8 · reader/osm 7+1 · coll 7 · util 5 ·
routing/querygraph 5 · storage/index 4 · routing/subnetwork 3 · reader/dem 3 · storage 2 ·
routing/weighting 2 · routing/ev 2 · isochrone/algorithm 2 · routing/util(+parsers) 2 ·
routing/lm 1 · reader 1 · GraphHopper.kt 1.

Classification:
- (a) internal-only: ~60 of 75 files — no hppc type escapes the file/package.
- (b) leaks into public API consumed by other modules:
  - `Path.getEdges()/setEdges(): IntArrayList`, `Path.calcNodes(): IntIndexedContainer` —
    consumed by tools/ui/MiniGraphUI.java:377 (`IntIndexedContainer nodes = tmpPath.calcNodes()`).
  - `TarjanSCC.ConnectedComponents.getComponents(): List<IntArrayList>` and
    `.getSingleNodeComponents(): hppc BitSet` — consumed by
    reader-gtfs/.../gtfs/analysis/Analysis.java:20-36 (incl. a `BitSetIterator` loop!) via
    PtGraphAsAdjacencyList. reader-gtfs keeps hppc for its own maps, but THIS coupling forces a
    mechanical edit there (BitSetIterator → nextSetBit-style loop on our BitSet port).
  - `ReaderWay.getNodes(): LongArrayList`, `ArrayUtil` (~16 public IntArrayList-typed helpers),
    `GHUtility` (private-only hppc use, no leak), `MultiplePointsNotFoundException.pointsNotFound:
    IntArrayList`, `AvoidEdgesWeighting.setAvoidedEdges(IntSet)`, `QueryOverlay.adjustValues(
    LongArrayList,...)`, `EdgeRestrictions.getUnfavoredEdges(): IntArrayList`,
    `WayToEdgeConverter(LongFunction<Iterator<IntCursor>>)` (cursor type in a signature),
    `HeadingResolver.getEdgesWithDifferentHeading(): IntArrayList`.
  - Consumers constructing hppc types locally (no core-API coupling): tools/Measurement.java:541
    (IntArrayList), map-matching/MapMatching.java:452-453 (IntHashSet ×2, membership only).
  - web / web-api / web-bundle / navigation: ZERO hppc usage (PtIsochroneResource consumes the
    hppc-free ReadableTriangulation surface).
- (c) test-only: 40 Java test files import hppc (36× IntArrayList, 4× LongArrayList,
  4× IntCursor, 3× IntIndexedContainer, 3× IntHashSet, 2× IntSet, 2× BitSet, 1× IntObjectMap,
  1× DoubleArrayList) — see §5 for the minimal edit footprint.

### 2. Iteration-order audit — what is actually order-sensitive

Audited every forEach/cursor/values()/toArray() on a hash container in core main. Result:
almost everything is keyed access, membership, or order-independent aggregation (per-key writes,
max/min/count). The complete list of ORDER-SENSITIVE sites is in §4. Notable SAFE-by-audit sites
(for the record, so nobody re-audits): WayToEdgesMap (LongIntScatterMap keyed via
indexOf/indexGet/indexReplace; getEdges() returns insertion-ordered slices), OSMNodeData
LongScatterSet (membership), TarjanSCC + EdgeBasedTarjanSCC (scatter map/set keyed-only; DFS and
component order are graph-traversal-driven — component output is hash-independent),
PrepareRoutingSubnetworks, LandmarkStorage (both forEach sites do per-node keyed writes;
landmark selection is heap-driven), EdgeBasedNodeContractor's own sets (once-guard/dedup
membership), NodeBased CH prep entirely, RoadDensityCalculator (FP sum ordered by BFS deque, set
is membership-only), dem IntDoubleHashMap cursors (keyed writes; rejection loop collects-then-
removes), EdgeElevationSmoothingMovingAverage (keyed), QueryGraph/QueryRoutingCHGraph/
EdgeChangeBuilder (keyed copies), storage/index dedup sets, Dijkstra/AStar/bidir SPT maps during
search (PQ-driven), GraphHopper.kt (IndirectSort by Hilbert key, stable, hash-free).
Already-nondeterministic-today (default-mixed hppc = randomized per instance; androidx makes
them deterministic, no regression): LocationIndexTree/LineIntIndex dedup sets, Triangulation's
vertex maps — all keyed/membership anyway.

### 3. Type mapping table

| hppc | androidx / replacement | care needed |
|---|---|---|
| IntArrayList | MutableIntList | `.buffer`→`.content`, `.elementsCount`→`._size` (both PUBLIC in androidx — direct pokes in ArrayUtil, CHPreparationGraph.sortAndTrim/build, ArrayEdgeIntAccess, DijkstraOneToMany reset, EdgeBasedWitnessPathSearcher reset, WayToEdgeConverter map fine). `from(...)`→`mutableIntListOf(...)` (Java: `IntListKt.mutableIntListOf`). `toString()` format identical ("[1, 2]"). equals content-based both. **final class**: InMemConstructionIndex.InMemLeafEntry and DijkstraOneToMany.IntArrayListWithCap EXTEND IntArrayList → rewrite as composition (2 small refactors). No `remove(index)`→`removeAt`; `trimToSize`→`trim` |
| LongArrayList / DoubleArrayList | MutableLongList / MutableDoubleList (exists in 1.6.0 — DECISION section said no doubles; wrong, no gap needed) | same notes as IntArrayList |
| IntIndexedContainer / LongIndexedContainer | IntList / LongList (read-only base class) | calcNodes() etc. return types |
| IntContainer (CH contractNode returns) | IntSet (androidx base) at the NodeContractor seam — except the neighborSet itself, see §4 | iteration via forEach |
| IntHashSet / LongHashSet / IntScatterSet / LongScatterSet / IntIntScatterMap / IntObjectScatterMap / IntLongScatterMap / LongObjectHashMap / LongLongHashMap / IntLongHashMap / ObjectIntHashMap (keyed/membership sites only) | MutableIntSet / MutableLongSet / MutableIntIntMap / MutableIntObjectMap / MutableIntLongMap / MutableLongObjectMap / MutableLongLongMap / MutableObjectIntMap | `removeAll(key)->count` (OSMNodeData)→`remove():Boolean`; `release()`→`clear()` (keeps capacity! CH contractors call release to FREE memory — use fresh instance or accept retention, decide per site); `getOrDefault` exists; `putOrAdd/addTo`→`put(k, getOrDefault+..)`; `indexOf/indexGet/indexInsert/indexReplace` (WayToEdgesMap, RestrictionSetter) → plain containsKey/get/put (one extra probe, cold paths); `IntHashSet.from(...)`→`mutableIntSetOf` |
| GH* wrappers (GHIntObjectHashMap, GHIntHashSet, GHLongObjectHashMap, GHIntLongHashMap, GHLongLongHashMap, GHLongHashSet, GHObjectIntHashMap) + order-critical scatter sites | **Kotlin port of hppc hash layout** (same BitMixer, 0.75/0.5 load factors, same resize/probing ⇒ bit-identical iteration order), seed constant inlined; FQCNs unchanged so ~15 call-site files and 5 GH*-using test files need NO edit. Keyed-only wrappers (GHLongLongHashMap in OSMReader, GHLongObjectHashMap/GHIntLongHashMap/GHObjectIntHashMap/GHLongHashSet) MAY alternatively become thin androidx delegates — but the port skeleton is shared, uniformity is cheaper than a second review of "is it really keyed-only" | this is the §4 zero-checksum strategy; surface = only the methods actually used (audited); cursors → inline `forEach(k,v)`; `ObjectIntAssociativeContainer` param (GHObjectIntHashMap.putAll) → drop/narrow |
| BitSet | **gap-filler port `com.graphhopper.coll.GrowableBitSet`** (hppc BitSet is growable; java.util.BitSet's is too but field layout matters, androidx has none) | MUST keep exactly the Jackson-visible fields `bits: long[]` + `wlen: int` — ExternalBooleanEncodedValue's field is FIELD-serialized into stored graphs as `{"bits":[...],"wlen":N}` (pinned in EncodedValueSerializerTest). hppc-BitSet port satisfies this for free. Also update the two "Was meant to be hppc BitSet" ISE guards in TarjanSCC/EdgeBasedTarjanSCC (message text = type check, meaning preserved). reader-gtfs Analysis.java BitSetIterator loop → port's nextSetBit loop |
| IntArrayDeque | CircularIntArray (addLast/popFirst/popLast) | RoadDensityCalculator pokes `deque.head/tail` directly to clear → `clear()` (private fields in androidx); DepthFirstSearch uses addLast/removeLast → popLast |
| LongArrayDeque | **gap-filler port** (androidx has no CircularLongArray) — TarjanSCC + EdgeBasedTarjanSCC dfs stacks | hot during import (subnetwork prep on full graph) — keep array-backed like the hppc original |
| IntDoubleMap / IntDoubleHashMap / IntDoubleScatterMap | **gap-filler shim** over MutableIntLongMap via Double.toRawLongBits (no double-valued maps in androidx) | sites: QueryOverlay.WeightsAndTimes + QueryGraphWeighting (keyed), BridgeTunnelTowerCorrection ×2 + EdgeElevationSmoothingMovingAverage (keyed + order-safe cursor loops → forEach) |
| IndirectSort + IndirectComparator | **gap-filler port** (~80 lines, stable mergesort) | sites: ArrayUtil.calcSortOrder (public API takes IndirectComparator — replace param with a GH-owned `fun interface`), GraphHopper.sortGraphAlongHilbertCurve, CHPreparationGraph.build (AscendingIntComparator) |
| cursors (IntCursor...) / procedures / predicates | inline forEach lambdas (zero alloc from Kotlin); WayToEdgesMap/WayToEdgeConverter's public `Iterator<IntCursor>` seam → GH-owned primitive iterator or `LongFunction<IntList>` (small internal API redesign, 2 tests touch it) | AlternativeRoute*/QueryOverlayBuilder predicates always return true (audited: no early termination anywhere) → plain forEach |
| HashOrderMixing / HashOrderMixingStrategy | disappears into the port (seed constants 123321123321123312L and BridgePathFinder's 123 inlined/parameterized) | — |

### 4. Order-critical sites (each would alter deterministic outputs on a naive switch)

Zero-checksum strategy per site = use the hppc-layout port (§3). "Option B" = switch to androidx
order anyway and re-baseline — requires Peter signoff PER SITE; the germany gate would then
legitimately differ and pinned tests would need literal updates (conflicts with the
never-change-tests rule, hence not recommended).

STORED-GRAPH (checksum) sites:
1. **RestrictionSetter.kt** — `artificialEdgeKeysByIncViaPairs.forEach(LongIntProcedure)` (≈L99)
   + 4 nested `IntSet.forEach` sites (≈L78/109/126/145): turn-restriction CREATION order feeds
   TurnCostStorage's `turnCostsCount++` index + per-node linked-list prepend ⇒ on-disk turn-cost
   layout. Already documented in docs/pinned-behavior.md. → port LongIntScatterMap + IntScatterSet.
2. **EdgeElevationInterpolator.kt:98** — `outerNodeIds.toArray()` (GHIntHashSet) order feeds FP
   weighted-sum interpolation (ElevationInterpolator pointList loop) ⇒ stored tower-node
   elevations wherever a bridge/tunnel has ≥2 outer nodes. NEW finding (not in
   pinned-behavior.md yet — add entry when the batch lands). → GHIntHashSet port.
3. **CHPreparationGraph.kt:59** `neighborSet: IntScatterSet` — returned from `disconnect()`
   (code comment says it exists to guarantee deterministic order) and iterated at
   PrepareContractionHierarchies.kt:296 where `rand.nextInt(100)` is drawn PER NEIGHBOR in
   iteration order with capped updates ⇒ contraction order ⇒ node+edge shortcut counts/bytes.
   → IntScatterSet port.
4. **BridgePathFinder.kt:51** — result map is `IntObjectHashMap(16, 0.5,
   HashOrderMixing.constant(123))` — the constant exists precisely to pin its iteration order;
   iterated at EdgeBasedNodeContractor.kt:195 driving shortcut creation/dedup/ID assignment ⇒
   edge-based shortcut count 28,589,795. → seeded hash-map port (seed 123, loadFactor 0.5).

QUERY/TEST-VISIBLE sites (no stored bytes, but pinned tests / response content):
5. **QueryOverlayBuilder** — `edge2res.forEach(IntObjectPredicate)` (GHIntObjectHashMap)
   assigns virtual node ids SEQUENTIALLY per iterated edge ⇒ multi-snap requests get permuted
   virtual node ids; QueryGraphTest pins ids. → GHIntObjectHashMap port (site then needs no edit).
6. **AlternativeRoute.kt / AlternativeRouteCH.kt / AlternativeRouteEdgeCH.kt** — forEach over the
   bidir SPT maps collects candidates; later weight-sorts are STABLE ⇒ equal-weight tie order =
   map order; EdgeCH additionally builds a last-write-wins map by adjNode. → covered by the
   GHIntObjectHashMap port automatically (SPT maps in AbstractBidirAlgo/Dijkstra stay GH*).
7. **ShortestPathTree.kt** (isochrone) — `for (cursor in fromMap.values())` builds the isochrone
   edge list ⇒ polygon assembly input order. → covered by GHIntObjectHashMap port.
8. **RandomGraph.kt** — `LongArrayList(treeEdges)` materializes a LongScatterSet in hash order ⇒
   generated random test graphs change shape ⇒ randomized differential tests still valid but
   explore different cases (meaning preserved, coverage shifts). → LongScatterSet port (cheap,
   shares skeleton) keeps even this identical.
9. **ExternalBooleanEncodedValue.bits** — not order but FORMAT: nested JSON `{"bits":[..],
   "wlen":N}` is pinned stored-graph metadata → GrowableBitSet port with identical field names
   (see §3). No signoff needed — zero-diff by construction.

Port list therefore: seeded int-keyed hash map (generic value) + seeded IntHashSet +
IntScatterSet + LongIntScatterMap + LongScatterSet + GrowableBitSet + IndirectSort +
LongArrayDeque + IntDouble shim. Shared probing/mixing skeleton; estimate ~1.2–1.8k lines Kotlin
incl. KDoc + attribution headers, plus ~600–900 lines of new unit tests (each port gets an
iteration-order pin test whose expected literals are GENERATED FROM CURRENT HPPC BEHAVIOR before
the switch — this is the mechanical proof of order identity, complementing
AndroidxCollectionDeterminismTest which guards the androidx side).

### 5. Test footprint (core/src/test — never-change-tests rule)

All 40 files need mechanical edits (every one both imports AND constructs hppc types); ZERO can
stay untouched, but NONE changes meaning:
- 30 files: import swap + construction swap only (`IntArrayList.from(...)` →
  `mutableIntListOf(...)`, `new IntArrayList()` → `new MutableIntList()`); includes the
  set-equality asserts (content-based equals on both sides — order-independent, meaning intact).
- 4 files need small loop/buffer rewrites: EdgeBasedRoutingAlgorithmTest (1 IntCursor loop),
  EdgeBasedTarjanSCCTest (5 cursor loops), TarjanSCCTest (cursor loop + `Arrays.sort(c.buffer)`
  → `.content` slice or toArray), ArrayUtilTest (5× `.buffer` → `.content`/size-bounded).
- 1 cast fix: RoutingAlgorithmTest (`(IntArrayList) refPath.calcNodes()`).
- 2 files (WayToEdgeConverterTest, WayToEdgesMapTest) follow the `Iterator<IntCursor>` seam
  redesign; RestrictionSetterTest + OSMRestrictionSetterTest construct `new BitSet(n)` →
  GrowableBitSet (same ctor/set surface).
- toString pin QueryGraphTest `getRemovedEdges().toString()=="[1]"` — androidx format identical,
  no edit. GH-owned bitset toString pins (AbstractMyBitSetTest "{1, 12}", GHTBitSetTest) —
  GHBitSet family keeps its own toString, unaffected.
- 5 GH*-wrapper-using tests (elevation interpolator tests, GHUtilityTest, GraphHopperOSMTest,
  BreadthFirst/DepthFirstSearchTest) need NO edit under the port strategy (FQCNs stable).
- Every touched test file gets its own commit note per protocol rule 5.

### 6. Consumer-module footprint (mechanical, one commit each)

- tools: Measurement.java:541 local IntArrayList → MutableIntList; MiniGraphUI.java:377
  IntIndexedContainer → IntList (GHBitSet/GHTBitSet imports unaffected).
- map-matching: MapMatching.java:452-453 IntHashSet ×2 → MutableIntSet (membership only).
- reader-gtfs: pom gains its own `com.carrotsearch:hppc` dependency (currently inherited from
  core). Analysis.java: TarjanSCC results → List<MutableIntList> + GrowableBitSet loop rewrite
  (the ONLY logic-shaped consumer edit; still mechanical iteration).
- web/web-api/web-bundle/navigation: nothing.
- root pom: hppc stays in dependencyManagement for reader-gtfs; core/pom.xml drops it at the end.

### 7. Batch plan (each batch = one commit, gated; NEVER two maven runs concurrently)

Gates: G1 = `mvn -B -pl core test` green; G2 = full `mvn -B clean test`; G3 = germany LOAD gate
(kotlin core loads measurements/germany-gh-compat, anchors + route checksum 27749812 exact);
G4 = paired A/B query bench (measurements/ab-bench.sh); G5 = full fresh germany import,
ALL anchors exact.

- **H1 — gap-fillers, no call sites** (GrowableBitSet, IndirectSort+comparator fun-interface,
  LongArrayDeque, IntDouble shim) + unit tests + NOTICE.md entries. Gate G1.
  Risk: low. Checksum impact: none (unused).
- **H2 — hppc-layout hash ports, no call sites** (shared skeleton; seeded IntObject map +
  IntHashSet; scatter IntSet/LongSet/LongIntMap) + order-pin tests with literals harvested from
  live hppc (write the harvest test FIRST, commit the literals). Gate G1.
  Risk: medium (subtle layout math) — but fully unit-testable against hppc side-by-side while
  both are on the classpath. Checksum impact: none yet.
- **H3 — rewire GH* wrappers onto the H2 ports** (FQCNs unchanged; drop hppc inheritance,
  expose the audited method surface incl. inline forEach; BridgePathFinder result map → seeded
  port(123)). Touches ~15 main files' imports at most. Gates G1+G2+G3.
  Risk: THE critical batch — everything order-sensitive flips here in one reviewable step.
  Checksum impact: ZERO expected; G3 proves it (turn costs + routes exercised by load-gate).
- **H4 — order-critical scatter call sites** (RestrictionSetter, CHPreparationGraph.neighborSet,
  RandomGraph, ExternalBooleanEncodedValue→GrowableBitSet + serializer test green). Gates
  G1+G2+G3. Checksum impact: zero expected; if G3 differs → bisect within batch.
- **H5 — lists** (IntArrayList/Long/Double/IndexedContainer → androidx across ~50 files incl.
  public API Path/ReaderWay/ArrayUtil/TarjanSCC-results/QueryOverlay + the two
  extends-IntArrayList refactors + `Iterator<IntCursor>` seam) + consumer edits (tools,
  map-matching, reader-gtfs Analysis) + the 40 test files. Largest but most mechanical batch;
  may split H5a core-internal / H5b public-API+consumers+tests. Gates G1 then G2.
  Risk: medium (volume; `.content/._size` pokes; hot-path `Path.addEdge` etc.) — lists are
  insertion-ordered, NO checksum risk by construction. Perf note: IntList.forEach/get inline
  from Kotlin; hot loops already indexed.
- **H6 — deques + BitSet call sites** (DepthFirstSearch, RoadDensityCalculator,
  TarjanSCC/EdgeBasedTarjanSCC deques+bitsets+ISE guards, OSMReader encBits,
  PrepareRoutingSubnetworks, BaseGraphNodesAndEdges, GraphHopper.kt, ArrayUtil.isPermutation).
  Gates G1+G2. Checksum: none (bit arrays index-ordered).
- **H7 — keyed-only hash sites → androidx** (OSMNodeData, WayToEdgesMap, storage/index sets,
  RoundTripRouting, LandmarkStorage sets+forEach, EdgeBasedNodeContractor sets (+release()
  decision), BridgePathFinder internal map, EdgeBasedTarjanSCC start-edge maps, QueryOverlay
  scatter maps→shim, dem maps→shim, Triangulation, GHTBitSet internals). Gates G1+G2+G3.
  Risk: medium — this batch bets on the §2 "keyed-only" audit; G3 catches a wrong verdict.
- **H8 — drop hppc from core** (core/pom.xml removal; grep proves zero com.carrotsearch in
  core; reader-gtfs own dep; NOTICE.md final review — hppc entry now covers the derived ports).
  Gates G2 + G4 (paired A/B: CH prep + queries) + **G5 full fresh germany import — every
  deterministic anchor EXACT** + spot `-Dtest=GraphHopperTest` before starting G5.
- **H9 — docs**: pinned-behavior.md entries (elevation-interpolation order finding; "hash-port
  iteration order = stored-graph format" rule), progress log update, real-route diff report
  feeds the existing final checklist item.

Batches expected to change deterministic outputs: NONE (that is the point of H2–H4). If any G3/G5
gate differs, the offending site gets isolated into its own commit with an explicit
"changes stored output — needs Peter signoff" callout before proceeding.

### 8. Effort / risk estimate

- New code: ~2–2.5k lines (ports + shims) + ~1k lines new tests. Call-site edits: ~75 main
  files, 40 test files, 4 consumer files — mostly mechanical.
- Sessions: H1+H2 ≈ 2, H3+H4 ≈ 1.5 (careful review + gates), H5 ≈ 2, H6+H7 ≈ 1.5, H8+H9 ≈ 1
  (G5 germany import ≈ 5.5h wall-clock on this box, MMAP). Total ≈ 8 working sessions.
- Perf: SPT maps and all order-critical containers keep hppc's exact algorithm (ported) → hot
  routing paths are perf-neutral by construction; androidx sites are lists/keyed maps where
  androidx's layout is comparable or better; G4 gates it. Budget stays ±3%/identical counts.
- Biggest risks: (1) port-layout fidelity — mitigated by harvest-from-hppc order-pin tests in
  H2 while hppc is still on the classpath; (2) the keyed-only audit being wrong somewhere —
  mitigated by G3 after H3/H4/H7; (3) volume errors in H5 — mitigated by G2 and per-batch
  commits for rollback.
- Deviation from the earlier DECISION section (androidx + only 4 gap-fillers): the audit shows
  order-identity, not just determinism, is required at 9 sites → hash-layout ports added.
  DoubleArrayList gap turned out unnecessary (MutableDoubleList exists). IntArrayDeque count was
  4 files not 1. This keeps Peter's androidx decision for ~85% of usages and confines the
  hppc-derived code to `com.graphhopper.coll` with attribution, canary + order-pin tests.

### 9. Signoff needed from Peter before H3

- Confirm hybrid (Option A, zero-checksum) over Option B (accept androidx order + re-baseline
  germany anchors + update pinned tests) — per-site list in §4.
- `release()` semantics in CH contractors (free memory vs androidx clear-keeps-capacity).
- reader-gtfs: own hppc dep (planned) — confirm.

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
