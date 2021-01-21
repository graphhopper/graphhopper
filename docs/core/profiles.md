# Profiles

GraphHopper lets you customize how different kinds of roads shall be prioritized during its route calculations. For
example when travelling long distances with a car you typically want to use the highway to minimize your travelling
time. However, if you are going by bike you certainly do not want to use the highway and rather take some shorter route,
use designated bike lanes and so on. GraphHopper provides built-in vehicle types that cover some standard vehicles. They
can be used with a few different weightings like the 'fastest' weighting that chooses the fastest route (minimum
travelling time), or the 'shortest' weighting that chooses the shortest route (minimum travelling distance). The
selection of a vehicle and weighting is called 'profile', and we refer to these built-in choices as 'standard profiles'
here. For more flexibility there is a special kind of weighting, called 'custom' weighting that allows for fine-grained
control over which roads GraphHopper will prioritize. Such custom profiles still use one of the built-in vehicles, but
you can modify, e.g. the travelling speed for certain road types. Both types of profiles, the standard ones and custom
profiles, are explained in the following.

## Standard Profiles

Standard profiles require little configuration and all you need to choose is a unique name, a 'vehicle', and a
'weighting' type. All profiles are specified in the 'profiles' section of `config.yml` and there has to be at least one
profile. Here is an example:

```yaml
profiles:
  - name: car
    vehicle: car
    weighting: fastest
  - name: some_other_profile
    vehicle: bike
    weighting: shortest
```

The vehicle field must correspond to one of GraphHopper's built-in vehicle types:

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
If you are interested in the low-level Java API note that the vehicles correspond to implementations of
the `FlagEncoder` interface.

The weighting determines the 'cost function' for the route calculation and must match one of the following built-in
weightings:

- fastest (minimum travel time)
- shortest (minimum travel distance)
- short_fastest (yields a compromise between short and fast routes)
- curvature (prefers routes with lots of curves for enjoyable motorcycle rides)
- custom (enables custom profiles, see the next section)

The profile name is used to select the profile when executing routing queries. To do this use the `profile` request
parameter, for example `/route?point=49.5,11.1&profile=car` or `/route?point=49.5,11.1&profile=some_other_profile`.

## Custom Profiles

*Disclaimer*: Custom profiles should still be considered a beta feature. Using them should be working, but details about
the weight formula and the meaning of the different parameters is still subject to change. Also this feature will
strongly benefit from community feedback, so do not hesitate to share your experience, your favorite custom model or
some of the problems you ran into when you tried custom profiles.

You can adjust the cost function of GraphHopper's route calculations in much more detail by using so called 'custom'
profiles. Every custom profile builds on top of a 'base' vehicle from which the profile inherits the road accessibility
rules and default speeds for the different road types. However, you can specify a set of rules to change these default
values. For example you can change the speed only for a certain type of road (and much more).

Custom profiles are specified like this:

```yaml
profiles:
  - name: my_custom_profile
    vehicle: car
    weighting: custom
    custom_model_file: path/to/my_custom_profile.yaml

```

The name and vehicle fields are the same as for standard profiles and the vehicle field is used as the 'base' vehicle
for the custom profile. The weighting is always set to `custom` for custom profiles. The details about the custom
profile go into a separate YAML (or JSON) file and `custom_model_file` holds the path to this file.

Using custom profiles for your routing requests works just the same way as for standard profiles. Simply add
`profile=my_custom_profile` as request parameter to your routing request.

We will now explain how the custom weighting works and how to create the custom_model_file.

### Custom Weighting

The weight or 'cost' of travelling along an 'edge' (a road segment of the routing network) depends on the length of the
road segment (the distance), the travelling speed, the 'priority' and the 'distance_influence' factor (see below). To be
more precise, the cost function has the following form:

```
edge_weight = edge_distance / (speed * priority) + edge_distance * distance_influence
```

The `edge_distance` is calculated during the initial import of the road network and you cannot change it here.
Note that the edge weights are proportional to this distance. What can be customized is the `speed`, the `priority` and
the `distance_influence`, which we will discuss in a moment. First we need to have a look at the 'properties' of an edge:

### Edge properties: Encoded Values

