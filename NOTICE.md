## Licenses

GraphHopper licensed under the Apache license, Version 2.0

Copyright 2012 - 2021 GraphHopper GmbH

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
 * org.locationtech:jts (EDL), see #1039
 * AngleCalc.atan2 from Jim Shima, 1999 (public domain)
 * list of Java keywords in EncodingManager from janino compiler (BSD-3-Clause license)
 * protobuf - New BSD license
 * OSM-binary - LGPL license
 * Osmosis - public domain, see osmosis-copying.txt under core/files

reader-gtfs:
 
 * some files from com.conveyal:gtfs-lib (BSD 2-clause license)
 * com.google.transit:gtfs-realtime-bindings (Apache license)

reader-shp:
 
 * org.geotools:gt-shapefile (LGPL)

tools:

 * uses Apache Compress (Apache license)

web:

 * org.eclipse.jetty:jetty-server (Apache License)
 * com.fasterxml.jackson.core:jackson-databind (Apache license)
 * com.google.inject (Apache license)
 * some images from mapbox https://www.mapbox.com/maki/, BSD License, see core/files

## Data

|source | license | used as default | included in repo |
|---------|-----------|---------|------|
|OpenStreetMap data for the road network | [ODBL](https://www.openstreetmap.org/copyright) | yes | yes
| SRTM elevation | [public domain](https://www2.jpl.nasa.gov/srtm/), [acknowledgement](https://lpdaac.usgs.gov/citing_our_data) | no | yes
| CGIAR elevation | [allowed usage for GraphHopper](https://gist.githubusercontent.com/karussell/4b54a289041ee48a16c00fd4e30e21b8/raw/45edf8ae85322cb20976baa30654093d0ca9bcd8/CGIAR.txt) | no | no
| SRTMGL1 elevation | [acknowledgement](https://lpdaac.usgs.gov/citing_our_data) | no | no
|OpenTopography mirror for SRTMGL1 | [acknowledgement OpenTopoGraphy](http://www.opentopography.org/citations) and [data source](http://opentopo.sdsc.edu/datasetMetadata?otCollectionID=OT.042013.4326.1) + SRTMGL1 | no | no
| GMTED | [public domain, acknowledgment](https://lta.cr.usgs.gov/citation) | no | no
| Tilezen Joerd (Skadi) | [acknowledgment](https://github.com/tilezen/joerd/blob/master/docs/attribution.md) | no | no
