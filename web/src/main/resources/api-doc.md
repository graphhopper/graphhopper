## Routing Web API Docs

In order to communicate with your own hosted GraphHopper server you need to understand how. 

If you intend to use the Web API without hosting GraphHopper on your own
servers you can have a look into [our packages](http://graphhopper.com/#enterprise)!

### A simple example
[http://localhost:8989/api/route?point=45.752193%2C-0.686646&point=46.229253%2C-0.32959](http://localhost:8989/api/route?point=45.752193%2C-0.686646&point=46.229253%2C-0.32959)

The end point of the local instance is [http://localhost:8989](http://localhost:8989)

The URL path to obtain the route is `api/route`

All official parameters are shown in the following table

Parameter   | Default | Description
:-----------|:--------|:-----------
point       | -       |Specifiy multiple points for which the route should be calculated. The order is important. Currently only two point parameter are supported. At least two points have to be specified.
locale      | en      | The locale of the result. E.g. pt_PT for Portuguese
instructions| true  | If instruction should be calculated and returned
vehicle     | car     | The vehicle for which the route should be calculated. Other vehicles are foot and bike
weighting   | fastest | Which kind of 'best' route calculation you need. Other option is 'shortest', currently not available in the public service.
algorithm   | dijkstrabi | The algorithm to calculate the route. Other options are dijkstra, astar and astarbi. For the public service only dijkstrabi is supported.
encodedPolyline | true | If the resulting route should be 'compressed' using a special algorithm leading to massive bandwith reduction. You'll need a special handling on the client, if enabled. We provide Open Source code in Java and JavaScript.
debug       | false   | If true, the output will be formated.
calcPoints  | true    | If the points for the route should be calculated at all. Sometimes only the distance and time is necessary.
type        | json    | Specifies the resulting format of the route, for json the content type will be application/json. Other possible format options: <br> jsonp you'll need to provide the callback function via the callback parameter. The content type will be application/javascript<br> gpx, the content type will be application/xml
minPathPrecision | 1  | Not recommended to change. Increase this number if you want to further reduce bandwith.

## Example output for the case type=json

Keep in mind that attributes which are not documented here can be removed in the future - so you should not rely on them!

```json
{ "info": {
    "routeFound": true,
    "took": 0.002
  },
  "route": {
    "bbox": [ 13.388849400, 52.4457314, 13.444651963, 52.5170358125 ],
    "coordinates": "yhb_Imp`qAOwE@c@Ee@F_@@m@[CA^KdBqAhC_B`EoCfIk@`CQdAu....",
    "distance": 10246.272216816824,
    "time": 857191,
    "instructions": {
      "descriptions": [
        "Continue onto Parchimer Allee",
        "Turn slight right onto Fulhamer Allee",
        "Turn right onto Britzer Damm",
        "Continue onto Britzer Brücke",
        ...
        "Finish!"
      ],
      "distances": [
        188.393,
        628.815,
        1058.67,
        43.245,
        ...
        0
      ],
      "indications": [
        0,
        1,
        2,
        0,
        ...
        4
      ],
      "latLngs": [
        [ 52.4457314546623, 13.44279753929394 ],
        [ 52.445991764500555, 13.44398439177836 ],
        [ 52.44897032042527, 13.436276393449523 ],
        [ 52.45799502263952, 13.436300049043448 ],
        ...
        [ 52.517035812555115,13.388849400593703 ]
      ],
      "millis": [
        26100,
        78113,
        84689,
        3459,
        ...
        0
      ]
    }
  }
}
```

The JSON result contains the following structure:

JSON path/attribute | Description
:-------------------|:------------
info.took           | How many ms the request took on the server, without latency taken into account.
info.routeFound     | Is true if route was found, false otherwise
route.bbox          | The bounding box of the route, format: <br> minLon, minLat, maxLon, maxLat
route.coordinates   | The polyline encoded coordinates of the route. Order is lat,lon as it is no geoJson! Not provided if encodedPolyline=false, which is not yet formalized to be documented.
route.distance      | The overall distance of the route, in meter
route.time          | The overall time of the route, in ms
route.instructions  | Contains information about the instructions for this route. The last instruction is always the Finish instruction and takes 0ms and 0meter. Keep in mind that instructions are currently under active development and can sometimes contain misleading information, so, make sure you always show an image of the map at the same time when navigating your users!
route.instructions.descriptions | A description what the user has to do in order to follow the route. The language depends on the locale parameter.
route.instructions.distances    | The array of distances of validity for every instruction, in meter
route.instructions.millis       | The array of durations of validity for every instruction, in ms
route.instructions.latLngs      | The array of the first point where an instruction should be presented to the user
route.instructions.indications  | The array of indication numbers for every instruction. If you want to display signs for right turn etc you need these numbers. <br>TURN_SHARP_LEFT = -3<br>TURN_LEFT = -2<br>TURN_SLIGHT_LEFT = -1<br>CONTINUE_ON_STREET = 0<br>TURN_SLIGHT_RIGHT = 1<br>TURN_RIGHT = 2<br>TURN_SHARP_RIGHT = 3


## Area information

If you need to find out defails about the area or need to ping the service use 'api/info'

[http://localhost:8989/api/info](http://localhost:8989/api/info)

### Example output:
```json
{ "buildDate":"2014-02-21T16:52",
  "bbox":[13.0726237909337,52.33350773901,13.7639719344073,52.679616459003],
  "version":"0.3",
  "supportedVehicles":"foot"
}
```

JSON path/attribute | Description
:-------------------|:------------
buildDate           | The GraphHopper build date
version             | The GraphHopper version
supportedVehicle    | A comma separated list of supported vehicles
bbox                | The maximum bounding box of the area, format: <br> minLon, minLat, maxLon, maxLat

