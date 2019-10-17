#!/bin/bash
# usage:
# benchmark/benchmark.sh <data_dir> <results_dir> <osm_map_path>
#
# where:
# <data_dir> = directory used to download map and store results
# <results_dir> = name of directory where results of this run are stored
# <osm_map_path> = path to osm map the measurement is run on

# make this script exit if a command fails, a variable is missing etc.
set -euo pipefail

defaultDataDir=measurements/
defaultSingleResultsDir=measurements/results/$(date '+%d-%m-%Y-%s%N')/
defaultMap=core/files/andorra.osm.pbf

# this is the directory where we read/write data from/to
DATA_DIR=${1:-$defaultDataDir}
RESULTS_DIR=${DATA_DIR}results/
TMP_DIR=${DATA_DIR}tmp/
SINGLE_RESULTS_DIR=${2:-$defaultSingleResultsDir}
OSM_MAP=${3:-$defaultMap}

# create directories
mkdir -p ${DATA_DIR}
mkdir -p ${RESULTS_DIR}
mkdir -p ${TMP_DIR}
mkdir -p ${SINGLE_RESULTS_DIR}

# actually run the benchmark
java -cp tools/target/graphhopper-tools-*-jar-with-dependencies.jar com.graphhopper.tools.Measurement \
datareader.file=${OSM_MAP} \
measurement.folder=${SINGLE_RESULTS_DIR} \
measurement.clean=true \
measurement.summaryfile=${RESULTS_DIR}summary.dat \
measurement.repeats=1 \
measurement.run_slow_routing=false \
prepare.ch.weightings=fastest \
prepare.lm.weightings=fastest \
graph.flag_encoders=car \
prepare.ch.edge_based=off \
graph.location=${TMP_DIR}measurement-gh \
prepare.min_network_size=10000 \
prepare.min_oneway_network_size=10000 \
measurement.json=true \
measurement.count=5000

