This example uses jQuery and Leaflet to display the calculated route from GraphHopper.

 1. Edit config.properties to point to your map
 2. Start jetty via 'mvn jetty:run' (in pom.xml you can edit the port)
 3. get the raw json query [here](http://localhost:8989/api?from=52.439688,13.276863&to=52.532932,13.479424)
 4. watch the demo at [http://localhost:8989/](http://localhost:8989/)

[![result image](https://raw.github.com/graphhopper/graphhopper-web/master/graphhopper-web.png)](http://graphhopper.com/maps/?from=rostock&to=m%C3%BCnchen)