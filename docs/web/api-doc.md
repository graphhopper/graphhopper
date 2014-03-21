## Routing Web API Docs

In order to communicate with your or [our](http://graphhopper.com/#enterprise) hosted GraphHopper 
server you need to understand how to use it.

### A simple example
[http://localhost:8989/api/route?point=45.752193%2C-0.686646&point=46.229253%2C-0.32959](http://localhost:8989/api/route?point=45.752193%2C-0.686646&point=46.229253%2C-0.32959)

The end point of the local instance is [http://localhost:8989](http://localhost:8989)

The URL path to obtain the route is `api/route`

All official parameters are shown in the following table

Parameter   | Default | Description
:-----------|:--------|:-----------
point       | -       |Specifiy multiple points for which the route should be calculated. The order is important. Currently only two point parameter are supported. At least two points have to be specified.
locale      | en      | The locale of the result. E.g. pt_PT for Portuguese or de for German
instructions| true  | If instruction should be calculated and returned
vehicle     | car     | The vehicle for which the route should be calculated. Other vehicles are foot and bike
weighting   | fastest | Which kind of 'best' route calculation you need. Other option is 'shortest', currently not available in the public service.
algorithm   | dijkstrabi | The algorithm to calculate the route. Other options are dijkstra, astar and astarbi. For the public service only dijkstrabi is supported.
points_encoded | true | If the resulting route should be 'compressed' using a special algorithm leading to massive bandwith reduction. You'll need a special handling on the client, if enabled. We provide Open Source code in Java and JavaScript.
debug       | false   | If true, the output will be formated.
calc_points  | true    | If the points for the route should be calculated at all. Sometimes only the distance and time is necessary.
type        | json    | Specifies the resulting format of the route, for json the content type will be application/json. Other possible format options: <br> jsonp you'll need to provide the callback function via the callback parameter. The content type will be application/javascript<br> gpx, the content type will be application/xml
min_path_precision | 1  | Not recommended to change. Increase this number if you want to further reduce bandwith.

## Example output for the case type=json

Keep in mind that some attributes which are not documented here can be removed in the future - 
so you should not rely on them!

```json
{
  "info": {"took": 0.00414920412003994},
  "paths": [{
    "bbox": [
      13.362853824187303,
      52.469481955531585,
      13.385836736460217,
      52.473849308838446
    ],
    "distance": 2138.3027624572337,
    "instructions": [
      {
        "distance": 1268.519329705091,
        "interval": [
          0,
          10
        ],
        "sign": 0,
        "text": "Geradeaus auf A 100",
        "time": 65237
      },
      {
        "distance": 379.74399999999997,
        "interval": [
          10,
          11
        ],
        "sign": 0,
        "text": "Geradeaus auf Strasse",
        "time": 24855
      },
      {
        "distance": 16.451,
        "interval": [
          11,
          11
        ],
        "sign": 0,
        "text": "Geradeaus auf Tempelhofer Damm",
        "time": 1316
      },
      {
        "distance": 473.58843275214315,
        "interval": [
          11,
          12
        ],
        "sign": -2,
        "text": "Links abbiegen auf Tempelhofer Damm, B 96",
        "time": 37882
      },
      {
        "distance": 0,
        "interval": [
          12,
          12
        ],
        "sign": 4,
        "text": "Ziel erreicht!",
        "time": 0
      }
    ],
    "points": "oxg_Iy|ppAl@wCdE}LfFsN|@_Ej@eEtAaMh@sGVuDNcDb@{PFyGdAi]FoC?q@sXQ_@?",
    "points_encoded": true,
    "time": 129290
  }]
}
```

The JSON result contains the following structure:

JSON path/attribute    | Description
:----------------------|:------------
info.took              | How many ms the request took on the server, of course without network latency taken into account.
paths                  | An array of possible paths
paths[0].distance      | The overall distance of the route, in meter
paths[0].time          | The overall time of the route, in ms
paths[0].points        | The polyline encoded coordinates of the route. Order is lat,lon as it is no geoJson! Not provided if encodedPolyline=false, which is not yet formalized to be documented.
paths[0].points_encoded| Is true if the points are encoded, if not paths[0].points contains the geo json of the path (then order is lon,lat), which is easier to handle but consumes more bandwidth
paths[0].bbox          | The bounding box of the route, format: <br> minLon, minLat, maxLon, maxLat
paths[0].instructions  | Contains information about the instructions for this route. The last instruction is always the Finish instruction and takes 0ms and 0meter. Keep in mind that instructions are currently under active development and can sometimes contain misleading information, so, make sure you always show an image of the map at the same time when navigating your users!
paths[0].instructions[0].description | A description what the user has to do in order to follow the route. The language depends on the locale parameter.
paths[0].instructions[0].distance    | The distance for this instruction, in meter
paths[0].instructions[0].time        | The duration for this instruction, in ms
paths[0].instructions[0].interval    | An array containing the first and the last index (relative to paths[0].points) of the points for this instruction
paths[0].instructions[0].sign        | A number which specifies the sign to show e.g. for right turn etc <br>TURN_SHARP_LEFT = -3<br>TURN_LEFT = -2<br>TURN_SLIGHT_LEFT = -1<br>CONTINUE_ON_STREET = 0<br>TURN_SLIGHT_RIGHT = 1<br>TURN_RIGHT = 2<br>TURN_SHARP_RIGHT = 3


## Area information

If you need to find out defails about the area or need to ping the service use 'api/info'

[http://localhost:8989/api/info](http://localhost:8989/api/info)

### Example output:
```json
{ "buildDate":"2014-02-21T16:52",
  "bbox":[13.0726237909337,52.33350773901,13.7639719344073,52.679616459003],
  "version":"0.3",
  "supported_vehicles":"foot"
}
```

JSON path/attribute | Description
:-------------------|:------------
build_date          | The GraphHopper build date
version             | The GraphHopper version
supported_vehicle   | A comma separated list of supported vehicles
bbox                | The maximum bounding box of the area, format: <br> minLon, minLat, maxLon, maxLat
import_date         | The date time at which the OSM import was done
prepare_date        | The date time at which the preparation (contraction hierarchies) was done. If nothing was done this is empty