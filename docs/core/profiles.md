# Profiles

## Standard Profiles

When calculating routes with GraphHopper you can customize how different kinds of roads shall be prioritized. For example
when travelling long distances with a car you typically want to use the highway to minimize your travelling time.
However, if you are going by bike you certainly do not want to use the highway and rather take some shorter route,
use designated bike lanes and so on.
 
GraphHopper includes the following pre-configured 'vehicles', that you can choose from depending on the type of vehicle
you want to calculate routes for: 

- foot
- hike
- wheelchair
- bike
- racingbike
- bike2
- mtb
- car
- car4wd
- motorcycle

By choosing a vehicle GraphHopper determines the accessibility and an average travel speed for the different road types.
If you are interested in the low-level Java API note that these pre-configured vehicles correspond to implementations of
the `FlagEncoder` interface. 
 
Besides the vehicle it is also possible to use different weightings (=cost functions) for the route calculation. 
GraphHopper includes the following weightings:

- fastest
- shortest
- short_fastest (yields a compromise between short and fast routes)
- curvature (prefers routes with lots of curves for enjoyable motorcycle rides)
- custom (see the next section)

The different travelling modes are called 'profiles'. When starting the GraphHopper server at least one such profile
needs to be configured upfront, which is done in the 'profiles' section of `config.yml`. Here is an example:

```yaml
profiles:
  - name: car
    vehicle: car
    weighting: fastest
  - name: some_other_profile
    vehicle: bike
    weighting: shortest
```

Every profile has to include a unique name that is used to select the profile when executing routing queries. The vehicle
and weighting fields are also required and need to match one of the values listed above. To choose one of the profiles you
need to use the `profile` parameter for your request, like `/route?point=49.5,11.1&profile=car` or 
`/route?point=49.5,11.1&profile=some_other_profile` in this example.

## Speed and Hybrid Mode

GraphHopper can drastically improve the execution time of the route calculations by preprocessing the routing profiles
during import. To enable this you need to list the profiles you want GraphHopper to do the preprocessing for like this:

```yaml
# profiles you want to use with speed mode need to go here
profiles_ch:
  - profile: car
  - profile: some_other_profile

# profiles you want to use with hybrid mode need to go here
profiles_lm:
  - profile: car
  - profile: some_other_profile 
```

Note that 'CH' is short for 'Contraction Hierarchies', the underlying technique used to realize speed mode and
'LM' is short for 'Landmarks', which is the algorithm used for the hybrid mode.

For hybrid mode there is a special feature that allows 're-using' the prepared data for different profiles. You can
do this like this:
```yaml
profiles_lm:
  - profile: car
  - profile: some_other_profile
    preparation_profile: car
```

which means that for `some_other_profile` the preparation of the `car` profile will be used. However, this will only
give correct routing results if `some_other_profile` yields larger or equal weights for all edges than the `car` profile.
Better do not use this feature unless you know what you are doing.  

## Custom Profiles

*Disclaimer*: Custom profiles should still be considered a beta feature. Using them should be working, but details about
the weight formula and the meaning of the different parameters is still subject to change. Also this feature will strongly
benefit from community feedback, so do not hesitate with sharing your experience, custom models or problems you are 
running into!
 
You can take the customization of the routing profiles much further than just selecting one of the default vehicles and
weightings, by using 'custom' profiles that let you adjust the cost function on a much more fine-grained level.
Using a custom profile you can make adjustments to a 'base' vehicle. By choosing the base vehicle you inherit the road
accessibilty rules and default speeds of this vehicle, but the custom weighting gives you the freedom to overwrite 
certain parts of the cost function depending on the different road properties. 

### Custom Weighting
The weight or 'cost' of travelling along an 'edge' (a road segment of the routing network) depends on the length
of the road segment (the distance), the travelling speed, the 'priority' and the 'distance_influence' factor (see below).
To be more precise, the cost function has the following form:

```
edge_weight = edge_distance / (speed * priority) + edge_distance * distance_influence
```

