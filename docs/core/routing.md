To do routing in your Java code you'll need just a few lines of code:

```java
// create singleton
GraphHopper hopper = new GraphHopper().forServer();
hopper.setOSMFile(osmFile);
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

// points, distance in meters and time in millis of the full path
PointList pointList = rsp.getPoints();
double distance = rsp.getDistance();
long timeInMs = rsp.getTime();

InstructionList il = rsp.getInstructions();
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

The default is to use the speed-up mode for one profile. If you need multiple profiles you 
specify a list of profiles (e.g. car,bike) and the speed-up mode is applied to the first profile only (e.g. car).
The other vehicles then use a more flexible routing.

You can also completely disable the speed-up mode to make all vehicles using the flexibility mode.
Then pick one vehicle and optionally the algorithm like 'bidirectional astar' as algorithm:

```java
GraphHopper hopper = new GraphHopper().forServer();
hopper.setCHEnable(false);
hopper.setOSMFile(osmFile);
hopper.setGraphHopperLocation(graphFolder);
hopper.setEncodingManager(new EncodingManager("car,bike"));

hopper.importOrLoad();

GHRequest req = new GHRequest(latFrom, lonFrom, latTo, lonTo).
    setVehicle("bike").setAlgorithm(AlgorithmOptions.ASTAR_BI);
GHResponse res = hopper.route(req);
```

In the flexibility mode it is also possible to add a desired heading (north based azimuth between 0 and 360 degree)
to any point,
```java
GHRequest req = new GHRequest().addPoint(new GHPoint (latFrom, lonFrom), favoredHeading).addPoint(new GHPoint (latTo, lonTo));
```
or to avoid u-turns at via points
```java
req.getHints().put("pass_through", true);
```
 
In case you need a web access in a Java or an Android application the GraphHopperWeb class comes handy,
 see the 'web' sub module or [the Java client for the GraphHopper Directions API](https://github.com/graphhopper/directions-api-java-client).

```java
GraphHopperAPI gh = new GraphHopperWeb();
gh.load("http://your-graphhopper-service.com");

// or for the GraphHopper Directions API https://graphhopper.com/#directions-api
// gh.load("https://graphhopper.com/api/1/route");

GHResponse rsp = gh.route(new GHRequest(...));
```
