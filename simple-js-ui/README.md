Leaflet.FileLayer
=================

Loads local files (GeoJSON, GPX, KML) into the map using the [HTML5 FileReader API](http://caniuse.com/filereader), **without server call** !

* A simple map control
* The user can browse a file locally
* It is read locally (``FileReader``) and converted to GeoJSON
* And loaded as a layer eventually!

Check out the [demo](http://makinacorpus.github.com/Leaflet.FileLayer/) !

For GPX and KML files, it currently depends on [Tom MacWright's togeojson.js](https://github.com/tmcw/togeojson).

[![Build Status](https://travis-ci.org/makinacorpus/Leaflet.FileLayer.png?branch=gh-pages)](https://travis-ci.org/makinacorpus/Leaflet.FileLayer)

Usage
-----

```javascript
    var map = L.map('map').fitWorld();
    ...
    L.Control.fileLayerLoad({
        // See http://leafletjs.com/reference.html#geojson-options
        layerOptions: {style: {color:'red'}},
        // Add to map after loading (default: true) ?
        addToMap: true,
        // File size limit in kb (default: 1024) ?
        fileSizeLimit: 1024,
        // Restrict accepted file formats (default: .geojson, .kml, and .gpx) ?
        formats: [
            '.geojson',
            '.kml'
        ]
    }).addTo(map);
```

Events:

```javascript
    var control = L.Control.fileLayerLoad();
    control.loader.on('data:loaded', function (e) {
        // Add to map layer switcher
        layerswitcher.addOverlay(e.layer, e.filename);
    });
```

* **data:error** (error)

Changelog
---------

### 0.4.0 ###

* Support whitelist for file formats (thanks CJ Cenizal)

### 0.3.0 ###

* Add `data:error` event (thanks @joeybaker)
* Fix multiple uploads (thanks @joeybaker)
* Add `addToMap` option (thanks @joeybaker)

(* Did not release version 0.2 to prevent conflicts with Joey's fork. *)

### 0.1.0 ###

* Initial working version

Authors
-------

[![Makina Corpus](http://depot.makina-corpus.org/public/logo.gif)](http://makinacorpus.com)

Contributions

* Joey Baker http://byjoeybaker.com