GraphHopper assigns values of different categories ('encoded values') to each road segment. For example for OSM data 
they are derived from the OSM way tags. All possible categories are defined in
[`DefaultEncodedValueFactory.java`](../../core/src/main/java/com/graphhopper/routing/profiles/DefaultEncodedValueFactory.java)
but only categories specified with `graph.encoded_values` field in the `config.yml` are available in the graph storage.
For example there are the following categories (some of their possible values are given in brackets).

- road_class: (OTHER, MOTORWAY, TRUNK, PRIMARY, SECONDARY, TRACK, STEPS, CYCLEWAY, FOOTWAY, ...)
- road_environment: (ROAD, FERRY, BRIDGE, TUNNEL, ...)
- road_access: (DESTINATION, DELIVERY, PRIVATE, NO, ...)
- surface: (PAVED, DIRT, SAND, GRAVEL, ...)
- toll: (NO, ALL, HGV)

To find out about all the possible values of a category you can take a look at the corresponding Java files like 
[`RoadClass.java`](../../core/src/main/java/com/graphhopper/routing/profiles/RoadClass.java), query the
`/info` endpoint of the server or use the auto-complete feature of the text box that opens when clicking the 'flex' icon
in the web UI.

Besides this kind of categories, which can take multiple different string values, there are also some that represent a
boolean value (they are either true or false for a given edge), like:

- get_off_bike
- road_class_link

And there are others that take on a numeric value, like:

- max_weight
- max_width

*Important note: Whenever you want to use any of these categories for a custom profile you need to add them to
`graph.encoded_values` in `config.yml`.*

### Creating the `custom_model_file`: Setting up a custom model

As we saw in one of the previous sections, the custom weighting function has three parameters that you can adjust:
speed, priority and distance_influence. You can set up rules that determine these parameters from the edge's properties.
A set of such rules is called a 'custom model' and it is written in a dedicated YAML or JSON file,
the `custom_model_file`. We will explain how to set up these rules. Customizing speed and priority is very similar, so
we will explain most of the details you need to know for speed, but they can be applied very much the same way for
priority as well.

#### The custom model language by the example of customizing `speed`

For every road segment a default speed is inherited from the profile's (base) vehicle. Adjusting the speed is done by a
series of conditional operations that are written in the `speed` section of the `custom_model_file` and modify the
default speed. Currently the custom model language only comprises two operators:

- `multiply by` multiplies the speed value with a given number
- `limit to` limits the speed value to a given number

The operations are applied to the default speed from top to bottom, i.e. operations that are lower in the list are
applied to the resulting value of previous operations. Each operation is only executed if the corresponding condition
applies for the edge the speed is calculated for.

##### `if` statements and the `multiply by` operation

Let's start with a simple example using `multiply by`:

```yaml
# this is the `custom_model_file`.
speed:
  - if: road_class == MOTORWAY
    multiply by: 0.5
```

This custom model reduces the speed of every road segment that has the value `MOTORWAY` for the category 'road_class' to
fifty percent of the default speed (the default speed is multiplied by `0.5`). Again, the default speed is the speed
that GraphHopper would normally use for the profile's vehicle. Note the `if` clause which means that the
operation (`multiply_by`) is only applied *if* the condition `road_class == MOTORWAY`
is fulfilled for the edge under consideration. The `==` indicates equality, i.e. the condition reads "the road_class
equals MOTORWAY". If you're a bit familiar with programming note that the condition (the value of the `if` key) is just
a boolean condition in Java language (other programming languages like C or JavaScript are very similar in this regard).
A more complex condition could look like this: `road_class == PRIMARY || road_class == TERTIARY` which uses the **or**
(`||`) operator and literally means "road_class equals PRIMARY or road_class equals TERTIARY".

As already stated there can be multiple such 'if statements' in the speed section and they are evaluated from top to
bottom:

```yaml
speed:
  - if: road_class == MOTORWAY
    multiply by: 0.5
  - if: road_class == PRIMARY || road_environment == TUNNEL
    multiply by: 0.7
```

In this example the default speed of edges with `road_class == MOTORWAY` will be multiplied by `0.5`, the default speed
of edges with `road_class == PRIMARY` will be multiplied by `0.7` and for edges with both `road_class == MOTORWAY` and
`road_environment == TUNNEL` the default speed will be multiplied first by `0.5` and then by `0.7` (the default speed
will be multiplied with `0.35` overall). For edges with `road_class == PRIMARY` and `road_environment == TUNNEL` we only
multiply by `0.7`, even though both parts of the second condition apply. It only matters whether the edge matches the
condition or not.

