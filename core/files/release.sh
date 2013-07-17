#!/bin/bash

GH_CORE_HOME=$(dirname $0)/..
cd $GH_CORE_HOME
VERSION=0.1
TARGET=./target

mvn -DskipTests=true clean install assembly:single
cp ../graphhopper.sh $TARGET/graphhopper.sh
JAR=`cd $TARGET && ls -1 *-with-dependencies.jar`

# use @ instead of the common / to avoid problems with paths in $JAR
sed -i "s@JAR=.*@JAR=$JAR@g" $TARGET/graphhopper.sh

# if you use -x option use backslash avoids shell substitution e.g. -x \*~
zip -j $TARGET/graphhopper-$VERSION-bin.zip $TARGET/graphhopper.sh $TARGET/$JAR ../config-example.properties ../*.txt ../*.md