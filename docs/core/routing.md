# Routing via Java API

To use the following examples you need to specify the dependency in
your [Maven config](/README.md#maven) correctly.

To do routing in your Java code you'll need just a few lines of code:

```java
// create one GraphHopper instance
GraphHopper hopper = new GraphHopperOSM().forServer();
hopper.setDataReaderFile(osmFile);
// where to store graphhopper files?
hopper.setGraphHopperLocation(graphFolder);
hopper.setEncodingManager(new EncodingManager("car"));

// now this can take minutes if it imports or a few seconds for loading
// of course this is dependent on the area you import
hopper.importOrLoad();

// simple configuration of the request object, see the GraphHopperServlet classs for more possibilities.
GHRequest req = new GHRequest(latFrom, lonFrom, latTo, lonTo).
    setWeighting("fastest").
    setVehicle("car").
    setLocale(Locale.US);
GHResponse rsp = hopper.route(req);

// first check for errors
if(rsp.hasErrors()) {
   // handle them!
   // rsp.getErrors()
   return;
}

// use the best path, see the GHResponse class for more possibilities.
PathWrapper path = rsp.getBest();

// points, distance in meters and time in millis of the full path
PointList pointList = path.getPoints();
double distance = path.getDistance();
long timeInMs = path.getTime();

InstructionList il = path.getInstructions();
// iterate over every turn instruction
for(Instruction instruction : il) {
   instruction.getDistance();
   ...
}

// or get the json
List<Map<String, Object>> iList = il.createJson();

// or get the result as gpx entries:
List<GPXEntry> list = il.createGPXList();
```

## Speed mode vs. Flexibility mode

The default is to use the speed-up mode. If you need multiple profiles you specify a list of profiles (e.g. car,bike). 

You can also completely disable the speed-up mode before import (see config.properties `prepare.ch.weightings=no`)
or for a per request setting (`ch.disable=true`). 

Then pick one vehicle and optionally an algorithm like 'bidirectional astar':

```java
GraphHopper hopper = new GraphHopperOSM().forServer();
hopper.setCHEnabled(false);
hopper.setOSMFile(osmFile);
hopper.setGraphHopperLocation(graphFolder);
hopper.setEncodingManager(new EncodingManager("car,bike"));

hopper.importOrLoad();

GHRequest req = new GHRequest(latFrom, lonFrom, latTo, lonTo).
    setVehicle("bike").setAlgorithm(Parameters.Algorithms.ASTAR_BI);
GHResponse res = hopper.route(req);
```

## Heading

In the flexibility mode it is also possible to add a desired heading (north based azimuth between 0 and 360 degree)
to any point:
```java
GHRequest req = new GHRequest().addPoint(new GHPoint (latFrom, lonFrom), favoredHeading).addPoint(new GHPoint (latTo, lonTo));
```
or to avoid u-turns at via points
```java
req.getHints().put(Parameters.Routing.PASS_THROUGH, true);
```

A heading with the value 'NaN' won't be enforced and a heading not within [0, 360] will trigger an IllegalStateException.
It is important to note that if you force the heading at via or end points the outgoing heading needs to be specified.
I.e. if you want to force "coming from south" to a destination you need to specify the resulting "heading towards north" instead, which is 0.

## Alternative Routes

In the flexibility mode you can get alternative routes via:
```java
req.setAlgorithm(Parameters.Algorithms.ALT_ROUTE)
```

Note that this setting can affect speed of your routing requests. 

You can tune the maximum numbers via:
```java
req.getHints().put(Parameters.AltRoute.MAX_PATHS, "3");
```

See the Parameters class for further hints.

## Java client
 
In case you need a web access in a Java or an Android application the GraphHopperWeb class comes handy,
 see the 'web' sub module or [the Java client for the GraphHopper Directions API](https://github.com/graphhopper/directions-api-java-client).

```java
GraphHopperAPI gh = new GraphHopperWeb();
gh.load("http://your-graphhopper-service.com");

// or for the GraphHopper Directions API https://graphhopper.com/#directions-api
// gh.load("https://graphhopper.com/api/1/route");

GHResponse rsp = gh.route(new GHRequest(...));
```
