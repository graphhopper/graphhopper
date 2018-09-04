#!/bin/bash
# PROD Setting : The Heap Memory settings are based on the assumptions that the application will run mostly on 8Gb Ram Box.  (For fine tuning Heap size look into : https://support.cloudbees.com/hc/en-us/articles/204859670-Java-Heap-settings-best-practice )
# PROD Setting : The G1GC Garbage collector is used initially, but based on the metrics and performance we can try -XX:+UseConcMarkSweepGC and keep whichever workds better.


./graphhopper.sh -a web -i ./osrm_location.osm.pbf
