var Flatpickr = require('flatpickr');
require('flatpickr/dist/l10n');

var L = require('leaflet');
require('leaflet-contextmenu');
require('leaflet-loading');
require('leaflet.heightgraph');
var moment = require('moment');
require('./lib/leaflet_numbered_markers.js');

global.jQuery = require('jquery');
global.$ = global.jQuery;
require('./lib/jquery-ui-custom-1.12.0.min.js');
require('./lib/jquery.history.js');
require('./lib/jquery.autocomplete.js');

var ghenv = require("./config/options.js").options;
console.log(ghenv.environment);

var GHInput = require('./graphhopper/GHInput.js');
var GHRequest = require('./graphhopper/GHRequest.js');
var host = ghenv.routing.host;
if (!host) {
    if (location.port === '') {
        host = location.protocol + '//' + location.hostname;
    } else {
        host = location.protocol + '//' + location.hostname + ":" + location.port;
    }
}

var AutoComplete = require('./autocomplete.js');
if (ghenv.environment === 'development') {
    var autocomplete = AutoComplete.prototype.createStub();
} else {
    var autocomplete = new AutoComplete(ghenv.geocoding.host, ghenv.geocoding.api_key);
}

var mapLayer = require('./map.js');
var nominatim = require('./nominatim.js');
var routeManipulation = require('./routeManipulation.js');
var gpxExport = require('./gpxexport.js');
var messages = require('./messages.js');
var translate = require('./translate.js');
var customModelEditor = require('custom-model-editor/dist/index.js');

var format = require('./tools/format.js');
var urlTools = require('./tools/url.js');
var tileLayers = require('./config/tileLayers.js');
if(ghenv.with_tiles)
   tileLayers.enableVectorTiles();

var debug = false;
var ghRequest = new GHRequest(host, ghenv.routing.api_key);
var bounds = {};
var metaVersionInfo;

// usage: log('inside coolFunc',this,arguments);
// http://paulirish.com/2009/log-a-lightweight-wrapper-for-consolelog/
if (global.window) {
    window.log = function () {
        log.history = log.history || [];   // store logs to an array for reference
        log.history.push(arguments);
        if (this.console && debug) {
            console.log(Array.prototype.slice.call(arguments));
        }
    };
}