`road_class` and `road_environment` are categories of 'enum' type, i.e. their value can only be one of a fixed set of
values, like `MOTORWAY` for `road_class`. The possible values were listed in one of the previous sections.

Other categories like `get_off_bike` are of `boolean` type. They can be used as conditions directly, for example:

```yaml
speed:
  - if: get_off_bike
    multiply by: 0.6
```

which means that for edges with `get_off_bike==true` the speed factor will be `0.6`.

For categories/encoded values with numeric values, like `max_width` you should not use the `==` (equality) or `!=` (
inequality)
operators, but the numerical comparison operators "bigger" `>`, "bigger or equals" `>=`, "smaller" `<`, or "smaller or
equals" `<=`, e.g.:

```yaml
speed:
  - if: max_width < 2.5
    multiply by: 0.8
``` 

which means that for all edges with `max_width` smaller than `2.5m` the speed is multiplied by `0.8`.

Categories of `string` type are used like this (note the quotes ""):

```yaml
speed:
  - if: country == "DEU"
    multiply by: 0
```

##### The `limit to` operation

As already mentioned, besides the `multiply by` operator there is also the `limit to` operator. As the name suggests
`limit to` limits the current value to the given value. Take this example:

```yaml
speed:
  - if: road_class == MOTORWAY
    multiply_by: 0.8
  - if: surface == GRAVEL
    limit to: 60
```

This implies that on all road segments with the `GRAVEL` value for `surface` the speed will be at most `60km/h`,
regardless of the default speed and the previous rules. So for a road segment with `road_class == MOTORWAY`,
`surface == GRAVEL` and default speed `100` the first statement reduces the speed from `100` to `80` and the second
statement further reduces the speed from `80` to `60`. If the `road_class` was `PRIMARY` and the default speed was `50`
the first rule would not apply and the second rule would do nothing, because limiting `50` to `60` is still `50`.

Note that all values used for `limit to` must be in the range `[0, max_vehicle_speed]` where `max_vehicle_speed` is the
maximum speed that is set for the base vehicle and cannot be changed.

A common use-case for the `limit to` operation is the following pattern:

```yaml
speed:
  - if: true
    limit to: 90
```

which means that the speed is limited to `90km/h` for all road segments regardless of its properties. The condition
`true` is always fulfilled.

##### `else` and `else if` statements

The `else` statement allows you to define that some operations should be applied if an edge does **not** match a
condition. So this example:

```yaml
speed:
  - if: road_class == MOTORWAY
    multiply by: 0.5
  - else:
    limit to: 50
```

means that for all edges with `road_class == MOTORWAY` we multiply the default speed by `0.5` and for all others we
limit the default speed to `50` (but never both). In case you want to distinguish more than two cases (edges that match
or match not a condition) you can use `else if` statements which are only evaluated in case the previous `if`
or `else if` statement did **not** match:

```yaml
speed:
  - if: road_class == MOTORWAY
    multiply by: 0.5
  - else if: road_environment == TUNNEL
    limit to: 70
  - else:
    multiply by: 0.9
```

So if the first condition matches (`road_class == MOTORWAY`) the default speed is multiplied by `0.5` but the other two
statements are ignored. Only if the first statement does not match (e.g. `road_class == PRIMARY`) the second statement
is even considered and only if it matches (`road_environment == TUNNEL`) the default speed is limited to 70. The last
operation (`multiply by: 0.9`) is only applied if the two previous conditions did not match.

`else` and `else if` statements always require a preceding `if` or `else if` statement. However, there can be multiple
'blocks' of subsequent `if/else if/else` statements in the list of rules for `speed`.

`else if` is useful for example in case you have multiple `multiply by` operations, but you do not want that the speed
gets reduced by all of them. For the following model

```yaml
speed:
  - if: road_class == MOTORWAY
    multiply by: 0.5
  - else if: road_environment == TUNNEL
    multiply by: 0.8
```

only the first factor (`0.5`) will be applied even for road segments that fulfill both conditions.

##### `areas`

