#!/bin/bash
# Paired A/B benchmark: alternates the JAVA baseline tools jar and the KOTLIN branch tools jar
# on the SAME prepared graph (java-written copy), queries only (no import/prep). Alternation +
# repetition separates VM/cache noise from real code differences on this small VM.
#
# Usage: measurements/ab-bench.sh [rounds]   (default 3 -> J K J K J K)
# Prereqs: kotlin jar built (mvn -B -DskipTests package -pl tools -am),
#          java baseline jar at /home/peter/gh-java-baseline/tools/target/,
#          graph at measurements/germany-gh-compat (copy of the java-written baseline graph).
# Results: measurements/ab/<timestamp>-{java,kotlin}-<round>.json + .log
set -e
cd "$(dirname "$0")/.."
ROUNDS=${1:-3}
STAMP=$(date +%Y%m%d-%H%M)
mkdir -p measurements/ab

JAVA_JAR=$(ls /home/peter/gh-java-baseline/tools/target/graphhopper-tools-*-jar-with-dependencies.jar)
KOTLIN_JAR=$(ls tools/target/graphhopper-tools-*-jar-with-dependencies.jar)

run() { # $1 label $2 jar $3 round
  java -cp "$2" -XX:+UseParallelGC -Xmx3g -Xms3g com.graphhopper.tools.Measurement \
    datareader.file=/home/peter/maps/germany-260621.osm.pbf \
    measurement.name="$1-r$3" measurement.folder=measurements/ab/ \
    measurement.filename="$STAMP-$1-$3.json" \
    measurement.clean=false measurement.stop_on_error=true measurement.json=true \
    measurement.count=5000 measurement.repeats=1 measurement.run_slow_routing=false \
    measurement.ch.node=true measurement.lm=true "measurement.lm.active_counts=[8]" \
    measurement.vehicle=car measurement.turn_costs=true \
    graph.dataaccess.default_type=MMAP \
    import.osm.ignored_highways=footway,cycleway,path,pedestrian,bridleway \
    prepare.min_network_size=10000 graph.location=measurements/germany-gh-compat \
    2>&1 | tee "measurements/ab/$STAMP-$1-$3.log" | grep -E "routingCH:|routingLM8:" || true
}

for r in $(seq 1 "$ROUNDS"); do
  echo "=== round $r: java ==="
  run java "$JAVA_JAR" "$r"
  echo "=== round $r: kotlin ==="
  run kotlin "$KOTLIN_JAR" "$r"
done

echo "=== summary (mean ms) ==="
python3 - "$STAMP" <<'EOF'
import json, glob, sys, statistics
stamp = sys.argv[1]
keys = ['routingCH.mean', 'routingCH_no_instr.mean', 'routingCH_edge.mean', 'routingLM8.mean',
        'routingCH.visited_nodes_mean', 'routingLM8.visited_nodes_mean']
for label in ('java', 'kotlin'):
    runs = [json.load(open(f))['metrics'] for f in sorted(glob.glob(f'measurements/ab/{stamp}-{label}-*.json'))]
    if not runs: continue
    print(f'--- {label} ({len(runs)} runs)')
    for k in keys:
        vals = [r[k] for r in runs if k in r]
        if vals:
            med = statistics.median(vals)
            print(f'  {k}: median={med:.4f} min={min(vals):.4f} max={max(vals):.4f}')
EOF
