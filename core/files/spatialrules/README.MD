The Polygons in this folder originate from Openstreetmap and have been downloaded via https://osm-boundaries.com

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

The rules are taken from the OSM Wiki: https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Access-Restrictions
