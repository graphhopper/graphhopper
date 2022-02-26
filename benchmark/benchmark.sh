#!/bin/bash
# usage:
# benchmark/benchmark.sh <data_dir> <results_dir> <small_osm_map_path> <big_osm_map_path> <use_measurement_time_as_ref_time>
#
# where:
# <data_dir> = base directory used to store results
# <results_dir> = name of directory where results of this run are stored (inside <data_dir>)
# <small_osm_map_path> = path to osm map the measurement is run on for slow measurements
# <big_osm_map_path> = path to osm map the measurement is run on for fast measurements
# <use_measurement_time_as_ref_time> = true/false, false by default, meaning the git commit time will be used as reference

# make this script exit if a command fails, a variable is missing etc.
set -euo pipefail
# print all commands
set -o xtrace

defaultDataDir=measurements/
defaultSingleResultsDir=measurements/results/$(date '+%d-%m-%Y-%s%N')/
defaultSmallMap=core/files/andorra.osm.pbf
defaultBigMap=core/files/andorra.osm.pbf
defaultUseMeasurementTimeAsRefTime=false

# this is the directory where we read/write data from/to
DATA_DIR=${1:-$defaultDataDir}
RESULTS_DIR=${DATA_DIR}results/
TMP_DIR=${DATA_DIR}tmp/
SINGLE_RESULTS_DIR=${2:-$defaultSingleResultsDir}
SMALL_OSM_MAP=${3:-$defaultSmallMap}
BIG_OSM_MAP=${4:-$defaultBigMap}
USE_MEASUREMENT_TIME_AS_REF_TIME=${5:-$defaultUseMeasurementTimeAsRefTime}

# create directories
mkdir -p ${DATA_DIR}
mkdir -p ${RESULTS_DIR}
mkdir -p ${TMP_DIR}
mkdir -p ${SINGLE_RESULTS_DIR}

# actually run the benchmarks:
echo "1 - small map: node- and edge-based CH + slow routing"
java -cp tools/target/graphhopper-tools-*-jar-with-dependencies.jar \
-XX:+UseParallelGC -Xmx20g -Xms20g \
com.graphhopper.tools.Measurement \
datareader.file=${SMALL_OSM_MAP} \
datareader.date_range_parser_day=2019-11-01 \
measurement.name=small_map \
measurement.folder=${SINGLE_RESULTS_DIR} \
measurement.clean=true \
measurement.stop_on_error=true \
measurement.summaryfile=${RESULTS_DIR}summary_small.dat \
measurement.repeats=1 \
measurement.run_slow_routing=true \
measurement.weighting=fastest \
measurement.ch.node=true \
measurement.ch.edge=true \
measurement.lm=false \
"graph.flag_encoders=car|turn_costs=true" \
graph.location=${TMP_DIR}measurement-small-gh \
prepare.min_network_size=10000 \
measurement.json=true \
measurement.count=5000 \
measurement.use_measurement_time_as_ref_time=${USE_MEASUREMENT_TIME_AS_REF_TIME} \
"measurement.block_area=49.394664,11.144428,49.348388,11.144943,49.355768,11.227169,49.411643,11.227512"

echo "2 - big map: node-based CH + landmarks (edge- & node-based for LM)"
java -cp tools/target/graphhopper-tools-*-jar-with-dependencies.jar \
-XX:+UseParallelGC -Xmx20g -Xms20g \
com.graphhopper.tools.Measurement \
datareader.file=${BIG_OSM_MAP} \
datareader.date_range_parser_day=2019-11-01 \
measurement.name=big_map \
measurement.folder=${SINGLE_RESULTS_DIR} \
measurement.clean=true \
measurement.stop_on_error=true \
measurement.summaryfile=${RESULTS_DIR}summary_big.dat \
measurement.repeats=1 \
measurement.run_slow_routing=false \
measurement.weighting=fastest \
measurement.ch.node=true \
measurement.ch.edge=false \
measurement.lm=true \
"measurement.lm.active_counts=[4,8,12,16]" \
measurement.lm.edge_based=true \
"graph.flag_encoders=car|turn_costs=true" \
graph.location=${TMP_DIR}measurement-big-gh \
prepare.min_network_size=10000 \
measurement.json=true \
measurement.count=5000 \
measurement.use_measurement_time_as_ref_time=${USE_MEASUREMENT_TIME_AS_REF_TIME} \
"measurement.block_area=49.394664,11.144428,49.348388,11.144943,49.355768,11.227169,49.411643,11.227512"