The `edge_distance` is calculated during the initial import of the road network and you cannot change it here.
Note that the edge weights are proportional to this distance. What can be customized is the `speed`, the `priority` and
the `distance_influence`, which we will discuss in a moment. First we need to have a look at the 'properties' of an edge:

### Edge properties: Encoded Values

GraphHopper assigns values of different categories ('encoded values') to each road segment. For example for OSM data 
they are derived from the OSM way tags. The available categories are specified by using the `graph.encoded_values` field
in `config.yml` and (unless you do further customization of GraphHopper) the possible categories are defined in [`DefaultEncodedValueFactory.java`](../../core/src/main/java/com/graphhopper/routing/profiles/DefaultEncodedValueFactory.java).
For example there are the following categories (some of their possible values are given in brackets).

- road_class: (other,motorway,trunk,primary,secondary,track,steps,cycleway,footway,...)
- road_environment: (road,ferry,bridge,tunnel,...)
- road_access: (destination,delivery,private,no,...)
- surface: (paved,dirt,sand,gravel,...)

To find out about all the possible values of a category you can take a look at the corresponding Java files like 
[`RoadClass.java`](../../core/src/main/java/com/graphhopper/routing/profiles/RoadClass.java), query the
`/info` endpoint of the server or use the auto-complete feature of the text box that opens when clicking the 'flex' icon
in the web UI.

Besides these kind of categories, which can take multiple different string values, there are also some that represent a
boolean value (they are either true or false for a given edge), like:

- get_off_bike
- road_class_link

And there are others that take on a numeric value, like: 

- max_weight
- max_width

*Important note: Whenever you want to use any of these categories for a custom profile you need to add them to 
`graph.encoded_values` in `config.yml`.*

### Setting up a Custom Model

As mentioned above, the custom weighting function has three parameters that you can adjust: speed, priority and
distance_influence. You can set up rules that determine these parameters from the edge's properties. A set of such rules
is called a 'custom model' and it is written in a dedicated YAML format. We will now see how the cost function parameters
can be influenced by the different fields of such a custom model.
  
#### Customizing `speed`

For every edge a default speed is inherited from the base vehicle, but you have multiple options to adjust it.
The first thing you can do is rescaling the default speeds using the `speed_factor` section. For example this is how you
can reduce the the speed of every edge that has the value 'motorway' for the category 'road_class' to fifty percent of 
the default speed that is normally used by the base vehicle for this road class:
```yaml
speed_factor:
  road_class: {motorway: 0.5}
```  
Note that `road_class: {motorway: 0.5}` is an alternative YAML notation that is equivalent to:
```yaml
speed_factor:
  road_class:
    motorway: 0.5
```

You can also setup speed factors for multiple road classes like this
```yaml
speed_factor:
  road_class: {motorway: 0.5, primary: 0.7, tertiary: 0.9}
```

and use multiple categories to influence the speed factor
```yaml
speed_factor:
  road_class: {motorway: 0.5}
  road_environment: {tunnel: 0.8}
```

If an edge matches multiple rules the speed factor values will be multiplied. For example, here the speed factor of 
a road segment that has `road_class=motorway` will be `0.5`, the speed factor of a road segment that additionally has 
`road_environment=tunnel` will be `0.4` and the speed factor of a road segment that has `road_class=secondary` and 
`road_environment=tunnel` will be `0.8`.

Instead of setting the speed factors for certain values you can instead set the speed factors for all *other* values using
as special key (`"*"`), like this:
```yaml
speed_factor: 
  road_class: {"*": 0.5}
  road_environment: {tunnel: 0.8, "*": 0.6}
```

So in this example we set a speed factor of `0.5` regardless of the `road_class` and all `road_environment` values yield
a speed factor of `0.6` *except* `tunnel` which gets a speed factor of `0.8`. And as mentioned above for edges that match
multiple of these rules the different factors get multiplied.

For encoded values with boolean values, like `get_off_bike` you set the speed factor like this:
```yaml
speed_factor:
  get_off_bike: {true: 0.6, false: 1.0}
```
which means that for edges with `get_off_bike=true` the speed factor will be `0.6` and otherwise it will be `1.0`.
You can skip any of these values to retain the default.

