var mainTemplate = require('./main-template.js');
var tileLayers = require('./config/tileLayers.js');

var routingLayer;
var map;
var menuStart;
var menuIntermediate;
var menuEnd;
var elevationControl = null;


function adjustMapSize() {
    var mapDiv = $("#map");
    var width = $(window).width() - 295;
    if (width < 400) {
        width = 290;
        mapDiv.attr("style", "position: relative; float: right;");
    } else {
        mapDiv.attr("style", "position: absolute; right: 0;");
    }
    var height = $(window).height();
    mapDiv.width(width).height(height);
    $("#input").height(height);
    // reduce info size depending on how high the input_header is and reserve space for footer
    $("#info").css("max-height", height - $("#input_header").height() - 58);
}

function initMap(bounds, setStartCoord, setIntermediateCoord, setEndCoord, selectLayer) {
    adjustMapSize();
    log("init map at " + JSON.stringify(bounds));

    var defaultLayer = tileLayers.selectLayer(selectLayer);

    // default
    map = L.map('map', {
        layers: [defaultLayer],
        contextmenu: true,
        contextmenuWidth: 145,
        contextmenuItems: [{
                separator: true,
                index: 3,
                state: ['set_default']
            }, {
                text: 'Show coordinates',
                callback: function (e) {
                    alert(e.latlng.lat + "," + e.latlng.lng);
                },
                index: 4,
                state: [1, 2, 3]
            }, {
                text: 'Center map here',
                callback: function (e) {
                    map.panTo(e.latlng);
                },
                index: 5,
                state: [1, 2, 3]
            }],
        zoomControl: false,
        loadingControl: false
    });


    var _startItem = {
        text: 'Set as start',
        callback: setStartCoord,
        disabled: false,
        index: 0
    };
    var _intItem = {
        text: 'Set intermediate',
        callback: setIntermediateCoord,
        disabled: true,
        index: 1
    };
    var _endItem = {
        text: 'Set as end',
        callback: setEndCoord,
        disabled: false,
        index: 2
    };
    menuStart = map.contextmenu.insertItem(_startItem, _startItem.index);
    menuIntermediate = map.contextmenu.insertItem(_intItem, _intItem.index);
    menuEnd = map.contextmenu.insertItem(_endItem, _endItem.index);

    var zoomControl = new L.Control.Zoom({position: 'topleft'}).addTo(map);

    new L.Control.loading({
        zoomControl: zoomControl
    }).addTo(map);

    map.contextmenu.addSet({
        name: 'markers',
        state: 2
    });

    map.contextmenu.addSet({
        name: 'path',
        state: 3
    });

    L.control.layers(tileLayers.getAvailableTileLayers()/*, overlays*/).addTo(map);

    map.on('baselayerchange', function (a) {
        if (a.name) {
            tileLayers.activeLayerName = a.name;
            $("#export-link a").attr('href', function (i, v) {
                return v.replace(/(layer=)([\w\s]+)/, '$1' + tileLayers.activeLayerName);
            });
        }
    });

    L.control.scale().addTo(map);

    map.fitBounds(new L.LatLngBounds(new L.LatLng(bounds.minLat, bounds.minLon),
            new L.LatLng(bounds.maxLat, bounds.maxLon)));

    //if (isProduction())
    //    map.setView(new L.LatLng(0, 0), 2);

    map.attributionControl.setPrefix('');

    var myStyle = {
        "color": 'black',
        "weight": 2,
        "opacity": 0.3
    };
    var geoJson = {
        "type": "Feature",
        "geometry": {
            "type": "LineString",
            "coordinates": [
                [bounds.minLon, bounds.minLat],
                [bounds.maxLon, bounds.minLat],
                [bounds.maxLon, bounds.maxLat],
                [bounds.minLon, bounds.maxLat],
                [bounds.minLon, bounds.minLat]]
        }
    };

    if (bounds.initialized)
        L.geoJson(geoJson, {
            "style": myStyle
        }).addTo(map);

    routingLayer = L.geoJson().addTo(map);
    routingLayer.options = {
        style: {color: "#00cc33", "weight": 5, "opacity": 0.6}, // route color and style
        contextmenu: true,
        contextmenuItems: [{
                text: 'Route ',
                disabled: true,
                index: 0,
                state: 3
            }, {
                text: 'Set intermediate',
                callback: setIntermediateCoord,
                index: 1,
                state: 3
            }, {
                separator: true,
                index: 2,
                state: 3
            }],
        contextmenuAtiveState: 3
    };
    /*
     routingLayer.options = {style: {color: "#1F40C4", "weight": 5, "opacity": 0.6}, onEachFeature: function (feature, layer) {
     layer.on('contextmenu', function (e) {
     alert('The GeoJSON layer has been clicked');
     });
     }}; // route color and style
     */
}

