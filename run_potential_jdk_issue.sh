set -euo pipefail
ALL_IN_ONE_JAR=tools/target/graphhopper-tools-*-jar-with-dependencies.jar
DEFAULT_OUTPUT_FILE=potential_jdk_issue.dat
OUTPUT_FILE=${1:-$DEFAULT_OUTPUT_FILE}
if [ ! -f $ALL_IN_ONE_JAR ]; then
    mvn package -am -pl tool -DskipTests
fi
# this runs the program with some extra code that does affect the results (routingCH.mean increases), even though
# it should not
java -cp $ALL_IN_ONE_JAR com.graphhopper.tools.Measurement \
   measurement.count_new_edge_tests=5000 \
   measurement.count=5000 \
   measurement.summaryfile=$OUTPUT_FILE

# this runs the program without the extra code resulting in lower values of routingCH.mean
java -cp $ALL_IN_ONE_JAR com.graphhopper.tools.Measurement \
   measurement.count_new_edge_tests=0 \
   measurement.count=5000 \
   measurement.summaryfile=$OUTPUT_FILE
