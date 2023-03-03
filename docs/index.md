# GraphHopper Documentation

## First Steps

Try out our live demo called [GraphHopper Maps](https://graphhopper.com/maps)

 [![GraphHopper Maps](https://karussell.files.wordpress.com/2014/12/graphhopper-maps-0-4-preview.png)](https://graphhopper.com/maps)

[The Readme](../README.md#features) lists all features or [this list of slides](https://graphhopper.com/public/slides/).
See [users of GraphHopper](https://www.graphhopper.com/showcases/) and let us know your application!

## Community

For all questions regarding the GraphHopper routing engine please use [our forum](https://discuss.graphhopper.com). 
For bugs see our contribute section below.

---

## Installation

Install the GraphHopper routing engine with the GraphHopper Maps UI with [this installation guide](../README.md#installation) on your
machine. This will also install a web API that can be used in many programming languages.

 * [Routing API](./web/api-doc.md): Documentation of the Web API to communicate with any GraphHopper server via http.
 * [Deployment Guide](./core/deploy.md): Details about deploying GraphHopper 
 * There are official API clients in [Java](https://github.com/graphhopper/graphhopper/tree/master/client-hc) and [JavaScript](https://github.com/graphhopper/directions-api-js-client).

#### Configuration

You can configure several aspects either programmatically or just via a [configuration](../config-example.yml).
The configuration of routing profiles is documented [here](./core/profiles.md).
The elevation configuration is separately documented [here](./core/elevation.md).

---

## Installation For Developers

[The quickstart](./core/quickstart-from-source.md) is an introduction for developers. Explains git checkout, IDE setup and commands for setting up a GraphHopper server.

Find all changes in previous and current versions in the [changelogs](../CHANGELOG.md).

#### Contribute

Read [here](../CONTRIBUTING.md) on how to contribute as a developer and translator.

#### Technical

Various topics are explained in more detail separately:

 * [Technical overview](./core/technical.md): Technical details about how GraphHopper its calculations are working.
 * [Custom models](./core/custom-models.md): This tutorial explains how to customize an existing vehicle profile to your needs without to know Java.
 * [Simple routing](./core/routing.md): Tutorial how to integrate GraphHopper in your Java application (or pick any JVM language)
 * [Create custom weighting](./core/weighting.md): Documentation about how to create a custom weighting class to influence the track calculation.
 * [Import GTFS](../reader-gtfs): Simple steps to get GTFS import and routing done.
 * [LocationIndex](../example/src/main/java/com/graphhopper/example/LocationIndexExample.java): Code about how to get the location index for getting i.e. the nearest edge. 
 * [Hybrid Mode](./core/landmarks.md): Details about speeding up the route calculation via A* and landmarks.
 * [Speed Mode](./core/ch.md): Details about speeding up the route calculations via [Contraction Hierarchies](http://en.wikipedia.org/wiki/Contraction_hierarchies).
 * [Low level API](./core/low-level-api.md): Instructions how to use GraphHopper as a Java library.
 * [Create new FlagEncoder](./core/create-new-flagencoder.md): Documentation to create new routing profiles to influence which ways to favor and how the track-time is calculated.
 * [Custom Areas and Country Rules](./core/custom-areas-and-country-rules.md): Instructions on how to on how to use and create new SpatialRules. SpatialRules are used to enforce country-specific routing rules.
 * [Turn Restrictions](./core/turn-restrictions.md): Details on how to enable and use turn restrictions.
 * [Isochrone generation in Java](./isochrone/java.md): Instruction on how to create isochrones using the low-level Java API.
 * [Postgis query script](../core/files/postgis)


#### Other links

 * [Add GraphHopper Maps to your Browser](./web/open-search.md): Instructions how to setup GraphHopper as the standard search engine in your browser.
 * [Embed GraphHopper on your website](https://github.com/karussell/graphhopper-embed-form): A small code snippet on how to integrate GraphHopper Maps in your web site like a contact form

#### Android & iOS

The Android demo, that shows how to use GraphHopper for offline routing on
Android, was available until [GraphHopper 1.0](https://github.com/graphhopper/graphhopper/tree/1.0/android).

There is a GraphHopper fork for iOS that allows to do offline routing on
iOS. See the Instructions on how to setup this [here](https://github.com/graphhopper/graphhopper-ios/) including a sample application.
See the necessary changes for modern iOS and GraphHopper 1.0 in [this pull request](https://github.com/graphhopper/graphhopper-ios/pull/47).

#### Windows

Install the Windows Subsystem for Linux (WSL) or cygwin and follow the [normal installation steps](../README.md#installation).

When using Powershell or `cmd` the easiest way is to download all files manually (see the `wget` parameters) and then run the `java`
command but replace the wildcard (`-jar graphhopper*.jar`) with the exact jar file.

#### Eclipse

Setup in IntelliJ and NetBeans is just via open project. See [this document](./core/eclipse-setup.md) 
to set up GraphHopper in Eclipse with maven.
