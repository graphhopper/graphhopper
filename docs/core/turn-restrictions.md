# How to work with Turn Restrictions

GraphHopper supports [turn restrictions](http://wiki.openstreetmap.org/wiki/Relation:restriction).
Currently, it is not possible to use turn restrictions with the speed mode (contraction hierarchies),
but it's possible using the flexible mode (landmarks) and without any post-import optimization.
Turn restrictions are crucial for correct vehicle navigation and help to avoid forbidden turn.

Without turn restrictions (the turn is not allowed):

![turn without turn restrictions](./images/turn-restrictions-wrong.png)

With turn restrictions:

![turn with turn restrictions](./images/turn-restrictions-correct.png)

Turn restrictions have to be enabled on a vehicle basis. To enable it for one vehicle add
`|turn_costs=true` in the config, for example: `graph.flag_encoders=car|turn_costs=true`.
Turn restrictions are not available for every vehicle as they have low relevance
for some vehicles like `foot`.