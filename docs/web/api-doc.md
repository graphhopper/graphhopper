## Routing Web API Docs

In order to communicate with your or [our](http://graphhopper.com/#enterprise) hosted GraphHopper 
server you need to understand how to use it. There is a separate [JavaScript](https://github.com/graphhopper/directions-api-js-client) and [Java](https://github.com/graphhopper/directions-api-java-client) client for this API or use the plain JSON response for your language.

### A simple example
[http://localhost:8989/route?point=45.752193%2C-0.686646&point=46.229253%2C-0.32959](http://localhost:8989/route?point=45.752193%2C-0.686646&point=46.229253%2C-0.32959)

The end point of the local instance is [http://localhost:8989](http://localhost:8989)

The URL path to obtain the route is `/route`

All official parameters are shown in the following table

Parameter   | Default | Description
:-----------|:--------|:-----------
point       | -       | Specifiy multiple points for which the route should be calculated. The order is important. Specify at least two points.
locale      | en      | The locale of the result. E.g. `pt_PT` for Portuguese or `de` for German
instructions| true    | If instruction should be calculated and returned
vehicle     | car     | The vehicle for which the route should be calculated. Other vehicles are foot and bike
weighting   | fastest | Which kind of 'best' route calculation you need. Other option is 'shortest', currently not available in the WEB API.
elevation   | false   | If `true` a third dimension - the elevation - is included in the polyline or in the GeoJson. IMPORTANT: If enabled you have to use a modified version of the decoding method or set points_encoded to `false`. See the points_encoded attribute for more details. Additionally a request can fail if the vehicle does not support elevation. See the features object for every vehicle.
algorithm   | dijkstrabi     | The algorithm to calculate the route. Other options are dijkstra, astar and astarbi. The WEB API supports only dijkstrabi.
points_encoded     | true    | If `false` a GeoJson array in `point` is returned. If `true` the resulting route will be encoded leading to big bandwith reduction. You'll need a special handling for the decoding of this string on the client-side. We provide Open Source code in [Java](https://github.com/graphhopper/graphhopper/blob/d70b63660ac5200b03c38ba3406b8f93976628a6/web/src/main/java/com/graphhopper/http/WebHelper.java#L43) and [JavaScript](https://github.com/graphhopper/graphhopper/blob/d70b63660ac5200b03c38ba3406b8f93976628a6/web/src/main/webapp/js/ghrequest.js#L139). It is especially important to use our decoding methods if you set `elevation=true`!
debug              | false   | If true, the output will be formated.
calc_points        | true    | If the points for the route should be calculated at all. Sometimes only the distance and time is necessary.
type               | json    | Specifies the resulting format of the route, for json the content type will be application/json. Other possible format options: <br> jsonp you'll need to provide the callback function via the callback parameter. The content type will be application/javascript<br> gpx, the content type will be application/xml

## Example output for the case type=json

Keep in mind that some attributes which are not documented here can be removed in the future - 
you should not rely on them! The JSON result contains the following structure:

JSON path/attribute        | Description
:--------------------------|:------------
info.took                  | How many ms the request took on the server, of course without network latency taken into account.
paths                      | An array of possible paths
paths[0].distance          | The overall distance of the route, in meter
paths[0].time              | The overall time of the route, in ms
paths[0].points            | The polyline encoded coordinates of the path. Order is lat,lon,elelevation as it is no geoJson!
paths[0].points_encoded    | Is true if the points are encoded, if not paths[0].points contains the geo json of the path (then order is lon,lat,elevation), which is easier to handle but consumes more bandwidth compared to encoded version
paths[0].bbox              | The bounding box of the route, format: <br> minLon, minLat, maxLon, maxLat
paths[0].instructions      | Contains information about the instructions for this route. The last instruction is always the Finish instruction and takes 0ms and 0meter. Keep in mind that instructions are currently under active development and can sometimes contain misleading information, so, make sure you always show an image of the map at the same time when navigating your users!
paths[0].instructions[0].text                 | A description what the user has to do in order to follow the route. The language depends on the locale parameter.
paths[0].instructions[0].distance             | The distance for this instruction, in meter
paths[0].instructions[0].time                 | The duration for this instruction, in ms
paths[0].instructions[0].interval             | An array containing the first and the last index (relative to paths[0].points) of the points for this instruction. This is useful to know for which part of the route the instructions are valid.
paths[0].instructions[0].sign                 | A number which specifies the sign to show e.g. for right turn etc <br>TURN_SHARP_LEFT = -3<br>TURN_LEFT = -2<br>TURN_SLIGHT_LEFT = -1<br>CONTINUE_ON_STREET = 0<br>TURN_SLIGHT_RIGHT = 1<br>TURN_RIGHT = 2<br>TURN_SHARP_RIGHT = 3<br>FINISH = 4<br>VIA_REACHED = 5<br>USE_ROUNDABOUT = 6
paths[0].instructions[0].annotation_text      | [optional] A text describing the instruction in more detail, e.g. like surface of the way, warnings or involved costs
paths[0].instructions[0].annotation_importance| [optional] 0 stands for INFO, 1 for warning, 2 for costs, 3 for costs and warning
paths[0].instructions[0].exit_number          | [optional] Only available for USE_ROUNDABOUT instructions. The count of exits at which the route leaves the roundabout.
paths[0].instructions[0].turn_angle           | [optional] Only available for USE_ROUNDABOUT instructions. The radian of the route within the roundabout: 0<r<2*PI for clockwise and -2PI<r<0 for counterclockwise transit. Is null the direction of rotation is undefined.

```json
{
  "info": {"took": 4},
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

## Area information

If you need to find out details about the area or need to ping the service use '/info'

[http://localhost:8989/info](http://localhost:8989/info)

### Example output:
```json
{ "build_date":"2014-02-21T16:52",
  "bbox":[13.072624,52.333508,13.763972,52.679616],
  "version":"0.3",
  "features": { "foot" : { "elevation" : true  }, 
                "car"  : { "elevation" : false } }
}
```

JSON path/attribute | Description
:-------------------|:------------
version             | The GraphHopper version
bbox                | The maximum bounding box of the area, format: <br> minLon, minLat, maxLon, maxLat
features            | A json object per supported vehicles with name and supported features like elevation
build_date          | [optional] The GraphHopper build date
import_date         | [optional] The date time at which the OSM import was done
prepare_date        | [optional] The date time at which the preparation (contraction hierarchies) was done. If nothing was done this is empty
supported_vehicles  | [deprecated] An array of strings for all supported vehicles

### Error Output
```json
{
  "message": "Cannot find point 2: 2248.224673, 3.867187",
  "hints": [{"message": "something", ...}]
}
```

Sometimes a point can be "off the road" and you'll get 'cannot find point', this normally does not
indicate a bug in the routing engine and is expected to a certain degree if too far away.

JSON path/attribute    | Description
:----------------------|:------------
message                | Not intended to be displayed to the user as it is not translated
hints                  | An optional list of details regarding the error message e.g. `[{"message": "first error message in hints"}]`


### HTTP Error codes

HTTP error code | Reason
:---------------|:------------
500             | Internal server error. It is strongly recommended to send us the message and the link to it, as it is very likely a bug in our system.
501             | Only a special list of vehicles is supported
400             | Something was wrong in your request
