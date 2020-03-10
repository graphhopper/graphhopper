#!/bin/bash
# usage:
# benchmark/download_map.sh <osm_map_url> <osm_map_path>
#
# where:
# <osm_map_url> = remote url the map is downloaded from
# <osm_map_path> = local path the map is downloaded to

# make this script exit if a command fails, a variable is missing etc.
set -euo pipefail

# download OSM map if it does not exist already
if [ ! -f $2 ];
then
  wget -S -nv -O $2 $1
fi
