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

For the world wide case we limit the routing in a certain way for landmarks.
You can only route within the EU, Africa and Asia. The other algorithms are
not affected from this limitation though. For landmarks this is important
as we store the weight approximation in a short (two bytes) and for large distances
more bytes would be necessary but e.g. for the world wide case this would
mean several GB per weighting.

Furthermore this short has the problem that smaller distances cannot be
differentiated sometimes which could lead to very slightly suboptimal routes.
If just small areas (country or city) are imported this can be avoided via setting a different
rounding factor.