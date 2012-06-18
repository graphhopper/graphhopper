# GraphHopper [![Build Status](https://secure.travis-ci.org/karussell/GraphHopper.png?branch=master)](http://travis-ci.org/karussell/GraphHopper)

Memory efficient data structures & algorithms on (geo) graphs

Build graph hopper:
> cd core; mvn -DskipTests=true clean assembly:assembly

Example: routing
------------------

Now import and view Germany OSM. It takes approx 25 min for import 
and roughly 2 long minutes for the ugly&slow UI to pop up.
You can download the OSM [here](http://download.geofabrik.de/osm/)
> java -XX:PermSize=20m -XX:MaxPermSize=20m -Xmx2700m -Xms2700m -cp target/graphhopper-1.0-SNAPSHOT-jar-with-dependencies.jar de.jetsli.graph.ui.MiniGraphUI germany.osm

 * now the ui should pop up
 * drag to move the map or scroll to zoom like in ordinary maps apps
 * click once to select a departure and another click to select the destination
 * a route should pop up like in this image ![from twitter](https://p.twimg.com/AvidlNPCMAA5e_n.png:medium)

### Warning
 1. at the moment all operations require redrawing the graph which takes quite some time!
  so don't click or drag too much ;)
 2. when executing the command again the osm won't be parsed again.
  Then you need to lower the Xmx and Xms value to 700m

Further examples
-----------------

This software stands under Apache License 2

For more information have a look at
http://karussell.github.com/GraphHopper/
