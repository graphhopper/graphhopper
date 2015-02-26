## Technical Overview of GraphHopper

To get a better understanding also take a look in the source code, especially in the unit tests and in 
some resources we [published](http://karussell.wordpress.com/2014/01/23/graphhopper-news-article-in-java-magazine-and-fosdem-2014/)
or [here](http://graphhopper.com/public/slides/).

There are mainly three parts:

### 1. Data Import

The default import is done via OSMReader which imports OpenStreetMap data. You can configure it via API 
or use the graphhopper.sh script which utilizes the config.properties where you can specify if it should 
read CAR, FOOT etc or all at once. You'll have to make sure that you allocate enough memory for your 
specific graph (E.g. ~1GB for Germany) e.g. `export JAVA_OPTS="-Xmx1g"`. The import process is fast e.g. 
complete germany takes about 10 minutes on my oldish laptop. Additionally it will take time if you choose 
prepare.chWeighting=fastest in the config.properties which will dramatically improve query time
but requires more RAM on import.

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
 * One 'node row' consists of latitude,longitude (not shown) and the first edgeID
 * One 'edge row' consists of two edgeIDs: nextA and nextB, then two nodeIDs nodeA and nodeB, and finally some properties like the distance and the flags.
 * One node has several edges which is implemented as a linked list. E.g. node 3 points to its first edge in the edge area at position 0 to edge 0-3 (nodeA-nodeB where nodeA is always smaller than nodeB). To get the next edge of node 3 you need nextB and this goes to edge 1-3, again node 3 is nodeB, but for the next edge 3-5 node 3 is nodeA ... and so on.
 * For you custom data import keep in mind that although the nodes 4 and 6 have no edges they still 'exist' and consume space in the current implementations of DataAccess. For OSMReader this cannot be the case as separate networks with only a small number of nodes are removed (very likely OSM bugs).

For some algorithms there are special implementations of the Graph. E.g. there is a LevelGraphStorage which is a Graph with the possibility to store shortcut edges and a level for every node. This special storage is necessary for _Contraction Hierarchies_. For this the graph needs also some preprocessing (which can take several hours for bigger areas like Europe) which is done in the OSMReader when configured (prepare.chWeighting=fastest) or via API in PrepareContractionHierarchies. In order to use the shortcuts and get the benefits of the optimized graph you must use the algorithm returned from createAlgo() in the preparation class.

A LevelGraphStorage (and all subclasses of GraphStorage) cannot read files created with GraphStorage and vice versa. Also there is a file version which is changed if the data structure of GraphHopper gets incompatible to the previous versions.

### 3. The Algorithms

In the routing package you'll find some shortest path algorithms like Dijkstra or A* etc. For those 
algorithms you need a _Graph_.

An algorithm needs a kind of path extraction: from the shortest-path-tree one needs to determine the route 
(list of edges) including the distance and time. Afterwards from this list the exact point (latitude,longitude) 
can be determined. For bidirectional algorithms this is a bit more complicated and done in PathBidirRef. 
For [_Contraction Hierarchies_](http://ad-wiki.informatik.uni-freiburg.de/teaching/EfficientRoutePlanningSS2012)
 we use the _LevelGraph_ which additionally holds shortcuts. While path extraction we need to identify those
 shortcuts and get the edges recursivly, this is done in Path4CH.

## 3.1 OriginalGraph

See issue [#116](https://github.com/graphhopper/graphhopper/issues/116) for the progress of this feature.

In order to traverse the _LevelGraph_ like a normal _Graph_ one needs to hide the shortcuts, which
is done automatically for you if you call graph.getBaseGraph(). This is necessary in a 
_LocationIndex_ and partially in the _Path_ class in order to identify how many streets leave a junction
or similar. See #116 for more information.


### 4. Connecting the Real World to the Graph

## 4.1 LocationIndex

In real world we have addresses and/or coordinates for the start and end point. 
To get the coordinate from an address you will need a geocoding solution not part of GraphHopper,
e.g. have a look into our [Routing Web API](http://graphhopper.com/#enterprise) for more information about this topic.

To get the closest node or edge id from a coordinate we provide you with an efficient lookup concept:
the LocationIndex. There are multiple implementations
where the LocationIndexTree is the most precise and scalable one and used in almost all places.
See [here](./location-index.md) for more information. See #17 and #221.


## 4.2 QueryGraph

In order to route not only from junctions (which are nodes) we introduced the _QueryGraph_ in issue #27,
which creates virtual nodes and edges at the query coordinates. It provides a lightweight wrapper around
the _Graph_ and is created per query so that queries do not influence each other.