You can not only modify the speed of road segments based on properties as we saw in the previous examples, but you can
also modify the speed of road segments based on their location. To do this you need to first create and add some areas
to the `areas` section of in the custom model file. You can then use the name of these areas in the condition we used in
the `if/else/else if` statements we saw in the previous sections.

In the following example we multiply the speed of all edges in an an area called `custom1` with `0.7` and also limit it
to `50km/h`. Note that each area's name needs to be prefixed with `in_area_`:

```yaml
speed:
  - if: in_area_custom1
    multiply by: 0.7
  - if: in_area_custom1
    limit to: 50

areas:
  custom1:
    type: "Feature"
    geometry:
      type: "Polygon"
      coordinates: [
        [ 10.75, 46.65 ],
        [ 9.54, 45.65 ],
        [ 10.75, 44.65 ],
        [8.75, 44.65],
        [8.75, 45.65],
        [8.75, 46.65]
      ]
```

The areas are given in GeoJson format. Note that JSON can be directly copied into YAML without further modifications.
Using the `areas` feature you can also block entire areas i.e. by multiplying the speed with `0`, but for this you
should rather use the `priority` section that we will explain next.

#### Customizing `priority`

Looking at the custom cost function formula above might make you wonder what the difference between `speed`
and `priority` is, because it enters the formula in the same way. When calculating the edge weights
(which determine the optimal route) changing the speed in fact has the same effect as changing the priority. However, do
not forget that GraphHopper not only calculates the optimal route, but also the time (and distance) it takes to drive (
or walk) this route. Changing the speeds also means changing the resulting travel times, but `priority`
allows you to alter the route calculation *without* changing the travel time of a given route.

By default, the priority is `1` for every edge, so without doing anything it does not affect the weight. However,
changing the priority of a road can yield a relative weight difference in comparison to other roads.

Customizing the `priority` works very much like changing the `speed`, so in case you did not read the section about
`speed` now should be the time to do this. The only real difference is that there is no `limit to` operator for
`priority`. As a quick reminder here is an example for priority:

```yaml
priority:
  - if: road_class == MOTORWAY
    multiply by: 0.5
  - else if: road_class == SECONDARY
    multiply by: 0.9
  - if: road_environment == TUNNEL
    multiply by: 0.1
```

means that road segments with `road_class==MOTORWAY` and `road_environment==TUNNEL` get priority `0.5*0.1=0.05` and
those with `road_class==SECONDARY` and no TUNNEL, get priority `0.9` and so on.

Edges with lower priority values will be less likely part of the optimal route calculated by GraphHopper, higher values
mean that these road segments shall be preferred. If you do not want to state which road segments shall be avoided, but
rather which ones shall be preferred, you need to **decrease** the priority of others:

```yaml
priority:
  - if: road_class != CYCLEWAY
    multiply by: 0.8
```

means decreasing the priority for all road_classes *except* cycleways.

Just like we saw for `speed` you can also adjust the priority for road segments in a certain area. It works exactly the
same way:

```yaml
priority:
  - if: in_area_custom1
    multiply by: 0.7
```

To block an entire area completely set the priority value to `0`. Some other useful encoded values to restrict access to
certain roads depending on your vehicle dimensions are the following:

```yaml
priority:
- if: max_width < 2.5
  multiply by: 0
- if: max_length < 10
  multiply by: 0
- if: max_weight < 3.5
  multiply by: 0
```
which means that the priority for all road segments that allow a maximum vehicle width of `2.5m`, a maximum vehicle
length of `10m` or a maximum vehicle weight of `3.5tons`, or less, is zero, i.e. these "tight" road segments are
blocked.

#### Customizing `distance_influence`

`distance_influence` allows you to control the trade-off between a fast route (minimum time) and a short route
(minimum distance). Setting it to `0` means that GraphHopper will return the fastest possible route, *unless* you set
the priority of any edges to something other than `1.0`. Using the priority factors will always to some part favor some
routes because they are shorter as well. Higher values of `distance_influence` will prioritize routes that are fast
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

#### Using JSON instead of YAML notation

You can use a slightly shorter syntax than the one we used in the previous examples, e.g.:

```yaml
speed:
  - if: "road_class == MOTORWAY",     multiply by: 0.5
  - if: "road_environment == TUNNEL", multiply by: 0.8
```

You can even use JSON language like this:

