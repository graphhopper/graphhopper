# Spatial Rules

Spatial rules allow you to create rules for certain areas. One famous example is that `highway=track` should be accessible 
in Austria, whereas it should be marked as `access=destination`. Other examples are different max speeds for different
countries. More information on different road rules can be found in the OSM wiki. The article about different 
[accessibilities](https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Access-Restrictions) and about different 
[speeds](https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed#Motorcar).

## Enabling Rules

I you have a working GraphHopper setup it is easy to enable Spatial Rules, **but they only work the DataFlagEncoder**.
We provide a set of approximate country borders, within the GraphHopper repository. If you need exact borders you can
get the exact borders from [here](https://github.com/datasets/geo-countries). Go to your `config.properties` and
uncommend the line: `spatial_rules.location` and point it to where your rules are. You need to re-import your graph after 
that.

## Creating Rules

Writing your own rules is simple. If you write a rule for a country that is not provided in GraphHopper yet, we'd love
if you would contribute your rules. You can have a look at the [GermanySpatialRule](https://github.com/graphhopper/graphhopper/blob/master/core/src/main/java/com/graphhopper/routing/util/spatialrules/countries/GermanySpatialRule.java).
Create something similar for your contry. After that you have to extend the [CountriesSpatialRuleFactory](https://github.com/graphhopper/graphhopper/blob/master/web/src/main/java/com/graphhopper/spatialrules/CountriesSpatialRuleFactory.java) 
to match your country code. 