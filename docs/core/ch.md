# Contraction Hierarchies

CH is a post-import process which makes routing faster. 
You can enable this when you add the profile to `profiles_ch` in the config.yml

To make CH work in GraphHopper an additional shortcut storage and logic is
implemented.

A prepared graph can also be used for normal graph traversal IF you use chGraph.getBaseGraph().

Details about the edge-based version of CH, that also allows taking into account turn costs and restrictions can be found [here](./edge-based-ch.md).
