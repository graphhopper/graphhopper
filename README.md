# GraphHopper [![Build Status](https://secure.travis-ci.org/karussell/GraphHopper.png?branch=master)](http://travis-ci.org/karussell/GraphHopper)

[Memory efficient data structures & algorithms on (geo) graphs](http://karussell.github.com/GraphHopper/)

License
----------------

This software stands under Apache License 2

Purpose
---------------

Solving shortest path (related) problems is the main goal. GraphHopper is a routing engine which
makes implementing arbitrary shortest path problems in Java easier and much more memory efficient than
a naive implementation.
GraphHopper is tuned for road networks at the moment but can be useful for public transport problems in
the future as well.

Features
---------------

 * 100% Java
 * 100% Open Source via Apache License (=> business friendly)
 * Memory efficient (=> suited for big data)
 * Easy to use and small library with only a few dependencies
 * Latest algorithms and highly tuned to be very fast
 * Heavily tested

Routing Usage
---------------

The following command will make a part of Germany routable:

> cd core; ./run.sh unterfranken

  1. it downloads 40MB, unzips it to 450MB and creates road-files for graphhopper (40MB)
  2. it builds graphhopper
  3. and runs some shortest path queries on it

When executing the command again, then the existing graphhopper road-files and jars will be used. So, the UI should pop up fast (~2 seconds).
After the UI popped up you can drag to move the map or scroll to zoom like in ordinary maps apps.
Click once to select a departure and another click to select the destination.
Then a route should pop up like in this SHINY ;) image ![from twitter](http://karussell.files.wordpress.com/2012/06/graphhopper.png)

Visualization of
 * [a bidirectional Dijkstra](http://karussell.files.wordpress.com/2012/06/bidijkstra.gif)
 * [A*](http://karussell.files.wordpress.com/2012/07/astar.gif)

If you want to import full Germany do:

> cd core; ./run.sh germany

 1. For Germany it takes approx 25 minutes for the import and roughly 1 minute for the ugly&slow UI to pop up.
 2. At the moment the UI is a bit rough and simple so, don't click or drag too much as it takes some time for this large road network


Further Links
---------------
 * [Simple Routing - full Germany](http://karussell.wordpress.com/2012/07/16/running-shortest-path-algorithms-on-the-german-road-network-within-a-1-5gb-jvm/)
 * [Spatial Key](http://karussell.wordpress.com/2012/05/23/spatial-keys-memory-efficient-geohashes/)
 * [Speed up your Quad-tree](http://karussell.wordpress.com/2012/05/29/tricks-to-speed-up-neighbor-searches-of-quadtrees-geo-spatial-java/)
 * [Spatial Hashtable](http://karussell.wordpress.com/2012/06/17/failed-experiment-memory-efficient-spatial-hashtable/)
 * [Author@Twitter](https://twitter.com/timetabling)
 * [Lumeo - Implementing a Graph API via Lucene](https://github.com/karussell/lumeo)
 * [Cassovary - A production ready (?) in-memory & memory efficient Graph DB](https://github.com/twitter/cassovary)