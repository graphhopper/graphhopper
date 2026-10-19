# Measured matrix benchmarks

The complete in-process grid produced **960 measured requests**: 20 repetitions after 5 warm-ups for each of two profiles, eight workloads and three backends. Results below compare the shared flexible matrix with named pairwise Dijkstra and prepared-CH baselines on the same imported Monaco fixture. They do not establish commercial or regional throughput.

For **foot 100×100**, flexible request median was **152.718 ms**, versus 6,120.656 ms for pairwise Dijkstra and 4,112.076 ms for prepared CH. Median current-thread allocation was 39.896 MiB, versus 1,410.314 MiB and 796.399 MiB respectively.

For **car 100×100**, flexible request median was **114.655 ms**, versus 3,482.541 ms for pairwise Dijkstra and 1,807.315 ms for prepared CH. Median current-thread allocation was 53.119 MiB, versus 1,663.854 MiB and 977.520 MiB respectively.

The [http-benchmark-report.json](http-benchmark-report.json) HTTP run covers workloads of 10, 100 completed cells. The configured-CH route baseline had lower median latency in **0 of 4** profile/workload groups. The raw comparison recorded **3680** metric/reachability flags across repetitions. The separate case-bound CH precision proof is approved in the recorded final review; original flags remain unchanged.

The [regional-http-benchmark/http-benchmark-report.json](regional-http-benchmark/http-benchmark-report.json) HTTP run covers workloads of 3, 9, 12 completed cells. The configured-CH route baseline had lower median latency in **5 of 6** profile/workload groups. The raw comparison recorded **760** metric/reachability flags across repetitions. The separate case-bound CH precision proof is approved in the recorded final review; original flags remain unchanged.

A ratio is the named baseline median divided by the flexible median: values above 1 favor flexible; values below 1 favor that baseline. Request timings include production validation and exclude the harness's later consistency checks. Matrix JSON serialization is measured separately and added per sample; CH has no matrix serialization stage. HTTP end-to-end comparisons are reported separately.

Machine: `Intel(R) Core(TM) i7-10610U CPU @ 1.80GHz`; 8 JVM processors; physical RAM snapshot 48,904 MiB. OS `Windows 11 10.0 amd64`. JVM `Java HotSpot(TM) 64-Bit Server VM` 22.0.2; actual arguments `['-Xmx2g', '-Xms190m', '-Duser.language=en']`.

Data: bundled `core/files/monaco.osm.gz`, SHA-256 `cca67724618cf38c2e8ff39082b19daa03a66b0725a5025515feb0fc678dcdfa`, 1,217 imported nodes/1,619 edges. Foot is node-based; car uses the configured turn costs and 60-second U-turn penalty. Seed 20261019; exact tower-node coordinates and duplicates are exported in [benchmark-requests.json](benchmark-requests.json). These workloads use the explicit benchmark-only limits in [benchmark-metadata.json](benchmark-metadata.json).

Cold import/preparation: 2,230.481 ms total; OSM reader 889.337 ms; CH preparation for both profiles 579.838 ms. These phases are outside warm request timings. Backends rotate order within one JVM; these are warm repeated-request measurements, not independent cold-JVM samples. p95 uses nearest rank over 20 measured values.

## foot latency

All values are median / p95 milliseconds.

| Workload | Pairwise Dijkstra | Flexible matrix | Prepared CH calls | Dijkstra/flex | CH/flex | Flexible + JSON |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| local_1x10 | 7.559 / 9.065 | 2.170 / 2.721 | 5.954 / 8.576 | 3.48 | 2.74 | 2.385 / 2.997 |
| 1x100 | 51.229 / 59.360 | 7.845 / 10.697 | 23.541 / 29.772 | 6.53 | 3.00 | 8.147 / 10.853 |
| 1x1000 | 454.462 / 499.962 | 50.197 / 55.328 | 232.929 / 255.369 | 9.05 | 4.64 | 50.923 / 56.098 |
| 10x10 | 42.103 / 54.800 | 10.016 / 12.622 | 25.353 / 29.388 | 4.20 | 2.53 | 10.198 / 12.748 |
| 10x100 | 374.149 / 403.590 | 13.842 / 16.443 | 231.480 / 258.765 | 27.03 | 16.72 | 14.341 / 17.009 |
| 100x10 | 381.006 / 427.862 | 88.841 / 113.155 | 230.945 / 280.685 | 4.29 | 2.60 | 89.334 / 113.724 |
| 100x100 | 6,120.656 / 8,620.233 | 152.718 / 236.447 | 4,112.076 / 5,234.240 | 40.08 | 26.93 | 159.502 / 244.900 |
| duplicate_100x100 | 2,852.930 / 3,045.254 | 4.376 / 5.350 | 1,877.957 / 2,032.700 | 652.00 | 429.18 | 6.966 / 8.425 |

