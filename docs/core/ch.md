# Contraction Hierarchies

CH is a post-import process which makes routing faster. 
In GraphHopper CH is enabled by default but can be easily disabled.

To make CH work in GraphHopper a LevelGraphStorage instead of the normal GraphStorage 
is necessary which allows to store shortcuts too.

A prepared graph can also be used for normal graph traversal IF you use graph.getBaseGraph().

If CH is enabled multiple vehicles will work but only one works in speed-up mode and is faster, see issue #111.
