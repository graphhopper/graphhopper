# Profiles

## Standard Profiles

When calculating routes with GraphHopper you can customize how different kind of roads shall be prioritized. For example
when travelling long distances with a car you typically want to use the highway, because this way you will reduce your
travelling time. However, if you are going by bike you certainly do not want to use the highway and rather take some
shorter route, use designated bike lanes and so on. To prefer routes that are useful for a given type of vehicle 
GraphHopper includes the following pre-configured vehicle:

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

By choosing a vehicle GraphHopper determines the accessibilty and an average travel speed for the different road types.
Note to readers interested in the low-level Java API: These pre-configured vehicles correspond to implementations of the
`FlagEncoder` Java interface. 
 
Besides the vehicle it is also possible to choose between different weightings (=cost functions) for the route calculation.
GraphHopper includes the following weightings:

- fastest
- shortest
- short_fastest (yields a compromise between short and fast routes)
- curvature (prefers routes with lots of curves for enjoyable motorcycle rides)
- custom (see the next section)

GraphHopper calls the different travelling modes 'profiles' and when starting the GraphHopper server at least one such
profile needs to be configured upfront. This needs to be done in the 'profiles' section in `config.yml`, here is an
example:

```yaml
profiles:
  - name: car
    vehicle: car
    weighting: fastest
  - name: some_other_profile
    vehicle: bike
    weighting: shortest
```

Every profile has to include a unique name that is used to select a profile when executing routing queries, and one of
the vehicles and weightings listed above. To use one of the profiles you need to use the `profile` parameter for your 
request, like `/route?point=49.5,11.1&profile=car` or `/route?point=49.5,11.1&profile=some_other_profile` in this example.

## Speed and Hybrid Mode

GraphHopper can drastically improve the execution time of the route calculations by preprocessing the routing profiles
upfront. To enable this you need to list the profiles you want GraphHopper to do the preprocessing for like this:

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

# todonow: maybe rename to speed_mode_profiles and hybrid_mode_profiles?
Note that 'CH' is short for 'Contraction Hierarchies', the underlying technique used to realize speed mode and
'LM' is short for 'Landmarks', which is the algorithm used to by hybrid mode.

## Custom Profiles

You can take the customization of the routing profiles much further by using 'custom' profiles that let you adjust the
cost function on a much more fine-grained level. A custom profile is based on a 'base' vehicle that you like to adjust.
By choosing the base vehicle you inherit the road accessibilty rules and default speeds of this vehicle, but the custom
weighting gives you the freedom to overwrite certain parts of the cost function depending on the different road
properties. 

### Custom Weighting
The weight or 'cost' of travelling along an 'edge' (a road segment of the routing network) depends on the length
of the road segment (the distance), the travelling speed, the 'priority' and the 'distance_influence' factor (see below).
To be more precise, the cost function has the following form:

```
edge_weight = edge_distance / (speed * priority) + edge_distance * distance_influence
```

The `edge_distance` is calculated during the initial import of the road network and you cannot change it here.
Note that the edge weights are proportional to this distance. What can be customized is the `speed`, the
`priority` and the `distance_influence`, which we will be looking at in a moment. But first we need to have a look
at the 'properties' of an edge:

### Edge properties: Encoded Values

GraphHopper assigns values of different categories to each road segment. For OSM data they are derived from the OSM
way tags. The available categories are specified by using the `graph.encoded_values` field in `config.yml` and (unless
you do further customization of GraphHopper) the possible categories are defined in `DefaultEncodedValueFactory.java`.
For example there are these categories (some of their possible values are given in brackets).

# todonow: should we try to list them all here?
- road_class: (other,motorway,trunk,primary,secondary,track,steps,cycleway,footway,...)
- road_environment: (road,ferry,bridge,tunnel,...)
- road_access: (destination,delivery,private,no,...)
- surface: (paved,dirt,sand,gravel,...)

To find out about all the possible values of a categories you can take a look at the corresponding Java files, query the
`/info` endpoint of the server or use the auto complete feature of the text boxt that opens when clicking 'flex' in the
web UI.

Besides these kind of categories which can take multiple different values there are also some that represent a boolean
value (they are either true or false for a given edge), like:

- get_off_bike
- road_class_link

Important note: Whenever you want to use any of these categories for a custom profile you need to add them to 
`graph.encoded_values` in `config.yml`.

### Setting up a Custom Model

As mentioned above the custom weighting function has three parameters that you can adjust: speed, priority and
distance_influence. These parameters are derived from rules that determine the parameters from the edge's properties
A set of such rules is called a 'custom model' and it is written a dedicated YAML format. We will now see how 
the cost function parameters can be influenced by the different sections of such a custom model file.
  
*speed*

