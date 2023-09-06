# Profiles

GraphHopper lets you customize how different kinds of roads shall be prioritized during its route calculations. For
example when travelling long distances with a car you typically want to use the highway to minimize your travelling
time. However, if you are going by bike you certainly do not want to use the highway and rather take some shorter route,
use designated bike lanes and so on. GraphHopper provides built-in vehicle types that cover some standard use cases. They
can be used with a few different weightings like the 'fastest' weighting that chooses the fastest route (minimum
travelling time), or the 'shortest' weighting that chooses the shortest route (minimum travelling distance). The
selection of a vehicle and weighting is called 'profile', and we refer to these built-in choices as 'standard profiles'
here. For more flexibility there is a special kind of weighting, called 'custom' weighting that allows for fine-grained
control over GraphHopper's road prioritization. Such custom profiles still use one of the built-in vehicles, but you can
modify, e.g. the travelling speed for certain road types. Both types of profiles, the standard ones and custom profiles,
are explained in the following.

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
    weighting: fastest
```

The vehicle field must correspond to one of GraphHopper's built-in vehicle types:

- foot
- wheelchair
- bike
- racingbike
- mtb
- car

By choosing a vehicle GraphHopper determines the accessibility and a default travel speed for the different road types.

The weighting determines the 'cost function' for the route calculation and must match one of the following built-in
weightings:

- fastest (minimum travel time)
- short_fastest (yields a compromise between short and fast routes)
- custom (enables custom profiles, see the next section)

Another important profile setting is `turn_costs: true/false`. Use this to enable turn restrictions for each profile. 
You can learn more about this setting [here](./turn-restrictions.md)

The profile name is used to select the profile when executing routing queries. To do this use the `profile` request
parameter, for example `/route?point=49.5,11.1&profile=car` or `/route?point=49.5,11.1&profile=some_other_profile`.

## Custom Profiles

You can adjust the cost function of GraphHopper's route calculations in much more detail by using so called 'custom'
profiles. Every custom profile builds on top of a 'base' vehicle from which the profile inherits the road accessibility
rules and default speeds for the different road types. However, you can specify a set of rules to change these default
values. For example, you can change the speed only for a certain type of road (and much more).

Custom profiles are specified in the server-side config.yml file like this:

```yaml
profiles:
  - name: my_custom_profile
    vehicle: car
    weighting: custom
    custom_model: {
      "speed": [
        {
          "if": "road_class == MOTORWAY",
          "multiply_by": "0.8"
        }
      ]               
    }
```

The name and vehicle fields are the same as for standard profiles and the vehicle field is used as the 'base' vehicle
for the custom profile. The weighting must be always set to `custom` for custom profiles. The custom model itself goes
into the `custom_model` property. Alternatively, you can also set a path to a custom model file using the
`custom_models.directory` and `custom_model_files` properties.

Using custom profiles for your routing requests works just the same way as for standard profiles. Simply add
`profile=my_custom_profile` as request parameter to your routing request.

All details about the custom model specification are explained [here](./custom-models.md)

### Setting up Encoded Values

As explained [here](./custom-models.md), custom models make use of the encoded values, which are often derived from the
OSM way tags. All built-in encoded values are defined in
[`DefaultEncodedValueFactory.java`](../../core/src/main/java/com/graphhopper/routing/ev/DefaultEncodedValueFactory.java)
but only categories specified with `graph.encoded_values` field in the `config.yml` will be available in the graph
storage. To find out about all the built-in values of an encoded value you can also take a look at the corresponding
Java files like [`RoadClass.java`](../../core/src/main/java/com/graphhopper/routing/ev/RoadClass.java). You can also
check which encoded values are available for your server using the `/info` endpoint.

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

To use this feature you need to send the custom model along with your request in JSON format.
The syntax for the custom model is the same as for server-side custom models (but using
JSON not YAML notation, see above). The routing request has the same format as for `/route`, but with an
additional `custom_model` field that contains the custom model. You still need to set the `profile` parameter for your request
and the given profile must be a custom profile.

Now you might be wondering which custom model is used, because there is one set for the route request, but there is also
the one given for the profile that we specify via the `profile` parameter. The answer is "both" as the two custom models
are merged into one. The two custom models are merged by appending all expressions of the query custom model to the
server-side custom model. The `distance_influence` of the query custom model overwrites the one from the server-side 
custom model *unless* it is not specified.

And if the hybrid mode is used (using Landmarks not just Dijkstra or A*) the merge process has to ensure that all 
weights resulting from the merged custom model are equal or larger than those of the base profile that was used during 
the preparation process (this is necessary to maintain the optimality of the underlying routing algorithm). This leads 
to two limitations while merging:
* for the query custom model all values of `multiply_by` need to be within the range of `[0, 1]` otherwise an error will be thrown
* the `distance_influence` of the query custom model must not be smaller than the existing one.

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
    weighting: custom
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

You do not necessarily need to define a proper custom model on the server side, but you can also use the special
value `custom_model_files: []` or `custom_model: {}`, which means an empty custom model containing no
statements will be used on the server-side. This way you can leave it entirely up to the user/client how the custom
model shall look like.


