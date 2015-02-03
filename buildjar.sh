#!/bin/bash
mvn clean install

#
# Don't have to do this anymore as they finally removed log4j.properties from the jar
#
#mkdir ~/tmp/repack
#cd ~/tmp/repack
#jar xvf ~/dev/samsix/graphhopper/core/target/graphhopper-0.4-SNAPSHOT.jar
#rm log4j.properties
#mv META-INF/MANIFEST.MF ../manifest

#cd ..
#jar cvfm graphhopper-0.4-SNAPSHOT.jar ~/tmp/manifest -C ~/tmp/repack/ .
#rm -rf ~/tmp/repack ~/tmp/manifest

#mv graphhopper-0.4-SNAPSHOT.jar ~/dev/samsix/nrg/lib/graphhopper
cp core/target/graphhopper-0.4-SNAPSHOT.jar ../nrg/lib/graphhopper

. godev
cd nrg
ant -Djarfile=lib/graphhopper/graphhopper-0.4-SNAPSHOT.jar s6signjar