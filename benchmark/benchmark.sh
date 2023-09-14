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
defaultSmallMap=core/files/andorra.osm.pbf
defaultBigMap=core/files/andorra.osm.pbf
defaultUseMeasurementTimeAsRefTime=false

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
java -cp tools/target/graphhopper-tools-*-jar-with-dependencies.jar \
-XX:+UseParallelGC -Xmx20g -Xms20g \
com.graphhopper.tools.Measurement \
datareader.file=${SMALL_OSM_MAP} \
datareader.date_range_parser_day=2019-11-01 \
measurement.name=small_map \
measurement.folder=${RESULTS_DIR} \
measurement.clean=true \
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
measurement.json=true \
measurement.count=5000 \
measurement.use_measurement_time_as_ref_time=${USE_MEASUREMENT_TIME_AS_REF_TIME}

