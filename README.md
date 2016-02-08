# GraphHopper Route Planner

[![Build Status](https://secure.travis-ci.org/graphhopper/graphhopper.png?branch=master)](http://travis-ci.org/graphhopper/graphhopper)

GraphHopper is a fast and memory efficient Java road routing engine released under Apache License 2.0.
Per default it uses OpenStreetMap data but can import other data sources.

GraphHopper for the Web
--------------

See GraphHopper in action on [GraphHopper Maps](https://graphhopper.com/maps)

[![GraphHopper Maps](https://karussell.files.wordpress.com/2014/12/graphhopper-maps-0-4-preview.png)](https://graphhopper.com/maps)

GraphHopper Maps uses the [Directions API for Business](https://graphhopper.com/#directions-api) under the hood, which provides 
a Routing API via GraphHopper, a Route Optimization API via [jsprit](http://jsprit.github.io/), a fast Matrix API
and an address search via [Photon](https://github.com/komoot/photon). Additionally the map tiles from various providers are used 
where the default is [Omniscale](http://omniscale.com/), and all is available for free, via encrypted connections and from German servers
for a nice and private route planning experience!


GraphHopper for Mobile
---------------

There are subprojects to make GraphHopper working offline
on [Android](https://github.com/graphhopper/graphhopper/tree/master/android)
and [iOS](http://github.com/graphhopper/graphhopper-ios)


Get Started
---------------

Read through our Documentation ([0.6](https://github.com/graphhopper/graphhopper/blob/0.6/docs/index.md), [unstable](https://github.com/graphhopper/graphhopper/blob/master/docs/index.md)), 
ask questions on [Stackoverflow](http://stackoverflow.com/questions/tagged/graphhopper)
and sign up to the [discussion](https://discuss.graphhopper.com/).


Contribute
---------------

Read through [how to contribute](https://github.com/graphhopper/graphhopper/blob/master/CONTRIBUTING.md)
like finding and fixing bugs and improving our documentation or translations!


Features
---------------

 * Written in Java
 * Open Source
 * Memory efficient and fast
 * Highly customizable
 * Works on the desktop, as a web service and offline on Android or iOS
 * Large test suite

## Overview

GraphHopper supports several algorithms like Dijkstra and A* and its bidirectional variants. 
Furthermore it allows you to use Contraction Hierarchies (CH) very easily, we call this 
**speed mode** and in contrast to the speed mode we call everything without CH the
**flexibility mode**. BTW: This does not mean that the flexibility mode is *slow*.

The speed mode comes with much faster and lightweight (less RAM) responses and it does not use heuristics.
The downsides are that the speed mode allows only a pre-defined vehicle profile and requires a time 
consuming and resource intense preparation. And implementing certain features are not possible or 
very complex compared to the flexibility mode. But since 0.4 you can use both modes at the same time since. 
See [here](https://github.com/graphhopper/graphhopper/pull/631) for more details.

Here is a list of the more detailed features including a link to the documentation:

 * [a web API](./docs/web/api-doc.md) including JavaScript and Java clients
 * turn instructions in more than 30 languages, contribute or improve [here](./docs/core/translations.md)
 * [including elevation](./docs/core/elevation.md) (per default disabled)
 * [real time changes to edge weights](https://graphhopper.com/blog/2015/04/08/visualize-and-handle-traffic-information-with-graphhopper-in-real-time-for-cologne-germany-koln/) (flexibility only)
 * Customized routing profiles per request (flexibility only)
 * A '[heading](./docs/core/routing.md)' for start, end and via points for navigation applications via pass_through or favoredHeading parameters (flexibility only)
 * [alternative routes](https://discuss.graphhopper.com/t/alternative-routes/424) (flexibility only)
 * [conditional access restrictions](https://github.com/graphhopper/graphhopper/pull/621)
 * [turn costs and restrictions](https://github.com/graphhopper/graphhopper/pull/55#issuecomment-31089096) (flexibility only)
 * multiple profiles and weightings (flexibility and speed mode since 0.5)
 * several pre-built routing profiles: car, bike, racingbike, mountain bike, foot, motorcycle
 * [map matching](https://github.com/graphhopper/map-matching) (flexibility only)
