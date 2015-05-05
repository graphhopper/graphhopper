## Weighting

In order to create a custom Weighting you need to do the following:

 1. implement the Weighting class
 2. create a subclass of GraphHopper and overwrite createWeighting where you return a new instance of your custom weighting if e.g. the string 'customweighting' is specified. Otherwise let the super class handle it.

Here is an example of a custom weighting which avoids certain edges (i.e. returns infinity weight):

```java

class BlockingWeighting implements Weighting 
{
    private final FlagEncoder encoder;
    private final double maxSpeed;
    private Set<Integer> forbiddenEdges;

    public BlockingWeighting( FlagEncoder encoder, Set<Integer> forbiddenEdges)
    {
        this.encoder = encoder;
        this.maxSpeed = encoder.getMaxSpeed();
        this.forbiddenEdges = forbiddenEdges;
    }

    @Override
    public double getMinWeight( double distance )
    {
        return distance / maxSpeed;
    }

    @Override
    public double calcWeight( EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId )
    {
        if(forbiddenEdges.contains(edge.getEdge()))
            return Double.POSITIVE_INFINITY;

        double speed = reverse ? encoder.getReverseSpeed(edge.getFlags()) : encoder.getSpeed(edge.getFlags());
        if (speed == 0)
            return Double.POSITIVE_INFINITY;
        return edge.getDistance() / speed;
    }

    @Override
    public String toString()
    {
        return "BLOCKING";
    }
}
```

Now you need to create your custom GraphHopper:

```java
class MyGraphHopper extends GraphHopper {

    Set<Integer> forbiddenEdges;
    public void determineForbiddenEdges() {
       forbiddenEdges = ...
    }

    @Override
    public Weighting createWeighting( WeightingMap wMap, FlagEncoder encoder )
    {
        String weighting = wMap.getWeighting();
        if ("BLOCKING".equalsIgnoreCase(weighting))
            return new BlockingWeighting(encoder, forbiddenEdges);
        else
            return super.createWeighting(weighting, encoder);
    }
}
```

For forbiddenEdges you need to determine the edges from some GPS coordinates. 
Have a look into the [location index docs](./location-index.md). 

If your blocking edges change per-request you need to disable the speed mode e.g. via `prepare.chWeighting=no`
