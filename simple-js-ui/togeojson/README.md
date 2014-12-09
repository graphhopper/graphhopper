[![build status](https://secure.travis-ci.org/mapbox/togeojson.png)](http://travis-ci.org/mapbox/togeojson) [![Coverage Status](https://coveralls.io/repos/mapbox/togeojson/badge.png)](https://coveralls.io/r/mapbox/togeojson)

# Convert KML and GPX to GeoJSON.

This converts [KML](https://developers.google.com/kml/documentation/) & [GPX](http://www.topografix.com/gpx.asp)
to
[GeoJSON](http://www.geojson.org/), in a browser or with [nodejs](http://nodejs.org/).

It is

* Dependency-free
* Tiny
* Written in vanilla javascript that's jshint-friendly
* Tested

It is not

* Concerned about ugly extensions to KML
* Concerned with having an 'internal format' of its own

Want to use this with [Leaflet](http://leafletjs.com/)? Try [leaflet-omnivore](https://github.com/mapbox/leaflet-omnivore)!

## API

### `toGeoJSON.kml(doc)`

Convert a KML document to GeoJSON. The first argument, `doc`, must be a KML
document as an XML DOM - not as a string. You can get this using jQuery's default
`.ajax` function or using a bare XMLHttpRequest with the `.response` property
holding an XML DOM.

The output is a Javascript object of GeoJSON data. You can convert it to a string
with [JSON.stringify](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/JSON/stringify)
or use it directly in libraries like [mapbox.js](http://www.mapbox.com/mapbox.js/).

### `toGeoJSON.gpx(doc)`

Convert a GPX document to GeoJSON. The first argument, `doc`, must be a GPX
document as an XML DOM - not as a string. You can get this using jQuery's default
`.ajax` function or using a bare XMLHttpRequest with the `.response` property
holding an XML DOM.

The output is a Javascript object of GeoJSON data, same as `.kml` outputs.


## Using it as a console utility

Install it into your path with `npm install -g togeojson`.

```
~> togeojson file.kml > file.geojson
```

## Using it as a nodejs library

Install it into your project with `npm install --save togeojson`.

```javascript
// using togeojson in nodejs

var tj = require('togeojson'),
    fs = require('fs'),
    // node doesn't have xml parsing or a dom. use jsdom
    jsdom = require('jsdom').jsdom;

var kml = jsdom(fs.readFileSync('foo.kml', 'utf8'));

var converted = tj.kml(kml);

var converted_with_styles = tj.kml(kml, { styles: true });
```

## Using it as a browser library

Download it into your project like

    wget https://raw.github.com/tmcw/togeojson/gh-pages/togeojson.js

```html
<script src='jquery.js'></script>
<script src='togeojson.js'></script>
<script>
$.ajax('test/data/linestring.kml').done(function(xml) {
    console.log(toGeoJSON.kml(xml));
});
</script>
```

toGeoJSON doesn't include AJAX - you can use [jQuery](http://jquery.com/) for
just AJAX.

## KML

Supported:

* Point
* Polygon
* LineString
* name & description
* ExtendedData
* SimpleData
* MultiGeometry -> GeometryCollection
* Styles with hashing
* Tracks & MultiTracks with `gx:coords`, including altitude
* [TimeSpan](https://developers.google.com/kml/documentation/kmlreference#timespan)

Not supported yet:

* NetworkLinks
* GroundOverlays

## GPX

Supported:

* Line Paths
* 'name', 'desc', 'author', 'copyright', 'link', 'time', 'keywords' tags

## FAQ

### What is hashing?

KML's style system isn't semantic: a typical document made through official tools
(read Google) has hundreds of identical styles. So, togeojson does its best to
make this into something usable, by taking a quick hash of each style and exposing
`styleUrl` and `styleHash` to users. This lets you work backwards from the awful
representation and build your own styles or derive data based on the classes
chosen.

Implied here is that this does not try to represent all data contained in KML
styles.

## Protips:

Have a string of XML and need an XML DOM?

```js
var dom = (new DOMParser()).parseFromString(xmlStr, 'text/xml');
```