function focus(coord, zoom, index) {
    if (coord.lat && coord.lng) {
        if (!zoom)
            zoom = 11;
        routingLayer.clearLayers();
        map.setView(new L.LatLng(coord.lat, coord.lng), zoom);
        mainTemplate.setFlag(coord, index);
    }
}

module.exports.clearLayers = function () {
    routingLayer.clearLayers();
};

module.exports.getRoutingLayer = function () {
    return routingLayer;
};

module.exports.addDataToRoutingLayer = function (geoJsonFeature) {
    routingLayer.addData(geoJsonFeature);
};

module.exports.setDisabledForMapsContextMenu = function (entry, value) {
    if (entry === 'start')
        map.contextmenu.setDisabled(menuStart, value);
    if (entry === 'end')
        map.contextmenu.setDisabled(menuEnd, value);
    if (entry === 'intermediate')
        map.contextmenu.setDisabled(menuIntermediate, value);
};

module.exports.fitMapToBounds = function (bounds) {
    map.fitBounds(bounds);
};

module.exports.removeLayerFromMap = function (layer) {
    map.removeLayer(layer);
};

module.exports.focus = focus;
module.exports.initMap = initMap;
module.exports.adjustMapSize = adjustMapSize;

module.exports.addElevation = function (geoJsonFeature) {
    if (elevationControl === null) {
        elevationControl = L.control.elevation({
            position: "bottomright",
            theme: "white-theme", //default: lime-theme
            width: 450,
            height: 125,
            margins: {
                top: 10,
                right: 20,
                bottom: 30,
                left: 50
            },
            useHeightIndicator: true, //if false a marker is drawn at map position
            interpolation: "linear", //see https://github.com/mbostock/d3/wiki/SVG-Shapes#wiki-area_interpolate
            hoverNumber: {
                decimalsX: 2, //decimals on distance (always in km)
                decimalsY: 0, //decimals on height (always in m)
                formatter: undefined //custom formatter function may be injected
            },
            xTicks: undefined, //number of ticks in x axis, calculated by default according to width
            yTicks: undefined, //number of ticks on y axis, calculated by default according to height
            collapsed: false    //collapsed mode, show chart on click or mouseover
        });
        elevationControl.addTo(map);
    }

    elevationControl.addData(geoJsonFeature);
};

module.exports.clearElevation = function () {
    if (elevationControl)
        elevationControl.clear();
};

module.exports.getMap = function () {
    return map;
};

var FROM = 'from', TO = 'to';
function getToFrom(index, ghRequest) {
    if (index === 0)
        return FROM;
    else if (index === (ghRequest.route.size() - 1))
        return TO;
    return -1;
}

var iconFrom = L.icon({
    iconUrl: './img/marker-icon-green.png',
    shadowSize: [50, 64],
    shadowAnchor: [4, 62],
    iconAnchor: [12, 40]
});

var iconTo = L.icon({
    iconUrl: './img/marker-icon-red.png',
    shadowSize: [50, 64],
    shadowAnchor: [4, 62],
    iconAnchor: [12, 40]
});

module.exports.createMarker = function (index, coord, setToEnd, setToStart, deleteCoord, ghRequest) {
    var toFrom = getToFrom(index, ghRequest);
    return L.marker([coord.lat, coord.lng], {
        icon: ((toFrom === FROM) ? iconFrom : ((toFrom === TO) ? iconTo : new L.NumberedDivIcon({number: index}))),
        draggable: true,
        contextmenu: true,
        contextmenuItems: [{
                text: 'Marker ' + ((toFrom === FROM) ?
                        'Start' : ((toFrom === TO) ? 'End' : 'Intermediate ' + index)),
                disabled: true,
                index: 0,
                state: 2
            }, {
                text: 'Set as ' + ((toFrom !== TO) ? 'End' : 'Start'),
                callback: (toFrom !== TO) ? setToEnd : setToStart,
                index: 2,
                state: 2
            }, {
                text: 'Delete from Route',
                callback: deleteCoord,
                index: 3,
                state: 2,
                disabled: (toFrom !== -1 && ghRequest.route.size() === 2) ? true : false // prevent to and from
            }, {
                separator: true,
                index: 4,
                state: 2
            }],
        contextmenuAtiveState: 2
    }).addTo(routingLayer).bindPopup(((toFrom === FROM) ?
            'Start' : ((toFrom === TO) ? 'End' : 'Intermediate ' + index)));
};
