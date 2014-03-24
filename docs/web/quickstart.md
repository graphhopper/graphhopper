## Quickstart

If you want to build GraphHopper from source look at the [[Developers]] page. 
The following steps are simpler and only need the JRE, jetty, a war file and and an OSM file.

 1. Download the new [graphhopper distribution](http://oss.sonatype.org/content/groups/public/com/graphhopper/graphhopper-web/0.3/TODO)
 2. Download the [config example](https://raw.github.com/graphhopper/graphhopper/master/config-example.properties).
 3. Download an OSM file. For example [berlin-latest.osm.pbf](http://download.geofabrik.de/europe/germany/berlin.html)
 4. Now start GraphHopper Maps via: java -jar -jar graphhopper-web-[version].jar jetty.port=8989 config=config-example.properties osmreader.osm=berlin-latest.osm.pbf 
 5. Go to [http://localhost:8989/](http://localhost:8989/) and you should see a map of Berlin. You should be able to click on the map and a route appears.

## Troubleshooting

 * Make sure JRE7 or 8 is installed. If not get Java [here](http://java.com).
 * Regarding step 4:
    * The folder should contain the following files: berlin-latest.osm.pbf, config-example.properties and `graphhopper-web-[version].war`
    * The first time you execute this it'll take ~30 seconds (for Berlin), further starts will only load the graph and should be nearly instantaneous. You should see log statements but no exceptions and the last entry should be something like: Started server at HTTP 8989
 * Join the [mailing list](http://graphhopper.com/#developers) and do not hesitate to ask questions!