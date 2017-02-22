# ALT is an algorithm based on A*, landmarks and triangle inequality

To avoid confusion with alternative route search we talk not about ALT but about
the 'landmarks' algorithm. The landmarks algorithm is like CH a post-import 
process which makes routing faster. It is disable by default. To enable it
set e.g. `prepare.lm.weightings=fastest` in the config.properties.

The interesting part is that we just have to set a special landmarks WeightApproximator
for (bidirectional) A* and are done. It is important that although a weight
**approximation** is used the landmarks algorithm is still not heuristic per
default. You can make it faster and more heuristical behaving when you set
the epsilon parameter lower than 1.