$(document).ready(function (e) {
    // fixing cross domain support e.g in Opera
    jQuery.support.cors = true;

    gpxExport.addGpxExport(ghRequest);
    // we start without encoded values, they will be loaded later
    var cmEditor = customModelEditor.create({}, function (element) {
        $("#custom-model-editor").append(element);
    });

    cmEditor.validListener = function(valid) {
        $("#custom-model-search-button").prop('disabled', !valid);
    };
    ghRequest.cmEditor = cmEditor;
    ghRequest.cmEditorActive = false;
    var toggleCustomModelBox = function(sendRoute) {
        $("#custom-model-box").toggle();
        ghRequest.cmEditorActive = !ghRequest.cmEditorActive;
        // avoid default action, so use a different search button
        $("#searchButton").toggle();
        mapLayer.adjustMapSize();
        cmEditor.cm.refresh();
        cmEditor.cm.focus();
        cmEditor.cm.setCursor(cmEditor.cm.lineCount());
        if (sendRoute)
            sendCustomData();
    };
    $("#custom-model-button").click(function() {
        toggleCustomModelBox(true);
        // we only show the gpx button when the custom model box is closed, because the gpx export does not work for custom model routes (#2635)
        $("#gpxExportButton").toggle();
        $("#gpx_dialog").dialog('close');
    });
    function showCustomModelExample() {
        cmEditor.value =
            "{"
            + "\n \"speed\": ["
            + "\n  {"
            + "\n   \"if\": \"road_class == MOTORWAY\","
            + "\n   \"multiply_by\": \"0.8\""
            + "\n  }"
            + "\n ],"
            + "\n \"priority\": ["
            + "\n  {"
            + "\n   \"if\": \"road_environment == TUNNEL\","
            + "\n   \"multiply_by\": \"0.5\""
            + "\n  },"
            + "\n  {"
            + "\n   \"if\": \"max_weight < 3\","
            + "\n   \"multiply_by\": \"0.0\""
            + "\n  }"
            + "\n ]"
            + "\n}";
        cmEditor.cm.focus();
        cmEditor.cm.setCursor(0);
        cmEditor.cm.execCommand('selectAll');
        cmEditor.cm.refresh();
    }
    $("#custom-model-example").click(function () {
        showCustomModelExample();
        return false;
    });

    $("#export-link").click(function (e) {
        try {
          e.preventDefault();
          var url = location.href;
          if(url.indexOf("?") > 0)
            url = url.substring(0, url.indexOf("?")) + ghRequest.createHistoryURL() + "&layer=" + encodeURIComponent(tileLayers.activeLayerName);
          if(ghRequest.cmEditorActive) {
            var text = cmEditor.value.replaceAll("&","%26");
            url += "&custom_model=" + new URLSearchParams(JSON.stringify(JSON.parse(text))).toString();
          }
          navigator.clipboard.writeText(url).then(() => { alert('Link copied to clipboard'); });
        } catch(e) { console.warn(e); }
    });

    var sendCustomData = function () {
        ghRequest.ignoreCustomErrors = false;
        mySubmit();
    };

    var sendCustomDataIgnoreErrors = function () {
        ghRequest.ignoreCustomErrors = true;
        mySubmit();
    }

    cmEditor.setExtraKey('Ctrl-Enter', sendCustomDataIgnoreErrors);
    $("#custom-model-search-button").click(sendCustomData);

    if (isProduction())
        $('#hosting').show();

    var History = window.History;
    if (History.enabled) {
        History.Adapter.bind(window, 'statechange', function () {
            // No need for workaround?
            // Chrome and Safari always emit a popstate event on page load, but Firefox doesn’t
            // https://github.com/defunkt/jquery-pjax/issues/143#issuecomment-6194330

            var state = History.getState();
            initFromParams(state.data, true);
        });
    }

    $('#locationform').submit(function (e) {
        // no page reload
        e.preventDefault();
        mySubmit();
    });

    var urlParams = urlTools.parseUrlWithHisto();

    var customModelJSON = urlParams.custom_model;
    if(customModelJSON) {
        toggleCustomModelBox(false);
        cmEditor.value = customModelJSON; // if json parsing fails we still have the partial custom model in the box
        try {
            var tmpObj = JSON.parse(customModelJSON);
            cmEditor.value = JSON.stringify(tmpObj, null, 2);
        } catch(e) {
            console.warn('cannot pretty print custom model: ' + e);
        }

    } else {
        // todo: the idea was to highlight everything so if we start typing the example is overwritten. But unfortunately
        // this does not work. And not even sure this is so useful?
        showCustomModelExample();
    }

    $.when(ghRequest.fetchTranslationMap(urlParams.locale), ghRequest.getInfo())
            .then(function (arg1, arg2) {
                // init translation retrieved from first call (fetchTranslationMap)
                var translations = arg1[0];
                autocomplete.setLocale(translations.locale);
                ghRequest.setLocale(translations.locale);
                translate.init(translations);

                // init bounding box from getInfo result
                var json = arg2[0];
                var tmp = json.bbox;
                bounds.initialized = true;
                bounds.minLon = tmp[0];
                bounds.minLat = tmp[1];
                bounds.maxLon = tmp[2];
                bounds.maxLat = tmp[3];
                nominatim.setBounds(bounds);
                var profilesDiv = $("#profiles");

                function createButton(profile, hide) {
                    var vehicle = profile.vehicle;
                    var profileName = profile.name;
                    var button = $("<button class='vehicle-btn' title='" + profileDisplayName(profileName) + "'/>");
                    if (hide)
                        button.hide();

                    button.attr('id', profileName);
                    button.html("<img src='img/" + vehicle.toLowerCase() + ".png' alt='" + profileDisplayName(profileName) + "'></img>");
                    button.click(function () {
                        ghRequest.setProfile(profileName);
                        ghRequest.removeLegacyParameters();
                        resolveAll();
                        if (ghRequest.route.isResolved())
                          routeLatLng(ghRequest);
                    });
                    return button;
                }

                if (json.profiles) {
                    var profiles = json.profiles;
                    // first sort alphabetically to maintain a consistent order, then move certain elements to front/back
                    profiles.sort(function (a, b) {
                         return a.vehicle < b.vehicle ? -1 : a.vehicle > b.vehicle ? 1 : 0;
                    });
                    // the desired order is car,foot,bike,<others>,motorcycle
                    var firstVehicles = ["car", "foot", "bike"];
                    for (var i=firstVehicles.length-1; i>=0; --i) {
                        profiles = moveToFront(profiles, function(p) { return p.vehicle === firstVehicles[i]; });
                    }
                    var lastVehicles = ["mtb", "motorcycle"];
                    for (var i=0; i<lastVehicles.length; ++i) {
                        profiles = moveToFront(profiles, function(p) { return p.vehicle !== lastVehicles[i]; });
                    }
                    ghRequest.profiles = profiles;
                    ghRequest.setElevation(json.elevation);

                    // only show all profiles if the url already specifies an existing profile that is not amongst the 'firstVehicles'
                    var urlProfile = profiles.find(function (profile) { return urlParams.profile && profile.name === urlParams.profile; });
                    var showAllProfiles = urlProfile && firstVehicles.indexOf(urlProfile.vehicle) >= 0;
                    if (profiles.length > 0)
                        ghRequest.setProfile(profiles[0].name);

                    var numVehiclesWhenCollapsed = 3;
                    var hiddenVehicles = [];
                    for (var i = 0; i < profiles.length; ++i) {
                        var btn = createButton(profiles[i], !showAllProfiles && i >= numVehiclesWhenCollapsed);
                        profilesDiv.append(btn);
                        if (i >= numVehiclesWhenCollapsed)
                            hiddenVehicles.push(btn);
                    }

                    if (!showAllProfiles && profiles.length > numVehiclesWhenCollapsed) {
                        var moreBtn = $("<a id='more-vehicle-btn'> ...</a>").click(function () {
                            moreBtn.hide();
                            for (var i in hiddenVehicles) {
                                hiddenVehicles[i].show();
                            }
                        });
                        profilesDiv.append($("<a class='vehicle-info-link' href='https://docs.graphhopper.com/#section/Map-Data-and-Routing-Profiles/OpenStreetMap'>?</a>"));
                        profilesDiv.append(moreBtn);
                    }
                }
                $("button#" + profiles[0].name).addClass("selectprofile");

                metaVersionInfo = messages.extractMetaVersionInfo(json);
                // a very simplistic helper system that shows the possible entries and encoded values
                if(json.encoded_values) {
                    const categories = {};
                    Object.keys(json.encoded_values).forEach((k) => {
                        const v = json.encoded_values[k];
                        if (v.length === 2 && v[0] === 'true' && v[1] === 'false') {
                            categories[k] = {type: 'boolean'};
                        } else if (v.length === 2 && v[0] === '>number' && v[1] === '<number') {
                            categories[k] = {type: 'numeric'};
                        } else {
                            categories[k] = {type: 'enum', values: v.sort()};
                        }
                    });
                    cmEditor.categories = categories;
                }

                mapLayer.initMap(bounds, setStartCoord, setIntermediateCoord, setEndCoord, urlParams.layer, urlParams.use_miles);

                // execute query
                initFromParams(urlParams, true);

                checkInput();
            }, function (err) {
                console.log(err);
                $('#error').html('GraphHopper API offline? <a href="#" onclick="location.reload();event.preventDefault();">Refresh</a>' + '<br/>Status: ' + err.statusText + '<br/>' + host);

                bounds = {
                    "minLon": -180,
                    "minLat": -90,
                    "maxLon": 180,
                    "maxLat": 90
                };
                nominatim.setBounds(bounds);
                mapLayer.initMap(bounds, setStartCoord, setIntermediateCoord, setEndCoord, urlParams.layer, urlParams.use_miles);
            });

    var language_code = urlParams.locale && urlParams.locale.split('-', 1)[0];
    if (language_code != 'en') {
        // A few language codes are different in GraphHopper and Flatpickr.
        var flatpickr_locale;
        switch (language_code) {
            case 'ca':  // Catalan
                flatpickr_locale = 'cat';
                break;
            case 'el':  // Greek
                flatpickr_locale = 'gr';
                break;
            default:
                flatpickr_locale = language_code;
        }
        if (Flatpickr.l10ns.hasOwnProperty(flatpickr_locale)) {
            Flatpickr.localize(Flatpickr.l10ns[flatpickr_locale]);
        }
    }

    $(window).resize(function () {
        mapLayer.adjustMapSize();
    });
    $("#locationpoints").sortable({
        items: ".pointDiv",
        cursor: "n-resize",
        containment: "parent",
        handle: ".pointFlag",
        update: function (event, ui) {
            var origin_index = $(ui.item[0]).data('index');
            sortable_items = $("#locationpoints > div.pointDiv");
            $(sortable_items).each(function (index) {
                var data_index = $(this).data('index');
                if (origin_index === data_index) {
                    //log(data_index +'>'+ index);
                    ghRequest.route.move(data_index, index);
                    if (!routeIfAllResolved())
                        checkInput();
                    return false;
                }
            });
        }
    });

    $('#locationpoints > div.pointAdd').click(function () {
        ghRequest.route.add(new GHInput());
        checkInput();
    });

    checkInput();
});