For every edge a default speed is inherited from the base vehicle, but you have multiple options to adjust it.
The first thing you can do is rescaling the default speeds using the `speed_factor` section. For example this is how you
can specify that the speed of every edge that has the value 'motorway' for the category 'road_class' should be twice as
high as the default speed normally used by the base vehicle for this road class:
```yaml
speed_factor:
  road_class: {motorway: 2}
```  

You can also setup speed factors for multiple road classes like this
```yaml
speed_factor:
  road_class: {motorway: 2, primary: 1.5, tertiary: 0.5}
```

and use multiple categories to influence the speed factor
```yaml
speed_factor:
  road_class: {motorway: 2}
  road_environment: {tunnel: 1.2}
```

If an edge matches multiple of the rules the speed factor values will be multiplied. For example the speed factor of 
a road segment that has `road_class=motorway` will be `2`, the speed factor of a road segment that additionally has 
`road_environment=tunnel` will be `2.4` and the speed factor of a road segment that has `road_class=secondary` and 
`road_environment=tunnel` will be `1.2`.

Another way to change the speed is using the `max_speed` section, for example:
```yaml
max_speed:
  surface: {gravel: 60}
```

implies that on all road segments with `surface=gravel` the speed will be at most `60km/h`, regardless of the default 
speed of this edge or the adjustments made by the `speed_factor` section. Just like with `speed_factor` you can setup
`max_speed` values for multiple category values and different categories. If multiple rules match for a given edge the
most restrictive rule will determine the speed (the minimum `max_speed` will be applied).

The last thing you can do to customize the speed is using the `max_speed_fallback` of your custom model. By default this 
is set to the maximum speed of the base vehicle. It allows to set a global maximum for the speeds, so for example
```yaml
max_speed_fallback: 50
```

means that the speed is at most `50km/h` for any edge regardless of its properties. 

*priority*

Looking at the custom cost function formula above might make you wonder what the difference between speed and priority is
because it enters the formula in the same way. When calculating the edge weights (which determine the optimal route) changing
the speed in fact has the same effect as changing the priority. But do not forget that GraphHopper not only calculates the
optimal route, but also the time (and distance) it takes to take this route. Changing the speeds also means changing the
resulting travelling times, but priority allows you to alter the route calculation without changing the travelling time 
of a given route.  

By default the priority is `1` for every edge, so without doing anything it does not affect the route calculation. You 
can change the `priority` very much like you can change the `speed_factor`, so
```yaml
priority:
  road_class: {motorway: 0.5, secondary: 1.5}
  road_environment: {tunnel: 0.1}
```

means that road segments with `road_class=motorway` and `road_environment=tunnel` get priority `0.5*0.1=0.05` and those 
with `road_class=secondary` get priority `1.5` and so on.

Edges with lower priority values will be less likely part of the optimal route calculated by GraphHopper, higher values 
mean that these kind of road segments shall be preferred (for example you might want to increase `road_class=cycleway`) 
when going by bike.   

The priority can also be used to restrict access to certain roads depending on your vehicle dimensions. To do this
you need to add `max_width`,`max_height`,`max_length` and/or `max_weight` to `graph.encoded_values` (see above).

In your custom model you can setup your vehicle dimensions like this:

```yaml
vehicle_weight: 1.5 # in tons
vehicle_height: 2.1 # in meters
vehicle_length: 7.6 # in meters
vehicle_width: 2.3 # in meters
```

By default non of these restrictions will be applied and you can enable them separately by adding these definitions to
your custom model file.

*distance_influence*

`distance_influence` allows you to control the trade-off between a fast route (minimum time) and a short route
(minimum distance). Setting it to `0` means that GraphHopper will return the fastest possible route (while still 
taking into account the priorities, see above), and for higher values it will prioritize routes that are fast (but 
maybe take a little longer) and at the same time are short in distance.

You use it like this:
```yaml
distance_influence: 100
``` 

The default is `70`.

## Setting up a Custom Profile

Custom profiles work very much the same way as the 'standard' profiles described at the beginning of this document. The
custom model needs to be written in a separate YAML file and the `weighting` has to be set to `custom`. Use the 
`custom_model_file` field to point to the custom model file like this:

```yaml
profiles:
  name: my_custom_profile
  vehicle: car
  weighting: custom
  custom_model_file: path/to/my_custom_profile.yaml
  
```

Selecting a custom profile works the same way as selecting a standard profile, in our example just add `profile=my_custom_profile`
to your routing request. Also setting up hybrid- or speed-mode works the same way, simply use the `profiles_ch/lm` section
(see above) and add the name of your custom profile.

# todonow: check md formatting
# todonow: distance_influence details
# todonow: mention separate custom route endpoint
# todonow: using another profile as base profile
# todonow: sending different custom models per request (and merging with existing ones)
# todonow: cross-querying with LM profiles
# todonow: areas feature
# todonow: GeoToValue entries?
# todonow: mention priority normalization?