#!/bin/bash
# usage:
# benchmark/benchmark.sh <graph_dir> <results_dir> <summary_dir> <small_osm_map_path> <big_osm_map_path> <use_measurement_time_as_ref_time>
#
# where:
# <graph_dir> = name of directory used to store graphs
# <results_dir> = name of directory where results of this run are stored
# <summary_dir> = name of directory where the summary file is stored
# <small_osm_map_path> = path to osm map the measurement is run on for slow measurements
# <big_osm_map_path> = path to osm map the measurement is run on for fast measurements
# <use_measurement_time_as_ref_time> = true/false, false by default, meaning the git commit time will be used as reference

# make this script exit if a command fails, a variable is missing etc.
set -euo pipefail
# print all commands
set -o xtrace

defaultGraphDir=measurements/
defaultResultsDir=measurements/results/$(date '+%d-%m-%Y-%s%N')/
defaultSummaryDir=measurements/
defaultSmallMap=map-matching/files/leipzig_germany.osm.pbf
defaultBigMap=map-matching/files/leipzig_germany.osm.pbf
defaultUseMeasurementTimeAsRefTime=false

# optional env-var overrides (defaults keep the historic behavior, so CI stays unaffected):
#   GH_TOOLS_JAR    path to the tools jar (e.g. a jar built from another branch/checkout)
#   GH_JAVA_OPTS    JVM options (default: -XX:+UseParallelGC -Xmx20g -Xms20g)
#   GH_CLEAN        true/false: false reuses an existing graph in <graph_dir> and skips
#                   import+preparation - graph-compatibility + query-speed comparison only
#   GH_COUNT        number of measured routing queries per mode (default 5000)
#   GH_DATAACCESS   e.g. MMAP to benchmark on machines with little RAM (default RAM_STORE)
#   GH_CUSTOM_BACKEND janino|closure|kotlinsource custom-model weighting backend (default kotlinsource).
#                     kotlinsource = build-time generated Kotlin weighting (compiled into the tools
#                     jar, no runtime code generation); it can only route the models its classes were
#                     generated from (core's car.json + benchmark/very_custom.json). To keep the three
#                     backends comparable, scenarios 1-3 always run a custom model for EVERY backend
#                     (car.json for scenarios 1 & 2, very_custom.json for scenario 3), so switching the
#                     backend changes only how that model is evaluated, not what work is measured.
GH_TOOLS_JAR=${GH_TOOLS_JAR:-$(ls tools/target/graphhopper-tools-*-jar-with-dependencies.jar 2>/dev/null | head -1)}
GH_JAVA_OPTS=${GH_JAVA_OPTS:--XX:+UseParallelGC -Xmx20g -Xms20g}
GH_CLEAN=${GH_CLEAN:-true}
GH_COUNT=${GH_COUNT:-5000}
GH_DATAACCESS=${GH_DATAACCESS:-RAM_STORE}
GH_CUSTOM_BACKEND=${GH_CUSTOM_BACKEND:-closure}

# Scenarios 1 & 2 run the SAME car.json custom weighting for EVERY backend, so janino / closure /
# kotlinsource are compared on an identical graph and identical workload - the only variable is the
# backend. (Previously the custom model was applied only for kotlinsource, so janino/closure fell back
# to the plain accessAndSpeed weighting and never engaged the backend at all; that made kotlinsource
# look "slower" when it was in fact the only backend doing custom-model work.) kotlinsource additionally
# requires the model to be one it was generated from at build time, which car.json is.
GH_CUSTOM_MODEL_FILE=${GH_CUSTOM_MODEL_FILE:-core/src/main/resources/com/graphhopper/custom_models/car.json}
# car.json references these EVs which the benchmark does not auto-add for the car vehicle
CUSTOM_MODEL_ARG="measurement.weighting=custom measurement.custom_model_file=${GH_CUSTOM_MODEL_FILE} graph.encoded_values=road_environment,ferry_speed,max_speed"

# scenario 4 (foot/outdoor) uses no custom weighting, so the custom-weighting backend is a no-op for
# its measurements; but the kotlinsource backend refuses to start without a custom model, so fall it
# back to janino there (no custom weighting is evaluated, so the measured numbers are unaffected).
NON_CUSTOM_BACKEND=${GH_CUSTOM_BACKEND}
if [ "${GH_CUSTOM_BACKEND}" = "kotlinsource" ]; then
  NON_CUSTOM_BACKEND=janino
fi

GRAPH_DIR=${1:-$defaultGraphDir}
RESULTS_DIR=${2:-$defaultResultsDir}
SUMMARY_DIR=${3:-$defaultSummaryDir}
SMALL_OSM_MAP=${4:-$defaultSmallMap}
BIG_OSM_MAP=${5:-$defaultBigMap}
USE_MEASUREMENT_TIME_AS_REF_TIME=${6:-$defaultUseMeasurementTimeAsRefTime}

# create directories
mkdir -p ${GRAPH_DIR}
mkdir -p ${RESULTS_DIR}
mkdir -p ${SUMMARY_DIR}

