# Offline routing on Android

GraphHopper does not officially maintain Android as a target: the Android
demo app was removed after 1.0, and there is no dedicated Android build,
device testing, or CI for it. See [this discussion](https://github.com/graphhopper/graphhopper/issues/1940)
for the background and reasoning.

That said, embedding `graphhopper-core` directly as a library dependency in
an Android app, and calling it in-process to route between two points
offline, does work on modern GraphHopper (verified against **11.0**). This
page documents the technique and its caveats. It is community-contributed
and not covered by GraphHopper's own test suite or CI — if it stops working
on a later version, or you find a cleaner approach, please add to the
discussion linked above.

## The actual blocker: Janino, not the Java version

GraphHopper builds every weighting by compiling the profile's custom model
to JVM bytecode *at runtime*, using [Janino](https://janino-compiler.github.io/janino/),
a transitive dependency. Android's ART runtime only loads DEX bytecode, not
JVM class files, so this fails with `Cannot compile expression: can't load
this type of class file` and the graph never loads.

This is a narrower problem than "GraphHopper needs a newer JVM than Android
provides" — it's specifically the *runtime compilation* step that ART can't
do, and it has a narrow fix.

## The fix: a `WeightingFactory` that never compiles anything

Subclass `GraphHopper` and override `createWeightingFactory()` to return a
plain weighting instead of letting it build and compile the custom model:

```java
public class OfflineGraphHopper extends GraphHopper {
    @Override
    protected WeightingFactory createWeightingFactory() {
        return (profile, hints, disableTurnCosts) ->
            new SpeedWeighting(encodingManager.getDecimalEncodedValue(speedKeyFor(profile)));
    }
}
```

(`speedKeyFor(profile)` is a stand-in for whatever your profile's speed
encoded-value key actually is — it depends on your vehicle/profile setup.)

No compilation happens, so Janino is never invoked, and the graph loads.

### Why routing quality survives this

If your graph packs have **CH (Contraction Hierarchies) enabled**, the CH
shortcuts were pre-computed at *build* time using the real custom model — CH
queries read those stored weights, not the `Weighting` object handed to them
at load time. The overridden factory above only matters for the parts of
routing that still consult a live weighting at query time.

**This only holds with CH.** If a pack is ever built without CH, routing
silently falls back to the plain speed weighting above and **ignores
whatever the real custom model encoded** — one-ways, private roads, surface
preferences, access restrictions, all of it. Not a crash, not a warning:
routes that are simply wrong in ways that look plausible. Keep CH enabled
for any pack used this way, or implement a real, non-Janino `Weighting`
instead of a placeholder.

## Two more things this needs, both easy to miss

- **The profile hash must match the graph exactly** (the pack's `properties`
  file records `profiles=<name>|<hash>`, and loading fails if it doesn't
  match). Reproducing that hash from a `Profile` constructed in code is
  subtler than it looks:
  - `new Profile("car")` starts with a *non-null empty* custom model, so
    adding a `custom_model_files` hint throws `Do not use custom_model_files
    and custom_model together`.
  - Setting `customModel` directly from the profile's JSON produces the
    *wrong* hash.
  - What works: deserialize the profile from JSON with only
    `custom_model_files` set (leave `customModel` null) and let
    `GraphHopper.init()` resolve it itself. That reproduces the same hash
    the original build-time pipeline produced.
- **`import.osm.ignored_highways` must be set even when only loading a
  pre-built graph**, or `init()` refuses to proceed. It reads like an
  import-time-only setting but is checked on load too.

## Testing this without a device

Run the real graph on a desktop JVM with Janino explicitly removed from the
classpath. That reproduces ART's exact limitation — no JVM bytecode loading
— without needing an emulator or a device. If it loads and routes there, it
will load and route on Android. This is also a real, automatable check:
relevant to the lack of Android CI mentioned in
[#803](https://github.com/graphhopper/graphhopper/issues/803), since it
needs no Android runner at all, just the core jar and a modified classpath.

## Requirements

- GraphHopper 11.x core embedded as an Android library dependency.
- `minSdk 26` — GraphHopper 11 uses `MethodHandle.invoke`, which D8 cannot
  dex below API 26.
- CH-enabled graph packs (see the caveat above).

## Status

Verified end-to-end in a real Android app on GraphHopper 11.0: graph load in
roughly 0.1 s, a real multi-point route with turn instructions produced
on-device. Not tested against other GraphHopper versions. Questions or
reports of this working (or not) elsewhere are welcome on
[#1940](https://github.com/graphhopper/graphhopper/issues/1940).
