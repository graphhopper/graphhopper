# GraphHopper Routing Engine

[![Build Status](https://secure.travis-ci.org/graphhopper/graphhopper.png?branch=master)](http://travis-ci.org/graphhopper/graphhopper)

GraphHopper is a fast and memory efficient Java routing engine, released under Apache License 2.0.
By default it uses OpenStreetMap and GTFS data, but it can import other data sources.

# Community

We have a prospering community and welcome everyone. Let us know your problems, use cases or just [say hello](https://discuss.graphhopper.com/). Please see our [community guidelines](https://graphhopper.com/agreements/cccoc.html).

## Get Started

To get started, read through our documentation and install the GraphHopper Web Service locally.

 * 0.13.0: [stable documentation](https://github.com/graphhopper/graphhopper/blob/0.13/docs/index.md), [web service jar](https://graphhopper.com/public/releases/graphhopper-web-0.13.0.jar), [Android APK](https://graphhopper.com/public/releases/graphhopper-android-0.13.0.apk), [announcement](https://www.graphhopper.com/blog/2019/09/18/graphhopper-routing-engine-0-13-released/) 
 * unstable: [unstable documentation](https://github.com/graphhopper/graphhopper/blob/master/docs/index.md), [web service jar](https://oss.sonatype.org/content/groups/public/com/graphhopper/graphhopper-web/1.0-SNAPSHOT/)
 * 0.12.0: [documentation](https://github.com/graphhopper/graphhopper/blob/0.12/docs/index.md), [web service jar](https://graphhopper.com/public/releases/graphhopper-web-0.12.0.jar), [Android APK](https://graphhopper.com/public/releases/graphhopper-android-0.12.0.apk), [announcement](https://www.graphhopper.com/blog/2019/03/26/graphhopper-routing-engine-0-12-released/)
 * 0.11.0: [documentation](https://github.com/graphhopper/graphhopper/blob/0.11/docs/index.md), [web service jar](https://graphhopper.com/public/releases/graphhopper-web-0.11.0.jar), [Android APK](https://graphhopper.com/public/releases/graphhopper-android-0.11.0.apk), [announcement](https://www.graphhopper.com/blog/2018/09/17/graphhopper-routing-engine-0-11-release-open-sourcing-the-isochrone-module/)
 * 0.10.0: [documentation](https://github.com/graphhopper/graphhopper/blob/0.10/docs/index.md), [web service zip](https://graphhopper.com/public/releases/graphhopper-web-0.10.3-bin.zip), [Android APK](https://graphhopper.com/public/releases/graphhopper-android-0.10.3.apk), [announcement](https://www.graphhopper.com/blog/2018/03/08/graphhopper-routing-engine-0-10-released/)
 * 0.9.0: [documentation](https://github.com/graphhopper/graphhopper/blob/0.9/docs/index.md), [web service zip](https://graphhopper.com/public/releases/graphhopper-web-0.9.0-bin.zip), [Android APK](https://graphhopper.com/public/releases/graphhopper-android-0.9.0.apk), [announcement](https://www.graphhopper.com/blog/2017/05/31/graphhopper-routing-engine-0-9-released/)
 * 0.8.2: [documentation](https://github.com/graphhopper/graphhopper/blob/0.8/docs/index.md), [web service zip](https://graphhopper.com/public/releases/graphhopper-web-0.8.2-bin.zip), [Android APK](https://graphhopper.com/public/releases/graphhopper-android-0.8.2.apk), [announcement](https://www.graphhopper.com/blog/2016/10/18/graphhopper-routing-engine-0-8-released/)
 * 0.7.0: [documentation](https://github.com/graphhopper/graphhopper/blob/0.7/docs/index.md), [web service zip](https://graphhopper.com/public/releases/graphhopper-web-0.7.0-bin.zip), [Android APK](https://graphhopper.com/public/releases/graphhopper-android-0.7.0.apk), [announcement](https://www.graphhopper.com/blog/2016/06/15/graphhopper-routing-engine-0-7-released/)
 
### Java API Changes

See our [changelog file](./core/files/changelog.txt).

### Docker

Run a dockerized GraphHopper from sources:

```bash
docker build -t graphhopper:master .
docker run -d --name graphhopper -v <path_data_directory>/data:/data -p 8989:8989 graphhopper:master
```

See also the builds at [Docker Hub](https://hub.docker.com/r/graphhopper/graphhopper)

### DEB or RPM package

[There is an experimental step by step guide](https://github.com/graphhopper/graphhopper/issues/561#issuecomment-473567727) that creates a debian or rpm package from the cloned and compiled repository. Before we can recommend this we need some more testers and feedback :)

## Questions

All questions can go to our [forum](https://discuss.graphhopper.com/) where we also have subsections specially for developers, mobile usage (iOS&Android), and [our map matching component](https://github.com/graphhopper/map-matching). Another place to ask questions would be on [Stackoverflow](http://stackoverflow.com/questions/tagged/graphhopper), but please do **not** use our issue section. Create new issues only if you are sure that this is a bug. Also, see how to contribute in the next section.

## Contribute

Read through [how to contribute](CONTRIBUTING.md) for information on topics
like finding and fixing bugs and improving our documentation or translations!

## For the Web

See GraphHopper in action on [GraphHopper Maps](https://graphhopper.com/maps) and [install it](./docs/index.md) on your own machine:

[![GraphHopper Maps](https://karussell.files.wordpress.com/2014/12/graphhopper-maps-0-4-preview.png)](https://graphhopper.com/maps)

GraphHopper Maps uses the [Directions API for Business](https://graphhopper.com/#directions-api) under the hood, which provides 
a Routing API via GraphHopper, a Route Optimization API via [jsprit](http://jsprit.github.io/), a fast Matrix API
and an address search via [Photon](https://github.com/komoot/photon). Additionally, map tiles from various providers are used 
where the default is [Omniscale](http://omniscale.com/). All of these are available for free, via encrypted connections and from German servers for a nice and private route planning experience!

## For Public Transit

[Get started](./reader-gtfs/README.md#quick-start)

[![Realtime Demo](https://www.graphhopper.com/wp-content/uploads/2018/05/Screen-Shot-2018-05-16-at-21.23.25-600x538.png)](./reader-gtfs/README.md#quick-start)

## For Mobile Apps

### Offline

There are subprojects to make GraphHopper working offline on [Android](./android/README.md) and [iOS](http://github.com/graphhopper/graphhopper-ios)

[![simple routing](https://www.graphhopper.com/wp-content/uploads/2016/10/android-demo-screenshot-2.png)](./android/README.md)

### Online

There is an extension that creates a [navigation endpoint](https://github.com/graphhopper/graphhopper-navigation) to feed a 
[lightweight navigation Android client](https://github.com/graphhopper/graphhopper-navigation-example).

[![android navigation demo app](https://raw.githubusercontent.com/graphhopper/graphhopper-navigation-example/master/files/graphhopper-navigation-example.png)](https://github.com/graphhopper/graphhopper-navigation-example)

## For Analysis

There is the isochrone subproject to calculate and visualize the reachable area for a certain travel mode

**[Isochrone Web API](./docs/web/api-doc.md#isochrone)**

[![Isochrone API image](./docs/isochrone/images/isochrone.png)](./docs/web/api-doc.md#isochrone)

**[Shortest Path Tree API](//www.graphhopper.com/blog/2018/07/04/high-precision-reachability/)**

[![high precision reachability image](https://www.graphhopper.com/wp-content/uploads/2018/06/berlin-reachability-768x401.png)](https://www.graphhopper.com/blog/2018/07/04/high-precision-reachability/)

To support these high precision reachability approaches there is a special /spt
endpoint (shortest path tree). [See #1577](https://github.com/graphhopper/graphhopper/pull/1577)

# Technical Overview

GraphHopper supports several routing algorithms, such as 
<a href="https://en.wikipedia.org/wiki/Dijkstra%27s_algorithm">Dijkstra</a> and 
<a href="https://en.wikipedia.org/wiki/A*_search_algorithm">A</a>`*` and its bidirectional variants. 
Furthermore, it allows you to use 
<a href="https://en.wikipedia.org/wiki/Contraction_hierarchies">Contraction Hierarchies</a> (CH) 
very easily. We call this **speed mode**; without this CH preparation, we call it **flexible mode**.

The speed mode comes with very fast and lightweight (less RAM) responses, although it does not use heuristics 
in its default settings. The downsides are that the speed mode allows only pre-defined vehicle profiles (multiple possible in GraphHopper) 
and requires a time consuming and resource-intensive preparation. Finally, implementing certain features is not possible 
or very complex compared to the flexible mode. 

The **hybrid mode** also requires preparation time and memory,
but it is much more flexible regarding changing properties per request or e.g. integrating traffic data and more. 
Furthermore, this hybrid mode is slower than the speed mode, but it is an 
order of magnitude faster than the flexible mode and uses also less RAM for one request.

You can switch between all modes at request time.

## License

We chose the Apache License to make it easy for you to embed GraphHopper in your products, even closed source.
We suggest that you contribute back your changes, as GraphHopper evolves fast,
but of course this is not necessary.

## OpenStreetMap Support

OpenStreetMap is directly supported from GraphHopper. Without the amazing data from
OpenStreetMap, GraphHopper wouldn't be possible at all.
Other map data will need a custom import procedure, see e.g. <a href="https://github.com/graphhopper/graphhopper/issues/277">Ordnance Survey</a>,
<a href="https://github.com/graphhopper/graphhopper-reader-shp">Shapefile like ESRI</a> or <a href="https://github.com/knowname/morituri">Navteq</a>.

## Written in Java

GraphHopper is written in Java and runs on Linux, Mac OS X,
Windows, BSD, Solaris, Raspberry Pi, Android, Blackberry, and even iOS.

### Maven

Embed GraphHopper with OpenStreetMap support into your Java application via the following snippet:

```xml
<dependency>
    <groupId>com.graphhopper</groupId>
    <artifactId>graphhopper-reader-osm</artifactId>
    <version>[LATEST-VERSION]</version>
</dependency>
```

If you want to write your own import procedure or you don't need OSM import like
on [Android](./docs/android/index.md), then use:

```xml
<dependency>
    <groupId>com.graphhopper</groupId>
    <artifactId>graphhopper-core</artifactId>
    <version>[LATEST-VERSION]</version>
</dependency>
```

## Customizable

We've built the GraphHopper class which makes simple things easy and complex things like multi-modal routing possible. 
Still, you can use the low level API of GraphHopper and you'll see that
it was created to allow fast and memory efficient use of the underlying data structures and algorithms.

### Android / Blackberry

On Android and Blackberry (since 10.2.1), we provide an integration with Mapsforge which makes offline navigation one step closer.
Due to the usage of memory mapped files and Contraction Hierarchies,
we avoid allocating too much memory. This makes it possible to run Germany-wide queries with only 
32MB in a few seconds. We provide an Android studio project as well as the Maven-Android integration to be 
used in other IDEs.

### Web UI and API

With the web module, we provide code to query GraphHopper over HTTP and decrease bandwidth usage as much as possible.
For that we use a polyline encoding from Google, the Ramer–Douglas–Peucker algorithm, and a simple 
GZIP servlet filter.                 
On the client side, we provide Java and JavaScript code (via Leaflet) to consume that service and 
visualize the routes.

### Desktop

GraphHopper also runs on the Desktop in a Java application without internet access.
For debugging purposes GraphHopper can produce vector tiles, i.e. a visualization of the road network in the browser (see #1572). Also a more low level Swing-based UI is provided via MiniGraphUI in the tools module, see some
visualizations done with it [here](https://graphhopper.com/blog/2016/01/19/alternative-roads-to-rome/).
A fast and production ready map visualization for the Desktop can be implemented via [mapsforge](https://github.com/mapsforge/mapsforge) or [mapsforge vtm](https://github.com/mapsforge/vtm).

# Features

Here is a list of the more detailed features including a link to the documentation:

 * [Quick installation and start for users](./docs/web/quickstart.md) - just Java necessary! [Simple start for developers](./docs/core/quickstart-from-source.md) due to Maven.
 * Works out of the box with OpenStreetMap (osm/xml and pbf) and can be adapted to custom data
 * OpenStreetMap integration: stores and considers road type, speed limit, the surface, barriers, access restrictions, ferries, [conditional access restrictions](https://github.com/graphhopper/graphhopper/pull/621), ...
 * GraphHopper is fast. And with the so called "Contraction Hierarchies" it can be even faster (enabled by default).
 * Memory efficient data structures, algorithms and [the low and high level API](./docs/core/low-level-api.md) is tuned towards ease of use and efficiency
 * Provides a simple [web API](./docs/web/api-doc.md) including JavaScript and Java clients
 * Multiple weightings (fastest/shortest/...) and pre-built routing profiles: car, bike, racingbike, mountain bike, foot, motorcycle, ...
 * Supports public transit routing and [GTFS](./reader-gtfs/README.md).
 * Offers turn instructions in more than 42 languages, contribute or improve [here](./docs/core/translations.md)
 * Displays and takes into account [elevation data](./docs/core/elevation.md) (per default disabled)
 * Can apply [real time changes to edge weights](https://graphhopper.com/blog/2015/04/08/visualize-and-handle-traffic-information-with-graphhopper-in-real-time-for-cologne-germany-koln/) (flexible and hybrid mode only)
 * Customization of vehicle profiles per request are possible (flexible and hybrid mode only)
 * Possibility to specify a [heading parameter](./docs/core/routing.md) of the vehicle for start, end and via points for navigation applications via `pass_through` or `heading` parameters (flexible and hybrid mode only)
 * [Alternative routes](https://discuss.graphhopper.com/t/alternative-routes/424) (flexible and hybrid mode only)
 * [Turn costs and restrictions](./docs/core/turn-restrictions.md)
 * Country specific routing via SpatialRules
 * The core uses only a few dependencies (hppc, jts and slf4j)
 * Scales from small indoor-sized to world-wide-sized graphs
 * Finds nearest point on street e.g. to get elevation or 'snap to road' or being used as spatial index (see [#1485](https://github.com/graphhopper/graphhopper/pull/1485))
 * Does [map matching](https://github.com/graphhopper/map-matching)
 * Calculates [isochrones](./docs/web/api-doc.md#isochrone) and [shortest path trees](https://github.com/graphhopper/graphhopper/pull/1577)
 * Shows the whole road network in the browser for debugging purposes ("vector tile support") [#1572](https://github.com/graphhopper/graphhopper/pull/1572)
 * Shows details along a route like road_class or max_speed ("path details") [#1142](https://github.com/graphhopper/graphhopper/pull/1142) or for the whole road network via the "vector tile support"
