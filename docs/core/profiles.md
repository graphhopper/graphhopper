# Profiles

TODO

examples:

```yaml
profiles:
  - name: car
    vehicle: car # use a previously defined flag encoder
    weighting: fastest
    turn_costs: true
  - name: car_custom
    vehicle: car
    weighting: custom

profiles_ch:
  - profile: car

```

## Content custom_model_file

```yaml
  profile: car_custom       # use a previously defined custom profile
  max_speed_fallback: 100
  priority: { road_class: { primary: 0.5 } }
```

### Root entries

Other root entries, other than 'max_speed_fallback' are:

  * `vehicle_weight` in tons
  * `vehicle_width` in meter
  * `vehicle_height` in meter
  * `vehicle_length` in meter
  * `distance_influence`
  * `priority`
  * `max_speed`
  * `speed_factor`

The entries 'priority', 'speed_factor' and 'max_speed' are again maps where the encoded values defined above like
`road_class` or `surface` can be used.

### Available encoded values

country, get_off_bike (boolean, see cargo_bike.yml), hazmat, hazmat_tunnel, hazmat_water, road_access, road_class,
road_class_link, road_environment, roundabout, bike_network, foot_network, surface, toll,
track_type.

The values for these encoded values like e.g. `primary` or `secondary` for 'road_class' can be easily determined 
in the web UI (click flex and hover the cursor under 'road_class') or in the
`/info` endpoint.