function profileDisplayName(profileName) {
   // custom profile names like 'my_car' cannot be translated and will be returned like 'web.my_car', so we remove
   // the 'web.' prefix in this case
   return translate.tr(profileName).replace("web.", "");
}

/**
 * Takes an array and returns another array with the same elements but sorted such that all elements matching the given
 * condition come first.
 */
function moveToFront(arr, condition) {
    return arr.filter(condition).concat(arr.filter(function (e) { return !condition(e); }))
}

function initFromParams(params, doQuery) {
    ghRequest.init(params);

    var flatpickr = new Flatpickr(document.getElementById("input_date_0"), {
        defaultDate: new Date(),
        allowInput: true, /* somehow then does not sync!? */
        minuteIncrement: 15,
        time_24hr: true,
        enableTime: true
    });
    if (ghRequest.isPublicTransit())
        $(".time_input").show();
    else
        $(".time_input").hide();
    if (ghRequest.getEarliestDepartureTime()) {
        flatpickr.setDate(ghRequest.getEarliestDepartureTime());
    }

    var count = 0;
    var singlePointIndex;
    if (params.point)
        for (var key = 0; key < params.point.length; key++) {
            if (params.point[key] !== "") {
                count++;
                singlePointIndex = key;
            }
        }

    var routeNow = params.point && count >= 2;
    if (routeNow) {
        resolveCoords(params.point, doQuery);
    } else if (params.point && count === 1) {
        ghRequest.route.set(params.point[singlePointIndex], singlePointIndex, true);
        resolveIndex(singlePointIndex).done(function () {
            mapLayer.focus(ghRequest.route.getIndex(singlePointIndex), 15, singlePointIndex);
        });
    }
}

