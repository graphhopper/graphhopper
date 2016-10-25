# Contraction Hierarchies

CH is a post-import process which makes routing faster. 
In GraphHopper CH is enabled by default but can be easily disabled.

To make CH work in GraphHopper an additional logic is added to GraphHoppperStorage (CHGraphImpl) 
which allows to store shortcuts too.

A prepared graph can also be used for normal graph traversal IF you use chGraph.getBaseGraph().

If CH is enabled multiple vehicles will work.
