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

## Recorded only (not externally observable)

- **IntFloatBinaryHeap.trimTo has an empty fill range** (`Arrays.fill(elements, toSize+1,
  size+1, 0)` runs after `size = toSize`, so start == end): `clear()` never zeroes the arrays
  despite the comment saying it does. Stale values are hidden by size-bounded access. If this is
  ever "fixed", nothing should break — but the comment is currently misleading. Candidate for a
  deliberate upstream cleanup outside the migration. (2026-07-02)

- **GHLongLongBTree.getMemoryUsage rounds after long/long integer division** — the quotient is
  truncated before `Math.round(float)`, so the reported MB value is a truncated, not rounded,
  figure. (2026-07-02)

## Open candidates (currently covered only indirectly)

- JaroWinkler similarity computes intermediate results in **float** precision (not double);
  changing that would shift scores slightly. Exercised indirectly via NameSimilarityEdgeFilter
  tests.
- SpatialKeyAlgo.encode performs int shifts that can **overflow into the sign bit before
  widening to long** for coordinates near the 16-bit-per-axis boundary. Exercised indirectly via
  location-index tests.
