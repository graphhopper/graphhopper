# Run the local matrix fork

Use cmd.exe from the repository root. Java and Maven must be on PATH.
The release source targets JDK17; this workspace uses Oracle JDK22.0.2 and Maven3.9.9.
The artifact's actual Maven version is11.0-SNAPSHOT, despite upstream's11.1 tag.

The original core-release reports remain historical evidence. The subsequently
authorized CH experiment is described in [ch-matrix-design.md](ch-matrix-design.md)
and [ch-matrix-validation.md](ch-matrix-validation.md); its new comparisons use
separate files and do not replace the original measurements. Development and the
review PR target [hulsiejames/graphhopper](https://github.com/hulsiejames/graphhopper),
with the `matrix-base-11.1` PR base pinned to the inspected release.

```bat
mvn -B -pl web -am verify
run-matrix.cmd
```

Monaco listens at http://localhost:8997, admin8998. The independent street profiles
are `foot` and turn-aware `car` (60-second U-turn cost). Both have ordinary CH
preparations for the strong point-to-point benchmark. Matrix auto uses shared flexible
Dijkstra. The optional `ch_experimental` backend uses node-based CH destination
buckets plus mandatory flexible verification; `auto` remains flexible.

```bat
python scripts\client.py --url http://localhost:8997 --profile foot --output matrix.json --csv matrix.csv
python scripts\client.py --url http://localhost:8997 --profile car --algorithm reference --output reference.json
python scripts\client.py --url http://localhost:8997 --profile foot --algorithm ch_experimental --output ch-matrix.json
```

The downloaded Greater Manchester PBF is separately checksum-pinned in
[regional-data.json](regional-data.json); its51.6MB file is outside the source checkout.
Use its isolated example configuration after verifying the file exists:

```bat
run-matrix.cmd regional
python scripts\client.py --url http://localhost:8999 --request docs\matrix\examples\manchester_one_to_many.json --output manchester-one-to-many.json
python scripts\client.py --url http://localhost:8999 --request docs\matrix\examples\manchester_rectangular.json --output manchester-matrix.json
python ..\prompt_pack\GraphHopper_Local_Matrix_Codex_Pack\validation\validate_http.py --base-url http://localhost:8999 --request ..\prompt_pack\GraphHopper_Local_Matrix_Codex_Pack\examples\manchester_rectangular.json --output docs\matrix\http-smoke-manchester.json
```

Manchester listens at8999, admin9000, with a4GiB maximum heap (Monaco uses2GiB).
Both examples use separate target graph
directories and retain configured request/memory admission limits. Stop with Ctrl+C.
No existing production graph or running service is replaced.

The CH experiment supports prepared static node profiles such as this example's
`foot`. Turn-aware `car` uses an explicitly reported flexible fallback. Every
experimental row is independently checked/corrected by flexible routing, including
all requested metrics and nulls. Its additional work is counted in the request
limits and measured latency; see the experiment reports before choosing it.

The Python tools use only the standard library. For optional pandas conversion,
create a separate Conda environment, install Conda packages before any pip packages,
and retain nulls as missing values:

```bat
conda create -n graphhopper-matrix-client python=3.12 pandas
conda run -n graphhopper-matrix-client python scripts\client.py --output matrix.json
```

```python
import json
import pandas as pd
with open("matrix.json", encoding="utf-8") as stream:
    result = json.load(stream)
times = pd.DataFrame(result["times_ms"], dtype="Int64")
```

Read [api.md](api.md) for units, supported fields, null/error semantics and operational
limits, [design.md](design.md) for the algorithm, and [upgrade-notes.md](upgrade-notes.md)
before rebasing.

[Validation evidence](validation-report.md) records the full Maven gate, independent
oracles, regional replay and [manual geometry review](manual-geometry-review.json).
[Measured benchmarks](benchmark-report.md) cover 960 in-process Monaco samples and
400 HTTP samples. The shared matrix improved the recorded Monaco workloads;
prepared CH routes were faster for five of six small Manchester profile/workload
groups. These measurements do not establish national-scale or commercial throughput.

The raw HTTP comparisons retain weight-only `MISMATCH` results. Their separate
[Monaco](ch-weight-reconciliation.json) and
[Manchester](regional-http-benchmark/ch-weight-reconciliation.json) path proofs
classify specific CH storage-precision differences without changing routing or
widening a global tolerance. Read their retained-history limits alongside
[the final review](final-review.json), [validation summary](validation-summary.json),
[artifact provenance](provenance.json) and [captured runtimes](runtime-manifest.json).
Those artifact/runtime identities belong to the earlier core release. A new CH
artifact and evidence identity is recorded separately in
[ch-matrix-release.json](ch-matrix-release.json).

The accepted CH build uses committed source
`a9102e2039b079a43dcb1494d77619e4e989acaa`. Its full reactor passed 3,071 tests,
with 232 skipped and zero failures/errors. The
[final live smoke](ch-matrix-live-smoke-final.json) checks the packaged server's
duplicate axes, CH selection, exact flexible equality, car fallback, nulls and
strict errors, then confirms both ports closed. The
[client check](ch-matrix-client-validation.json) also verifies six output cells
and their CSV round trip. The rejected stale packaging attempt and the first
smoke's incorrect all-connected car-fixture assumption remain separate evidence.

The follow-on [comparison report](ch-matrix-benchmark-report.md) includes 600 fresh
in-process measurements across Monaco and Manchester. Monaco is the small bundled
map extract; a case is one origin/destination comparison, not a production incident.
Certified CH includes flexible verification and is slower than flexible routing
on these workloads. Ordinary prepared CH is much faster for the Manchester
100x100 grid. That stress measurement used elevated limits and does not complete
within the shipped matrix defaults. Keep `auto` on flexible routing.
