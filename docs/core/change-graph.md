# Change Graph Endpoint

It is possible to change the graph without restarting GraphHopper. This is possible by using the `/change` endpoint.
Currently, it is possible to change the speed of edges as well as the access value of edges.

### Before you start

You should be aware that changing either the access or the speed will not influence the results of ch requests.
We recommend using `prepare.ch.weightings=no` or you should know what you are doing. This can be used in combination
with [Landmarks](landmarks.md), but only if you increase the weight, decreasing the weight might lead to incorrect
routing results. Per default the `/change` endpoint is disabled for security reasons, you can however enable the 
endpoint first. Be aware that this endpoint is not secured and everybody can call it and change the graph. 
You should also be aware that the changes are not persistent, when you restart the server the changes are lost,
unless you call `graphHopperStorage.flush()`.

### Getting started

First, enable the `/change` endpoint. On a Unix system, open the terminal and go to the GraphHopper directory.
Type `export GH_WEB_OPTS=-Dgraphhopper.web.change_graph.enabled=true`.
Start graphhopper by typing `./graphhopper.sh -a web -i <your-pbf>`.
In this example we use `baden-wuerttemberg-latest.osm.pbf`.

You can view the test route [here](http://localhost:8989/maps/?point=48.69232%2C9.264393&point=48.683594%2C9.257913).

For this example we will assume that you use the car profile. You can now send the following Geojson as POST to the 
`/change` endpoint. This Geojson will change the access value of the road at `48.685266, 9.260648` to false.

```
{
     "type": "FeatureCollection",
     "features": [{
       "type": "Feature",
       "geometry": {
         "type": "Point",
         "coordinates": [9.260648, 48.685266]
       },
       "properties": {
         "vehicles": ["car"],
         "access": false
        }
     }]
}
```

You can send the POST with curl like this: `curl -H "Content-Type: application/json" -X POST -d '{"type": "FeatureCollection","features": [{"type": "Feature","geometry": {"type": "Point","coordinates": [9.260648, 48.685266]},"properties": {"vehicles": ["car"],"access": false}}]}' http://localhost:8989/change`

If you visit the test url from above again, you will see that the route changed and parts of the `L 1202` have become
inaccessible for GraphHopper's car profile. Bike or Foot will still use this road (if you enabled them as well). 

You can create more complicated Geojsons than the one shown in the example. We allow passing most Geojson Geometries,
including Polygons, BBoxes, and Points.  