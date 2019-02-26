# How to work with Turn Restrictions

GraphHopper supports [turn restrictions](http://wiki.openstreetmap.org/wiki/Relation:restriction).
Turn restrictions are crucial for correct vehicle navigation and help to avoid forbidden turns.

[Without turn restrictions](https://graphhopper.com/maps/?point=23.1047%2C-82.44319&point=23.10544%2C-82.44316) (the turn is not allowed):

![turn without turn restrictions](./images/turn-restrictions-wrong.png)

[With turn restrictions](https://graphhopper.com/maps/?point=23.1047%2C-82.44319&point=23.10544%2C-82.44316&ch.disable=true):

![turn with turn restrictions](./images/turn-restrictions-correct.png)

Turn restrictions have to be enabled on a vehicle basis. To enable it for one vehicle add
`|turn_costs=true` in the config, for example: `graph.flag_encoders=car|turn_costs=true`.
Turn restrictions are not available for every vehicle as they have low relevance
for some vehicles like `foot`. 
To enable turn restrictions when using the 'speed mode' additional graph preparation is required, because turn restrictions
require edge-based (vs. node-based) traversal of the graph. First you have to set the weightings for which the graph 
preparation should be run using e.g. `prepare.ch.weightings=fastest`, just like when you use the 'speed mode' without 
turn restrictions. Additionally you need to set `prepare.ch.turn_costs` to `edge_or_node` or `edge_and_node`
(see `config-example.yml` for further details). At request time you need to add `edge_based=true` as URL parameter to 
enable turn restricted routing and to disable the 'speed mode' per request you can add `ch.disable=true`.

While OSM data only contains turn *restrictions*, the GraphHopper routing engine can also deal with turn *costs*, i.e.
you can specify custom turn costs for each turn at each junction.