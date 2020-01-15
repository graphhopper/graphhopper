## Quickstart

If you want to build GraphHopper from source look at the [Developers page](../core/quickstart-from-source.md). 
The following steps are simpler and only need the JRE, a jar file and an OSM file.

 1. Install the latest JRE 
 2. Get the [GraphHopper Web Service](https://github.com/graphhopper/graphhopper/blob/master/README.md#get-started)
as jar file and download this [config-example.yml](https://raw.githubusercontent.com/graphhopper/graphhopper/master/config-example.yml)
 3. Copy an OSM file to the same directory. For example [berlin-latest.osm.pbf](http://download.geofabrik.de/europe/germany/berlin.html)
 4. Start GraphHopper Maps via: 
    ```bash
    java -Dgraphhopper.datareader.file=berlin-latest.osm.pbf -jar *.jar server config-example.yml
    ```
 5. After you see 'Started server at HTTP 8989' go to [http://localhost:8989/](http://localhost:8989/) and you should see a map of Berlin. You should be able to click on the map and a route appears.

See [here](./../core/elevation.md) how to easily enable elevation data. To see how GraphHopper is configured for production usage, see the [deployment guide](./../core/deploy.md).

## Troubleshooting

 * Make sure JRE8 is installed. If not get Java [here](http://java.com).
 * Regarding step 2:
    * The folder where you execute the java command should contain the following files: berlin-latest.osm.pbf, config-example.yml and `graphhopper-web-[version].jar`
    * The first time you execute this it'll take ~30 seconds (for Berlin), further starts will only load the graph and should be nearly instantaneous. You should see log statements but no exceptions and the last entry should be something like: Started server at HTTP 8989
 * Regarding step 3:
    * Depending on the size of the map you might run into `java.lang.OutOfMemoryError`. In this case you need to increase the memory settings of the JVM by starting the above command with `java -Xmx2g -Xms2g ...` (example for 2GB memory)
 * Or [contact us](../index.md#contact)
