## Licenses

GraphHopper licensed under the Apache license, Version 2.0

Copyright 2012 - 2024 GraphHopper GmbH

The core module includes the following additional software:

 * slf4j.org - SLF4J distributed under the MIT license. 
 * com.carrotsearch:hppc (Apache license)
 * Snippets regarding mmap, vint/vlong and compression from Lucene (Apache license)
 * XMLGraphics-Commons for CGIAR elevation files (Apache License)
 * Apache Commons Lang - we copied the implementation of the Levenshtein Distance - see com.graphhopper.apache.commons.lang3 (Apache License)
 * Apache Commons Collections - we copied parts of the BinaryHeap - see com.graphhopper.apache.commons.collections (Apache License)
 * java-string-similarity - we copied the implementation of JaroWinkler (MIT license)
 * Jackson (Apache License)
 * org.locationtech:jts (EDL)
 * AngleCalc.atan2 from Jim Shima, 1999 (public domain)
 * janino compiler (BSD-3-Clause license)
 * osm-legal-default-speeds-jvm (BSD-3-Clause license)
 * kotlin stdlib (Apache License)
 * protobuf - (New BSD license)
 * OSM-binary - (LGPL license)
 * Osmosis - public domain, see their github under package/copying.txt

reader-gtfs:

 * some files from com.conveyal:gtfs-lib (BSD 2-clause license)
 * com.google.transit:gtfs-realtime-bindings (Apache license)
 * com.google.guava:guava (Apache license)
 * com.opencsv:opencsv (Apache license)
 * commons-io:commons-io (Apache license)
 * org.apache.commons:commons-lang3 (Apache license)
 * org.mapdb:mapdb (Apache license)

tools:

 * uses Apache Compress (Apache license)

client-hc:

 * okhttp (Apache license)

web:

 * org.eclipse.jetty:jetty-server (Apache License)
 * Dropwizard and dependencies (Apache license)
 * classes in no.ecc are a copy of no.ecc.vectortile:java-vector-tile, see #2698 (Apache license)

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
