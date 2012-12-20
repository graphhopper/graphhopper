# GraphHopper [![Build Status](https://secure.travis-ci.org/graphhopper/graphhopper.png?branch=master)](http://travis-ci.org/graphhopper/graphhopper)

Java road routing engine

Purpose
---------------

Solving shortest path (related) problems is the main goal. GraphHopper is a routing engine which
makes implementing shortest path problems in Java easier and more efficient (faster, less memory, etc) than
a naive implementation.
GraphHopper is tuned for road networks at the moment but can be useful for public transport problems in
the future as well.

Features
---------------

 * 100% Java and 100% Open Source
 * Memory efficient
 * Easy to use and small library (~3MB)
 * Works on the [desktop](http://karussell.files.wordpress.com/2012/06/graphhopper.png), 
   [from the web](https://github.com/graphhopper/graphhopper-web) 
   and even offline [on Android](https://github.com/graphhopper/graphhopper/wiki/Android)
 * Well tested

Usage
---------------

```java
 // Initialization for the API to be used on a desktop or server pc
 GraphHopperAPI gh = new GraphHopper().forServer();
 gh.load("graph-hopper-folder");

 // Offline API on Android
 GraphHopperAPI gh = new GraphHopper().forAndroid();
 gh.load("graph-hopper-folder");

 // Online: easily connect to your own hosted graphhopper web service
 GraphHopperAPI gh = new GraphHopper();
 gh.load("http://your-graphhopper-service.com/api");

 
 GHRequest request = new GHRequest(new GeoPoint(fromLat, fromLon), new GeoPoint(toLat, toLon));
 request.algorithm("astar");
 GHResponse response = gh.route(request);
 print(response.distance() + " " + response.time());
 for(GHPoint point : response.points()) {
    add(point.lat, point.lon);
 }
```