function resolveCoords(pointsAsStr, doQuery) {
    for (var i = 0, l = pointsAsStr.length; i < l; i++) {
        var pointStr = pointsAsStr[i];
        var coords = ghRequest.route.getIndex(i);
        if (!coords || pointStr !== coords.input || !coords.isResolved())
            ghRequest.route.set(pointStr, i, true);
    }

    checkInput();

    if (ghRequest.route.isResolved()) {
        resolveAll();
        routeLatLng(ghRequest, doQuery);
    } else {
        // at least one text input from user -> wait for resolve as we need the coord for routing
        $.when.apply($, resolveAll()).done(function () {
            routeLatLng(ghRequest, doQuery);
        });
    }
}

var FROM = 'from', TO = 'to';
function getToFrom(index) {
    if (index === 0)
        return FROM;
    else if (index === (ghRequest.route.size() - 1))
        return TO;
    return -1;
}

function checkInput() {
    var template = $('#pointTemplate').html(),
            len = ghRequest.route.size();

    // remove deleted points
    if ($('#locationpoints > div.pointDiv').length > len) {
        $('#locationpoints > div.pointDiv:gt(' + (len - 1) + ')').remove();
    }

    // properly unbind previously click handlers
    $("#locationpoints .pointDelete").off();

    var deleteClickHandler = function () {
        var index = $(this).parent().data('index');
        ghRequest.route.removeSingle(index);
        mapLayer.clearLayers();
        checkInput();
        routeLatLng(ghRequest, false);
    };

    // console.log("## new checkInput");
    for (var i = 0; i < len; i++) {
        var div = $('#locationpoints > div.pointDiv').eq(i);
        // console.log(div.length + ", index:" + i + ", len:" + len);
        if (div.length === 0) {
            $('#locationpoints > div.pointAdd').before(translate.nanoTemplate(template, {id: i}));
            div = $('#locationpoints > div.pointDiv').eq(i);
        }

        var toFrom = getToFrom(i);
        div.data("index", i);
        div.find(".pointFlag").attr("src",
                (toFrom === FROM) ? 'img/marker-small-green.png' :
                ((toFrom === TO) ? 'img/marker-small-red.png' : 'img/marker-small-blue.png'));
        if (len > 2) {
            div.find(".pointDelete").click(deleteClickHandler).prop('disabled', false).removeClass('ui-state-disabled');
        } else {
            div.find(".pointDelete").prop('disabled', true).addClass('ui-state-disabled');
        }

        autocomplete.showListForIndex(ghRequest, routeIfAllResolved, i);
        if (translate.isI18nIsInitialized()) {
            var input = div.find(".pointInput");
            if (i === 0)
                $(input).attr("placeholder", translate.tr("from_hint"));
            else if (i === (len - 1))
                $(input).attr("placeholder", translate.tr("to_hint"));
            else
                $(input).attr("placeholder", translate.tr("via_hint"));
        }
    }
}

