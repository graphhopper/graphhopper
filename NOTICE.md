## Licenses

GraphHopper licensed under the Apache license, Version 2.0

Copyright 2012 - 2017 GraphHopper GmbH

The core module includes the following software:

 * slf4j.org - SLF4J distributed under the MIT license. 
 * com.carrotsearch:hppc (Apache license)
 * SparseArray from the Android project (Apache license)
 * Snippets regarding mmap, vint/vlong and compression from Lucene (Apache license)
 * XMLGraphics-Commons for CGIAR elevation files (Apache License)
 * Apache Commons Lang - we copied the implementation of the Levenshtein Distance (Apache License)
 * Apache Commons Collections - we copied parts of the BinaryHeap (Apache License)
 * java-string-similarity - we copied the implementation of JaroWinkler (MIT license)
 * com.fasterxml.jackson.core:jackson-annotations (Apache License)
 * com.vividsolutions:jts (LGPL), see #1039

reader-osm:

 * protobuf - New BSD license
 * OSM-binary - LGPL license
 * Osmosis - public domain, see osmosis-copying.txt under core/files

reader-gtfs:
 
 * com.conveyal:gtfs-lib (BSD 2-clause license)
 * com.google.transit:gtfs-realtime-bindings (Apache license)

reader-json:

 * com.bedatadriven:jackson-datatype-jts (Apache license)
 * com.fasterxml.jackson.core:jackson-databind (Apache license)

reader-shp:
 
 * org.geotools:gt-shapefile (LGPL)

tools:

 * uses Apache Compress (Apache license)

web:

 * org.eclipse.jetty:jetty-server (Apache License)
 * com.fasterxml.jackson.core:jackson-databind (Apache license)
 * com.google.inject (Apache license)
 * some images from mapbox https://www.mapbox.com/maki/, BSD License, see core/files

android:

 * android (Apache license)
 * org.mapsforge, LGPL
 * VTM, LGPL

## Data

|source | license | used as default | included in repo |
|---------|-----------|---------|------|
|OpenStreetMap data for the road network | [ODBL](https://www.openstreetmap.org/copyright) | yes | yes
| SRTM elevation | [public domain](https://www2.jpl.nasa.gov/srtm/), [acknowledgement](https://lpdaac.usgs.gov/citing_our_data) | no | yes
| CGIAR elevation | [allowed usage for GraphHopper](https://graphhopper.com/public/license/CGIAR.txt) | no | no
| SRTMGL1 elevation | [acknowledgement](https://lpdaac.usgs.gov/citing_our_data) | no | no
|OpenTopography mirror for SRTMGL1 | [acknowledgement OpenTopoGraphy](http://www.opentopography.org/citations) and [data source](http://opentopo.sdsc.edu/datasetMetadata?otCollectionID=OT.042013.4326.1) + SRTMGL1 | no | no
| GMTED | [public domain, acknowledgment](https://lta.cr.usgs.gov/citation) | no | no
