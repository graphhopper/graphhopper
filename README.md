# BikeHopper

This is a fork of [GraphHopper](https://github.com/graphhopper/graphhopper) used by the [BikeHopper](https://github.com/bikehopper) multi modal routing project. We have made some changes to bike routing and public transit routing to make the weights play nicely together to generate (mostly) sensible multimodal routes. This README contains some instructions for BikeHopper specific setup, and some excerpts from the main GraphHopper README. Please check out the main project for more details, this router is quite excellent

## Configuring local BikeHopper router instance for the Bay Area

If you're actively making changes to [our fork of GraphHopper](https://github.com/bikehopper/graphhopper), follow these steps.

1. You'll need a local OSM cutout for Northern California.

   ```sh
   wget http://download.geofabrik.de/north-america/us/california/norcal-latest.osm.pbf
   ```

   Place the OSM cutout at `graphhopper/data/norcal-latest.osm.pbf`.

2. You'll also need GTFS data. Follow steps on this page, under "To Use the Feed and Ask Questions": https://www.interline.io/blog/mtc-regional-gtfs-feed-release/

   Place the GTFS zip file at `graphhopper/data/GTFSTransitData_RG.zip`.

# GraphHopper Routing Engine

GraphHopper is a fast and memory-efficient routing engine released under Apache License 2.0. It can be used as a Java library or standalone web server to calculate the distance, time, turn-by-turn instructions and many road attributes for a route between two or more points. Beyond this "A-to-B" routing it supports ["snap to road"](README.md#Map-Matching), [Isochrone calculation](README.md#Analysis), [mobile navigation](README.md#mobile-apps) and [more](README.md#Features). GraphHopper uses OpenStreetMap and GTFS data by default and it can import [other data sources too](README.md#OpenStreetMap-Support).

# Community

We have an open community and welcome everyone. Let us know your problems, use cases or just [say hello](https://discuss.graphhopper.com/). Please see our [community guidelines](https://graphhopper.com/agreements/cccoc.html).

## Questions

All questions go to our [forum](https://discuss.graphhopper.com/) where we also have subsections specially for developers, mobile usage, and [our map matching component](./map-matching). You can also search [Stackoverflow](http://stackoverflow.com/questions/tagged/graphhopper) for answers. Please do not use our issue section for questions :)

## Contribute

Read through [how to contribute](CONTRIBUTING.md) for information on topics
like finding and fixing bugs and improving our documentation or translations! 
We even have [good first issues](https://github.com/graphhopper/graphhopper/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22) to get started.

## Installation

To install the [GraphHopper Maps](https://graphhopper.com/maps/) UI and the web service locally you [need a JVM](https://adoptopenjdk.net/) (>= Java 8) and do:

```bash
wget https://github.com/graphhopper/graphhopper/releases/download/5.0/graphhopper-web-5.0.jar https://raw.githubusercontent.com/graphhopper/graphhopper/5.x/config-example.yml http://download.geofabrik.de/europe/germany/berlin-latest.osm.pbf
java -Ddw.graphhopper.datareader.file=berlin-latest.osm.pbf -jar *.jar server config-example.yml
```

After a while you see a log message with 'Server - Started', then go to http://localhost:8989/ and
you'll see a map of Berlin. You should be able to right click on the map to create a route.

For more details about the installation, see [here](./docs/web/quickstart.md).

### Docker

The Docker images created by the community from the `master` branch can be found [here](https://hub.docker.com/r/israelhikingmap/graphhopper)
(currently daily). See the [Dockerfile](https://github.com/IsraelHikingMap/graphhopper-docker-image-push) for more details.

## License

We chose the Apache License to make it easy for you to embed GraphHopper in your products, even closed source.
We suggest that you contribute back your changes, as GraphHopper evolves fast,
but of course this is not necessary.