function setToStart(e) {
    var latlng = e.relatedTarget.getLatLng(),
            index = ghRequest.route.getIndexByCoord(latlng);
    ghRequest.route.move(index, 0);
    routeIfAllResolved();
}

function setToEnd(e) {
    var latlng = e.relatedTarget.getLatLng(),
            index = ghRequest.route.getIndexByCoord(latlng);
    ghRequest.route.move(index, -1);
    routeIfAllResolved();
}

function setStartCoord(e) {
    ghRequest.route.set(e.latlng.wrap(), 0);
    resolveFrom();
    routeIfAllResolved();
}

function setIntermediateCoord(e) {
    var routeLayers = mapLayer.getSubLayers("route");
    var routeSegments = routeLayers.map(function (rl) {
        return {
            coordinates: rl.getLatLngs(),
            wayPoints: rl.feature.properties.snapped_waypoints.coordinates.map(function (wp) {
                return L.latLng(wp[1], wp[0]);
            })
        };
    });
    var index = routeManipulation.getIntermediatePointIndex(routeSegments, e.latlng);
    ghRequest.route.add(e.latlng.wrap(), index);
    ghRequest.do_zoom = false;
    resolveIndex(index);
    routeIfAllResolved();
}

function deleteCoord(e) {
    var latlng = e.relatedTarget.getLatLng();
    ghRequest.route.removeSingle(latlng);
    mapLayer.clearLayers();
    routeLatLng(ghRequest, false);
}

function setEndCoord(e) {
    var index = ghRequest.route.size() - 1;
    ghRequest.route.set(e.latlng.wrap(), index);
    resolveTo();
    routeIfAllResolved();
}

function routeIfAllResolved(doQuery) {
    if (ghRequest.route.isResolved()) {
        routeLatLng(ghRequest, doQuery);
        return true;
    }
    return false;
}

function setFlag(coord, index) {
    if (coord.lat) {
        var toFrom = getToFrom(index);
        // intercept openPopup
        var marker = mapLayer.createMarker(index, coord, setToEnd, setToStart, deleteCoord, ghRequest);
        marker._openPopup = marker.openPopup;
        marker.openPopup = function () {
            var latlng = this.getLatLng(),
                    locCoord = ghRequest.route.getIndexFromCoord(latlng),
                    content;
            if (locCoord.resolvedList && locCoord.resolvedList[0] && locCoord.resolvedList[0].locationDetails) {
                var address = locCoord.resolvedList[0].locationDetails;
                content = format.formatAddress(address);
                // at last update the content and update
                this._popup.setContent(content).update();
            }
            this._openPopup();
        };
        var _tempItem = {
            text: translate.tr('set_start'),
            icon: './img/marker-small-green.png',
            callback: setToStart,
            index: 1
        };
        if (toFrom === -1)
            marker.options.contextmenuItems.push(_tempItem); // because the Mixin.ContextMenu isn't initialized
        marker.on('dragend', function (e) {
            mapLayer.clearLayers();
            // inconsistent leaflet API: event.target.getLatLng vs. mouseEvent.latlng?
            var latlng = e.target.getLatLng();
            autocomplete.hide();
            ghRequest.route.getIndex(index).setCoord(latlng.lat, latlng.lng);
            resolveIndex(index);
            // do not wait for resolving and avoid zooming when dragging
            ghRequest.do_zoom = false;
            routeLatLng(ghRequest, false);
        });
    }
}

function resolveFrom() {
    return resolveIndex(0);
}

function resolveTo() {
    return resolveIndex((ghRequest.route.size() - 1));
}

function resolveIndex(index) {
    if(!ghRequest.route.getIndex(index))
        return;
    setFlag(ghRequest.route.getIndex(index), index);
    if (index === 0) {
        if (!ghRequest.to.isResolved())
            mapLayer.setDisabledForMapsContextMenu('start', true);
        else
            mapLayer.setDisabledForMapsContextMenu('start', false);
    } else if (index === (ghRequest.route.size() - 1)) {
        if (!ghRequest.from.isResolved())
            mapLayer.setDisabledForMapsContextMenu('end', true);
        else
            mapLayer.setDisabledForMapsContextMenu('end', false);
    }

    return nominatim.resolve(index, ghRequest.route.getIndex(index));
}

