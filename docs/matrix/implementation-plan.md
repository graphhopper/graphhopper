# Implementation plan

1. G0: preserve the unmodified Maven baseline and pack verification evidence.
2. G1: strict API, bounded request/resource handling and ordinary routing reference.
3. G2: target-set Dijkstra with directed edge state and request QueryGraph snapping.
4. G3: independent fixture/differential/random/HTTP/OSM/concurrency checks.
5. G4: measured benchmark comparisons, complete runnable configuration and release notes.
6. C1: opt-in node-based bucket CH candidate backend, explicit flexible certification and fallbacks.
7. C2: independent synthetic/QueryGraph/API tests, exact public-output checks, and fresh candidate/baseline diagnoses.
8. C3: new Monaco and Manchester comparisons including bucket preparation, metric extraction, mandatory certification and allocation.
9. C4: full Maven verification, updated artifact/provenance, commit and PR into `hulsiejames/graphhopper:matrix-base-11.1`.

Integration is additive: core matrix service/search; web-api request model;
web-bundle resource and registration; independent core and web tests.
Do not modify upstream routing semantics to make comparisons agree.
Serial row searches and bounded request admission avoid shared mutable search state.
Record each failed or unavailable gate accurately and preserve replay evidence.
The user has authorized the CH milestone and publication to their fork only. Existing
core-release evidence remains historical and immutable; new CH evidence uses separate paths.
`auto` stays flexible. Existing CH preparation cannot guarantee the original double-cost
optimum, so the experiment always verifies/corrects against the complete-overlay flexible search.
