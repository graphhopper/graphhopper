## Weighting

A weighting allows to create a "weight" for an edge. The weight of an edge reflects the cost of travelling along this edge. 
All implementations of [RoutingAlgorithm](https://github.com/graphhopper/graphhopper/blob/master/core/src/main/java/com/graphhopper/routing/RoutingAlgorithm.java)
in GraphHopper calculate the shortest path, which means the path with the [lowest overall cost from A to B](https://en.wikipedia.org/wiki/Shortest_path_problem). 
The definition of the cost function is up to you, so you can create a cost function (we call this weighting) for the fastest path. 
GraphHopper will still calculate the path with the lowest cost, but you can define the cost as a mix of distance speed, as shown in the [FastestWeighting](https://github.com/graphhopper/graphhopper/blob/master/core/src/main/java/com/graphhopper/routing/weighting/FastestWeighting.java).

In order to create a custom weighting you need to do the following:

 1. Implement the Weighting class
 2. Create a subclass of GraphHopper and overwrite `createWeighting`

### Implement your own weighting

A simple weighting is the [ShortestWeighting](https://github.com/graphhopper/graphhopper/blob/master/core/src/main/java/com/graphhopper/routing/weighting/ShortestWeighting.java),
it calculates the shortest path. You could go from there and create your own weighting.

If you only want to change small parts of an existing weighting, it might be a good idea to extend the [AbstractAdjustedWeighting](https://github.com/graphhopper/graphhopper/blob/master/core/src/main/java/com/graphhopper/routing/weighting/AbstractAdjustedWeighting.java),
a sample can be found in the [AvoidEdgesWeighting](https://github.com/graphhopper/graphhopper/blob/master/core/src/main/java/com/graphhopper/routing/weighting/AvoidEdgesWeighting.java).
If your weights change on a per-request base, like the [BlockAreaWeighting](https://github.com/graphhopper/graphhopper/blob/bbd62fded97be060fc09177f9fae794cea284554/core/src/main/java/com/graphhopper/routing/weighting/BlockAreaWeighting.java),
you cannot use the 'speed mode', but have to use the 'hybrid mode' or 'flexible mode' (more details [here](https://github.com/graphhopper/graphhopper#technical-overview)).
If you haven't disabled the 'speed mode' in your config, you have to disable it for the requests by appending `ch.disable=true`
in the request url.

### Extend GraphHopper

For general information on how to extend GraphHopper have a look [here](low-level-api.md).
 
Extending GraphHopper is easy, just need to override the `createWeighting` method of the GraphHopper class. 
We return a new instance of our custom weighting if the string `my_custom_weighting` is given. Otherwise let the super class handle it:

```java
class MyGraphHopper extends GraphHopper {

    @Override
    public Weighting createWeighting(HintsMap hintsMap, FlagEncoder encoder, Graph graph) {
        String weightingStr = hintsMap.getWeighting().toLowerCase();
        if ("my_custom_weighting".equals(weighting)) {
            return new MyCustomWeighting(encoder);
        } else {
            return super.createWeighting(hintsMap, encoder, graph);
        }
    }
}
```