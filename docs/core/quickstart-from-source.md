## Try out

For a start which requires only the JRE have a look [here](../web/quickstart.md). 
Windows user can find a quick guide [here](https://github.com/graphhopper/graphhopper/wiki/Windows). 

Now, before you proceed install git and jdk6, 7 or 8. Then do:

```bash
$ git clone git://github.com/graphhopper/graphhopper.git
$ cd graphhopper; ./graphhopper.sh web europe_germany_berlin.pbf
now go to http://localhost:8989/
```

  1. These steps make the Berlin area routable. It'll download and unzip the osm file for you.
  2. It builds the graphhopper jars. If Maven is not available it will automatically download it.
  3. Then it creates routable files for graphhopper in the folder europe_germany_berlin-gh. It'll skip this step if files are already present.
  4. Also check the instructions for [Android](https://github.com/graphhopper/graphhopper/wiki/Android)

For you favourite area do

```bash
$ ./graphhopper.sh web europe_france.pbf
$ ./graphhopper.sh web north-america_us_new-york.pbf
# the format follows the link structure at http://download.geofabrik.de
```

## Start Development

Open the project with NetBeans or enable Maven in your IDE. 
[Maven](http://maven.apache.org/download.cgi) is downloaded to ```graphhopper/maven``` if not 
installed when executing graphhopper.sh.

Have a look into the [Java API documentation](./) 
for further details.

For more details on Android-usage have a look into this [Android site](https://github.com/graphhopper/graphhopper/wiki/Android)

### Debug

No special setup is required since the core and the web module can be started via a main method now.

### Create your UI

 * For an example of how to use graphhopper in a web application see the [web subfolder](https://github.com/graphhopper/graphhopper/tree/master/web)
 * For an Android example see [the android folder](https://github.com/graphhopper/graphhopper/tree/master/android)
 * You can use graphhopper on the Desktop with the help of the yet outdated [mapsforge swing library](http://osm4j.svn.sourceforge.net/viewvc/osm4j/trunk/lib/). The new 'rewrite' branch of mapsforge will help here soon.

### Notes

If you develop for Android have a look into the android subfolder. For smallish graph (e.g. size of Berlin) use
 a RAMDataAccess driven GraphStorage (loads all into memory). For larger ones use the ContractionHierarchies 
preparation class and MMapDataAccess to avoid OutOfMemoryErrors. 

If you develop a web application have a look into the web demo ('web' subfolder). The simple API 
(json,jsonp) in this demo is optimized regarding several aspects:
 * It tries to return a smallish data set (encoded polyline, gzip filter)
 * It enables cross-site scripting on the server- and client-site (jQuery, header setting)
 * To make things simple it uses the GeoCoder called Nominatim to get the name for a latitude+longitude point or vice versa.
 * Where it utilizes the jquery Deferred object to chain ajax requests and avoids browser UI blocking when resolving locations in parallel.

## Web Service Deployment

For simplicity you could just start jetty from maven and schedule it as background job: 
`export GH_FOREGROUND=false && export JETTY_PORT=11111 && ./graphhopper.sh web europe_germany_berlin.pbf`. 
Then the service will be accessible on port 11111.

The Web API documentation is [here](../web)

For production usage you can install the latest jetty (8 or 9) as a service but we prefer to have it bundled as a 
simple jar. Tomcat should work too. To create a war file do `mvn clean war:war` and copy it from the target/ 
folder to your jetty installation. Then copy web/config.properties also there and change this properties 
file to point to the required graphhopper folder. Increase the Xmx/Xms values of your jetty server e.g. 
for world wide coverage with a hierarchical graph I do the following in bin/jetty.sh
```bash
export JAVA=java-home/bin/java
export JAVA_OPTIONS="-server -Xconcurrentio -Xmx15000m -Xms15000m"
```

For [World-Wide-Road-Network](https://github.com/graphhopper/graphhopper/wiki/World-Wide-Road-Network) we have a separate wiki page.

Important notes:
 * none-hierarchical graphs should be limited to a certain distance otherwise you'll require lots of RAM per request! See https://github.com/graphhopper/graphhopper/issues/104
 * if you have strange speed problems which could be related to low memory you can try to [entire disable swap](http://askubuntu.com/questions/103915/how-do-i-configure-swappiness). Or just try it out via `sudo swapoff -a`. Swapping out is very harmful to Java programs especially when the GC kicks in.

## Technical Overview

To get a better understanding also take a look in the source code, especially in the unit tests and in 
some resources we [published](http://karussell.wordpress.com/2014/01/23/graphhopper-news-article-in-java-magazine-and-fosdem-2014/). 

There are mainly three parts:

### 1. Data Import

The default import is done via OSMReader which imports OpenStreetMap data. You can configure it via API 
or use the graphhopper.sh script which utilizes the config.properties where you can specify if it should 
read CAR, FOOT etc or all at once. You'll have to make sure that you allocate enough memory for your 
specific graph (E.g. ~1GB for Germany) e.g. `export JAVA_OPTS="-Xmx1g"`. The import process is fast e.g. 
complete germany takes about 10 minutes on my oldish laptop. Additionally it will take time if you choose 
osmreader.chShortcuts=fastest in the config.properties which will dramatically improve query time.

### 2. The Graph

To process algorithms you need a _Graph_. At the moment there is one main implementation GraphHopperStorage 
which can be used: 
  * in-memory with a safe/flush option (RAMDataAccess) and 
  * a memory mapped (MMapDataAccess).

The interface _Graph_ is developed in the sense that the implementation can be as much efficient as possible
 - i.e. node ids and edge ids are successive (and so are just _indices_) and in the range of 0 to MAX-1. 
This design could be used to have an array-like structure in the underlying DataAccess implementation like 
it is currently the case.

The data layout for the DataAccess objects in GraphHopperStorage called 'nodes' and 'edges' is the following:

![storage layout](http://karussell.files.wordpress.com/2013/08/wiki-graph.png)

Some explanations:
 * One 'node row' consists of latitude,longitude (not shown) and an edgeID
 * One 'edge row' consists of two edgeIDs: nextA and nextB, then two nodeIDs nodeA and nodeB, and finally some properties like the distance and the flags.
 * One node has several edges which is implemented as a linked list. E.g. node 3 points to its first edge in the edge area at position 0 to edge 0-3 (nodeA-nodeB where nodeA is always smaller than nodeB). To get the next edge of node 3 you need nextB and this goes to edge 1-3, again node 3 is nodeB, but for the next edge 3-5 node 3 is nodeA ... and so on.
 * For you custom data import keep in mind that although the nodes 4 and 6 have no edges they still 'exist' and consume space in the current implementations of DataAccess. For OSMReader this cannot be the case as separate networks with only a small number of nodes are removed (very likely OSM bugs).

For some algorithms there are special implementations of the Graph. E.g. there is a LevelGraphStorage which is a Graph with the possibility to store shortcut edges and a level for every node. This special storage is necessary for _Contraction Hierarchies_. For this the graph needs also some preprocessing (which can take several hours for bigger areas like Europe) which is done in the OSMReader when configured (osmreader.chShortcuts=fastest) or via API in PrepareContractionHierarchies. In order to use the shortcuts and get the benefits of the optimized graph you must use the algorithm returned from createAlgo() in the preparation class.

A LevelGraphStorage (and all subclasses of GraphStorage) cannot read files created with GraphStorage and vice versa. Also there is a file version which is changed if the data structure of GraphHopper gets incompatible to the previous versions.

### 3. The Algorithms

In the routing package you'll find some shortest path algorithms like Dijkstra or A* etc. For those 
algorithms you need a _Graph_.

An algorithm needs a kind of path extraction: from the shortest-path-tree one needs to determine the route 
(list of edges) including the distance and time. Afterwards from this list the exact point (latitude,longitude) 
can be determined. For bidirectional algorithms this is a bit more complicated and done in PathBidirRef. 
For [_Contraction Hierarchies_](http://ad-wiki.informatik.uni-freiburg.de/teaching/EfficientRoutePlanningSS2012)
 one needs to additionally find the shortcutted edges and process them recursivly - this is done in Path4CH.

Further Links
---------------
 * [Spatial Key](http://karussell.wordpress.com/2012/05/23/spatial-keys-memory-efficient-geohashes/)
 * [Author@Twitter](https://twitter.com/timetabling)