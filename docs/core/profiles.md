# Profiles

GraphHopper lets you customize how different kinds of roads shall be prioritized during its route calculations. For
example when travelling long distances with a car you typically want to use the highway to minimize your travelling
time. However, if you are going by bike you certainly do not want to use the highway and rather take some shorter route,
use designated bike lanes and so on. GraphHopper provides built-in vehicle profiles that cover some standard use cases,
which can be modified by a custom model for fine-grained control over GraphHopper's road prioritization to
e.g. change the travelling speed for certain road types.

A profile is defined by a custom model and its turn restrictions. All profiles are specified
in the 'profiles' section of `config.yml` and there has to be at least one profile. Here is an example:

```yaml
profiles:
  - name: car
    custom_model_files: [car.json]
  - name: my_bike
    custom_model_files: [bike_elevation.json]
```

By choosing a custom model file GraphHopper determines the accessibility and a default travel speed for the different road types.

Another important profile setting is the `turn_costs` configuration. Use this to enable turn restrictions for each profile:

```yaml
profiles:
  - name: car
    turn_costs:
      restrictions: [motorcar, motor_vehicle]
    custom_model_files: [car.json]
```

You can learn more about this setting [here](./turn-restrictions.md)

The profile name is used to select the profile when executing routing queries. To do this use the `profile` request
parameter, for example `/route?point=49.5,11.1&profile=car` or `/route?point=49.5,11.1&profile=some_other_profile`.

## custom_model

You can adjust the cost function of GraphHopper's route calculations in much more detail by using 'custom_model'.
The profile builds on top of a 'base' vehicle from which the profile inherits the road accessibility
rules and default speeds for the different road types. However, you can specify a set of rules to
change these default values. For example, you can change the speed only for a certain type of road and much more:

```yaml
profiles:
  - name: my_custom_profile
    vehicle: car
    custom_model: {
      "speed": [
        {
          "if": "road_class == MOTORWAY",
          "multiply_by": "0.8"
        }
      ]               
    }
```

And instead of the custom_model entry you can also set a path to a custom model file using the
`custom_model_files` property with an optional `custom_models.directory`. This was used in the first
example above to modify the speed based on the elevation changes using the internal custom
model `bike_elevation.json`. You can find all internal custom models in the folder
`core/src/main/resources/com/graphhopper/custom_models` like `hike.json`, `truck.json`, `bus.json`,
`car4wd.json`, `motorcycle.json` or `curvature.json`.

You can leave the custom model empty with `custom_model: {}` or with `custom_model_files: []`.

All details about the custom model specification are explained in [the custom model documentation](./custom-models.md).

### Setting up Encoded Values

As explained [in the custom model documentation](./custom-models.md), custom models make use of
encoded values, which are often derived from the OSM way tags. All built-in encoded values are defined in
[`DefaultEncodedValueFactory.java`](../../core/src/main/java/com/graphhopper/routing/ev/DefaultEncodedValueFactory.java)
but only encoded values specified in the `graph.encoded_values` field in the `config.yml` will be available in the graph
storage.

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

For more information read about the different modes [here](routing.md).

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

So far we talked only about profiles that are configured on the server side in `config.yml`.
However, with flex- and hybrid mode it is even possible to define the custom model on a per-request basis. This enables
you to perform route calculations using custom models that you did not anticipate when setting up the server.

To use this feature you need to send the custom model along with your request in JSON format.
The syntax for the custom model is the same as for server-side custom models (but using
JSON not YAML notation, see above). The routing request has the same format as for `/route`, but with an
additional `custom_model` field that contains the custom model. You still need to set the `profile` parameter
for your request.

Now you might be wondering which custom model is used, because there is one set for the route request, but there is also
the one given for the profile that we specify via the `profile` parameter. The answer is "both" as the two custom models
are merged into one. The two custom models are merged by appending all expressions of the query custom model to the
server-side custom model. The `distance_influence` of the query custom model overwrites the one from the server-side 
custom model *unless* it is not specified.

So say your routing request (POST /route) looks like this:

```json
{
  "points": [
    [ 11.58199, 50.0141 ],
    [ 11.5865,  50.0095 ]
  ],
  "profile": "my_custom_car",
  "custom_model": {
    "speed": [
      {
        "if": "road_class == MOTORWAY",
        "multiply_by": "0.8"
      },
      {
        "else": "",
        "multiply_by": "0.9"
      }
    ],
    "priority": [
      {
        "if": "road_environment == TUNNEL",
        "multiply_by": "0.95"
      }
    ],
    "distance_influence": 0.7
  }
}
```

where in `config.yml` we have:

```yaml
custom_models.directory: path/to/my/custom/models
profiles:
  - name: my_custom_car
    vehicle: car
    custom_model_files: [my_custom_car.json]
```

and `my_custom_car.json` looks like this:

```json
{
  "speed": [
    {
      "if": "surface == GRAVEL",
      "limit_to": "100"
    }
  ]
}
```

then the resulting custom model used for your request will look like this:

```json
{
  "speed": [
    {
      "if": "surface == GRAVEL",
      "limit_to": "100"
    },
    {
      "if": "road_class == MOTORWAY",
      "multiply_by": "0.8"
    },
    {
      "else": "",
      "multiply_by": "0.9"
    }
  ],
  "priority": [
    {
      "if": "road_environment == TUNNEL",
      "multiply_by": "0.95"
    }
  ],
  "distance_influence": 0.7
}
```

And if the hybrid mode is used (using Landmarks not just Dijkstra or A*) the merge process has to ensure that all
weights resulting from the merged custom model are equal or larger than those of the base profile that was used during
the preparation process (this is necessary to maintain the optimality of the underlying routing algorithm). This leads
to two limitations for the hybrid mode:
* for the query custom model all values of `multiply_by` need to be within the range of `[0, 1]` otherwise an error will be thrown
* the `distance_influence` of the query custom model must not be smaller than the existing one.
