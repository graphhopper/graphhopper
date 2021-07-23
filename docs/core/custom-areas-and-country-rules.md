# Custom Areas and Country Rules

GraphHopper lets you specify arbitrary (custom) areas using the `custom_areas.directory` setting in `config.yml`. During
import all areas that contain a given way will be available for all TagParsers and FlagEncoders. This way you can adjust
or override the corresponding `handleWayTags` methods to modify encoded values based on the areas that contain a given
edge.

As a special case of custom areas, GraphHopper automatically includes country borders and finds the country for every
imported OSM way. See the `countries.geojson` file for GraphHopper's country borders and the next section for some
details about this file. Just as for custom areas the country information will be available to all TagParsers and
FlagEncoders during import, and of course can also be used to modify encoded values. For example GraphHopper stores the
country information using the CountryParser and the Country encoded value for every edge. Add `country` to
`graph.encoded_values` in `config.yml` to enable this. To change encoded values like max_speed and access depending on
the country it is probably easier to use country rules (see below).

GraphHopper uses so-called country rules to modify the routing behavior depending on country-specific details. There is
a separate implementation of the `CountryRule` interface for every country (unless it has not been created yet). For
example the `AustriaCountryRule` changes the default accessibility for `highway=track` to `access=yes`, while
the `GermanyCountryRule` changes it to `access=destination`. More information about such country-specific rules can be
found in the OSM wiki
for [access restrictions](https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Access-Restrictions)
and [maximum speeds](https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed#Motorcar). Feel free to add
country rules for your country and contribute to GraphHopper!

# Note about the country rules used by GraphHopper

The `countries.geojson` file originates from Openstreetmap and has been downloaded via https://osm-boundaries.com

The GeoJSON was generated with https://www.mapshaper.org/ and can be recreated with these steps:

* download all country boundaries with admin level 2 (download about 1/3 of all countries at once to avoid download limit)
* import the GeoJSON files as separate layers with the options `detect line intersections`, `snap vertices` and the import option `combine-files`
* merge the layers into one using the console command `merge-layers`
* fix issues in the dataset using the console command `clean`
* simplify
  * enable `prevent shape removal`
  * select visvalingam / weighted area
  * use 2.0%
* repair intersections
* export as GeoJSON
