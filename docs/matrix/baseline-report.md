# Unmodified upstream baseline

The pristine upstream reactor run **failed** with one existing PT application-startup error. It is not a green full baseline.

Commit: `ebb578f4e94db72e82f2f8d3bc69417758e16e1c`. Pristine worktree Git status was clean before the replay. Command `mvn -B -pl web -am verify` exited 1 after the log's 06:33 minutes.

| Module | Tests | Passed | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| client-hc | 24 | 24 | 0 | 0 | 0 |
| core | 2564 | 2545 | 0 | 0 | 19 |
| map-matching | 0 | 0 | 0 | 0 | 0 |
| navigation | 31 | 30 | 0 | 0 | 1 |
| reader-gtfs | 305 | 100 | 0 | 0 | 205 |
| web | 201 | 199 | 0 | 1 | 1 |
| web-api | 43 | 43 | 0 | 0 | 0 |
| web-bundle | 9 | 9 | 0 | 0 | 0 |
| **Total** | **3177** | **2950** | **0** | **1** | **226** |

Counts combine original Surefire and Failsafe XML reports. reader-gtfs includes 284 Surefire cases (205 skipped) plus 21 passing Failsafe integration cases; map-matching reported no test sources. A class-level startup failure counts as one reported error; it does not mean all eleven TripBased methods executed.

The sole error is `PtRouteResourceTripBasedTest`: `FileNotFoundException: ./target/gtfs-app-gh/pt_to_street`. The ordinary `PtRouteResourceTest` completed its eleven tests immediately before it.

Both upstream test classes hardcode `web/target/gtfs-app-gh` and call `Helper.removeDir` in both `@BeforeAll` and `@AfterAll`. Their Dropwizard extension starts/stops the application around those lifecycle methods. `Helper.removeDir` uses `File.delete()` and callers ignore unsuccessful deletions. The GTFS storage uses memory-mapped MapDB files, then decides that a cache exists from `transit_schedule` alone before deserializing `pt_to_street`.

After the failure the directory contained `gtfs_0`, `gtfs_0.p`, `location_index`, `transit_schedule` and `transit_schedule.p`, with no mapping files. Together with the passing isolated replay below, this supports a Windows partial-cleanup/cache-lifecycle collision: retained MapDB files make the next class try loading an incomplete cache. Individual Windows file-handle events were not instrumented. No matrix code exists in this pristine worktree.

Original failure evidence is retained in `../../../audit/baseline-pristine-maven.log` and the copied `../../../audit/baseline-pristine-pt-original.xml`. File hashes, exact counts, source anchors and remaining cache filenames are in [baseline-report.json](baseline-report.json).

## Safe focused replay

The root agent authorized clearing only the resolved generated `graphhopper-baseline/web/target/gtfs-app-gh` directory after its JVM exited, preserving the full original failure evidence and all Java source. The replay then selects the TripBased class alone:

```bat
cd graphhopper-baseline
mvn -B -pl web -am test -Dtest=PtRouteResourceTripBasedTest -Dsurefire.failIfNoSpecifiedTests=false
```

Replay status: **PASS**. Exit 0; 11 reported tests, 0 failures, 0 errors and 0 skipped, in 44.598 seconds. Both pre/post Git status were clean. Only the verified generated GTFS test cache was removed; no Java source changed. The isolated success supports the cache/order diagnosis, while the original full reactor remains FAIL. Replay log: `../../../audit/baseline-pt-isolated.log`.

## Narrow fixture-lifecycle correction

The dependency's pinned source confirms that automatic lifecycle handling calls
the static application extension before/after class methods. Its `after()` is
reference counted, so manually stopping the app while retaining automatic
extension handling would invoke it twice. The applied feature-branch test-only
correction removes automatic registration from these two classes, checks any
existing owned directory before explicitly starting the application in
`@BeforeAll`, and explicitly stops it once before best-effort cleanup in `@AfterAll`.
It changes no routing request inputs, existing assertions, expected values or
production code. The two classes use unique per-run UUID suffixes under
`target/gtfs-route-resource-gh-*` and `target/gtfs-trip-based-resource-gh-*`.
The feature API-package run passed all original PT routing assertions, but
post-stop cleanup still left mapped files on Windows/JDK22; its added cleanup
assertion failed in both classes (`../../../audit/matrix-api-package.log`).
The final fixture change reports retained files without asserting immediate
unmapping, and unique per-run ownership prevents reuse of incomplete caches.
Optional checked removal after the test JVM exits is provided by
`scripts/cleanup-pt-test-caches.py`.
The final feature `mvn -B verify` passed, including all 11 methods in each PT
class with zero failures/errors. The original pristine baseline result and source
remain unchanged; see [full-verification.json](full-verification.json).

Dependency source: [DropwizardExtensionsSupport 4.0.16](https://github.com/dropwizard/dropwizard/blob/v4.0.16/dropwizard-testing/src/main/java/io/dropwizard/testing/junit5/DropwizardExtensionsSupport.java),
[DropwizardAppExtension 4.0.16](https://github.com/dropwizard/dropwizard/blob/v4.0.16/dropwizard-testing/src/main/java/io/dropwizard/testing/junit5/DropwizardAppExtension.java),
and [DropwizardTestSupport 4.0.16](https://github.com/dropwizard/dropwizard/blob/v4.0.16/dropwizard-testing/src/main/java/io/dropwizard/testing/DropwizardTestSupport.java).
