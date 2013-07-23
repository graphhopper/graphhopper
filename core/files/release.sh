#!/bin/bash

# assumptions:
# 1. you have run unit and integration tests successfully
# 2. no changes on master

# TODO when we understand the commands we can use the release plugin
# mvn release:clean 
# # Prepare: build, test, release version update, commit, tag, next snapshot version update, commit
# mvn release:prepare -DgenerateBackupPoms=false
# # Perform: export a release from SCM, run the deploy goal
# mvn release:perform
# https://docs.sonatype.org/display/Repository/Sonatype+OSS+Maven+Repository+Usage+Guide

GH_CORE_HOME=$(dirname $0)/..
cd $GH_CORE_HOME
VERSION=0.1
NEW_VERSION=0.2-SNAPSHOT
TARGET=./target
GIT_E=true

mvn versions:set -DnewVersion=$VERSION -DgenerateBackupPoms=false
# as android is not referenced in parent we need to do it manually
cd android
mvn versions:set -DnewVersion=$VERSION -DgenerateBackupPoms=false
cd ..

if [ "x$GIT_E" = "xtrue" ]; then
  git checkout -b $VERSION
  git add .
  git commit -m "releasing $VERSION"
fi


##############################
# create jar and android files
mvn -DskipTests=true clean install assembly:single

if [ $? -ne 0 ]; then
  echo "cannot install jars?"
  exit
fi  

# now create binary distribution where no maven is necessary to run import
cp ../graphhopper.sh $TARGET/graphhopper.sh
JAR=`cd $TARGET && ls -1 *-with-dependencies.jar`
# use @ instead of the common / to avoid problems with paths in $JAR
sed -i "s@JAR=.*@JAR=$JAR@g" $TARGET/graphhopper.sh
# if you use -x option use backslash avoids shell substitution e.g. -x \*~
zip -j $TARGET/graphhopper-$VERSION-bin.zip $TARGET/graphhopper.sh $TARGET/$JAR ../config-example.properties ../*.txt ../*.md


########################
# deployment to sonatype
$MVN install deploy

if [ $? -ne 0 ]; then
  echo "cannot deploy to sonatype?"
  exit
fi    

cd android
export ANDROID_HOME=/install/android/sdk
$MVN clean install deploy
cd ..


##########################
# deployment to our server

# TODO

mvn versions:set -DnewVersion=$NEW_VERSION -DgenerateBackupPoms=false

if [ "x$GIT_E" = "xtrue" ]; then
  git checkout master
  git add .
  git commit -m "new development version $NEW_VERSION"
fi
