# GraphHopper [![Build Status](https://secure.travis-ci.org/karussell/GraphHopper.png?branch=master)](http://travis-ci.org/karussell/GraphHopper)

Memory efficient data structures & algorithms on (geo) graphs

Download OSM file and build graph hopper and run it:

> cd core; ./run-ui.sh

when executing the command again the osm won't be parsed again.

Hints
------------------

If you want to import the Germany OSM. run:

> cd core; ./run-ui.sh false

 * It takes approx 25 min for import and roughly 1 minute for the ugly&slow UI to pop up.
 * After the UI popped up you can drag to move the map or scroll to zoom like in ordinary maps apps
 * Click once to select a departure and another click to select the destination
 * Then a route should pop up like in this image ![from twitter](https://p.twimg.com/AvidlNPCMAA5e_n.png:medium)

Warning
-----------------

At the moment all operations require redrawing the graph which takes quite some time!
So don't click or drag too much ;)

License
----------------

This software stands under Apache License 2

Infos
----------------

For more information have a look at
http://karussell.github.com/GraphHopper/
