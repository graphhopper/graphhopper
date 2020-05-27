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
hopper.setEncodingManager(EncodingManager.create("car"));

// see docs/core/profiles.md to learn more about profiles
hopper.setProfiles(
    new Profile("car").setVehicle("car").setWeighting("fastest")
);
// this enables speed mode for the profile we call "car" here
hopper.getCHPreparationHandler().setCHProfiles(
    new CHProfile("car")
);

// now this can take minutes if it imports or a few seconds for loading
// of course this is dependent on the area you import
hopper.importOrLoad();

// simple configuration of the request object
GHRequest req = new GHRequest(latFrom, lonFrom, latTo, lonTo).
    // note that we have to specify which profile we are using even when there is only one like here
    setProfile("car").
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

See [GraphHopperIT.java](../../reader-osm/src/test/java/com/graphhopper/GraphHopperIT.java) for many more examples.

## Speed mode vs. Hybrid mode vs. Flexible mode

GraphHopper offers three different choices of algorithms for routing: The speed mode (which implements Contraction
Hierarchies (CH) and is by far the fastest), the hybrid mode (which uses Landmarks (LM) and is still fast, but also supports
some features CH does not support) and the flexible mode (Dijkstra or A*) which does not require calculating index data
and offers full flexibility but is a lot slower.

See the [profiles](./profiles.md) for an explanation how to configure the different routing modes. At query time you
can disable speed mode using `ch.disable=true`. In this case either hybrid mode (if there is an LM preparation for the
chosen profile) or flexible mode will be used. To use flexible mode in the presence of an LM preparation you need to 
also set `lm.disable=true`.

If you need multiple vehicle profiles you can specify a list of vehicle profiles (see
config.yml e.g. `graph.flag_encoders=car,bike` or use `EncodingManager.create("car,bike")`). 

To calculate a route you have to pick one vehicle and optionally an algorithm like `bidirectional_astar`:

```java
GraphHopper hopper = new GraphHopperOSM().forServer();
// ... see above
hopper.setEncodingManager(EncodingManager.create("car,bike"));

hopper.importOrLoad();

GHRequest req = new GHRequest(latFrom, lonFrom, latTo, lonTo).
    setProfile("bike").setAlgorithm(Parameters.Algorithms.ASTAR_BI);
GHResponse res = hopper.route(req);
```

## Heading

The flexible and hybrid modes allow adding a desired heading (north based azimuth between 0 and 360 degree)
to any point. Adding a heading makes it more likely that a route starts towards the provided direction, because
roads going into other directions are penalized (see the Routing.HEADING_PENALTY parameter)
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

The flexible and hybrid mode allows you to calculate alternative routes via:
```java
req.setAlgorithm(Parameters.Algorithms.ALT_ROUTE)
```

Note that this setting can affect speed of your routing requests. 

You can tune the maximum numbers via:
```java
req.getHints().put(Parameters.AltRoute.MAX_PATHS, "3");
```

See the Parameters class for further hints.

## Java client (client-hc)
 
If you want to calculate routes using the [GraphHopper Directions API](https://www.graphhopper.com/products/) or a self hosted instance of GraphHopper, you can use the [Java and Android client-hc](https://github.com/graphhopper/graphhopper/tree/master/client-hc) (there are also clients for [Java Script](https://github.com/graphhopper/directions-api-js-client) and [many other languages](https://github.com/graphhopper/directions-api-clients)). 

```java
GraphHopperAPI gh = new GraphHopperWeb();
gh.load("http://your-graphhopper-service.com");

// or for the GraphHopper Directions API https://graphhopper.com/#directions-api
// gh.load("https://graphhopper.com/api/1/route");

GHResponse rsp = gh.route(new GHRequest(...));
```