## foot current-thread allocation

MiB allocated per request (median); these are allocation volumes, not retained/peak/RSS memory.

| Workload | Dijkstra MiB | Flexible MiB | CH MiB | Dijkstra/flex | CH/flex | Flexible JSON MiB |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| local_1x10 | 1.060 | 0.315 | 0.770 | 3.36 | 2.44 | 0.009 |
| 1x100 | 17.051 | 3.036 | 8.187 | 5.62 | 2.70 | 0.075 |
| 1x1000 | 174.306 | 28.522 | 82.773 | 6.11 | 2.90 | 0.709 |
| 10x10 | 15.758 | 3.781 | 8.738 | 4.17 | 2.31 | 0.037 |
| 10x100 | 143.332 | 6.378 | 80.255 | 22.47 | 12.58 | 0.315 |
| 100x10 | 139.952 | 34.295 | 80.261 | 4.08 | 2.34 | 0.315 |
| 100x100 | 1,410.314 | 39.896 | 796.399 | 35.35 | 19.96 | 3.217 |
| duplicate_100x100 | 1,128.626 | 2.174 | 696.574 | 519.17 | 320.43 | 2.859 |

## car latency

All values are median / p95 milliseconds.

| Workload | Pairwise Dijkstra | Flexible matrix | Prepared CH calls | Dijkstra/flex | CH/flex | Flexible + JSON |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| local_1x10 | 1.998 / 3.440 | 0.836 / 1.404 | 3.427 / 7.164 | 2.39 | 4.10 | 0.887 / 1.504 |
| 1x100 | 64.154 / 82.831 | 6.610 / 9.735 | 32.950 / 45.251 | 9.71 | 4.98 | 6.805 / 9.941 |
| 1x1000 | 389.657 / 434.330 | 36.706 / 45.033 | 188.008 / 206.879 | 10.62 | 5.12 | 37.500 / 45.917 |
| 10x10 | 37.436 / 43.594 | 10.499 / 12.136 | 18.659 / 20.287 | 3.57 | 1.78 | 10.584 / 12.232 |
| 10x100 | 343.869 / 416.800 | 14.014 / 21.043 | 175.979 / 231.477 | 24.54 | 12.56 | 14.425 / 21.730 |
| 100x10 | 363.471 / 386.728 | 110.580 / 125.363 | 171.395 / 197.163 | 3.29 | 1.55 | 111.006 / 125.856 |
| 100x100 | 3,482.541 / 4,449.421 | 114.655 / 140.100 | 1,807.315 / 2,211.704 | 30.37 | 15.76 | 118.433 / 144.709 |
| duplicate_100x100 | 2,516.244 / 3,768.813 | 4.221 / 5.781 | 1,489.275 / 1,549.366 | 596.14 | 352.83 | 6.824 / 9.180 |

## car current-thread allocation

MiB allocated per request (median); these are allocation volumes, not retained/peak/RSS memory.

| Workload | Dijkstra MiB | Flexible MiB | CH MiB | Dijkstra/flex | CH/flex | Flexible JSON MiB |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| local_1x10 | 0.974 | 0.341 | 0.966 | 2.86 | 2.84 | 0.009 |
| 1x100 | 19.626 | 2.918 | 10.245 | 6.73 | 3.51 | 0.070 |
| 1x1000 | 198.290 | 26.985 | 104.157 | 7.35 | 3.86 | 0.656 |
| 10x10 | 17.735 | 4.788 | 9.543 | 3.70 | 1.99 | 0.039 |
| 10x100 | 166.939 | 7.628 | 94.277 | 21.88 | 12.36 | 0.320 |
| 100x10 | 171.562 | 48.418 | 94.245 | 3.54 | 1.95 | 0.299 |
| 100x100 | 1,663.854 | 53.119 | 977.520 | 31.32 | 18.40 | 3.140 |
| duplicate_100x100 | 1,280.803 | 2.359 | 853.974 | 543.00 | 362.05 | 2.953 |

## Work counters and output

Completed cells include diagnosed null outcomes. Reachable cells count numeric routes only; nulls never become successful routes. Flexible throughput here includes its separately measured serialization. Searches/visited states are measured counters; unavailable upstream counts stay null.

