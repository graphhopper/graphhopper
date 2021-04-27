# Routing via Java API

To use the following examples you need to specify the dependency in
your [Maven config](/README.md#maven) correctly.

To do routing in your Java code you'll need just a few lines of code.

See [this code example](../../example/src/main/java/com/graphhopper/example/RoutingExample.java)
on how to do that where also code examples for the next sections are
included.

## Speed mode vs. Hybrid mode vs. Flexible mode

GraphHopper offers three different choices of algorithms for routing: The speed mode (which implements Contraction
Hierarchies (CH) and is by far the fastest), the hybrid mode (which uses Landmarks (LM) and is still fast, but also supports
some features CH does not support) and the flexible mode (Dijkstra or A*) which does not require calculating index data
and offers full flexibility but is a lot slower.

See the [profiles](./profiles.md) for an explanation how to configure the different routing modes. At query time you
can disable speed mode using `ch.disable=true`. In this case either hybrid mode (if there is an LM preparation for the
chosen profile) or flexible mode will be used. To use flexible mode in the presence of an LM preparation you need to 
also set `lm.disable=true`.

To calculate a route you have to pick one vehicle and optionally an algorithm like
`bidirectional_astar`, see the test speedModeVersusHybridMode.

## Heading

The flexible and hybrid modes allow adding a desired heading (north based azimuth between 0 and 360 degree)
to any point. Adding a heading makes it more likely that a route starts towards the provided direction, because
roads going into other directions are penalized (see the Routing.HEADING_PENALTY
parameter and the test headingAndAlternativeRoute).

A heading with the value 'NaN' won't be enforced and a heading not within [0, 360] will trigger an IllegalStateException.
It is important to note that if you force the heading at via or end points the outgoing heading needs to be specified.
I.e. if you want to force "coming from south" to a destination you need to specify the resulting "heading towards north" instead, which is 0.

## Alternative Routes

The flexible and hybrid mode allows you to calculate alternative routes.
Note that this setting can affect speed of your routing requests. See
the test headingAndAlternativeRoute and the Parameters class for further hints.

## Java client (client-hc)
 
If you want to calculate routes using the [GraphHopper Directions API](https://www.graphhopper.com/products/) or a self hosted instance of GraphHopper, you can use the [Java and Android client-hc](https://github.com/graphhopper/graphhopper/tree/master/client-hc) (there are also clients for [Java Script](https://github.com/graphhopper/directions-api-js-client) and [many other languages](https://github.com/graphhopper/directions-api-clients)). 

```java
GraphHopperAPI gh = new GraphHopperWeb();
gh.load("http://your-graphhopper-service.com");

// or for the GraphHopper Directions API https://graphhopper.com/#directions-api
// gh.load("https://graphhopper.com/api/1/route");

GHResponse rsp = gh.route(new GHRequest(...));
```
