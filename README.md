# GraphHopper Navigation Web Service

[![Build Status](https://travis-ci.org/graphhopper/graphhopper-navigation.svg?branch=master)](https://travis-ci.org/graphhopper/graphhopper-navigation)

This web service returns JSON from the [GraphHopper routing engine](https://github.com/graphhopper/graphhopper) that is consumable with the [Android Navigation SDK](https://github.com/graphhopper/graphhopper-navigation-android). I.e. it provides the server side part of a navigation application.

An example for an Android app that uses the Navigation SDK is provided in [this repository](https://github.com/graphhopper/graphhopper-navigation-example).

# Integration with your Dropwizard application

 1. Create your dropwizard application
 2. Add this dependency to your project via e.g.:
 ```xml
 <dependency>
   <groupId>com.graphhopper</groupId>
   <artifactId>graphhopper-navigation</artifactId>
   <version>SOME_VERSION</version>
 </dependency>
 ```
 3. Create your Application class that adds the [GraphHopperBundle](https://github.com/graphhopper/graphhopper/blob/1.0-pre38/web-bundle/src/main/java/com/graphhopper/http/GraphHopperBundle.java). See this [MapMatchingApplication](https://github.com/graphhopper/map-matching/blob/1.0-pre38/matching-web/src/main/java/com/graphhopper/matching/http/MapMatchingApplication.java) class as an example.
 4. In the run method of this class call `environment.jersey().register(NavigateResource.class);` 

Or create your own bundle similar to the mentioned GraphHopperBundle and add the NavigateResource there.

# Community-Driven Alternatives

maphopper: https://github.com/Gadda27/maphopper