| Profile / workload | Completed / reachable / null | Flexible searches / visited | Completed / reachable cells/s with JSON | JSON KiB | Request GC count / ms over 20 repeats |
| --- | ---: | ---: | ---: | ---: | ---: |
| foot / local_1x10 | 10 / 10 / 0 | 1 / 22 | 4,192 / 4,192 | 1.828 | 0 / 0 |
| foot / 1x100 | 100 / 100 / 0 | 1 / 1,154 | 12,275 / 12,275 | 11.015 | 2 / 9 |
| foot / 1x1000 | 1000 / 971 / 29 | 1 / 1,177 | 19,637 / 19,068 | 102.262 | 4 / 4 |
| foot / 10x10 | 100 / 100 / 0 | 10 / 11,395 | 9,806 / 9,806 | 6.330 | 1 / 0 |
| foot / 10x100 | 1000 / 1000 / 0 | 10 / 11,568 | 69,729 / 69,729 | 48.820 | 1 / 1 |
| foot / 100x10 | 1000 / 1000 / 0 | 100 / 113,764 | 11,194 / 11,194 | 49.183 | 5 / 7 |
| foot / 100x100 | 10000 / 10000 / 0 | 100 / 115,738 | 62,695 / 62,695 | 424.489 | 6 / 9 |
| foot / duplicate_100x100 | 10000 / 10000 / 0 | 5 / 5,172 | 1,435,523 / 1,435,523 | 384.231 | 1 / 1 |
| car / local_1x10 | 10 / 10 / 0 | 1 / 202 | 11,272 / 11,272 | 2.020 | 0 / 0 |
| car / 1x100 | 100 / 95 / 5 | 1 / 1,425 | 14,696 / 13,961 | 12.607 | 0 / 0 |
| car / 1x1000 | 1000 / 929 / 71 | 1 / 1,806 | 26,667 / 24,773 | 114.511 | 3 / 4 |
| car / 10x10 | 100 / 100 / 0 | 10 / 13,247 | 9,448 / 9,448 | 6.917 | 2 / 3 |
| car / 10x100 | 1000 / 953 / 47 | 10 / 14,268 | 69,323 / 66,065 | 53.586 | 0 / 0 |
| car / 100x10 | 1000 / 874 / 126 | 100 / 138,247 | 9,008 / 7,873 | 52.538 | 13 / 16 |
| car / 100x100 | 10000 / 9225 / 775 | 100 / 138,270 | 84,436 / 77,892 | 453.833 | 9 / 14 |
| car / duplicate_100x100 | 10000 / 10000 / 0 | 5 / 4,826 | 1,465,513 / 1,465,513 | 412.355 | 0 / 0 |

The raw CSV retains request, serializer and snapping/QueryGraph/backend/core-search/scatter phase timings, actual counters, output bytes, allocation deltas, heap-pool peak sums and GC observations for every repetition. Reference internals not exposed by ordinary routing are null. Phase medians do not necessarily sum to the median total.

`ThreadMXBean` allocation is recorded only when supported and already enabled; the benchmark does not enable it. It measures the serial calling thread and excludes allocations on other threads. Heap-pool peak sums include the live shared-JVM footprint and are not simultaneous peak heap, per-request retained memory or RSS. They must not be used to claim a smaller resident footprint. See [benchmark-analysis.json](benchmark-analysis.json) for median/p95 allocation, request/serialization GC totals and measured heap values for every named baseline.

## HTTP comparisons

### http-benchmark-report.json

Status **MEASURED** at `http://localhost:8997`; 160 measured samples, 5 warm-ups and 20 measured repeats per group. Request-set SHA-256 `71f16a4c08b450a7d7e42bd030a748752167f4b8ad4c8bbe6beea621e7ba9aab`. Timings include persistent HTTP transport, server serialization and client JSON parsing/validation; request bodies are pre-encoded. Output bytes exclude headers.

Server configuration SHA-256 `b394046edf588107ed1acfb34466de90809b21ab8154a598ee102241cffc9c9c`. Actual server JVM flags, JAR/config identities, isolated graph directories and ports are retained in [runtime-manifest.json](runtime-manifest.json); each report records matrix graph/profile/build fingerprints. Bundled Monaco data and exact coordinates are pinned in [benchmark-metadata.json](benchmark-metadata.json) and [benchmark-requests.json](benchmark-requests.json). These are warm requests on an already prepared server; cold import/CH preparation and server RSS/allocation were not measured by this HTTP harness.

| Profile / workload | Configured-CH HTTP routes median / p95 ms | Flexible HTTP matrix median / p95 ms | Route/matrix ratio | Matrix completed / reachable cells/s | Matrix body bytes |
| --- | ---: | ---: | ---: | ---: | ---: |
| foot / local_1x10 | 28.106 / 33.639 | 3.175 / 4.312 | 8.85 | 3,150 / 3,150 | 1,930 |
| foot / 1x100 | 158.532 / 224.523 | 8.948 / 13.880 | 17.72 | 11,186 / 11,186 | 11,337 |
| car / local_1x10 | 19.659 / 25.788 | 2.397 / 3.434 | 8.20 | 4,173 / 4,173 | 2,126 |
| car / 1x100 | 146.961 / 196.127 | 7.819 / 11.963 | 18.79 | 12,793 / 12,154 | 12,967 |

