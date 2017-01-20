var mainTemplate = require('./main-template.js');
var tileLayers = require('./config/tileLayers.js');
var translate = require('./translate.js');

var routingLayer;
var map;
var menuStart;
var menuIntermediate;
var menuEnd;
var elevationControl = null;

// Items added in every contextmenu.
var defaultContextmenuItems;

// called if window changes or before map is created
function adjustMapSize() {
    var mapDiv = $("#map");
    var width = $(window).width() - 280;
    if (width < 400) {
        width = 400;
        mapDiv.attr("style", "position: relative; float: right;");
    } else {
        mapDiv.attr("style", "position: absolute; right: 0;");
    }
    var height = $(window).height();
    if (height < 500)
        height = 500;

    mapDiv.width(width).height(height);
    $("#input").height(height);

    // console.log("adjustMapSize " + height + "x" + width);

    // reduce info size depending on how heigh the input_header is and reserve space for footer
    var instructionInfoMaxHeight = height - 60 -
            $("#input_header").height() - $("#footer").height() - $(".route_description").height();
    var tabHeight = $("#route_result_tabs li").height();
    if (!isNaN(tabHeight))
        instructionInfoMaxHeight -= tabHeight;
    $(".instructions_info").css("max-height", instructionInfoMaxHeight);

    // reduce info size depending on how high the input_header is and reserve space for footer
    // $("#info").css("height", height - $("#input_header").height() - 100);
}

function initMap(bounds, setStartCoord, setIntermediateCoord, setEndCoord, selectLayer, useMiles) {
    adjustMapSize();
    // console.log("init map at " + JSON.stringify(bounds));

    var defaultLayer = tileLayers.selectLayer(selectLayer);

    defaultContextmenuItems = [{
        separator: true,
        index: 10
    }, {
        text: translate.tr('show_coords'),
        callback: function (e) {
            alert(e.latlng.lat + "," + e.latlng.lng);
        },
        index: 11
    }, {
        text: translate.tr('center_map'),
        callback: function (e) {
            map.panTo(e.latlng);
        },
        index: 12
    }];

    // default
    map = L.map('map', {
        layers: [defaultLayer],
        minZoom: 2,
        // zoomSnap: 0,  // allow fractional zoom levels
        contextmenu: true,
        contextmenuItems: defaultContextmenuItems,
        zoomControl: false,
        loadingControl: false
    });

    var _startItem = {
        text: translate.tr('set_start'),
        icon: './img/marker-small-green.png',
        callback: setStartCoord,
        index: 0
    };
    var _intItem = {
        text: translate.tr('set_intermediate'),
        icon: './img/marker-small-blue.png',
        callback: setIntermediateCoord,
        disabled: true,
        index: 1
    };
    var _endItem = {
        text: translate.tr('set_end'),
        icon: './img/marker-small-red.png',
        callback: setEndCoord,
        index: 2
    };
    menuStart = map.contextmenu.insertItem(_startItem, _startItem.index);
    menuIntermediate = map.contextmenu.insertItem(_intItem, _intItem.index);
    menuEnd = map.contextmenu.insertItem(_endItem, _endItem.index);

    var zoomControl = new L.Control.Zoom({
        position: 'topleft',
        zoomInTitle: translate.tr('zoom_in'),
        zoomOutTitle: translate.tr('zoom_out')
    }).addTo(map);

    new L.Control.loading({
        zoomControl: zoomControl
    }).addTo(map);

    L.control.layers(tileLayers.getAvailableTileLayers()/*, overlays*/).addTo(map);

    map.on('baselayerchange', function (a) {
        if (a.name) {
            tileLayers.activeLayerName = a.name;
            $("#export-link a").attr('href', function (i, v) {
                return v.replace(/(layer=)([\w\s]+)/, '$1' + tileLayers.activeLayerName);
            });
        }
    });

    scaleControl = L.control.scale(useMiles ? {
        metric: false
    } : {
        imperial: false
    }).addTo(map);

    map.fitBounds(new L.LatLngBounds(new L.LatLng(bounds.minLat, bounds.minLon),
            new L.LatLng(bounds.maxLat, bounds.maxLon)));

    //if (isProduction())
    //    map.setView(new L.LatLng(0, 0), 2);

    map.attributionControl.setPrefix(false);

    var myStyle = {
        color: 'black',
        weight: 2,
        opacity: 0.3
    };
    var geoJson = {
        type: "Feature",
        geometry: {
            type: "LineString",
            coordinates: [
                [bounds.minLon, bounds.minLat],
                [bounds.maxLon, bounds.minLat],
                [bounds.maxLon, bounds.maxLat],
                [bounds.minLon, bounds.maxLat],
                [bounds.minLon, bounds.minLat]
            ]
        }
    };

    if (bounds.initialized)
        L.geoJson(geoJson, {
            style: myStyle
        }).addTo(map);

    routingLayer = L.geoJson().addTo(map);

    routingLayer.options = {
        // use style provided by the 'properties' entry of the geojson added by addDataToRoutingLayer
        style: function (feature) {
            return feature.properties && feature.properties.style;
        },
        contextmenu: true,
        contextmenuItems: defaultContextmenuItems.concat([{
                text: translate.tr('route'),
                disabled: true,
                index: 0
            }, {
                text: translate.tr('set_intermediate'),
                icon: './img/marker-small-blue.png',
                callback: setIntermediateCoord,
                index: 1
            }]),
        contextmenuInheritItems: false
    };
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

module.exports.getSubLayers = function(name) {
    var subLayers = routingLayer.getLayers();
    return subLayers.filter(function(sl) {
        return sl.feature && sl.feature.properties && sl.feature.properties.name === name;
    });
};

module.exports.addDataToRoutingLayer = function (geoJsonFeature) {
    routingLayer.addData(geoJsonFeature);
};

module.exports.eachLayer = function (callback) {
    routingLayer.eachLayer(callback);
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
    map.fitBounds(bounds, {
        padding: [42, 42]
    });
};

module.exports.removeLayerFromMap = function (layer) {
    map.removeLayer(layer);
};

module.exports.focus = focus;
module.exports.initMap = initMap;
module.exports.adjustMapSize = adjustMapSize;

module.exports.addElevation = function (geoJsonFeature, useMiles) {
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
                left: 60
            },
            useHeightIndicator: true, //if false a marker is drawn at map position
            interpolation: "linear", //see https://github.com/mbostock/d3/wiki/SVG-Shapes#wiki-area_interpolate
            hoverNumber: {
                decimalsX: 2, //decimals on distance (in km or mi)
                decimalsY: 0, //decimals on height (in m or ft)
                formatter: undefined //custom formatter function may be injected
            },
            xTicks: undefined, //number of ticks in x axis, calculated by default according to width
            yTicks: undefined, //number of ticks on y axis, calculated by default according to height
            collapsed: false    //collapsed mode, show chart on click or mouseover
        });
        elevationControl.addTo(map);
    }
    elevationControl.options.imperial = useMiles;
    elevationControl.addData(geoJsonFeature);
};

