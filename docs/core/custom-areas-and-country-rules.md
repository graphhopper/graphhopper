# Custom Areas and Country Rules

GraphHopper lets you specify arbitrary (custom) areas using the `custom_areas.directory` setting in `config.yml`. During
import all areas that contain a given way will be available for all TagParsers. This way you can adjust
or override the corresponding `handleWayTags` methods to modify encoded values based on the areas that contain a given
edge.

As a special case of custom areas, GraphHopper automatically includes country borders and finds the country for every
imported OSM way. See the `countries.geojson` file for GraphHopper's country borders and the next section for some
details about this file. Just as for custom areas the country information will be available to all TagParsers during 
import, and of course can also be used to modify encoded values. For example GraphHopper stores the
country information using the CountryParser and the Country encoded value for every edge. Add `country` to
`graph.encoded_values` in `config.yml` to enable this. 

Another special case is max_speed for which country-dependent values will be used when 
1. `max_speed_calculator.enabled: true` is set, 
2. `graph.urban_density.threads` is set to at least 1 and 
3. the `country` encoded value is added.

GraphHopper uses so-called country rules to modify the routing behavior depending on country-specific details. There is
a separate implementation of the `CountryRule` interface for every country (unless it has not been created yet). For
example the `AustriaCountryRule` changes the default accessibility for `highway=track` to `access=yes`, while
the `GermanyCountryRule` changes it to `access=destination`. More information about such country-specific rules can be
found in the OSM wiki
for [access restrictions](https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Access-Restrictions)
and [maximum speeds](https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed#Motorcar). Feel free to add
country rules for your country and contribute to GraphHopper!

# Note about the country rules used by GraphHopper

The `countries.geojson` file originates from json - see [this PR](https://github.com/graphhopper/graphhopper/pull/2658).
