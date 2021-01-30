## Map Matching


Snaps GPX traces to the road. 
        
Read more about the map matching problem at [Wikipedia](https://en.wikipedia.org/wiki/Map_matching). 

See the demo in action (black is GPS track, green is matched result):

![map-matching-example](https://cloud.githubusercontent.com/assets/129644/14740686/188a181e-0891-11e6-820c-3bd0a975f8a5.png)

### Web app

Start via:
```bash
java -jar web/target/graphhopper-web-3.0-SNAPSHOT.jar server config.yml
```

Access the simple UI via `http://localhost:8989/maps/map-matching/` (including the trailing slash).

You can post GPX files and get back snapped results as GPX or as JSON. An example curl request is:
```bash
curl -XPOST -H "Content-Type: application/gpx+xml" -d @web/src/test/resources/test1.gpx "localhost:8989/match?vehicle=car&type=json"
```

#### Java usage

Have a look at `MapMatchingResource.java` to see how the web service is implemented on top
of library functions to get an idea how to use map matching in your own project.

Use this Maven dependency:
```xml
<dependency>
    <groupId>com.graphhopper</groupId>
    <artifactId>graphhopper-map-matching</artifactId>
    <version>3.0-SNAPSHOT</version>
</dependency>
```

### Note

Note that the edge and node IDs from GraphHopper will change for different PBF files,
like when updating the OSM data.

### About

The map matching algorithm mainly follows the approach described in

*Newson, Paul, and John Krumm. "Hidden Markov map matching through noise and sparseness."
Proceedings of the 17th ACM SIGSPATIAL International Conference on Advances in Geographic
Information Systems. ACM, 2009.*

This algorithm works as follows. For each input GPS position, a number of
map matching candidates within a certain radius around the GPS position is computed.
The [Viterbi algorithm](https://en.wikipedia.org/wiki/Viterbi_algorithm) as provided by the
[hmm-lib](https://github.com/bmwcarit/hmm-lib) is then used to compute the most likely sequence
of map matching candidates. Thereby, the distances between GPS positions and map matching
candidates as well as the routing distances between consecutive map matching candidates are taken
into account. The GraphHopper routing engine is used to find candidates and to compute routing
distances.