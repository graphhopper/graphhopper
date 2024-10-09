# Custom Models

GraphHopper provides an easy-to-use way to customize its route calculations: Custom models allow you to modify the
default routing behavior by specifying a set of rules in JSON language. Here we will first explain some theoretical
background and then show how to use custom models in practice.

Try some live examples in [this blog post](https://www.graphhopper.com/blog/2020/05/31/examples-for-customizable-routing/)
and the [custom_models](../../core/src/main/resources/com/graphhopper/custom_models) folder on how to use them on the server-side.

## How GraphHopper's route calculations work

One of GraphHopper's most important functionality is the calculation of the 'optimal' route between two locations. To do
this, GraphHopper subdivides the entire road network into so called 'edges'. Every edge represents a certain road
segment between two junctions. Therefore finding the optimal route between two locations means finding the optimal
sequence of edges that connect the two locations. GraphHopper stores certain attributes (so called 'encoded values') for
every edge and applies a formula (the so called 'weighting') to calculate a numeric 'weight' for every edge. The total
weight of a route is the sum of all the edge's weights. The optimal route is the one where the total weight is the
smallest.

For example you can imagine the edge weight to be the time you need to travel from one junction to another. Finding the
fastest route from A to B is then equivalent to finding the route for which the total time is minimal. Or similarly, you
might want to find not the fastest, but the shortest route. In this case an edge's weight would be simply the distance
of the corresponding road segment and again the optimal route would be the one with the minimum weight. However, it does
not have to be as simple as that. To find a short route that is still fast the weighting might involve distance *and*
time. Or maybe certain roads should be avoided, then the weight should be very large for the corresponding edges, such
that routes that include these roads come out with a large total weight and others that do not include these roads come
out with a smaller total weight and thus will be preferred.

Internally, GraphHopper uses the following formula for the weighting:

```
edge_weight = edge_distance / (speed * priority) + edge_distance * distance_influence
```

To simplify the discussion, let's first assume that `distance_influence=0` and `priority=1` so the formula simply reads:

```
edge_weight = edge_distance / speed
```

The weight is the larger the longer the road segment is and the smaller the faster we travel. The weight is simply the
travel time. The `speed` obviously depends on the type of the road and our vehicle type. Riding a bike you are faster on
concrete than on gravel and driving a car you are faster than a scooter for example. Therefore, GraphHopper stores the
speed for every edge based on the road type for different vehicles.

It is important to note that changing the speed not only changes the edge weight which, as we just learned, is used to
determine the optimal route, but also the actual travelling time of a route. But what if we want to increase an edge's
weight, so it won't be part of the optimal route in case there is a better alternative, but we do not want to modify the
travelling time? This is the reason why there is the `priority` factor in the above formula. It works the same way
as `speed`, but changing the priority only changes the edge weight, and not the travelling time. By default, `priority`
is always `1`, so it has no effect, but it can be used to modify the edge weights as we will see in the next section.

Finally, `distance_influence` allows us to control the trade-off between a fast route (minimum time) and a short route
(minimum distance). For example if `priority=1` setting `distance_influence=0` means that GraphHopper will return the
fastest possible route and the larger `distance_influence` is the more GraphHopper will prioritize routes with a small
total distance. More precisely, the `distance_influence` is the time you need to save on a detour (a longer distance
route option) such that you prefer taking the detour compared to a shorter route. Again assuming that `priority=1`, a
value of zero means that no matter how little time you can save when doing a detour you will take it, i.e. you always
prefer the fastest route no matter how large the detour is. A value of `30` means that one extra kilometer of detour
must save you `30s` of travelling time or else you are not willing to take the detour. Or to put it another way, if a
reference route takes `600s` and is `10km` long, `distance_influence=30` means that you are willing to take an
alternative route that is `11km` long only if it takes no longer than `570s` (saves `30s`). Things get a bit more
complicated when `priority` is not strictly `1`, but the effect stays the same: The larger
`distance_influence` is, the more GraphHopper will focus on finding short routes.

### Edge attributes used by GraphHopper: Encoded Values

GraphHopper stores different attributes, so called 'encoded values', for every road segment. Some frequently used
encoded values are the following (some of their possible values are given in brackets):

- road_class: (OTHER, MOTORWAY, TRUNK, PRIMARY, SECONDARY, TRACK, STEPS, CYCLEWAY, FOOTWAY, ...)
- road_environment: (ROAD, FERRY, BRIDGE, TUNNEL, ...)
- road_access: (DESTINATION, DELIVERY, PRIVATE, NO, ...)
- surface: (PAVED, DIRT, SAND, GRAVEL, ...)
- smoothness: (EXCELLENT, GOOD, INTERMEDIATE, ...)
- toll: (MISSING, NO, HGV, ALL)
- bike_network, foot_network: (MISSING, INTERNATIONAL, NATIONAL, REGIONAL, LOCAL, OTHER)
- country: (`MISSING` or the country as a `ISO3166-1:alpha3` code e.g. `DEU`)
- state: (`MISSING` or the state as `ISO3166-2` code e.g. `US_CA`)
- hazmat: (YES, NO), hazmat_tunnel: (A, B, .., E), hazmat_water: (YES, PERMISSIVE, NO)
- hgv: (MISSING, YES, DESIGNATED, ...)
- track_type: (MISSING, GRADE1, GRADE2, ..., GRADE5)
- urban_density: (RURAL, RESIDENTIAL, CITY)
- max_weight_except: (NONE, DELIVERY, DESTINATION, FORESTRY)
- footway: (MISSING, SIDEWALK, CROSSING, ACCESS_AISLE, LINK, TRAFFIC_ISLAND, ALLEY)
- crossing: (MISSING, RAILWAY_BARRIER, RAILWAY, TRAFFIC_SIGNALS, UNCONTROLLED, MARKED, UNMARKED, NO)
- tactile_paving: (MISSING, YES, NO, CONTRASTED, PRIMITIVE, INCORRECT, PARTIAL)
- kerb: (MISSING, FLUSH, LOWERED, NO, RAISED, ROLLED, YES)
- traffic_signals_sound: (MISSING, YES, NO, LOCATE, WALK)


To learn about all available encoded values you can query the `/info` endpoint

Besides this kind of categories, which can take multiple different string values, there are also some that represent a
boolean value (they are either true or false for a given road segment), like:

- get_off_bike
- road_class_link
- roundabout
- with postfix `_access` contains the access (as boolean) for a specific vehicle

There are also some that take on a numeric value, like:

- average_slope: a number for 100 * "elevation change" / edge_distance for a road segment; it changes the sign in reverse direction; see max_slope
- curvature: "beeline distance" / edge_distance (0..1) e.g. a curvy road is smaller than 1
- hike_rating, horse_rating, mtb_rating: a number from 0 to 6 for the `sac_scale` in OSM, e.g. 0 means "missing", 1 means "hiking", 2 means "mountain_hiking" and so on
- lanes: number of lanes
- max_slope: a signed decimal for the maximum slope (100 * "elevation change / distance_i") of an edge with `sum(distance_i)=edge_distance`. Important for longer road segments where ups (or downs) can be much bigger than the average_slope.
- max_speed: the speed limit from a sign (km/h)
- max_height (meter), max_width (meter), max_length (meter)
- max_weight (ton), max_axle_load (in tons)
- with postfix `_average_speed` contains the average speed (km/h) for a specific vehicle
- with postfix `_priority` contains the road preference without changing the speed for a specific vehicle (0..1)

In the next section will see how we can use these encoded values to customize GraphHopper's route calculations.

## How you can customize GraphHopper's route calculations: Custom Models

*Disclaimer*: Custom models should still be considered a beta feature. They work, but details about the weighting
formula and the meaning of the different parameters is still subject to change. Also this feature will strongly benefit
from community feedback, so do not hesitate to share your experience, your favorite custom model or some of the problems
you ran into when you tried building your own with custom model.

As described in the previous sections, GraphHopper's route calculations are controlled by the weighting of the different
road segments. GraphHopper offers a simple way to modify this weighting based on the edges' encoded values. To make use
of this you need to specify a so called 'custom model', which is a set of rules that determine the `speed` and the
`priority` of an edge. The custom model is written in JSON language and also includes a few more parameters like the
`distance_influence`.

Here is a complete request example for a POST /route query in berlin that includes a custom model:

```json
{
  "points": [
    [ 13.31543, 52.509535 ],
    [ 13.29779, 52.512434 ]
  ],
  "profile": "car",
  "ch.disable": true,
  "custom_model": {
    "speed": [
      {
        "if": "true",
        "limit_to": "100"
      }
    ],
    "priority": [
      {
        "if": "road_class == MOTORWAY",
        "multiply_by": "0"
      }
    ],
    "distance_influence": 100
  }
} 
```

Note that this only works for custom profiles and so far only for POST /route (but not GET /route or /isochrone, /spt or
/map-matching).

GraphHopper Maps offers an interactive text editor that can be used to comfortably enter custom models. You can open it
by pressing the 'custom' button. It will check the syntax of your custom model and mark errors in red. You can press
Ctrl+Space or Alt+Enter to retrieve auto-complete suggestions. Pressing Ctrl+Enter will send a routing request for the
custom model you entered.

In the following we will explain custom models in detail. Setting up rules for `speed` and `priority` is very similar,
so in our examples we will first concentrate on the `speed` rules, but as you will see they can be applied very much the
same way for priority as well.

### Custom models by the example of customizing `speed`

When using custom models you do not need to define rules that specify a speed for every edge, but rather GraphHopper
assumes a default speed that is set on the server-side. All you need to do is adjust this default speed to your
use-case. Typically, you will use the custom model in conjunction with a routing profile which is used to
determine the default speed.

The custom model is a JSON object and the first property we will learn about here is the `speed` property. The `speed`
property's value is a list of conditional statements that modify the default speed. Every such statement consists of a
condition and an operation. The different statements are applied to the default speed from top to bottom, i.e.
statements that come later in the list are applied to the resulting value of previous operations. Each statement is only
executed if the corresponding condition applies for the current edge. This will become more clear in the following
examples.

The custom model language supports three operators:

- `multiply_by` multiplies the speed value with a given number or expression
- `limit_to` limits the speed value to a given number or expression
- `do` lists sub-statements that are executed

#### `if` statements and the `multiply_by` operation

Let's start with a simple example using `multiply_by`:

```json
{
  "speed": [
    {
      "if": "road_class == MOTORWAY",
      "multiply_by": "0.5"
    }
  ]
}
```

This custom model reduces the speed of every road segment for which the `road_class` encoded value is `MOTORWAY` to
fifty percent of the default speed (the default speed is multiplied by `0.5`). Again, the default speed is the speed
that GraphHopper would normally use for the profile's vehicle. Note the `if` clause which means that the operation
(`multiply_by`) is only applied *if* the condition `road_class == MOTORWAY` is fulfilled for the edge under
consideration. The `==` indicates equality, i.e. the condition reads "the road_class equals MOTORWAY". If you're a bit
familiar with programming note that the condition (the value of the `if` key) is just a boolean condition in Java
language (other programming languages like C or JavaScript are very similar in this regard). A more complex condition
could look like this: `road_class == PRIMARY || road_class == TERTIARY` which uses the **or**
(`||`) operator and literally means "road_class equals PRIMARY or road_class equals TERTIARY".

There can be multiple such 'if statements' in the speed section, and they are evaluated from top to bottom:

```json
{
  "speed": [
    {
      "if": "road_class == MOTORWAY",
      "multiply_by": "0.5"
    },
    {
      "if": "road_class == PRIMARY || road_environment == TUNNEL",
      "multiply_by": "0.7"
    }
  ]
}
```

In this example the default speed of edges with `road_class == MOTORWAY` will be multiplied by `0.5`, the default speed
of edges with `road_class == PRIMARY` will be multiplied by `0.7` and for edges with both `road_class == MOTORWAY` and
`road_environment == TUNNEL` the default speed will be multiplied first by `0.5` and then by `0.7`. So overall the
default speed will be multiplied by `0.35`. For edges with `road_class == PRIMARY` and `road_environment == TUNNEL` we
only multiply by `0.7`, even though both parts of the second condition apply. It only matters whether the edge matches
the condition or not.

`road_class` and `road_environment` are categories of 'enum' type, i.e. their value can only be one of a fixed set of
values, like `MOTORWAY` for `road_class`.

Other categories like `get_off_bike` are of `boolean` type. They can be used as conditions directly, for example:

```json
{
  "speed": [
    {
      "if": "get_off_bike",
      "multiply_by": "0.6"
    }
  ]
}
```

which means that for edges with `get_off_bike==true` the speed factor will be `0.6`.

For categories/encoded values with numeric values, like `max_width` you should not use the `==` (equality) or `!=` (
inequality) operators, but the numerical comparison operators "bigger" `>`, "bigger or equals" `>=`, "smaller" `<`, or
"smaller or equals" `<=`, e.g.:

```json
{
  "speed": [
    {
      "if": "max_width < 2.5",
      "multiply_by": "0.8"
    }
  ]
}
``` 

which means that for all edges with `max_width` smaller than `2.5m` the speed is multiplied by `0.8`.

##### Country

Reduce speed for a country:

```json
{
  "speed": [
    {
      "if": "country == USA",
      "multiply_by": "0.9"
    }
  ]
}
```

You can also differentiate between the states:

```json
{
  "speed": [
    {
      "if": "state == US_CA",
      "multiply_by": "0.9"
    }
  ]
}
```

#### The `limit_to` operation

Besides the `multiply_by` operator there is also the `limit_to` operator. As the name suggests `limit_to` limits the
current value to the given value. Take this example:

```json
{
  "speed": [
    {
      "if": "road_class == MOTORWAY",
      "multiply_by": "0.8"
    },
    {
      "if": "surface == GRAVEL",
      "limit_to": "60"
    }
  ]
}
```

This implies that on all road segments with the `GRAVEL` value for `surface` the speed will be at most `60km/h`,
regardless of the default speed and the previous rules. So for a road segment with `road_class == MOTORWAY`,
`surface == GRAVEL` and default speed `100` the first statement reduces the speed from `100` to `80` and the second
statement further reduces the speed from `80` to `60`. If the `road_class` was `PRIMARY` and the default speed was `50`
the first rule would not apply and the second rule would do nothing, because limiting `50` to `60` still yields `50`.

A common use-case for the `limit_to` operation is the following pattern:

```json
{
  "speed": [
    {
      "if": "true",
      "limit_to": "90"
    }
  ]
}
```

which means that the speed is limited to `90km/h` for all road segments regardless of its properties. The condition
`true` is always fulfilled.

#### The `do` operation

The `do` operation allows multiple statements for an `if`, `else_if`, and `else` statement.
For example, for an `if` statement, it can be used as follows:

```json
{
  "if": "country == DEU",
  "do": [
    { "if": "road_class == PRIMARY", "multiply_by": "0.8" },
    { "if": "road_class == SECONDARY", "multiply_by": "0.7" }
  ]
}
```

And then the two nested statements under `do` are only executed if the expression `country == DEU` is true.

For `else` the `do` operation can be used in a similar way:

```json
[
  { "if": "max_speed > 70", "limit": "70" },
  { "else": "",
    "do":  [
      { "if": "road_class == PRIMARY", "multiply_by": "0.8" },
      { "if": "road_class == SECONDARY", "multiply_by": "0.7" }
    ]
  }
]
```

Further nesting is also possible:

```json
{
  "if": "country == DEU",
  "do": [
    {
      "if": "road_class == PRIMARY",
      "do": [
        { "if": "max_speed > 70", "multiply_by": "0.5" }
      ]
    }
  ]
}
```

#### `else` and `else_if` statements

The `else` statement allows you to define that some operations should be applied if an edge does **not** match a
condition. So this example:

```json
{
  "speed": [
    {
      "if": "road_class == MOTORWAY",
      "multiply_by": "0.5"
    },
    {
      "else": "",
      "limit_to": "50"
    }
  ]
}
```

means that for all edges with `road_class == MOTORWAY` we multiply the default speed by `0.5` and for all others we
limit the default speed to `50` (but never both).

In case you want to distinguish more than two cases (edges that match or match not a condition) you can use `else_if`
statements which are only evaluated in case the previous `if` or `else_if` statement did **not** match:

```json
{
  "speed": [
    {
      "if": "road_class == MOTORWAY",
      "multiply_by": "0.5"
    },
    {
      "else_if": "road_environment == TUNNEL",
      "limit_to": "70"
    },
    {
      "else": "",
      "multiply_by": "0.9"
    }
  ]
}
```

So if the first condition matches (`road_class == MOTORWAY`) the default speed is multiplied by `0.5`, but the other two
statements are ignored. Only if the first statement does not match (e.g. `road_class == PRIMARY`) the second statement
is even considered and only if it matches (`road_environment == TUNNEL`) the default speed is limited to 70. The last
operation (`multiply_by: 0.9`) is only applied if both previous conditions did not match.

`else` and `else_if` statements always require a preceding `if` or `else_if` statement. However, there can be multiple
'blocks' of subsequent `if/else_if/else` statements in the list of rules for `speed`.

`else_if` is useful for example in case you have multiple `multiply_by` operations, but you do not want that the speed
gets reduced by all of them. For the following model

```json
{
  "speed": [
    {
      "if": "road_class == MOTORWAY",
      "multiply_by": "0.5"
    },
    {
      "else_if": "road_environment == TUNNEL",
      "multiply_by": "0.8"
    }
  ]
}
```

only the first factor (`0.5`) will be applied even for road segments that fulfill both conditions.

#### `areas`

You can not only modify the speed of road segments based on properties, like we saw in the previous examples, but you
can also modify the speed of road segments based on their location. To do this you need to first add an area to the
`areas` section of the custom model. You can then use the "id" in the conditions of your `if/else/else_if` statements.

In the following example we multiply the speed of all edges in an area called `custom1` with `0.7` and also limit it
to `50km/h`. Note that each area's id needs to be prefixed with `in_`:

```json
{
  "speed": [
    {
      "if": "in_custom1",
      "multiply_by": "0.7"
    },
    {
      "if": "in_custom1",
      "limit_to": "50"
    }
  ],
  "areas": {
    "type": "FeatureCollection",
    "features": [{
      "type": "Feature",
      "id": "custom1",
      "properties": {},
      "geometry": {
        "type": "Polygon",
        "coordinates": [
          [
            [ 1.525, 42.511 ],
            [ 1.510, 42.503 ],
            [ 1.531, 42.495 ],
            [ 1.542, 42.505 ],
            [ 1.525, 42.511 ]
          ]
        ]
      }
    }]
  }
}
```

Areas are given in GeoJson format (FeatureCollection). Currently a member of this collection must be a `Feature` with a
geometry type `Polygon`. Note that the coordinates array of `Polygon` is an array of arrays that
each must describe a closed ring, i.e. the first point must be equal to the last, identical to the GeoJSON specs.
Each point is given as an array [longitude, latitude], so the coordinates array has three dimensions total.

Using the `areas` feature you can also block entire areas i.e. by multiplying the speed with `0`, but for this you
should rather use the `priority` section that we will explain next.

### Customizing `priority`

Make sure you read the introductory section of this document to learn what the `priority` factor means. In short it
allows similar modifications as `speed`, but instead of modifying the edge weights *and* travel times it will only
affect the edge weights. By default, the priority is `1` for every edge, so it does not affect the weight. However,
changing the priority of a road can yield a relative weight difference in comparison to other roads.

Customizing the `priority` works very much like changing the `speed`, so in case you did not read the section about
`speed` you should go back there and read it now. The only real difference is that there is no `limit_to` operator for
`priority`. As a quick reminder here is an example for priority:

```json
{
  "priority": [
    {
      "if": "road_class == MOTORWAY",
      "multiply_by": "0.5"
    },
    {
      "else_if": "road_class == SECONDARY",
      "multiply_by": "0.9"
    },
    {
      "if": "road_environment == TUNNEL",
      "multiply_by": "0.1"
    }
  ]
}
```

means that road segments with `road_class==MOTORWAY` and `road_environment==TUNNEL` get priority `0.5*0.1=0.05` and
those with `road_class==SECONDARY` and no TUNNEL, get priority `0.9` and so on.

Edges with lower priority values will be less likely part of the optimal route calculated by GraphHopper, higher values
mean that these road segments shall be preferred. If you do not want to state which road segments shall be avoided, but
rather which ones shall be preferred, you need to **decrease** the priority of others:

```json
{
  "priority": [
    {
      "if": "road_class != CYCLEWAY",
      "multiply_by": "0.8"
    }
  ]
}
```

means decreasing the priority for all road_classes *except* cycleways.

Just like we saw for `speed` you can also adjust the priority for road segments in a certain area. It works exactly the
same way:

```json
{
  "priority": [
    {
      "if": "in_custom1",
      "multiply_by": "0.7"
    }
  ]
}
```

To block an entire area set the priority value to `0`. You can even set the priority only for certain roads in an area
like this:

```json
{
  "priority": [
    {
      "if": "road_class == MOTORWAY && in_custom1",
      "multiply_by": "0.1"
    }
  ]
}
```

Some other useful encoded values to restrict access to certain roads depending on your vehicle dimensions are the
following:

```json
{
  "priority": [
    {
      "if": "max_width < 2.5",
      "multiply_by": "0"
    },
    {
      "if": "max_length < 10",
      "multiply_by": "0"
    },
    {
      "if": "max_weight < 3.5",
      "multiply_by": "0"
    }
  ]
}
```

which means that the priority for all road segments that allow a maximum vehicle width of `2.5m`, a maximum vehicle
length of `10m` or a maximum vehicle weight of `3.5tons`, or less, is zero, i.e. these "narrow" road segments are
blocked.

### The value expression

The value of `limit_to` or `multiply_by` is usually only a number but can be more complex expression like `max_speed`
or even something like `max_speed + 0.5`. In general one encoded value is accepted in combination with one or more 
operations with a number and the operator `+`, `*` and `-`.

This can be useful to reduce the speed of the base profile to a dynamic value. See e.g. the following example:

```json
{
  "speed": [
    { "if": "true", "limit_to": "max_speed * 0.9" }
  ]
}
```

This limits the speed on all roads to 90% of the maximum speed value if it exists.

Or you could use the following statements for a truck profile that needs a car-like speed but for faster roads the truck 
should be 10% slower and the maximum should be 100km/h:

```json
{
  "speed": [
    { "if": "true", "limit_to": "100" },
    { "if": "car_average_speed > 50", "limit_to": "car_average_speed * 0.9" },
    { "else": "", "limit_to": "car_average_speed" }
  ]
}
```

Note that the last `else` statement is optional if you use the `car` profile as base.

You can use a value expression also for `priority`, e.g. to pre-populated it based on a custom variable:

```json
{
  "priority": [
    { "if": "true", "limit_to": "my_precalculated_value" }
  ]
}
```

Note that when using a dynamic value like `my_precalculated_value` the maximum value correlates strongly with 
the response time of A-star routing requests (i.e. when CH and LM are disabled). This means that if you pick a
smaller or more narrow range, or if you can avoid them entirely, then these requests might get faster.

### Customizing `distance_influence`

We already explained the meaning of `distance_influence` in one of the previous sections. To specify its value simply
use the `distance_influence` property of the custom value like this:

```json
{
  "distance_influence": 100
}
``` 

If you do not use this property, GraphHopper will use the default value which is `70`.
