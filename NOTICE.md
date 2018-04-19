## Licenses

GraphHopper licensed under the Apache license, Version 2.0

Copyright 2012 - 2017 GraphHopper GmbH

### core

The core module includes the following software:

 * JTS: LGPL
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

### reader-osm

 * protobuf - New BSD license
 * OSM-binary - LGPL license
 * Osmosis - public domain, see osmosis-copying.txt under core/files

### reader-gtfs
 
 * com.conveyal:gtfs-lib (BSD 2-clause license)
 * com.google.transit:gtfs-realtime-bindings (Apache license)

### reader-json

 * com.bedatadriven:jackson-datatype-jts (Apache license)
 * com.fasterxml.jackson.core:jackson-databind (Apache license)

### reader-shp
 
 * org.geotools:gt-shapefile (LGPL)

### tools

 * uses Apache Compress (Apache license)

### web

 * dropwizard (Apache License)
 * jersey ([CDDL](https://en.wikipedia.org/wiki/Common_Development_and_Distribution_License))
 * org.eclipse.jetty:jetty-server (Apache License)
 * com.fasterxml.jackson.core:jackson-databind (Apache license)
 * com.google.inject (Apache license)
 * some images from mapbox https://www.mapbox.com/maki/, BSD License, see core/files

### android

 * android (Apache license)
 * org.mapsforge, LGPL
 * VTM, LGPL
 
### map-matching

GraphHopper licensed under the Apache license, Version 2.0

### hmm-lib

Copyright (C) 2015-2016, BMW Car IT GmbH and BMW AG
Author: Stefan Holder (stefan.holder@bmw.de)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

This project has dependencies to:
  Apache Maven, under The Apache Software License, Version 2.0
  JUnit under Eclipse Public License - v 1.0

### offline_map_matching

Copyright (C) 2015-2016, BMW Car IT GmbH and BMW AG
Author: Stefan Holder (stefan.holder@bmw.de)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

This project has dependencies to:
  Apache Maven, under The Apache Software License, Version 2.0
  JUnit under Eclipse Public License - v 1.0
  hmm-lib, under The Apache Software License, Version 2.0


## Data

|source | license | used as default | included in repo |
|---------|-----------|---------|------|
|OpenStreetMap data for the road network | [ODBL](https://www.openstreetmap.org/copyright) | yes | yes
| SRTM elevation | [public domain](https://www2.jpl.nasa.gov/srtm/), [acknowledgement](https://lpdaac.usgs.gov/citing_our_data) | no | yes
| CGIAR elevation | [allowed usage for GraphHopper](https://graphhopper.com/public/license/CGIAR.txt) | no | no
| SRTMGL1 elevation | [acknowledgement](https://lpdaac.usgs.gov/citing_our_data) | no | no
|OpenTopography mirror for SRTMGL1 | [acknowledgement OpenTopoGraphy](http://www.opentopography.org/citations) and [data source](http://opentopo.sdsc.edu/datasetMetadata?otCollectionID=OT.042013.4326.1) + SRTMGL1 | no | no
| GMTED | [public domain, acknowledgment](https://lta.cr.usgs.gov/citation) | no | no