function resolveAll() {
    var ret = [];
    for (var i = 0, l = ghRequest.route.size(); i < l; i++) {
        ret[i] = resolveIndex(i);
    }

    if(ghRequest.isPublicTransit())
        ghRequest.setEarliestDepartureTime(
            moment($("#input_date_0").val(), 'YYYY-MM-DD HH:mm').toISOString());

    return ret;
}

function flagAll() {
    for (var i = 0, l = ghRequest.route.size(); i < l; i++) {
        setFlag(ghRequest.route.getIndex(i), i);
    }
}

function createRouteCallback(request, routeResultsDiv, urlForHistory, doZoom) {
    return function (json) {
       routeResultsDiv.html("");
       if (json.message) {
           var tmpErrors = json.message;
           console.log(tmpErrors);
           if (json.hints) {
               for (var m = 0; m < json.hints.length; m++) {
                   routeResultsDiv.append("<div class='error'>" + json.hints[m].message + "</div>");
               }
           } else {
               routeResultsDiv.append("<div class='error'>" + tmpErrors + "</div>");
           }
           return;
       }

       function createClickHandler(geoJsons, currentLayerIndex, tabHeader, oneTab, hasElevation, details, selectedDetail, detailSelected) {
           return function () {

               var currentGeoJson = geoJsons[currentLayerIndex];
               mapLayer.eachLayer(function (layer) {
                   // skip markers etc
                   if (!layer.setStyle)
                       return;

                   var doHighlight = layer.feature === currentGeoJson;
                   layer.setStyle(doHighlight ? highlightRouteStyle : alternativeRouteStye);
                   if (doHighlight) {
                       if (!L.Browser.ie && !L.Browser.opera)
                           layer.bringToFront();
                   }
               });

               if (hasElevation) {
                   mapLayer.clearElevation();
                   mapLayer.addElevation(currentGeoJson, details, selectedDetail, detailSelected);
               }

               headerTabs.find("li").removeClass("current");
               routeResultsDiv.find("div").removeClass("current");

               tabHeader.addClass("current");
               oneTab.addClass("current");
           };
       }

       var headerTabs = $("<ul id='route_result_tabs'/>");
       if (json.paths.length > 1) {
           routeResultsDiv.append(headerTabs);
           routeResultsDiv.append("<div class='clear'/>");
       }

       // the routing layer uses the geojson properties.style for the style, see map.js
       var defaultRouteStyle = {color: "#00cc33", "weight": 5, "opacity": 0.6};
       var highlightRouteStyle = {color: "#00cc33", "weight": 6, "opacity": 0.8};
       var alternativeRouteStye = {color: "darkgray", "weight": 6, "opacity": 0.8};
       var geoJsons = [];
       var firstHeader;

       // Create buttons to toggle between SI and imperial units.
       var createUnitsChooserButtonClickHandler = function (useMiles) {
           return function () {
               mapLayer.updateScale(useMiles);
               ghRequest.useMiles = useMiles;
               resolveAll();
               if (ghRequest.route.isResolved())
                 routeLatLng(ghRequest);
           };
       };

       if(json.paths.length > 0 && json.paths[0].points_order) {
           mapLayer.clearLayers();
           var po = json.paths[0].points_order;
           for (var i = 0; i < po.length; i++) {
               setFlag(ghRequest.route.getIndex(po[i]), i);
           }
       }

       for (var pathIndex = 0; pathIndex < json.paths.length; pathIndex++) {
           var tabHeader = $("<li>").append((pathIndex + 1) + "<img class='alt_route_img' src='img/alt_route.png'/>");
           if (pathIndex === 0)
               firstHeader = tabHeader;

           headerTabs.append(tabHeader);
           var path = json.paths[pathIndex];
           var style = (pathIndex === 0) ? defaultRouteStyle : alternativeRouteStye;

           var geojsonFeature = {
               "type": "Feature",
               "geometry": path.points,
               "properties": {
                   "style": style,
                   name: "route",
                   snapped_waypoints: path.snapped_waypoints
               }
           };

           geoJsons.push(geojsonFeature);
           mapLayer.addDataToRoutingLayer(geojsonFeature);
           var oneTab = $("<div class='route_result_tab'>");
           routeResultsDiv.append(oneTab);
           var detailSelected = function (id, type) {
               ghRequest.selectedDetail = type.text;
           }
           tabHeader.click(createClickHandler(geoJsons, pathIndex, tabHeader, oneTab, request.hasElevation(), path.details, request.selectedDetail, detailSelected));

           var routeInfo = $("<div class='route_description'>");
           if (path.description && path.description.length > 0) {
               routeInfo.text(path.description);
               routeInfo.append("<br/>");
           }

           var tempDistance = translate.createDistanceString(path.distance, request.useMiles);
           var tempRouteInfo;
           if(request.isPublicTransit()) {
               var tempArrTime = moment(ghRequest.getEarliestDepartureTime())
                                       .add(path.time, 'milliseconds')
                                       .format('LT');
               if(path.transfers >= 0)
                   tempRouteInfo = translate.tr("pt_route_info", [tempArrTime, path.transfers, tempDistance]);
               else
                   tempRouteInfo = translate.tr("pt_route_info_walking", [tempArrTime, tempDistance]);
           } else {
               var tmpDuration = translate.createTimeString(path.time);
               tempRouteInfo = translate.tr("route_info", [tempDistance, tmpDuration]);
           }

           routeInfo.append(tempRouteInfo);

           var kmButton = $("<button class='plain_text_button " + (request.useMiles ? "gray" : "") + "'>");
           kmButton.text(translate.tr2("km_abbr"));
           kmButton.click(createUnitsChooserButtonClickHandler(false));

           var miButton = $("<button class='plain_text_button " + (request.useMiles ? "" : "gray") + "'>");
           miButton.text(translate.tr2("mi_abbr"));
           miButton.click(createUnitsChooserButtonClickHandler(true));

           var buttons = $("<span style='float: right;'>");
           buttons.append(kmButton);
           buttons.append('|');
           buttons.append(miButton);

           routeInfo.append(buttons);

           if (request.hasElevation()) {
               routeInfo.append(translate.createEleInfoString(path.ascend, path.descend, request.useMiles));
           }

           routeInfo.append($("<div style='clear:both'/>"));
           oneTab.append(routeInfo);

           if (path.instructions) {
               var instructions = require('./instructions.js');
               oneTab.append(instructions.create(mapLayer, path, urlForHistory, request));
           }

           var detailObj = path.details;
           if(detailObj && request.api_params.debug) {
               // detailKey, would be for example average_speed
               for (var detailKey in detailObj) {
                   var pathDetailsArr = detailObj[detailKey];
                   for (var i = 0; i < pathDetailsArr.length; i++) {
                       var pathDetailObj = pathDetailsArr[i];
                       var firstIndex = pathDetailObj[0];
                       var value = pathDetailObj[2];
                       var lngLat = path.points.coordinates[firstIndex];
                       L.marker([lngLat[1], lngLat[0]], {
                           icon: L.icon({
                               iconUrl: './img/marker-small-blue.png',
                               iconSize: [15, 15]
                           }),
                           draggable: true,
                           autoPan: true
                       }).addTo(mapLayer.getRoutingLayer()).bindPopup(detailKey + ":" + value);
                   }
               }
           }
       }
       // already select best path
       firstHeader.click();

       mapLayer.adjustMapSize();
       // TODO change bounding box on click
       var firstPath = json.paths[0];
       if (firstPath.bbox && doZoom) {
           var minLon = firstPath.bbox[0];
           var minLat = firstPath.bbox[1];
           var maxLon = firstPath.bbox[2];
           var maxLat = firstPath.bbox[3];
           var tmpB = new L.LatLngBounds(new L.LatLng(minLat, minLon), new L.LatLng(maxLat, maxLon));
           mapLayer.fitMapToBounds(tmpB);
       }

       $('.defaulting').each(function (index, element) {
           $(element).css("color", "black");
       });
   }
}

