#!/bin/bash

if [ "x$ROBO" = "x" ]; then
 echo "please specify your robovm path. E.g. via export ROBO=/your/robo_path"
 exit
fi
OS=linux
GH=..
GH_JAR=target/ios-0.2-SNAPSHOT-jar-with-dependencies.jar
M_REPO=`echo ~/.m2/repository`
CP=$GH_JAR:$ROBO/lib/robovm-objc.jar
#$M_REPO/org/slf4j/slf4j-log4j12/1.7.5/slf4j-log4j12-1.7.5.jar:$M_REPO/log4j/log4j/1.2.17/log4j-1.2.17.jar
GRAPH=/media/SAMSUNG/maps/berlin-gh
$ROBO/bin/robovm -verbose -arch x86 -os $OS -cp $CP -run com.graphhopper.ios.Start config=$GH/config.properties graph.location=$GRAPH