echo "3 - big map with a custom model that is 'a little customized', i.e. similar to the standard fastest-car profile"
echo "node-based CH + LM"
java -cp tools/target/graphhopper-tools-*-jar-with-dependencies.jar \
-XX:+UseParallelGC -Xmx20g -Xms20g \
com.graphhopper.tools.Measurement \
datareader.file=${BIG_OSM_MAP} \
datareader.date_range_parser_day=2019-11-01 \
measurement.name=big_map_little_custom \
measurement.folder=${SINGLE_RESULTS_DIR} \
measurement.clean=true \
measurement.stop_on_error=true \
measurement.summaryfile=${RESULTS_DIR}summary_big_little_custom.dat \
measurement.repeats=1 \
measurement.run_slow_routing=false \
measurement.weighting=custom \
measurement.custom_model_file=benchmark/little_custom.yml \
graph.encoded_values=max_width,max_height,toll,hazmat \
measurement.ch.node=true \
measurement.ch.edge=false \
measurement.lm=true \
"measurement.lm.active_counts=[8]" \
measurement.lm.edge_based=false \
"graph.flag_encoders=car|turn_costs=true" \
graph.location=${TMP_DIR}measurement-big-little-custom-gh \
prepare.min_network_size=10000 \
measurement.json=true \
measurement.count=5000 \
measurement.use_measurement_time_as_ref_time=${USE_MEASUREMENT_TIME_AS_REF_TIME} \
"measurement.block_area=49.394664,11.144428,49.348388,11.144943,49.355768,11.227169,49.411643,11.227512"

echo "4 - big map with a custom model that is 'very customized', i.e. has many custom weighting rules"
echo "node-based CH + LM"
java -cp tools/target/graphhopper-tools-*-jar-with-dependencies.jar \
-XX:+UseParallelGC -Xmx20g -Xms20g \
com.graphhopper.tools.Measurement \
datareader.file=${BIG_OSM_MAP} \
datareader.date_range_parser_day=2019-11-01 \
measurement.name=big_map_very_custom \
measurement.folder=${SINGLE_RESULTS_DIR} \
measurement.clean=true \
measurement.stop_on_error=true \
measurement.summaryfile=${RESULTS_DIR}summary_big_very_custom.dat \
measurement.repeats=1 \
measurement.run_slow_routing=false \
measurement.weighting=custom \
measurement.custom_model_file=benchmark/very_custom.yml \
graph.encoded_values=max_width,max_height,toll,hazmat \
measurement.ch.node=true \
measurement.ch.edge=false \
measurement.lm=true \
"measurement.lm.active_counts=[8]" \
measurement.lm.edge_based=false \
"graph.flag_encoders=car|turn_costs=true" \
graph.location=${TMP_DIR}measurement-big-very-custom-gh \
prepare.min_network_size=10000 \
measurement.json=true \
measurement.count=5000 \
measurement.use_measurement_time_as_ref_time=${USE_MEASUREMENT_TIME_AS_REF_TIME} \
"measurement.block_area=49.394664,11.144428,49.348388,11.144943,49.355768,11.227169,49.411643,11.227512"

echo "5 - big map, outdoor: node-based CH + landmarks (edge- & node-based for LM)"
java -cp tools/target/graphhopper-tools-*-jar-with-dependencies.jar \
-XX:+UseParallelGC -Xmx20g -Xms20g \
com.graphhopper.tools.Measurement \
datareader.file=${BIG_OSM_MAP} \
datareader.date_range_parser_day=2019-11-01 \
measurement.name=big_map_outdoor \
measurement.folder=${SINGLE_RESULTS_DIR} \
measurement.clean=true \
measurement.stop_on_error=true \
measurement.summaryfile=${RESULTS_DIR}summary_big_outdoor.dat \
measurement.repeats=1 \
measurement.run_slow_routing=false \
measurement.weighting=fastest \
measurement.ch.node=true \
measurement.ch.edge=false \
measurement.lm=true \
"measurement.lm.active_counts=[4,8,12,16]" \
measurement.lm.edge_based=false \
"graph.flag_encoders=foot" \
graph.location=${TMP_DIR}measurement-big-outdoor-gh \
prepare.min_network_size=10000 \
measurement.json=true \
measurement.count=5000 \
measurement.use_measurement_time_as_ref_time=${USE_MEASUREMENT_TIME_AS_REF_TIME} \
"measurement.block_area=49.394664,11.144428,49.348388,11.144943,49.355768,11.227169,49.411643,11.227512"