Paired HTTP metric/reachability disagreements across repetitions: **3680**; individual cases remain in the report and are not automatically waived. Retained diagnostics identify 184 distinct profile/workload/index cases; diagnostic kinds: `['weight']`. Connection evidence: `{'http_route_configured_ch': {'opened': 1, 'server_closes': 0}, 'http_matrix_flexible': {'opened': 1, 'server_closes': 0}}`. Upstream HTTP JSON does not identify the chosen CH backend; captured startup/runtime and actual route debug logs independently document CH selection. Separate [ch-weight-reconciliation.json](ch-weight-reconciliation.json) status **PASS_WITH_PATH_PROVEN_CH_STORAGE_PRECISION**, eligible **True**, explicitly approved **True**. It checks 184 indexed cases/184 unique pairs and 300 retained scalar records. Fresh paths prove the recorded cases; capped diagnostics omit some historical repetition paths/scalars, and stable aggregate checksums cannot exclude compensating changes in omitted records.

### regional-http-benchmark/http-benchmark-report.json

Status **MEASURED** at `http://localhost:8999`; 240 measured samples, 5 warm-ups and 20 measured repeats per group. Request-set SHA-256 `34add677b7bc0bb47408b0d0a292c226d7a5cf1900d223cc87ba247334b02ba0`. Timings include persistent HTTP transport, server serialization and client JSON parsing/validation; request bodies are pre-encoded. Output bytes exclude headers.

Server configuration SHA-256 `12a53dbb28af8abbbc5b38038de70192dff07cf6767b2e3a4185a595ed4605f1`. Actual server JVM flags, JAR/config identities, isolated graph directories and ports are retained in [runtime-manifest.json](runtime-manifest.json); each report records matrix graph/profile/build fingerprints. Regional inputs are pinned in [regional-data.json](regional-data.json). These are warm requests on an already prepared server; cold import/CH preparation and server RSS/allocation were not measured by this HTTP harness.

| Profile / workload | Configured-CH HTTP routes median / p95 ms | Flexible HTTP matrix median / p95 ms | Route/matrix ratio | Matrix completed / reachable cells/s | Matrix body bytes |
| --- | ---: | ---: | ---: | ---: | ---: |
| foot / manchester_one_to_many | 11.813 / 15.622 | 21.725 / 24.096 | 0.54 | 138 / 138 | 1,479 |
| car / manchester_one_to_many | 12.049 / 16.684 | 21.688 / 24.856 | 0.56 | 138 / 138 | 1,440 |
| foot / manchester_rectangular | 38.937 / 44.225 | 38.181 / 45.631 | 1.02 | 314 / 314 | 2,142 |
| car / manchester_rectangular | 31.048 / 34.483 | 35.515 / 42.774 | 0.87 | 338 / 338 | 2,045 |
| foot / manchester_square | 24.032 / 28.431 | 47.000 / 49.642 | 0.51 | 191 / 191 | 1,856 |
| car / manchester_square | 19.361 / 21.950 | 33.661 / 36.819 | 0.58 | 267 / 267 | 1,790 |

Paired HTTP metric/reachability disagreements across repetitions: **760**; individual cases remain in the report and are not automatically waived. Retained diagnostics identify 38 distinct profile/workload/index cases; diagnostic kinds: `['weight']`. Connection evidence: `{'http_route_configured_ch': {'opened': 1, 'server_closes': 0}, 'http_matrix_flexible': {'opened': 1, 'server_closes': 0}}`. Upstream HTTP JSON does not identify the chosen CH backend; captured startup/runtime and actual route debug logs independently document CH selection. Separate [regional-http-benchmark/ch-weight-reconciliation.json](regional-http-benchmark/ch-weight-reconciliation.json) status **PASS_WITH_PATH_PROVEN_CH_STORAGE_PRECISION**, eligible **True**, explicitly approved **True**. It checks 38 indexed cases/20 unique pairs and 520 retained scalar records. Fresh paths prove the recorded cases; capped diagnostics omit some historical repetition paths/scalars, and stable aggregate checksums cannot exclude compensating changes in omitted records.

## Coverage limits

The first interrupted recorder-only benchmark attempt is preserved separately and is excluded from these 960 samples. No commercial-speed claim, national-scale throughput or regional performance result is inferred from Monaco. Ordinary CH is a strong pairwise baseline; a dedicated CH matrix is not implemented. Imported graphs, selected profiles and these coordinate distributions bound the comparison. Correctness evidence remains in [validation-report.md](validation-report.md), including every individually classified virtual-split/restriction-copy discrepancy.