module.exports.clearElevation = function () {
    if (elevationControl)
        elevationControl.clear();
};

module.exports.getMap = function () {
    return map;
};

module.exports.updateScale = function (useMiles) {
    if (scaleControl === null) {
        return;
    }
    scaleControl.remove();
    var options = useMiles ? {metric: false} : {imperial: false};
    scaleControl = L.control.scale(options).addTo(map);
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
        contextmenuItems: defaultContextmenuItems.concat([{
                text: translate.tr("marker") + ' ' + ((toFrom === FROM) ?
                        translate.tr("start_label") : ((toFrom === TO) ?
                        translate.tr("end_label") : translate.tr("intermediate_label") + ' ' + index)),
                disabled: true,
                index: 0
            }, {
                text: translate.tr((toFrom !== TO) ? "set_end" : "set_start"),
                icon: (toFrom !== TO) ? './img/marker-small-red.png' : './img/marker-small-green.png',
                callback: (toFrom !== TO) ? setToEnd : setToStart,
                index: 2
            }, {
                text: translate.tr("delete_from_route"),
                callback: deleteCoord,
                disabled: (toFrom !== -1 && ghRequest.route.size() === 2) ? true : false, // prevent to and from
                index: 3
            }]),
        contextmenuInheritItems: false
    }).addTo(routingLayer).bindPopup(((toFrom === FROM) ?
            translate.tr("start_label") : ((toFrom === TO) ?
            translate.tr("end_label") : translate.tr("intermediate_label") + ' ' + index)));
};
