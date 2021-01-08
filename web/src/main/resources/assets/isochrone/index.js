/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

var menu = new Vue({
    el: '#menu',
    data: {
        layer: 'vector',
        showGraph: false,
        isochroneRadius: 600,
        useDeck: false,
        showSpt: false,
        isochronePoint: undefined
    },
    methods: {
        changeLayer: function (event) {
            this.showGraph = false;
            setGHMvtVisible(map, false);
            removeIsoLayers();
            setLayer(map, event.target.id);
        },
        toggleGHMvt: function (event) {
            setGHMvtVisible(map, event.target.checked);
        },
        updateIsochrone: function (event) {
            if (!this.isochronePoint)
                return;
            var coordinates = this.isochronePoint.split(',')
                .map(item => item.trim())
                .map(parseFloat);
            if (coordinates.length != 2) {
                console.error('invalid point: ' + this.isochronePoint);
            } else {
                _updateIsochrone({ lng: coordinates[1], lat: coordinates[0] });
            }
        }
    }
});
var mapTilerKey = 'yrAYvi6TTYgg9U5mBtiY';
var rasterStyle = {
    'version': 8,
    'sources': {
        'raster-tiles-source': {
            'type': 'raster',
            'tiles': [
                'https://a.tile.openstreetmap.de/{z}/{x}/{y}.png',
                'https://b.tile.openstreetmap.de/{z}/{x}/{y}.png',
                'https://c.tile.openstreetmap.de/{z}/{x}/{y}.png'
            ]
        }
    },
    'layers': [
        {
            'id': 'raster-tiles',
            'type': 'raster',
            'source': 'raster-tiles-source'
        }
    ]
};
var vectorStyle = 'https://api.maptiler.com/maps/basic/style.json?key=' + mapTilerKey;

var map = new mapboxgl.Map({
    container: 'map',
    style: vectorStyle,
    center: [13.4110450, 52.5214697],
    zoom: 9
});
map.on('style.load', function addGHMvt() {
    // add GraphHopper vector tiles of road network. this is also called when we change the style
    map.addSource('gh-mvt', {
        type: 'vector',
        tiles: ['/mvt/{z}/{x}/{y}.mvt?details=road_class']
    });
    map.addLayer({
        'id': 'gh',
        'type': 'line',
        'source': 'gh-mvt',
        'source-layer': 'roads',
        'paint': {
            'line-color': [
                'match',
                ['get', 'road_class'],
                'motorway', 'red',
                'primary', 'orange',
                'trunk', 'orange',
                'secondary', 'yellow',
                /*other*/ 'grey'
            ]
        },
        'layout': {
            'visibility': 'none'
        }
        // we make sure the map labels stay on top
    }, getFirstSymbolLayer(map));
});
map.on('click', function (e) {
    _updateIsochrone(e.lngLat);
});

function _updateIsochrone(lngLat) {
    menu.isochronePoint = lngLat.lat.toFixed(6) + "," + lngLat.lng.toFixed(6);
    if (menu.showSpt)
        fetchAndDrawSPT(lngLat);
    else
        fetchAndDrawIsoline(lngLat);
}

function fetchAndDrawSPT(point) {
    // fetch GraphHopper isochrone and draw on map
    var counter = 0;
    var coordinates = [];
    var radius = menu.isochroneRadius;
    Papa.parse("/spt?profile=car&point=" + point.lat + "," + point.lng + "&columns=prev_longitude,prev_latitude,longitude,latitude,distance,time&time_limit=" + radius, {
        download: true,
        worker: true,
        step: function (results) {
            var d = results.data;
            // skip the first line (column names) and the second (root node)
            if (counter > 1)
                coordinates.push([[parseFloat(d[0]), parseFloat(d[1])], [parseFloat(d[2]), parseFloat(d[3])]]);
            counter++;
        },
        complete: function () {
            var geojson = {
                'type': 'FeatureCollection',
                'features': [
                    {
                        'type': 'Feature',
                        'geometry': {
                            'type': 'MultiLineString',
                            'coordinates': coordinates
                        }
                    }
                ]
            };
            drawIsoLayer(geojson, coordinates);
        },
        error: function (e) {
            console.error('error when trying to show SPT', e);
        }
    });
}

function fetchAndDrawIsoline(point) {
    var radius = menu.isochroneRadius;
    fetch("/isochrone?profile=car&point=" + point.lat + "," + point.lng + "&time_limit=" + radius)
        .then(response => response.json())
        .then(data => {
            console.log('isoline took: ' + data.info.took + 'ms');
            // since we do not use the buckets parameter there is always just one polygon
            drawIsoLayer(data.polygons[0], undefined)
        })
        .catch(e => {
            console.error('error when trying to show isoline', e)
        });
}

function drawIsoLayer(geojson, coordinates) {
    removeIsoLayers();
    if (menu.useDeck) {
        var deckLayer = new deck.MapboxLayer({
            id: 'isochrone-deck-layer',
            // for some reason the deck.GeoJsonLayer does not work with our MultiLineString geojson we use for SPT => use LineLayer instead
            type: coordinates ? deck.LineLayer : deck.GeoJsonLayer,
            data: coordinates ? coordinates : geojson,
            getSourcePosition: coordinates ? d => d[0] : undefined,
            getTargetPosition: coordinates ? d => d[1] : undefined,
            getFillColor: [0, 0, 225, 100],
            getLineColor: [0, 0, 225],
            getColor: d => [0, 0, 225]
        });
        map.addLayer(deckLayer, getFirstSymbolLayer(map));
    } else {
        var source = map.getSource('isochrone');
        if (!source) {
            map.addSource('isochrone', {
                'type': 'geojson',
                'data': geojson
            });
        } else {
            source.setData(geojson);
        }
        map.addLayer({
            'id': 'isochrone-layer',
            'type': 'line',
            'source': 'isochrone',
            'paint': {
                'line-color': '#0000e1'
            }
        }, getFirstSymbolLayer(map));
    }
}

function removeIsoLayers() {
    if (map.getLayer('isochrone-layer')) {
        map.removeLayer('isochrone-layer');
    }
    if (map.getLayer('isochrone-deck-layer')) {
        map.removeLayer('isochrone-deck-layer');
    }
}

function setLayer(map, layerId) {
    if (layerId == 'vector') {
        map.setStyle(vectorStyle);
    } else if (layerId == 'raster') {
        map.setStyle(rasterStyle);
    }
}

function setGHMvtVisible(map, visible) {
    if (visible) {
        map.setLayoutProperty('gh', 'visibility', 'visible');
    } else {
        map.setLayoutProperty('gh', 'visibility', 'none');
    }
}

function getFirstSymbolLayer(map) {
    var layers = map.getStyle().layers;
    // Find the index of the first symbol layer in the map style
    for (var i = 0; i < layers.length; i++) {
        if (layers[i].type === 'symbol') {
            return layers[i].id;
        }
    }
}