# vehicle parameter

The `vehicle` parameter was replaced by explicit statements in the custom model. So instead of:

```yml
profiles:
 - name: car
   vehicle: car
   custom_model: {
     "distance_influence": 10
   }
```

You write:

```yml
profiles:
 - name: car
   custom_model: {
     "priority": [
       { "if": "!car_access", "multiply_by": "0" }
     ],
     "speed": [
       { "if": "true", "limit_to": "car_average_speed" }
     ],
     "distance_influence": 10
   }
```

Or for vehicles with a priority encoded value you write:

```yml
profiles:
 - name: foot
   custom_model: {
     "priority": [
       { "if": "foot_access", "multiply_by": "foot_priority" },
       { "else": "", "multiply_by": "0" }
     ],
     "speed": [
       { "if": "true", "limit_to": "foot_average_speed" }
     ]
   }
```

This looks more verbose but for the standard vehicles we have created default custom model files. So instead of the above custom model for car you can also just write:

```yml
profiles:
 - name: car
   custom_model_files: [car.json]
```

With turn costs this looks now:

```yml
profiles:
 - name: car
   turn_costs:
      restrictions: [motorcar, motor_vehicle]
      u_turn_costs: 60
   custom_model_files: [car.json]
```


Furthermore the new approach is more flexible. A profile that previously required the special vehicle `roads` (to get unlimited priority and speed) always created the encoded values roads_access and roads_average_speed and used unnecessary memory and introduced unnecessary limitations.
Now these artificial encoded values are no longer necessary and you could write custom models even without any encoded values and only one unconditional or if-else block is necessary for `speed`:

```yml
profiles:
 - name: foot
   custom_model: {
     "priority": [
       { "if": "road_class == MOTORWAY", "multiply_by": "0" }
     ],
     "speed": [
       { "if": "true", "limit_to": "6" }
     ]
   }
```

# graph.encoded_values

All encoded values that are used in a custom models must be listed here.

If you used a property like block_private=false for e.g. the `car` vehicle, you can now use this property for the encoded value `car_access`: 

```
  graph.encoded_values: car_access|block_private=false
```

# shortest and fastest weighting

Both weightings were replaced by the custom model. Instead of `weighting: fastest` you now use the default custom weighting as
explained in the previous section.

Instead of `weighting: shortest` you now use a custom weighting with a high `distance_influence`:

```yml
profiles:
 - name: car
   custom_model: {
     "priority": [
       { "if": "!car_access", "multiply_by": "0" }
     ],
     "speed": [
       { "if": "true", "limit_to": "car_average_speed" }
     ],
     "distance_influence": 200
   }
```

# temporal conditional access restrictions

`car_access`, `bike_access` and `foot_access` do no longer include the conditional
access restrictions by default. If you want the old behaviour e.g. for car you need
to add the following statement in the `priority` section of your custom model:

```json
{ "if": "car_temporal_access == NO", "multiply_by": "0" }
```

Depending on the use case e.g. for foot it might make more sense to use the
new default and show the conditional restriction value via the new path details
`access_conditional`, `vehicle_conditional` etc. built from the OSM tags
`access:conditional`, `vehicle:conditional` etc.
See how we utilized this for [GraphHopper Maps](https://graphhopper.com/maps/?point=50.909136%2C14.213924&point=50.90918%2C14.213549&profile=foot)
with a separate route hint (icon below the route distance).