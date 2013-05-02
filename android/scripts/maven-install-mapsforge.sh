MAPSFORGE=`ls ./libs/mapsforge*.jar`
echo "installing file: $MAPSFORGE"
mvn install:install-file -DgroupId=org.mapsforge \
 -DartifactId=mapsforge \
 -Dversion=0.3.1-SNAPSHOT \
 -Dpackaging=jar \
 -Dfile=$MAPSFORGE