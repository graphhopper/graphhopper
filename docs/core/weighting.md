## Weighting

In order to create a custom Weighting you need to do the following:

 1. implement the Weighting class
 2. create a subclass of GraphHopper and overwrite createWeighting where you return a new instance of your custom weighting if e.g. the string 'customweighting' is specified. Otherwise let the super class handle it.

See AvoidEdgesWeighting for an example of a weighting which avoids certain edges (i.e. returns infinity weight). 
If your blocking edges change per-request you need to disable the speed mode e.g. via `ch.disable=true` as URL or Java hints parameter.

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
        {
            AvoidEdgesWeighting w = new AvoidEdgesWeighting(encoder);
            w.addEdges(forbiddenEdges);
            return w;
        } else 
        {
            return super.createWeighting(weighting, encoder);
        }
    }
}
```

For `forbiddenEdges` you need to determine the edges from some GPS coordinates. 
Have a look into the [location index docs](./location-index.md). 
