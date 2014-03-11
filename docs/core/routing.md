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

GHRequest req = new GHRequest(latFrom, lonFrom, latTo, lonTo).setVehicle("car");
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
long millis = rsp.getMillis();

// get the turn instructions for the path
InstructionList il = rsp.getInstructions();
Translation tr = trMap.getWithFallBack(Locale.US);
List<String> iList = il.createDescription(tr);

// or get the result as gpx entries:
List<GPXEntry> list = il.createGPXList();
```

If you want a more flexible routing (but slower) you can disable contraction hierarchies
and import multiple vehicles. Then pick one vehicle and optionally the algorithm like
astar as algorithm:

```java
GraphHopper hopper = new GraphHopper().forServer();
hopper.disableCHShortcuts();
hopper.setInMemory(true);
hopper.setOSMFile(osmFile);
hopper.setGraphHopperLocation(graphFolder);
hopper.setEncodingManager(new EncodingManager("car,bike"));

hopper.importOrLoad();

GHRequest req = new GHRequest(latFrom, lonFrom, latTo, lonTo).setVehicle("bike").setAlgorithm("astar");
GHResponse res = hopper.route(req);
```

In case you need the online routing API in a Java or Android application the GraphHopperWeb comes handy - see the 'web' sub module.

```java
GraphHopperAPI gh = new GraphHopperWeb();
gh.load("http://your-graphhopper-service.com/api");
GHResponse rsp = gh.route(new GHRequest(...));
```