MAPSFORGE=`ls ./libs/mapsforge*.jar`
echo "installing file: $MAPSFORGE"
$MAVEN_HOME/bin/mvn install:install-file -DgroupId=org.mapsforge \
 -DartifactId=mapsforge \
 -Dversion=0.3.1-SNAPSHOT \
 -Dpackaging=jar \
 -Dfile=$MAPSFORGE