For encoded values with numeric values, like `max_width` you use the `<` and `>` operators, like this:
```yaml
speed_factor:
  max_width: {"<2.5": 0.8}
``` 
which means that for all edges with `max_width` smaller than `2.5m` the speed factor is `0.8`.

In any case values of `speed_factor` have to be in the range `[0,1]` and it is not possible to *increase* the speed for
edges of certain types. 

Another way to change the speed is using the `max_speed` section, for example:
```yaml
max_speed:
  surface: {gravel: 60}
```

implies that on all road segments with `surface=gravel` the speed will be at most `60km/h`, regardless of the default 
speed of this edge or the adjustments made by the `speed_factor` section. Just like with `speed_factor` you can setup
`max_speed` values for multiple category values and different categories. If multiple rules match for a given edge the
most restrictive rule will determine the speed (the minimum `max_speed` will be applied). 
Values for `max_speed` must be in the range `[0,max_vehicle_speed]` where `max_vehicle_speed` is the maximum speed that
is set for the base vehicle (which you cannot change).

You can also modify the speed for all edges in a certain area. To do this first add some areas to the `areas` section
of the custom model and then use this name to set a `speed_factor` or `max_speed` for this area. In the following
example we set the `speed_factor` of an area called `my_area` to `0.7`. For `max_speed` it works the same way. All area
names need to be prefixed with `area_`.  

```yaml
speed_factor:
  area_my_area: 0.7

max_speed:
  area_my_area: 50

areas:
  my_area:
    type: "Feature"
    geometry:
      type: "Polygon"
      coordinates: [
        [10.75, 46.65],
        [9.54, 45.65],
        [10.75, 44.65],
        [8.75, 44.65],
        [8.75, 45.65],
        [8.75, 46.65]
      ]
```

The areas are given in GeoJson format. Using the `areas` feature you can also block entire areas completely, but you 
should rather use the `priority` section for this (see below).

The last custom model field you can to customize the speed is the `max_speed_fallback`. By default this is set to the
maximum speed of the base vehicle. It allows setting a global maximum for the speeds, so for example
```yaml
max_speed_fallback: 50
```
means that the speed is at most `50km/h` for any edge regardless of its properties. 

#### Customizing `priority`

Looking at the custom cost function formula above might make you wonder what the difference between speed and priority is,
because it enters the formula in the same way. When calculating the edge weights (which determine the optimal route)
changing the speed in fact has the same effect as changing the priority. But do not forget that GraphHopper not 
only calculates the optimal route, but also the time (and distance) it takes to drive (or walk) this route. Changing the
speeds also means changing the resulting travelling times, but `priority` allows you to alter the route calculation 
*without* changing the travelling time of a given route.  

By default the priority is `1` for every edge,
so without doing anything it does not affect the weight. However, changing the priority for certain kinds of roads yields
a relative weight difference depending on the edges' properties.

You change the `priority` very much like you change the `speed_factor`, so
```yaml
priority:
  road_class: {motorway: 0.5, secondary: 0.9}
  road_environment: {tunnel: 0.1}
```

means that road segments with `road_class=motorway` and `road_environment=tunnel` get priority `0.5*0.1=0.05` and those 
with `road_class=secondary` get priority `0.9` and so on.

Edges with lower priority values will be less likely part of the optimal route calculated by GraphHopper, higher values 
mean that these kind of road segments shall be preferred. To prefer certain roads you need to `decrease` the priority of
others, which you can do using the special key `"*"`, i.e. 
```yaml
priority: 
  road_class: {cycleway: 1.0, "*": 0.8}
```
means decreasing the priority for all road_classes *except* cycleways. `priority` values need to be in the range `[0, 1]`.
Just like we saw for `speed_factor` and `max_speed` you can also adjust the priority for all edges in a certain area.
It works the same way:

```yaml
priority:
  area_my_area: 0.7 
```

