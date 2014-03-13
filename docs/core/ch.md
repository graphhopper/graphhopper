# Contraction Hierarchies

CH is a post-import process which makes routing faster. 
In GraphHopper CH is enabled by default but can be easily disabled.

To make CH work in GraphHopper a LevelGraphStorage instead of the normal GraphStorage 
is necessary which allows to store shortcuts too.

After a graph is prepared it cannot be used for graph exploration anymore, this is
a limitation of the preparation and storage and is handled in issue #116.
Due to that limitation a special location index is necessary (LocationIndexTreeSC).

Also at the moment only one vehicle can be used if CH is enabled, see issue #111.

So, if you still need graph exploration for your LevelGraphStorage you can specify 
graphHopper.doPrepare(false) before you call importOrLoad, which avoids the CH preparation.
Then do your graph explorations or whatever and store the graph.
If you call importOrLoad next time without doPrepare(false) the CH-preparation will be done.