# actually run the benchmarks:
echo "1 - small map: node- and edge-based CH + landmarks (edge- & node-based for LM) + slow routing"
java -cp ${GH_TOOLS_JAR} ${GH_JAVA_OPTS} \
com.graphhopper.tools.Measurement \
datareader.file=${SMALL_OSM_MAP} \
datareader.date_range_parser_day=2019-11-01 \
measurement.name=small_map \
measurement.folder=${RESULTS_DIR} \
measurement.clean=${GH_CLEAN} \
measurement.stop_on_error=true \
measurement.summaryfile=${SUMMARY_DIR}summary_small.dat \
measurement.repeats=1 \
measurement.run_slow_routing=true \
measurement.ch.node=true \
measurement.ch.edge=true \
measurement.lm=true \
"measurement.lm.active_counts=[4,8,12]" \
measurement.lm.edge_based=true \
measurement.vehicle=car \
import.osm.ignored_highways=footway,cycleway,path,pedestrian,bridleway \
measurement.turn_costs=true \
graph.location=${GRAPH_DIR}measurement-small-gh \
prepare.min_network_size=10000 \
graph.dataaccess.default_type=${GH_DATAACCESS} \
measurement.custom_weighting_backend=${GH_CUSTOM_BACKEND} \
${CUSTOM_MODEL_ARG} \
measurement.json=true \
measurement.count=${GH_COUNT} \
measurement.use_measurement_time_as_ref_time=${USE_MEASUREMENT_TIME_AS_REF_TIME}

echo "2 - big map: node-based CH + landmarks (edge- & node-based for LM) + slow routing"
java -cp ${GH_TOOLS_JAR} ${GH_JAVA_OPTS} \
com.graphhopper.tools.Measurement \
datareader.file=${BIG_OSM_MAP} \
datareader.date_range_parser_day=2019-11-01 \
measurement.name=big_map \
measurement.folder=${RESULTS_DIR} \
measurement.clean=${GH_CLEAN} \
measurement.stop_on_error=true \
measurement.summaryfile=${SUMMARY_DIR}summary_big.dat \
measurement.repeats=1 \
measurement.run_slow_routing=true \
measurement.ch.node=true \
measurement.ch.edge=false \
measurement.lm=true \
"measurement.lm.active_counts=[4,8,12]" \
measurement.lm.edge_based=true \
measurement.vehicle=car \
import.osm.ignored_highways=footway,cycleway,path,pedestrian,bridleway \
measurement.turn_costs=true \
graph.location=${GRAPH_DIR}measurement-big-gh \
prepare.min_network_size=10000 \
graph.dataaccess.default_type=${GH_DATAACCESS} \
measurement.custom_weighting_backend=${GH_CUSTOM_BACKEND} \
${CUSTOM_MODEL_ARG} \
measurement.json=true \
measurement.count=${GH_COUNT} \
measurement.use_measurement_time_as_ref_time=${USE_MEASUREMENT_TIME_AS_REF_TIME}

echo "3 - big map with a custom model that is 'very customized', i.e. has many custom weighting rules"
echo "node-based CH + LM + slow routing"
java -cp ${GH_TOOLS_JAR} ${GH_JAVA_OPTS} \
com.graphhopper.tools.Measurement \
datareader.file=${BIG_OSM_MAP} \
datareader.date_range_parser_day=2019-11-01 \
measurement.name=big_map_very_custom \
measurement.folder=${RESULTS_DIR} \
measurement.clean=${GH_CLEAN} \
measurement.stop_on_error=true \
measurement.summaryfile=${SUMMARY_DIR}summary_big_very_custom.dat \
measurement.repeats=1 \
measurement.run_slow_routing=true \
measurement.weighting=custom \
measurement.custom_model_file=benchmark/very_custom.json \
graph.encoded_values=max_width,max_height,toll,hazmat,road_access,road_class \
measurement.ch.node=true \
measurement.ch.edge=false \
measurement.lm=true \
"measurement.lm.active_counts=[8]" \
measurement.lm.edge_based=false \
measurement.vehicle=car \
import.osm.ignored_highways=footway,cycleway,path,pedestrian,bridleway \
measurement.turn_costs=true \
graph.location=${GRAPH_DIR}measurement-big-very-custom-gh \
prepare.min_network_size=10000 \
graph.dataaccess.default_type=${GH_DATAACCESS} \
measurement.custom_weighting_backend=${GH_CUSTOM_BACKEND} \
measurement.json=true \
measurement.count=${GH_COUNT} \
measurement.use_measurement_time_as_ref_time=${USE_MEASUREMENT_TIME_AS_REF_TIME}

echo "4 - big map, outdoor: node-based CH + landmarks (edge- & node-based for LM)"
java -cp ${GH_TOOLS_JAR} ${GH_JAVA_OPTS} \
com.graphhopper.tools.Measurement \
datareader.file=${BIG_OSM_MAP} \
datareader.date_range_parser_day=2019-11-01 \
measurement.name=big_map_outdoor \
measurement.folder=${RESULTS_DIR} \
measurement.clean=${GH_CLEAN} \
measurement.stop_on_error=true \
measurement.summaryfile=${SUMMARY_DIR}summary_big_outdoor.dat \
measurement.repeats=1 \
measurement.run_slow_routing=false \
measurement.ch.node=true \
measurement.ch.edge=false \
measurement.lm=true \
"measurement.lm.active_counts=[4,8,12]" \
measurement.lm.edge_based=false \
measurement.vehicle=foot \
import.osm.ignored_highways= \
measurement.turn_costs=false \
graph.location=${GRAPH_DIR}measurement-big-outdoor-gh \
prepare.min_network_size=10000 \
graph.dataaccess.default_type=${GH_DATAACCESS} \
measurement.custom_weighting_backend=${NON_CUSTOM_BACKEND} \
measurement.json=true \
measurement.count=${GH_COUNT} \
measurement.use_measurement_time_as_ref_time=${USE_MEASUREMENT_TIME_AS_REF_TIME}
