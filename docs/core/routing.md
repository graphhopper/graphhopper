To do routing in your Java code you'll need just a few lines of code:

```java
GraphHopper hopper = new GraphHopper().forServer();
hopper.setInMemory(true);
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
    setVehicle("car");
GHResponse rsp = hopper.route(req);

// first check for errors
if(rsp.hasErrors()) {
   // handle them!
   // rsp.getErrors()
   return;
}

// route was found? e.g. if disconnected areas (like island) 
// no route can ever be found
if(!rsp.isFound()) {
   // handle properly
   return;
}

// points, distance in meters and time in millis of the full path
PointList pointList = rsp.getPoints();
double distance = rsp.getDistance();
long timeInMs = rsp.getTime();

// get the turn instructions for the path
InstructionList il = rsp.getInstructions();
Translation tr = trMap.getWithFallBack(Locale.US);
List<String> iList = il.createDescription(tr);

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
hopper.disableCHShortcuts();
hopper.setInMemory(true);
hopper.setOSMFile(osmFile);
hopper.setGraphHopperLocation(graphFolder);
hopper.setEncodingManager(new EncodingManager("car,bike"));

hopper.importOrLoad();

GHRequest req = new GHRequest(latFrom, lonFrom, latTo, lonTo).
    setVehicle("bike").setAlgorithm(AlgorithmOptions.ASTAR_BI);
GHResponse res = hopper.route(req);
```

In case you need a web access in a Java or an Android application the GraphHopperWeb class comes handy,
 see the 'web' sub module.

```java
GraphHopperAPI gh = new GraphHopperWeb();
gh.load("http://your-graphhopper-service.com");

// or for the GraphHopper Directions API https://graphhopper.com/#directions-api
// gh.load("https://graphhopper.com/api/1/route");

GHResponse rsp = gh.route(new GHRequest(...));
```