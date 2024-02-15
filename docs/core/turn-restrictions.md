# How to work with Turn Restrictions

GraphHopper supports [turn restrictions](http://wiki.openstreetmap.org/wiki/Relation:restriction).
Turn restrictions are crucial for correct vehicle navigation and help to avoid forbidden turns.

Without turn restrictions the route will look like:

![turn without turn restrictions](./images/turn-restrictions-wrong.png)

[With turn restrictions](https://graphhopper.com/maps/?point=23.1047%2C-82.44319&point=23.10544%2C-82.44316) it is:

![turn with turn restrictions](./images/turn-restrictions-correct.png)

To enable turn restrictions for a profile set the turn_costs configuration like this:


```yaml
profiles:
  - name: car
    turn_costs:
      restrictions: [motorcar, motor_vehicle]
    ...
```

When using the Java API you can create the encoding manager like:

```java
DecimalEncodedValue speedEnc = VehicleSpeed.create("car", 5, 5, true);
DecimalEncodedValue turnCostEnc = TurnCost.create("car", maxTurnCosts);
EncodingManager encodingManager = new EncodingManager.Builder().add(speedEnc).addTurnCostEncodedValue(turnCostEnc).build();
```

Where maxTurnCosts=1 means the turn can either be legal or forbidden.

To enable turn restrictions when using the 'speed mode' additional graph preparation is required, because turn restrictions
require edge-based (vs. node-based) traversal of the graph. You have to configure the profiles for which the graph
preparation should be run using e.g. `profiles_ch`, just like when you use the 'speed mode' without turn restrictions.

You can also specify a time penalty for taking u-turns in the profile (turning from one road back to the same road at a junction).
Note, that this time-penalty only works reasonably when your weighting is time-based (like "fastest"). To use u-turn
costs with speed mode you need to specify the time penalty for each u-turn in the turn_costs configuration:
`u_turn_costs: 60`. See `config-example.yml` for further details regarding these configurations.

To disable the 'speed mode' per request you can add `ch.disable=true` and choose the value of `u_turn_costs` freely in the request.

While OSM data only contains turn *restrictions*, the GraphHopper routing engine can also deal with turn *costs*, i.e.
you can specify custom turn costs for each turn at each junction. See [this experimental branch](https://github.com/graphhopper/graphhopper/tree/turn_costs_calc).

Conditional turn restriction are supported. For example, the following no left turn restriction concerns only bus:

> type=restriction  
> restriction:bus=no_left_turn

Another example, using the *except* tag, means only *bicycle* are allowed to turn left:

> type=restriction  
> restriction=no_left_turn  
> except=bicycle

