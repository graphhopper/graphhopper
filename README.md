# GraphHopper [![Build Status](https://secure.travis-ci.org/karussell/GraphHopper.png?branch=master)](http://travis-ci.org/karussell/GraphHopper)

[Memory efficient data structures & algorithms on (geo) graphs](http://karussell.github.com/GraphHopper/)

License
----------------

This software stands under Apache License 2

Routing Usage
---------------

Download OSM file (40MB compressed, then 450MB uncompressed), build graph hopper and run it:

> cd core; ./run.sh unterfranken

the resulting GraphHopper file will be around 30MB

 * when executing the command again, then the OSM won't be parsed again, so the UI should pop up fast within 2 seconds.
 * After the UI popped up you can drag to move the map or scroll to zoom like in ordinary maps apps
 * Click once to select a departure and another click to select the destination
 * You want to get an impression of how bidirectional Dijkstra is working? [Click this image](http://karussell.files.wordpress.com/2012/06/bidijkstra.gif)
 * Then a route should pop up like in this SHINY ;) image ![from twitter](http://karussell.files.wordpress.com/2012/06/graphhopper.png)

If you want to import a bigger OSM (Germany) then run:

> cd core; ./run.sh germany

 * For Germany it takes approx 25 minutes for the import and roughly 1 minute for the ugly&slow UI to pop up. Probably you'll need to tune the memory settings - send me a mail if this fails!
 * At the moment the UI is a bit rough and simple so, don't click or drag too much ;)

QuadTree Usage
---------------

See the performacen comparison subproject and the articles:

http://karussell.wordpress.com/2012/06/17/failed-experiment-memory-efficient-spatial-hashtable/

http://karussell.wordpress.com/2012/05/29/tricks-to-speed-up-neighbor-searches-of-quadtrees-geo-spatial-java/

http://karussell.wordpress.com/2012/05/23/spatial-keys-memory-efficient-geohashes/