function routeLatLng(request, doQuery) {
    // do_zoom should not show up in the URL but in the request object to avoid zooming for history change
    var doZoom = request.do_zoom;
    request.do_zoom = true;
    request.removeProfileParameterIfLegacyRequest();

    var urlForHistory = request.createHistoryURL() + "&layer=" + tileLayers.activeLayerName;

    // not enabled e.g. if no cookies allowed (?)
    // if disabled we have to do the query and cannot rely on the statechange history event
    if (!doQuery && History.enabled) {
        // 2. important workaround for encoding problems in history.js
        var params = urlTools.parseUrl(urlForHistory);
        params.do_zoom = doZoom;
        // force a new request even if we have the same parameters
        params.mathRandom = Math.random();
        History.pushState(params, messages.browserTitle, urlForHistory);
        return;
    }
    var infoDiv = $("#info");
    infoDiv.empty();
    infoDiv.show();
    var routeResultsDiv = $("<div class='route_results'/>");
    infoDiv.append(routeResultsDiv);

    mapLayer.clearElevation();
    mapLayer.clearLayers();
    flagAll();

    mapLayer.setDisabledForMapsContextMenu('intermediate', false);

    $("#profiles button").removeClass("selectprofile");
    var buttonToSelectId = request.getProfile();
    // for legacy requests this might be undefined then we just do not select anything
    if (buttonToSelectId)
    $("button#" + buttonToSelectId.toLowerCase()).addClass("selectprofile");

    routeResultsDiv.html('<img src="img/indicator.gif"/> Search Route ...');
    if (request.cmEditorActive) {
        doCustomRequest(request, routeResultsDiv);
    } else {
        var urlForAPI = request.createURL();
        request.doRequest(urlForAPI, createRouteCallback(request, routeResultsDiv, urlForHistory, doZoom));
    }
}