To block an entire area completely set the priority value to `0`. Some other useful encoded values to restrict access
to certain roads depending on your vehicle dimensions are the following:
```yaml
priority: 
  max_width: {"<2.5": 0}
  max_length: {"<10.0": 0}
  max_weight: {"<3.5": 0}
```
which means that the priority for all edges that allow a maximum vehicle width of `2.5m`, a maximum vehicle length of 
`10m` or a maximum vehicle weight of `3.5tons` is zero (these edges are blocked).

#### Customizing `distance_influence`

`distance_influence` allows you to control the trade-off between a fast route (minimum time) and a short route
(minimum distance). Setting it to `0` means that GraphHopper will return the fastest possible route, *unless* you set 
the priority of any edges to something different than `1.0`. Using the priority factors will always to some part favor
some routes because they are shorter as well. Higher values of `distance_influence` will prioritize routes that are fast
(but maybe not fastest), but at the same time are short in distance.

You use it like this:
```yaml
distance_influence: 100
``` 

The default is `70`. More precisely, by specifying the `distance_influence` you tell the routing engine how much time
you need to save on a detour (a longer distance route) such that you prefer taking the detour compared to a shorter 
distance route. Assuming that all priorities are `1` a value of zero means that no matter how little time you can save 
when doing a detour you will take it, i.e. you always prefer the fastest route. A value of `30` means that one extra
kilometer must save you `30s` of travelling time. Or to put it another way if a reference route takes `600s` and is 
`10km` long, `distance_influence=30` means that you are willing to take an alternative route that is `11km` long only if
it takes no longer than `570s` (saves `30s`).      

## Setting up a Custom Profile

Custom profiles work very much the same way as the 'standard' profiles described at the beginning of this document. The
custom model needs to be written in a separate YAML (or JSON) file and the `weighting` has to be set to `custom`. Use the 
`custom_model_file` field to point to the custom model file like this:

```yaml
profiles:
  - name: my_custom_profile
    vehicle: car
    weighting: custom
    custom_model_file: path/to/my_custom_profile.yaml
  
```

Selecting a custom profile also works like selecting a standard profile. In our example just add `profile=my_custom_profile`
to your routing request. To set up hybrid- or speed-mode, simply use the `profiles_ch/lm` sections and add the name of
your custom profile (just like you do for standard profiles).

## Changing the Custom Profile for a single routing request

With flex- and hybrid mode its even possible to define the custom model on a per-request basis and doing this you can 
even adjust a custom model configured on the server side for a single request. To do this you first set up a 'base' 
custom profile in your server configuration, like:

```yaml
profiles:
  - name: my_flexible_car_profile
    vehicle: car
    weighting: custom
    custom_model_file: my_custom_model.yml
``` 

You do not necessarily need to define a proper custom model here and instead you can also set `custom_model_file: empty`
(which means an empty custom model containing no rules will be used on the server-side). You then use the `/route-custom`
(*not* `/route`) endpoint and send your custom model (using the format explained above, but as JSON) with the request 
body. The model you send will be merged with the profile you select using the `profile` parameter (which has to be 
a custom profile). For the Hybrid mode (not only "pure" Dijkstra or A*) the merge process has to ensure that all weights
resulting from the merged custom model are equal or larger than those of the base profile that was used during the 
preparation process. This is necessary to maintain the optimality of the underlying routing algorithm. 
Therefore we use the following rules to merge the two models:

 * for priority: an existing factor is multiplied with the factor specified in the request
 * for speed_factor: an existing factor is multiplied with the factor specified in the request
 * for max_speed: an existing speed value is replaced if it is higher than the speed value specified in the request. 
   If it is smaller an exception is thrown.
 * Comparisons like `max_weight: { "<3.5": 0 }` are special cases. The merge will be only accepted if the factor in the
   query is 0. And the range can be only "expanded", which means:
    * The 'smaller than' comparison key (like in this example the `<3.5`) can only be replaced by bigger comparison keys like `<4.5`.
    * The 'greater than' comparison key (e.g. `>2`) can only be replaced by smaller comparison keys like `>1.9`.