```json
{
  "speed": [
    {
      "if": "road_class == MOTORWAY",
      "multiply by": 0.5
    },
    {
      "if": "road_environment == TUNNEL",
      "multiply by": 0.8
    }
  ]
}
```

To use the `else` statement in JSON you need to use the `null` value where in YAML you could leave the value blank:

```yaml
speed:
  - if: road_class == MOTORWAY
    multiply by: 0.6
  - else:
    multiply by: 0.8
```

becomes

```json
{
  "speed": [
    {
      "if": "road_class == MOTORWAY",
      "multiply by": 0.6
    },
    {
      "else": null,
      "multiply by": 0.8
    }
  ]
}
```

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

The values given under `profile` are just the profile names you specified in the profile definitions. Note that 'CH' is
short for 'Contraction Hierarchies', the underlying technique used to realize speed mode and
'LM' is short for 'Landmarks', which is the algorithm used for the hybrid mode.

For hybrid mode there is a special feature that allows 're-using' the prepared data for different profiles. You can do
this like this:

```yaml
profiles_lm:
  - profile: car
  - profile: some_other_profile
    preparation_profile: car
```

which means that for `some_other_profile` the preparation of the `car` profile will be used. However, this will only
give correct routing results if `some_other_profile` yields larger or equal weights for all edges than the `car`profile.
Better do not use this feature unless you know what you are doing.

## Using different custom models on a per-request basis

So far we talked only about standard and custom profiles that are configured on the server side in `config.yml`.
However, with flex- and hybrid mode it is even possible to define the custom model on a per-request basis. This enables
you to perform route calculations using custom models that you did not anticipate when setting up the server.

To use this feature you need to query the `/route-custom` (*not* `/route`) endpoint and send the custom model along with
your request in JSON format. The syntax for the custom model is the same as for server-side custom models (but using
JSON not YAML notation, see above). The routing request has the same format as for `/route`, but with an
additional `model` field that contains the custom model. You still need to set the `profile` parameter for your request
and the given profile must be a custom profile.

Now you might be wondering which custom model is used, because there is one set for the route request, but there is also
the one given for the profile that we specify via the `profile` parameter. The answer is "both" as the two custom models
are merged into one. The two custom models are merged according to the following rules:

* all expressions in the custom model of the query are appended to the existing custom model.
* for the custom model of the query all values of `multiply by` need to be within the range of `[0, 1]` otherwise an
  error will be thrown
* the `distance_influence` of the query custom model overwrites the one from the server-side custom model *unless*
  it is not specified. However, the given value must not be smaller than the existing one.

If you're curious about the second rule note that for the Hybrid mode (using Landmarks not just Dijkstra or A*)
the merge process has to ensure that all weights resulting from the merged custom model are equal or larger than those
of the base profile that was used during the preparation process. This is necessary to maintain the optimality of the
underlying routing algorithm.

So say your routing request (POST /route-custom) looks like this:

```json
{
  "points": [
    [
      11.58199,
      50.0141
    ],
    [
      11.5865,
      50.0095
    ]
  ],
  "profile": "my_custom_car",
  "model": {
    "speed": [
      {
        "if": "road_class == MOTORWAY",
        "multiply by": 0.8
      },
      {
        "else": null,
        "multiply by": 0.9
      }
    ],
    "priority": [
      {
        "if": "road_environment == TUNNEL",
        "multiply by": 0.95
      }
    ],
    "distance_influence": 0.7
  }
}
```

where in `config.yml` we have:

```yaml
profiles:
  - name: my_custom_car
    vehicle: car
    weighting: custom
    custom_model_file: path/to/my_custom_car.yml
```

and `my_custom_car.yml` looks like this:

```yaml
speed:
  - if: surface == GRAVEL
    limit to: 100
```

then the resulting custom model used for your request will look like this:

```yaml
speed:
  - if: surface == GRAVEL
    limit to: 100
  # this was appended due to the custom model given by the request
  - if: road_class == MOTORWAY
    multiply by: 0.8
  - else:
    multiply by: 0.9

priority:
  - if: road_environment == TUNNEL
    multiply by: 0.95

distance_influence: 0.7
```

You do not necessarily need to define a proper custom model on the server side, but you can also use the special
value `custom_model_file: empty` which means an empty custom model containing no statements will be used on the
server-side. This way you can leave it entirely up to the user/client how the custom model shall look like.