function doCustomRequest(request, routeResultsDiv) {
    var customModelErrors = request.cmEditor.getCurrentErrors(request.cmEditor.cm.getValue(), request.cmEditor.cm);
    if (customModelErrors.errors.length !== 0) {
        if (request.ignoreCustomErrors)
            console.warn('sending custom model that is likely invalid');
        else {
            routeResultsDiv.html('');
            routeResultsDiv.append("<div class='error'>Invalid custom model</div>");
            return;
        }
    }
    var customModel = request.cmEditor.jsonObj;
    var details = request.cmEditor.getUsedCategories();
    details.push('average_speed');
    details.push('distance');
    details.push('time');

    var points = [];
    for (var idx = 0; idx < ghRequest.route.size(); idx++) {
        var point = ghRequest.route.getIndex(idx);
        points.push([point.lng, point.lat]);
    }

    var reqBody = {
        points: points,
        points_encoded: false,
        elevation: ghRequest.api_params.elevation,
        profile: ghRequest.api_params.profile,
        custom_model: customModel,
        locale: ghRequest.api_params.locale,
        "ch.disable": true,
        details: details
    }
    var reqURL = host + "/route";
    if(ghRequest.api_params.key) reqURL += "?key=" + ghRequest.api_params.key;

    $.ajax({
        url: reqURL,
        type: "POST",
        contentType: 'application/json; charset=utf-8',
        dataType: "json",
        data: JSON.stringify(reqBody),
        success: createRouteCallback(ghRequest, routeResultsDiv, "", true),
        error: function (err) {
            routeResultsDiv.html("Error response: cannot process input");
            var json = JSON.parse(err.responseText);
            createRouteCallback(ghRequest, routeResultsDiv, "", true)(json);
        }
    });
}

function mySubmit() {
    var fromStr,
            toStr,
            viaStr,
            allStr = [],
            inputOk = true;
    var location_points = $("#locationpoints > div.pointDiv > input.pointInput");
    var len = location_points.size;
    $.each(location_points, function (index) {
        if (index === 0) {
            fromStr = $(this).val();
            if (fromStr !== translate.tr("from_hint") && fromStr !== "")
                allStr.push(fromStr);
            else
                inputOk = false;
        } else if (index === (len - 1)) {
            toStr = $(this).val();
            if (toStr !== translate.tr("to_hint") && toStr !== "")
                allStr.push(toStr);
            else
                inputOk = false;
        } else {
            viaStr = $(this).val();
            if (viaStr !== translate.tr("via_hint") && viaStr !== "")
                allStr.push(viaStr);
            else
                inputOk = false;
        }
    });
    if (!inputOk) {
        // TODO print warning
        return;
    }
    if (fromStr === translate.tr("from_hint")) {
        // no special function
        return;
    }
    if (toStr === translate.tr("to_hint")) {
        // lookup area
        ghRequest.from.setStr(fromStr);
        $.when(resolveFrom()).done(function () {
            mapLayer.focus(ghRequest.from, null, 0);
        });
        return;
    }
    // route!
    if (inputOk)
        resolveCoords(allStr);
}

function isProduction() {
    return host.indexOf("graphhopper.com") > 0;
}

module.exports.setFlag = setFlag;
