# GraphHopper Documentation

## Getting Started

Try out our live demo called [GraphHopper Maps](https://graphhopper.com/maps)

 [![GraphHopper Maps](https://karussell.files.wordpress.com/2014/12/graphhopper-maps-0-4-preview.png)](https://graphhopper.com/maps)

[The Readme](../README.md#features) lists all features or [this list of slides](https://graphhopper.com/public/slides/).

See [more users of GraphHopper](https://www.graphhopper.com/showcases/) or [Android apps](./android/index.md#apps) and let us know your application!

Try the web demo 'GraphHopper Maps' with [this user guide](./web/quickstart.md) on your machine
or the [Android demo](https://graphhopper.com/public/releases/graphhopper-android-0.8.2.apk).

Other links:

 * [Add GraphHopper Maps to your Browser](./web/open-search.md): Instructions how to setup GraphHopper as the standard search enginge in your browser.
 * [Embed GraphHopper on your website](https://github.com/karussell/graphhopper-embed-form): A small code snippet on how to integrate GraphHopper Maps in your web site like a contact form

## Contact

For all questions regarding the GraphHopper routing engine please use [our forum](https://discuss.graphhopper.com). 

For bugs see our contribute section below.

## Web

The web module provides a web API for other programming languages as well as starts a simple user interface called GraphHopper Maps

 * [Routing API](./web/api-doc.md): Documentation of the Web API to communicate with any GraphHopper server via http.
 * [Deployment Guide](./core/deploy.md): Details about deploying GraphHopper
 * [Postgis query script](../core/files/postgis)
 * There are official API clients in [Java](https://github.com/graphhopper/directions-api-java-client/) and [JavaScript](https://github.com/graphhopper/directions-api-js-client).

### Configuration

You can configure several aspects either programmatically or just via the [config.properties](../config-example.properties).

The elevation configuration is separately documented [here](./core/elevation.md).

## Developer

[The quickstart](./core/quickstart-from-source.md) is an introduction for developers. Explains git checkout, IDE setup and commands for setting up a GraphHopper server.

Find all changes in previous and current versions in the [changelogs](../core/files/changelogs.txt).

### Contribute

Read [here](../.github/CONTRIBUTING.md) on how to contribute as a developer and translator.

### Technical

Various topics are explained in more detail separately:

 * [Technical overview](./core/technical.md): Technical details about how GraphHopper its calculations are working.
 * [Simple routing](./core/routing.md): Tutorial how to integrate GraphHopper in your Java application (or pick any JVM language)
 * [Create custom weighting](./core/weighting.md): Documentation about how to create a custom weighting class to influence the track calculation.
 * [LocationIndex](./core/location-index.md): Documentation about how to get the location index for getting i.e. the nearest edge. 
 * [Hybrid Mode](./core/landmarks.md): Details about speeding up the route calculation via A* and landmarks.
 * [Speed Mode](./core/ch.md): Details about speeding up the route calculations via [Contraction Hierarchies](http://en.wikipedia.org/wiki/Contraction_hierarchies).
 * [Low level API](./core/low-level-api.md): Instructions how to use GraphHopper as a Java library.
 * [Create new FlagEncoder](./core/create-new-flagencoder.md): Documentation to create new routing profiles to influence which ways to favor and how the track-time is calculated.

### Android

 * [Android](./android/index.md): Instructions how to setup the demo project for GraphHopper on Android
 * [Android Studio Setup](./android/android-studio-setup.md)

### iOS

Instructions on how to setup the GraphHopper-iOS clone for iOS development are [here](https://github.com/graphhopper/graphhopper-ios/)
including a sample application.

### Windows

Documentation about how to get an GraphHopper instance running on windows via cygwin is available [here](./core/windows-setup.md).

### Eclipse

[Set up GraphHopper in Eclipse](./core/eclipse-setup.md) with maven and [tomcat](./core/eclipse-tomcat-setup.md).
