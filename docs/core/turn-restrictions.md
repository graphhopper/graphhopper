# How to work with Turn Restrictions

GraphHopper supports [turn restrictions](http://wiki.openstreetmap.org/wiki/Relation:restriction).
Turn restrictions are crucial for correct vehicle navigation and help to avoid forbidden turns.

[Without turn restrictions](https://graphhopper.com/maps/?point=23.1047%2C-82.44319&point=23.10544%2C-82.44316) (the turn is not allowed):

![turn without turn restrictions](./images/turn-restrictions-wrong.png)

[With turn restrictions](https://graphhopper.com/maps/?point=23.1047%2C-82.44319&point=23.10544%2C-82.44316&ch.disable=true):

![turn with turn restrictions](./images/turn-restrictions-correct.png)

Turn restrictions have to be enabled on a vehicle basis. To enable it for one vehicle add
`|turn_costs=true` in the config, for example: `graph.flag_encoders=car|turn_costs=true`.
Or when using the Java API directly you can either create the encoding manager like `EncodingManager.create("car|turn_costs=true")` or
`new EncodingManager.Builder().add(new CarFlagEncoder(5, 5, 1)` where the last parameter of `CarFlagEncoder` represents
the maximum turn costs (a value of 1 means the turn can either be legal or forbidden).
Turn restrictions are not available for every vehicle as they have low relevance
for some vehicles like `foot`. 
To enable turn restrictions when using the 'speed mode' additional graph preparation is required, because turn restrictions
require edge-based (vs. node-based) traversal of the graph. You have to configure the profiles for which the graph
preparation should be run using e.g. `profiles_ch`, just like when you use the 'speed mode' without turn restrictions.
The edge-based CH preparation will be chosen if you configure `turn_costs: true` for the profile you are referencing
in `profiles_ch`.
You can also specify a time penalty for taking u-turns (turning from one road back to the same road at a junction).
Note that this time-penalty only works reasonably when your weighting is time-based (like "fastest"). To use u-turn 
costs with speed mode you need to specify the time penalty for each u-turn (again in the profile configuration):
 `u_turn_costs: 60`. See `config-example.yml` for further details regarding these configurations. 
If you prepare multiple 'speed mode' profiles you have to specify which 
one to use at request time: Use the `edge_based=true/false` parameter to enforce edge-based or node-based routing and 
the `u_turn_costs` parameter to specify the u-turn costs (only needed if there are multiple edge-based 'speed mode'
profiles with different u-turn costs). To disable the 'speed mode' per request you can add `ch.disable=true` and choose
the value of `u_turn_costs` freely.

While OSM data only contains turn *restrictions*, the GraphHopper routing engine can also deal with turn *costs*, i.e.
you can specify custom turn costs for each turn at each junction.

Conditional turn restriction are supported. For example, the following no left turn restriction concerns only bus :

> type=restriction  
> restriction:bus=no_left_turn

Another example, using the *except* tag, means only *bicycle* are allowed to turn left:

> type=restriction  
> restriction=no_left_turn  
> except=bicycle

You can overwrite `FlagEncoder#acceptsTurnRelation` to change the default handling of turn restrictions in your customized vehicle profile.