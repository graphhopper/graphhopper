# Pinned behavior & test-coverage findings

This file records subtle, load-bearing behaviors of the code base that were **not covered by any
existing test** when discovered — each entry is effectively a coverage-gap report ("a test for
our tests"). When such a behavior is found, the rule is: write a new test that pins it, and
document it here (what / why it matters / which test locks it). Entries marked *recorded only*
are internal quirks with no externally observable effect — preserved but not testable from the
outside.

Origin: these findings surfaced during the Java→Kotlin core migration (see KOTLIN_MIGRATION.md),
where every conversion required proving behavior identical. The findings are valuable
independently of that migration.

## Pinned by new tests

- **Hash-container iteration order must be reproducible across JVM runs.** Stored graphs and
  several tests depend on it; with HPPC it is pinned via `HashOrderMixing.constant(...)`, for
  androidx.collection 1.6.0 it holds because of a fixed hash constant — but it is an
  implementation property, not an API guarantee.
  Test: `core/.../coll/AndroidxCollectionDeterminismTest.java` (asserts exact observed
  sequences). If it fails after a dependency upgrade: re-baseline deliberately — iteration-order
  dependent results change; do not just update the literals. (2026-07-02)

- **GHLongLongBTree with an even `maxLeafEntries` splits at that value while
  splitIndex/factor/initLeafSize derive from value+1** (the constructor assigns the field before
  the even→odd adjustment). All pre-existing tests used odd values, so the even case was
  untested. Observable via tree height; verified identical against the compiled pre-migration
  Java implementation (height=4 for maxLeafEntries=4 after 100 inserts).
  Test: `core/.../coll/GHLongLongBTreePinnedBehaviorTest.java`. (2026-07-02)

- **BitUtil.toBitString(byte[]) narrows the shift result back to byte per iteration**, so bits
  never leak between bytes, and the LITTLE variant emits bytes in reversed order. Untested
  before; verified against the pre-migration Java implementation.
  Test: `core/.../util/UtilPinnedBehaviorTest.java`. (2026-07-02)

- **AngleCalc.convertAzimuth2xaxisAngle rejects NaN** because the range check uses
  `Double.compare` — a plain `<`/`>` comparison (a tempting "cleanup") would silently accept
  NaN. Untested before; message text pinned.
  Test: `core/.../util/UtilPinnedBehaviorTest.java`. (2026-07-02)

- **Unzipper's progress listener reports one cumulative byte count for the whole archive** —
  never reset between entries — and stays silent for directories and empty files. Untested
  before (UnzipperTest never passed a listener).
  Test: `core/.../util/UtilPinnedBehaviorTest.java#unzipperProgressAccumulatesAcrossEntries`.
  (2026-07-02)

- **TranslationMap.countOccurence uses java.lang.String.split semantics**: trailing empty
  strings are dropped, leading ones kept, and `Helper.isEmpty` trims so whitespace-only input
  returns 0. Kotlin's `String.split(Regex)` keeps trailing empties, so the Kotlin version must
  keep delegating to `java.lang.String.split`. Untested before.
  Test: `core/.../util/UtilPinnedBehaviorTest.java#countOccurenceUsesJavaSplitSemantics`.
  (2026-07-02)

- **Encoded-value enum constant names and ORDER are persisted in stored graphs** (ordinals via
  EnumEncodedValue) — reordering or renaming silently corrupts graph loading. No test locked
  this before. All 23 small ev enums pinned name-by-name in order; Country/State pinned by
  count + reference constants; plus the deliberate toString asymmetries (HazmatTunnel "A",
  Country alpha3 "DEU"/"---", State "US-CA"/"-") and find() default asymmetries
  (RoadAccess unknown→YES but "permit"→PRIVATE; Surface alias map + colon stripping;
  Smoothness unknown→OTHER but empty→MISSING; Country.find unknown→null NOT MISSING).
  Expected values extracted from the pre-migration Java implementation.
  Test: `core/.../routing/ev/EvEnumOrderPinnedTest.java`. Appending new constants at the END
  is safe; any other failure must not be "fixed" by updating literals. (2026-07-02)

- **EncodedValue JSON is FIELD-based storage format**: the serializer uses
  `PropertyAccessor.FIELD=ANY` with getters invisible, so private backing-field names and
  @JsonCreator parameter order ARE the stored-graph format — renaming even a private field
  breaks loading of existing graphs. Only Int/Decimal/SimpleBoolean were pinned before;
  Enum/String/ExternalBoolean formats now pinned with strings extracted from the pre-migration
  Java implementation (round-trip included). Includes the pre-existing quirk that
  ExternalBooleanEncodedValue serializes its hppc BitSet field (which can never deserialize).
  Test: `core/.../routing/ev/EncodedValueSerializerPinnedTest.java`. (2026-07-02)

- **Weighting.roundWeight rounds half-UP (Math.round)** — CH stores rounded weights, and
  kotlin.math.round's half-even would silently change them for *.5 weights. No test hit a tie
  before. Test: `core/.../routing/weighting/WeightingPinnedBehaviorTest.java`. (2026-07-02)

- **KVStorage.get() derails on stored byte[] values of 251–255 bytes**: the skip expression
  `1 + b & 0xFF` parses as `(1 + b) & 0xFF`, so a 255-byte value advances the pointer by 0 and
  subsequent keys misparse (AssertionError under -ea, IndexOutOfBoundsException without).
  Strings are safe (cutString caps at 250); only raw byte[] values can trigger it. Candidate
  for a real upstream fix (`1 + (b & 0xFF)`) outside the migration — would need a
  storage-format review since existing graphs could contain such values.
  Test: `core/.../search/KVStoragePinnedBehaviorTest.java`. (2026-07-02)

## Recorded only (not externally observable)

- **Location-index on-disk format quirks preserved verbatim** (guarded by the germany
  load-gate): store() advances the write pointer even for null subtree entries; leaf cells
  encode single entries as -edgeId-1 vs an exclusive end pointer for multi-entry cells; tree
  geometry derives from long-accumulated parts via Math.round(sqrt) int truncation. Also:
  Snap.getSnappedPoint() throws ISE when uncalculated but toString() NPEs (field read);
  findClosest marks edges seen BEFORE the filter accepts them. (2026-07-02)

- **Graph binary-format quirks preserved verbatim in the Kotlin storage layer** (guarded
  end-to-end by the germany load-gate rather than unit pins): 40-bit signed geoRef whose 5th
  byte is deliberately NOT masked (sign-extension for negative refs, #2985); partial-int flag
  storage shift/mask combos; CH weightFromDouble rejects non-integer weights with message
  "weight must be an exact multiple of 1" and uses an unsigned 0xFFFFFFFF sentinel round-trip;
  TurnCostStorage.loadExisting prints getHeader(0) in the mismatch message while comparing
  getHeader(4); edge linked-list prepend order determines iterator order and therefore
  visited-node counts. (2026-07-02)

- **Unzipper accumulates progress via Java's compound assignment** `sumBytes += len * factor`,
  i.e. `(long) (sumBytes + len * factor)` — truncation towards zero after scaling by
  compressedSize/size. Preserved with an explicit `.toLong()` in Kotlin; the fractional-factor
  truncation is not pinned by a test because the only fixture (test.zip) contains stored
  (factor==1) entries; the cumulative sequence itself IS pinned (see above). (2026-07-02)

- **Downloader.downloadAndUnzip reports percentages as** `(int) (100 * sumBytes / length)`
  widened back to long — long division truncation, and a server not sending Content-Length
  (length == -1) yields negative "percentages". Not testable without an HTTP fixture.
  (2026-07-02)

- **MiniPerfTest.formatDuration picks units at 1e7/1e4 ns**, so everything above 10 ms is
  already printed in (fractional) seconds and 10 µs–10 ms in ms. Only observable through the
  timing-dependent getReport() string. (2026-07-02)

- **RamerDouglasPeucker.setMaxDistance normalizes with the DistanceCalc active at call time**:
  a later setApproximation(...) switches the calculator but does NOT recompute normedMaxDist,
  so the effective tolerance depends on the configuration call order. Preserved verbatim.
  (2026-07-02)

- **IntFloatBinaryHeap.trimTo has an empty fill range** (`Arrays.fill(elements, toSize+1,
  size+1, 0)` runs after `size = toSize`, so start == end): `clear()` never zeroes the arrays
  despite the comment saying it does. Stale values are hidden by size-bounded access. If this is
  ever "fixed", nothing should break — but the comment is currently misleading. Candidate for a
  deliberate upstream cleanup outside the migration. (2026-07-02)

- **GHLongLongBTree.getMemoryUsage rounds after long/long integer division** — the quotient is
  truncated before `Math.round(float)`, so the reported MB value is a truncated, not rounded,
  figure. (2026-07-02)

- **Constants' static init order and fallbacks** (system props → version → builddate → gitinfo;
  `"${project.version}"` → `VERSION="0.0", SNAPSHOT=true`; gitinfo rejected unless exactly 6
  lines) — preserved verbatim in the Kotlin object; hard to unit-test without resource
  manipulation, so recorded here instead. (2026-07-02)
- **StopWatch.getTimeString() formats with the default locale** (decimal comma under de_DE) —
  behavior preserved; flagged for a deliberate Locale decision in a later cleanup, outside the
  migration. (2026-07-02)

## Open candidates (currently covered only indirectly)

- JaroWinkler similarity computes intermediate results in **float** precision (not double);
  changing that would shift scores slightly. Exercised indirectly via NameSimilarityEdgeFilter
  tests.
- SpatialKeyAlgo.encode performs int shifts that can **overflow into the sign bit before
  widening to long** for coordinates near the 16-bit-per-axis boundary. Exercised indirectly via
  location-index tests.
