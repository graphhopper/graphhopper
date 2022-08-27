var mainTemplate = require('./main-template.js');
var tileLayers = require('./config/tileLayers.js');
var translate = require('./translate.js');

var routingLayer;
var map;
var menuStart;
var menuIntermediate;
var menuEnd;
var elevationControl = null;
var fullscreenControl = null;

// Items added in every contextmenu.
var defaultContextmenuItems;

var expandElevationDiagram = true;

// called if window changes or before map is created
function adjustMapSize() {
    var mapDiv = $("#map");

    // ensure that map does never exceed current window width so that no scrollbars are triggered leading to a smaller total width
    mapDiv.width(100);
    if(fullscreenControl) {
        fullscreenControl.updateClass();
        if(fullscreenControl.isFullscreen()) {
            mapDiv.height( $(window).height() ).width( $(window).width() );
            $("#input").hide();
            map.invalidateSize();
            return;
         }
    }

    var height = $(window).height();
    height = height < 100? 100 : height;

    // to avoid height==0 for input_header etc ensure that it is not hidden
    $("#input").show();

    // reduce info size depending on how the height of the input_header is and reserve space for footer
    var instructionInfoMaxHeight = height - 60 - $("#input_header").height() - $("#footer").height();
    var tabHeight = $("#route_result_tabs li").height();
    instructionInfoMaxHeight -= isNaN(tabHeight)? 0 : tabHeight;
    var routeDescHeight = $(".route_description").height();
    instructionInfoMaxHeight -= isNaN(routeDescHeight)? 0 : routeDescHeight;
    $(".instructions_info").css("max-height", instructionInfoMaxHeight);
    var width = $(window).width() - $("#input").width() - 10;
    mapDiv.width(width).height(height);
    // somehow this does not work: map.invalidateSize();
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

    var full = false;
    L.Control.Fullscreen = L.Control.extend({
        isFullscreen: function() {
            return full;
        },
        updateClass: function() {
            var container = this.getContainer();
            L.DomUtil.setClass(container, full ? 'fullscreen-reverse-btn' : 'fullscreen-btn');
            L.DomUtil.addClass(container, 'leaflet-control');
        },
        onAdd: function (map) {
            var container = L.DomUtil.create('div', 'fullscreen-btn');
            container.title = "Fullscreen Mode";
            container.onmousedown = function(event) {
                full = !full;
                adjustMapSize();
            };

            return container;
        }
    });
    fullscreenControl = new L.Control.Fullscreen({ position: 'topleft'}).addTo(map);

    new L.Control.loading().addTo(map);

    if(tileLayers.getOverlays())
        L.control.layers(tileLayers.getAvailableTileLayers(), tileLayers.getOverlays()).addTo(map);
    else
        L.control.layers(tileLayers.getAvailableTileLayers()).addTo(map);

    map.on('baselayerchange', function (a) {
        if (a.name) {
            tileLayers.activeLayerName = a.name;
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

    // Don't show the elevation graph on small displays
    if(window.innerWidth < 900 || window.innerHeight < 400){
        expandElevationDiagram = false;
    }

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

module.exports.addElevation = function (geoJsonFeature, details, selectedDetail, detailSelected) {

    // TODO no option to switch to miles yet
    var options = {
        width: 600,
        height: 280,
        margins: {
            top: 10,
            right: 30,
            bottom: 55,
            left: 50
        },
        xTicks: 3,
        yTicks: 3,
        position: "bottomright",
        expand: expandElevationDiagram,
        expandCallback: function (expand) {
            expandElevationDiagram = expand;
        },
        mappings: {},
        selectedAttributeIdx: 0,
        chooseSelectionCallback: detailSelected
    };

    var GHFeatureCollection = [];

    var detailIdx = -1;
    var selectedDetailIdx = -1;
    for (var detailKey in details) {
        detailIdx++;
        if (detailKey === selectedDetail)
            selectedDetailIdx = detailIdx;
        GHFeatureCollection.push(sliceFeatureCollection(details[detailKey], detailKey, geoJsonFeature));
        options.mappings[detailKey] = getColorMapping(details[detailKey]);
    }

    // always show elevation and slope
    {
        geoJsonFeature.properties.attributeType = "elevation";
        var elevationCollection = {
            "type": "FeatureCollection",
            "features": [geoJsonFeature],
            "properties": {
                "Creator": "GraphHopper",
                "records": 1,
                "summary": "Elevation"
            }
        };
        detailIdx++;
        if (selectedDetail === 'Elevation')
            selectedDetailIdx = detailIdx;
        GHFeatureCollection.push(elevationCollection);
        // Use a fixed color for elevation
        options.mappings['Elevation'] = {'elevation': {text: 'Elevation [m]', color: '#27ce49'}};

        var slopeFeatures = [];
        for (var i = 0; i < geoJsonFeature.geometry.coordinates.length - 1; i++) {
            var from = geoJsonFeature.geometry.coordinates[i];
            var to = geoJsonFeature.geometry.coordinates[i + 1];
            var distance = getDist(from, to);
            var slope = 100.0 * (to[2] - from[2]) / distance;
            slopeFeatures.push({
                "type": "Feature",
                "geometry": {
                    "type": "LineString",
                    "coordinates": [from, to]
                },
                "properties": {
                    "attributeType": slope
                }
            })
        }
        var slopeCollection = {
            "type": "FeatureCollection",
            "features": slopeFeatures,
            "properties": {
                "records": slopeFeatures.length,
                "summary": "Slope"
            }
        };
        detailIdx++;
        if (selectedDetail === 'Slope')
            selectedDetailIdx = detailIdx;
        GHFeatureCollection.push(slopeCollection);
        options.mappings["Slope"] = slope2color;

        // tower slope: slope between tower nodes: use edge_id detail to find tower nodes
        if (details['edge_id']) {
            var detail = details['edge_id'];
            var towerSlopeFeatures = [];
            var points = geoJsonFeature.geometry.coordinates;
            for (var i = 0; i < detail.length; i++) {
                var featurePoints = points.slice(detail[i][0], detail[i][1] + 1);
                var from = featurePoints[0];
                var to = featurePoints[featurePoints.length - 1];
                var distance = getDist(from, to);
                var slope = 100.0 * (to[2] - from[2]) / distance;
                // for the elevations in tower slope diagram we do linear interpolation between the tower nodes. note that
                // we cannot simply leave out the pillar nodes, because otherwise the total distance would change
                var tmpDistance = 0;
                for (var j = 0; j < featurePoints.length; j++) {
                    var factor = tmpDistance / distance;
                    var ele = from[2] + factor * (to[2] - from[2]);
                    if (j === featurePoints.length - 1)
                        // there seem to be some small rounding errors which lead to ugly little spikes in the diagram,
                        // so for the last point use the elevation of the to point directly
                        ele = to[2];
                    featurePoints[j] = [featurePoints[j][0], featurePoints[j][1], ele];
                    if (j < featurePoints.length - 1)
                        tmpDistance += getDist(featurePoints[j], featurePoints[j + 1]);
                }
                towerSlopeFeatures.push({
                    "type": "Feature",
                    "geometry": {
                        "type": "LineString",
                        "coordinates": featurePoints
                    },
                    "properties": {
                        "attributeType": slope
                    }
                });
            }
            var towerSlopeCollection = {
                "type": "FeatureCollection",
                "features": towerSlopeFeatures,
                "properties": {
                    "records": towerSlopeFeatures.length,
                    "summary": "Towerslope"
                }
            };
            detailIdx++;
            if (selectedDetail === 'Towerslope')
                selectedDetailIdx = detailIdx;
            GHFeatureCollection.push(towerSlopeCollection);
            options.mappings["Towerslope"] = slope2color;
        }
    }

    if (selectedDetailIdx >= 0)
        options.selectedAttributeIdx = selectedDetailIdx;

    if (elevationControl === null) {
        elevationControl = L.control.heightgraph(options);
        elevationControl.addTo(map);
    }

    elevationControl.addData(GHFeatureCollection);
};

function getDist(p, q) {
    return L.latLng(p[1], p[0]).distanceTo(L.latLng(q[1], q[0]));
}

function slope2color(slope) {
    var colorMin = [0, 153, 247];
    var colorMax = [241, 23, 18];
    var absSlope = Math.abs(slope);
    absSlope = Math.min(25, absSlope);
    var factor = absSlope / 25;
    var color = [];
    for (var i = 0; i < 3; i++)
        color.push(colorMin[i] + factor * (colorMax[i] - colorMin[i]));
    return {
        text: slope.toFixed(2),
        color: 'rgb(' + color[0] + ', ' + color[1] + ', ' + color[2] + ')'
    }
}

function getColorMapping(detail) {
    var detailInfo = analyzeDetail(detail);
    if (detailInfo.numeric === true && detailInfo.minVal !== detailInfo.maxVal) {
        // for numeric details we use a color gradient, taken from here:  https://uigradients.com/#Superman
        var colorMin = [0, 153, 247];
        var colorMax = [241, 23, 18];
        return function (data) {
            var factor = (data - detailInfo.minVal) / (detailInfo.maxVal - detailInfo.minVal);
            var color = [];
            for (var i = 0; i < 3; i++)
                color.push(colorMin[i] + factor * (colorMax[i] - colorMin[i]));
            return {
                'text': data,
                'color': 'rgb(' + color[0] + ', ' + color[1] + ', ' + color[2] + ')'
            }
        }
    } else {
        // for discrete encoded values we use discrete colors
        var values = detail.map(function (d) {
            return d[2]
        });
        return function (data) {
            // we choose a color-blind friendly palette from here: https://personal.sron.nl/~pault/#sec:qualitative
            // see also this: https://thenode.biologists.com/data-visualization-with-flying-colors/research/
            var palette = ['#332288', '#88ccee', '#44aa99', '#117733', '#999933', '#ddcc77', '#cc6677', '#882255', '#aa4499'];
            var missingColor = '#dddddd';
            var index = values.indexOf(data) % palette.length;
            var color = data === 'missing' || data === 'unclassified' || data === 'Undefined'
                ? missingColor
                : palette[index];
            return {
                'text': data,
                'color': color
            }
        }
    }
}

function analyzeDetail(detail) {
    // we check if all detail values are numeric
    var numbers = new Set();
    var minVal, maxVal;
    var numberCount = 0;
    for (var i = 0; i < detail.length; i++) {
        var val = detail[i][2];
        if (typeof val === "number") {
            if (!minVal) minVal = val;
            if (!maxVal) maxVal = val;
            numbers.add(val);
            numberCount++;
            minVal = Math.min(val, minVal);
            maxVal = Math.max(val, maxVal);
        }
    }
    return {
        numeric: numberCount === detail.length,
        minVal: minVal,
        maxVal: maxVal
    }
}

function sliceFeatureCollection(detail, detailKey, geoJsonFeature){

    var feature = {
      "type": "FeatureCollection",
      "features": [],
      "properties": {
          "Creator": "GraphHopper",
          "summary": detailKey,
          "records": detail.length
      }
    };

    var points = geoJsonFeature.geometry.coordinates;
    for (var i = 0; i < detail.length; i++) {
        var detailObj = detail[i];
        var from = detailObj[0];
        // It's important to +1
        // Array.slice is exclusive the to element and the feature needs to include the to coordinate
        var to = detailObj[1] + 1;
        var value = detailObj[2];
        if (typeof value === "undefined" || value === null)
            value = "Undefined";
        var tmpPoints = points.slice(from, to);

        feature.features.push({
          "type": "Feature",
          "geometry": {
              "type": "LineString",
              "coordinates": tmpPoints
          },
          "properties": {
              "attributeType": value
          }
        });
    }

    return feature;
}

module.exports.clearElevation = function () {
    if (elevationControl){
        if(elevationControl._markedSegments){
            map.removeLayer(elevationControl._markedSegments);
        }
        // TODO this part is not really nice to remove and readd it to the map everytime
        elevationControl.remove();
        elevationControl = null;
    }
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
        autoPan: true,
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
