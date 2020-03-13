#!/bin/bash
# usage:
# benchmark/benchmark.sh <data_dir> <results_dir> <small_osm_map_path> <big_osm_map_path> <use_measurement_time_as_ref_time> <spatial_rule_borders_dir>
#
# where:
# <data_dir> = base directory used to store results
# <results_dir> = name of directory where results of this run are stored (inside <data_dir>)
# <small_osm_map_path> = path to osm map the measurement is run on for slow measurements
# <big_osm_map_path> = path to osm map the measurement is run on for fast measurements
# <use_measurement_time_as_ref_time> = true/false, false by default, meaning the git commit time will be used as reference
# <spatial_rule_borders_dir> = base directory to load borders of spatial rules from

# make this script exit if a command fails, a variable is missing etc.
set -euo pipefail

defaultDataDir=measurements/
defaultSingleResultsDir=measurements/results/$(date '+%d-%m-%Y-%s%N')/
defaultSmallMap=core/files/andorra.osm.pbf
defaultBigMap=core/files/andorra.osm.pbf
defaultUseMeasurementTimeAsRefTime=false
defaultBordersDirectory=core/files/spatialrules

# this is the directory where we read/write data from/to
DATA_DIR=${1:-$defaultDataDir}
RESULTS_DIR=${DATA_DIR}results/
TMP_DIR=${DATA_DIR}tmp/
SINGLE_RESULTS_DIR=${2:-$defaultSingleResultsDir}
SMALL_OSM_MAP=${3:-$defaultSmallMap}
BIG_OSM_MAP=${4:-$defaultBigMap}
USE_MEASUREMENT_TIME_AS_REF_TIME=${5:-$defaultUseMeasurementTimeAsRefTime}
BORDERS_DIRECTORY=${6:-$defaultBordersDirectory}

# create directories
mkdir -p ${DATA_DIR}
mkdir -p ${RESULTS_DIR}
mkdir -p ${TMP_DIR}
mkdir -p ${SINGLE_RESULTS_DIR}

# actually run the benchmarks:
# 1 - small map: node- and edge-based CH + slow routing
java -cp tools/target/graphhopper-tools-*-jar-with-dependencies.jar com.graphhopper.tools.Measurement \
datareader.file=${SMALL_OSM_MAP} \
datareader.date_range_parser_day=2019-11-01 \
measurement.folder=${SINGLE_RESULTS_DIR} \
measurement.clean=true \
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
prepare.min_oneway_network_size=10000 \
spatial_rules.borders_directory=${BORDERS_DIRECTORY} \
measurement.json=true \
measurement.count=5000 \
measurement.use_measurement_time_as_ref_time=${USE_MEASUREMENT_TIME_AS_REF_TIME}

# 2 - big map: node-based CH + landmarks (edge- & node-based for LM)
java -cp tools/target/graphhopper-tools-*-jar-with-dependencies.jar com.graphhopper.tools.Measurement \
datareader.file=${BIG_OSM_MAP} \
datareader.date_range_parser_day=2019-11-01 \
measurement.folder=${SINGLE_RESULTS_DIR} \
measurement.clean=true \
measurement.summaryfile=${RESULTS_DIR}summary_big.dat \
measurement.repeats=1 \
measurement.run_slow_routing=false \
measurement.weighting=fastest \
measurement.ch.node=true \
measurement.ch.edge=false \
measurement.lm=true \
"graph.flag_encoders=car|turn_costs=true" \
graph.location=${TMP_DIR}measurement-big-gh \
prepare.min_network_size=10000 \
prepare.min_oneway_network_size=10000 \
spatial_rules.borders_directory=${BORDERS_DIRECTORY} \
measurement.json=true \
measurement.count=5000 \
measurement.use_measurement_time_as_ref_time=${USE_MEASUREMENT_TIME_AS_REF_TIME}
