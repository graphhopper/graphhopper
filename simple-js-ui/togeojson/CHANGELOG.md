## 0.8.0

* Support for the `gx:Track` and `gx:MultiTrack` extensions to GPX

## 0.7.0

* GPX conversion creates MultiLineString geometries when given multiple track segments.

## 0.6.0

* Support for [simplestyle spec](https://github.com/mapbox/simplestyle-spec) 1.1.0
* Tests in [tape](https://github.com/substack/tape) rather than mocha/expect.js

## 0.5.0

* Elevation values along linestrings and in points for GPX are now parsed
  correctly into `Z` coordinate values in GeoJSON.

## 0.4.2

* No longer bundles xmldom for browsers

## 0.3.1

* Stricter check for browser versus browserify when requiring `xmldom`

## 0.3.0

* Support for pipes and streams

```sh
echo "LineString(0 0, 10 10)" | wellknown | tokml | togeojson > map.geojson
```

## 0.2.0

* Improve documentation
* Make style hashing on by default, remove options object

## 0.1.1

* Fix global leak.

## 0.1.0

* Comments
* GPX Support

## 0.0.1

* Support GeometryCollections as themselves.
