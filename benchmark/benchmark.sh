#!/bin/bash
# usage:
# benchmark/benchmark.sh <data_dir> <results_dir> <small_osm_map_path> <big_osm_map_path>
#
# where:
# <data_dir> = base directory used to store results
# <results_dir> = name of directory where results of this run are stored (inside <data_dir>)
# <small_osm_map_path> = path to osm map the measurement is run on for slow measurements
# <big_osm_map_path> = path to osm map the measurement is run on for fast measurements

# make this script exit if a command fails, a variable is missing etc.
set -euo pipefail

defaultDataDir=measurements/
defaultSingleResultsDir=measurements/results/$(date '+%d-%m-%Y-%s%N')/
defaultSmallMap=core/files/andorra.osm.pbf
defaultBigMap=core/files/andorra.osm.pbf

# this is the directory where we read/write data from/to
DATA_DIR=${1:-$defaultDataDir}
RESULTS_DIR=${DATA_DIR}results/
TMP_DIR=${DATA_DIR}tmp/
SINGLE_RESULTS_DIR=${2:-$defaultSingleResultsDir}
SMALL_OSM_MAP=${3:-$defaultSmallMap}
BIG_OSM_MAP=${4:-$defaultBigMap}

# create directories
mkdir -p ${DATA_DIR}
mkdir -p ${RESULTS_DIR}
mkdir -p ${TMP_DIR}
mkdir -p ${SINGLE_RESULTS_DIR}

# actually run the benchmarks:
# 1 - small map: node- and edge-based CH + slow routing
java -cp tools/target/graphhopper-tools-*-jar-with-dependencies.jar com.graphhopper.tools.Measurement \
datareader.file=${SMALL_OSM_MAP} \
measurement.folder=${SINGLE_RESULTS_DIR} \
measurement.clean=true \
measurement.summaryfile=${RESULTS_DIR}summary_small.dat \
measurement.repeats=1 \
measurement.run_slow_routing=true \
prepare.ch.weightings=fastest \
prepare.lm.weightings=no \
"graph.flag_encoders=car|turn_costs=true" \
prepare.ch.edge_based=edge_and_node \
graph.location=${TMP_DIR}measurement-small-gh \
prepare.min_network_size=10000 \
prepare.min_oneway_network_size=10000 \
measurement.json=true \
measurement.count=5000

# 2 - big map: node-based CH + landmarks
java -cp tools/target/graphhopper-tools-*-jar-with-dependencies.jar com.graphhopper.tools.Measurement \
datareader.file=${BIG_OSM_MAP} \
measurement.folder=${SINGLE_RESULTS_DIR} \
measurement.clean=true \
measurement.summaryfile=${RESULTS_DIR}summary_big.dat \
measurement.repeats=1 \
measurement.run_slow_routing=false \
prepare.ch.weightings=fastest \
prepare.lm.weightings=fastest \
graph.flag_encoders=car \
prepare.ch.edge_based=off \
graph.location=${TMP_DIR}measurement-big-gh \
prepare.min_network_size=10000 \
prepare.min_oneway_network_size=10000 \
measurement.json=true \
measurement.count=5000
