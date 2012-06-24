# GraphHopper [![Build Status](https://secure.travis-ci.org/karussell/GraphHopper.png?branch=master)](http://travis-ci.org/karussell/GraphHopper)

[Memory efficient data structures & algorithms on (geo) graphs](http://karussell.github.com/GraphHopper/)

License
----------------

This software stands under Apache License 2

Routing Usage
---------------

Download OSM file (40MB compressed, then 450MB uncompressed), build graph hopper and run it:

> cd core; ./run.sh unterfranken

If you want to import a bigger OSM (Germany) then run:

 * when executing the command again the OSM won't be parsed again, so the UI should pop up within 1 or 2 seconds.
 * After the UI popped up you can drag to move the map or scroll to zoom like in ordinary maps apps
 * Click once to select a departure and another click to select the destination
 * Then a route should pop up like in this SHINY ;) image ![from twitter](http://karussell.files.wordpress.com/2012/06/graphhopper.png)

> cd core; ./run.sh germany

 * For Germany it takes approx 25 minutes for the import and roughly 1 minute for the ugly&slow UI to pop up. Probably you'll need to tune the memory settings - send me a mail if this fails!
 * At the moment all operations require redrawing the graph which takes quite some time! So don't click or drag too much ;)

QuadTree Usage
---------------

See the performacen comparison subproject and the articles:

http://karussell.wordpress.com/2012/06/17/failed-experiment-memory-efficient-spatial-hashtable/

http://karussell.wordpress.com/2012/05/29/tricks-to-speed-up-neighbor-searches-of-quadtrees-geo-spatial-java/

http://karussell.wordpress.com/2012/05/23/spatial-keys-memory-efficient-geohashes/