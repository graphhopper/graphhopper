# GraphHopper [![Build Status](https://secure.travis-ci.org/karussell/GraphHopper.png?branch=master)](http://travis-ci.org/karussell/GraphHopper)

[Memory efficient data structures & algorithms on (geo) graphs](http://karussell.github.com/GraphHopper/)

License
----------------

This software stands under Apache License 2

Example Usage
------------------

You can download a smaller OSM file (40MB compressed, then 450MB uncompressed), build graph hopper and run it with one command:

> cd core && ./run-ui.sh

 * when executing the command again the OSM won't be parsed again, so the UI should pop up within 1 or 2 seconds.
 * After the UI popped up you can drag to move the map or scroll to zoom like in ordinary maps apps
 * Click once to select a departure and another click to select the destination
 * Then a route should pop up like in this image ![from twitter](https://p.twimg.com/AvidlNPCMAA5e_n.png:medium)

Hints
------------------

 * If you want to import the Germany road network run:
   > cd core; ./run-ui.sh false

   * It takes approx 25 minutes for the import and roughly 1 minute for the ugly&slow UI to pop up. Probably you'll need to tune the memory settings - send me a mail if this fails!
   * At the moment all operations require redrawing the graph which takes quite some time! So don't click or drag too much ;)
 * you can specify debug=false for the MiniGraphUI see run-ui.sh script. Then you can easier debug the graphs' draw-routine