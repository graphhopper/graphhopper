#!/bin/bash

if [ "x$ROBO" = "x" ]; then
 echo "please specify your robovm path. E.g. via export ROBO=/your/robo_path"
 exit
fi

GRAPH=/media/SAMSUNG/maps/berlin-gh

GH=..
#M_REPO=`echo ~/.m2/repository`
CP=target/graphhopper-ios-0.2-SNAPSHOT-jar-with-dependencies.jar:$ROBO/lib/robovm-objc.jar
# for com.graphhopper.ios.StartUI you need MacOS! https://github.com/robovm/robovm/issues/180
# org.apache.log4j.**


$ROBO/bin/robovm -rvm:log=warn -config robovm.xml -d out -verbose -cp $CP -run com.graphhopper.ios.Start config=$GH/config.properties graph.location=$GRAPH