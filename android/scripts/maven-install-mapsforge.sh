# do the following
# git clone https://code.google.com/p/mapsforge/
# cd mapsforge; and fix http://code.google.com/p/mapsforge/issues/detail?id=461
# mvn clean install
# cp mapsforge-map/target/mapsforge-map-0.3.1-SNAPSHOT-jar-with-dependencies.jar graphhopper/android/libs/mapsforge-0.3.1-SNAPSHOT.jar

# if we would do it via normal maven dependency management we run into strange things which I was not able to fix
# http://stackoverflow.com/a/8315600/194609

# MAVEN_HOME/bin/mvn
MVN=mvn
VERSION=0.4.0-SNAPSHOT
libs="map map-android map-reader core"

for lib in $libs; do
  FILE=$(ls ./libs/mapsforge-$lib-$VERSION.jar)
  echo "installing file: $FILE"
  ARGS="-DgroupId=com.graphhopper -DartifactId=mapsforge-$lib -Dversion=0.3-0.4.0-SNAPSHOT -Dpackaging=jar -Dfile=$FILE"
  $MVN install:install-file $ARGS
done
