#!/bin/bash

./graphhopper.sh import $1/us-midwest-latest.osm.pbf
./graphhopper.sh import $1/us-west-latest.osm.pbf
./graphhopper.sh import $1/us-northeast-latest.osm.pbf
./graphhopper.sh import $1/us-south-latest.osm.pbf

cd $1
tar -cvzf us-midwest-latest.osm.tgz us-midwest-latest.osm-gh
tar -cvzf us-west-latest.osm.tgz us-west-latest.osm-gh
tar -cvzf us-northeast-latest.osm.tgz us-northeast-latest.osm-gh
tar -cvzf us-south-latest.osm.tgz us-south-latest.osm-gh
