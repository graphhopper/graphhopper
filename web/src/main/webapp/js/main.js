/*
 * If you want to query another API append this something like
 * &host=http://localhost:9000/
 * to the URL or overwrite the 'host' variable.
 */
var tmpArgs = parseUrlWithHisto();
var host = tmpArgs["host"];
// var host = "http://graphhopper.com/api/1";
if (!host) {
    if (location.port === '') {
        host = location.protocol + '//' + location.hostname;
    } else {
        host = location.protocol + '//' + location.hostname + ":" + location.port;
    }
}

var ghRequest = new GHRequest(host);
var bounds = {};

//var nominatim = "http://open.mapquestapi.com/nominatim/v1/search.php";
//var nominatim_reverse = "http://open.mapquestapi.com/nominatim/v1/reverse.php";
var nominatim = "http://nominatim.openstreetmap.org/search";
var nominatim_reverse = "http://nominatim.openstreetmap.org/reverse";
var routingLayer;
var map;
var browserTitle = "GraphHopper Maps - Driving Directions";
var firstClickToRoute;
var defaultTranslationMap = null;
var enTranslationMap = null;
var routeSegmentPopup = null;
var elevationControl = null;

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

$(document).ready(function(e) {
    // fixing cross domain support e.g in Opera
    jQuery.support.cors = true;

    if (host.indexOf("graphhopper.com") > 0)
        $('#hosting').show();

    var History = window.History;
    if (History.enabled) {
        History.Adapter.bind(window, 'statechange', function() {
            // No need for workaround?
            // Chrome and Safari always emit a popstate event on page load, but Firefox doesn’t
            // https://github.com/defunkt/jquery-pjax/issues/143#issuecomment-6194330

            var state = History.getState();
            console.log(state);
            initFromParams(state.data, true);
        });
    }

    $('#locationform').submit(function(e) {
        // no page reload
        e.preventDefault();
        mySubmit();
    });

    $('#gpxExportButton a').click(function(e) {
        // no page reload
        e.preventDefault();
        exportGPX();
    });

    var urlParams = parseUrlWithHisto();
    $.when(ghRequest.fetchTranslationMap(urlParams.locale), ghRequest.getInfo())
            .then(function(arg1, arg2) {
                // init translation retrieved from first call (fetchTranslationMap)
                var translations = arg1[0];

                // init language
                // 1. determined by Accept-Language header, falls back to 'en' if no translation map available
                // 2. can be overwritten by url parameter        
                ghRequest.setLocale(translations["locale"]);
                defaultTranslationMap = translations["default"];
                enTranslationMap = translations["en"];
                if (defaultTranslationMap == null)
                    defaultTranslationMap = enTranslationMap;

                initI18N();

                // init bounding box from getInfo result
                var json = arg2[0];
                var tmp = json.bbox;
                bounds.initialized = true;
                bounds.minLon = tmp[0];
                bounds.minLat = tmp[1];
                bounds.maxLon = tmp[2];
                bounds.maxLat = tmp[3];
                var vehiclesDiv = $("#vehicles");
                function createButton(vehicle) {
                    var vehicle = vehicle.toLowerCase();
                    var button = $("<button class='vehicle-btn' title='" + tr(vehicle) + "'/>");
                    button.attr('id', vehicle);
                    button.html("<img src='img/" + vehicle + ".png' alt='" + tr(vehicle) + "'></img>");
                    button.click(function() {
                        ghRequest.vehicle = vehicle;
                        resolveFrom();
                        resolveTo();
                        routeLatLng(ghRequest);
                    });
                    return button;
                }

                if (json.supported_vehicles) {
                    var vehicles = json.supported_vehicles;
                    if (vehicles.length > 0)
                        ghRequest.vehicle = vehicles[0];

                    for (var i = 0; i < vehicles.length; i++) {
                        vehiclesDiv.append(createButton(vehicles[i]));
                    }
                }

                initMap();

                // execute query
                initFromParams(urlParams, true);
            }, function(err) {
                console.log(err);
                $('#error').html('GraphHopper API offline? ' + host);

                bounds = {
                    "minLon": -180,
                    "minLat": -90,
                    "maxLon": 180,
                    "maxLat": 90
                };
                initMap();
            });

    $(window).resize(function() {
        adjustMapSize();
    });

    setAutoCompleteList("from");
    setAutoCompleteList("to");
});

function initFromParams(params, doQuery) {
    ghRequest.init(params);
    var fromAndTo = params.from && params.to;
    var routeNow = params.point && params.point.length === 2 || fromAndTo;
    if (routeNow) {
        if (fromAndTo)
            resolveCoords(params.from, params.to, doQuery);
        else
            resolveCoords(params.point[0], params.point[1], doQuery);
    }
}

function resolveCoords(fromStr, toStr, doQuery) {
    if (fromStr !== ghRequest.from.input || !ghRequest.from.isResolved())
        ghRequest.from = new GHInput(fromStr);

    if (toStr !== ghRequest.to.input || !ghRequest.to.isResolved())
        ghRequest.to = new GHInput(toStr);

    if (ghRequest.from.lat && ghRequest.to.lat) {
        // two mouse clicks into the map -> do not wait for resolve
        resolveFrom();
        resolveTo();
        routeLatLng(ghRequest, doQuery);
    } else {
        // at least one text input from user -> wait for resolve as we need the coord for routing     
        $.when(resolveFrom(), resolveTo()).done(function(fromArgs, toArgs) {
            routeLatLng(ghRequest, doQuery);
        });
    }
}

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
    $("#info").css("max-height", height - $("#input_header").height() - 35);
}

function initMap() {
    adjustMapSize();
    console.log("init map at " + JSON.stringify(bounds));

    // mapquest provider
    var osmAttr = '&copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

    var tp = "ls";
    if (L.Browser.retina)
        tp = "lr";

    var lyrk = L.tileLayer('http://{s}.tiles.lyrk.org/' + tp + '/{z}/{x}/{y}?apikey=6e8cfef737a140e2a58c8122aaa26077', {
        attribution: osmAttr + ', <a href="http://geodienste.lyrk.de/">Lyrk</a>',
        subdomains: ['a', 'b', 'c']
    });

    var mapquest = L.tileLayer('http://{s}.mqcdn.com/tiles/1.0.0/osm/{z}/{x}/{y}.png', {
        attribution: osmAttr + ', <a href="http://open.mapquest.co.uk">MapQuest</a>',
        subdomains: ['otile1', 'otile2', 'otile3', 'otile4']
    });

    var mapquestAerial = L.tileLayer('http://{s}.mqcdn.com/tiles/1.0.0/sat/{z}/{x}/{y}.png', {
        attribution: osmAttr + ', <a href="http://open.mapquest.co.uk">MapQuest</a>',
        subdomains: ['otile1', 'otile2', 'otile3', 'otile4']
    });

    var thunderTransport = L.tileLayer('http://{s}.tile.thunderforest.com/transport/{z}/{x}/{y}.png', {
        attribution: osmAttr + ', <a href="http://www.thunderforest.com/transport/">Thunderforest Transport</a>',
        subdomains: ['a', 'b', 'c']
    });

    var thunderCycle = L.tileLayer('http://{s}.tile.thunderforest.com/cycle/{z}/{x}/{y}.png', {
        attribution: osmAttr + ', <a href="http://www.thunderforest.com/opencyclemap/">Thunderforest Cycle</a>',
        subdomains: ['a', 'b', 'c']
    });

    var thunderOutdoors = L.tileLayer('http://{s}.tile.thunderforest.com/outdoors/{z}/{x}/{y}.png', {
        attribution: osmAttr + ', <a href="http://www.thunderforest.com/outdoors/">Thunderforest Outdoors</a>',
        subdomains: ['a', 'b', 'c']
    });

    var wrk = L.tileLayer('http://{s}.wanderreitkarte.de/topo/{z}/{x}/{y}.png', {
        attribution: osmAttr + ', <a href="http://wanderreitkarte.de">WanderReitKarte</a>',
        subdomains: ['topo4', 'topo', 'topo2', 'topo3']
    });

    var osm = L.tileLayer('http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: osmAttr
    });

    var osmde = L.tileLayer('http://{s}.tile.openstreetmap.de/tiles/osmde/{z}/{x}/{y}.png', {
        attribution: osmAttr
    });

    // default
    map = L.map('map', {
        layers: [mapquest]
    });

    var baseMaps = {
        "Lyrk": lyrk,
        "MapQuest": mapquest,
        "MapQuest Aerial": mapquestAerial,
        "TF Transport": thunderTransport,
        "TF Cycle": thunderCycle,
        "TF Outdoors": thunderOutdoors,
        "WanderReitKarte": wrk,
        "OpenStreetMap": osm,
        "OpenStreetMap.de": osmde
    };

    //    var overlays = {
    //        "MapQuest Hybrid": mapquest
    //    };

    // no layers for small browser windows
    if ($(window).width() > 400) {
        L.control.layers(baseMaps/*, overlays*/).addTo(map);
    }

    L.control.scale().addTo(map);

    map.fitBounds(new L.LatLngBounds(new L.LatLng(bounds.minLat, bounds.minLon),
            new L.LatLng(bounds.maxLat, bounds.maxLon)));

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
    routingLayer.options = {style: {color: "#00cc33", "weight": 5, "opacity": 0.6}};

    firstClickToRoute = true;
    function onMapClick(e) {
        var latlng = e.latlng;
        latlng.lng = makeValidLng(latlng.lng);
        if (firstClickToRoute) {
            // set start point
            routingLayer.clearLayers();
            firstClickToRoute = false;
            ghRequest.from.setCoord(latlng.lat, latlng.lng);
            resolveFrom();
        } else {
            // set end point
            ghRequest.to.setCoord(latlng.lat, latlng.lng);
            resolveTo();
            // do not wait for resolving
            routeLatLng(ghRequest);
            firstClickToRoute = true;
        }
    }

    map.on('click', onMapClick);
}

function makeValidLng(lon) {
    if (lon < 180 && lon > -180)
        return lon;
    if (lon > 180)
        return (lon + 180) % 360 - 180;
    return (lon - 180) % 360 + 180;
}

function setFlag(coord, isFrom) {
    if (coord.lat) {
        var marker = L.marker([coord.lat, coord.lng], {
            icon: (isFrom ? iconFrom : iconTo),
            draggable: true
        }).addTo(routingLayer).bindPopup(isFrom ? "Start" : "End");
        marker.on('dragend', function(e) {
            routingLayer.clearLayers();
            // inconsistent leaflet API: event.target.getLatLng vs. mouseEvent.latlng?
            var latlng = e.target.getLatLng();
            hideAutoComplete();
            if (isFrom) {                
                ghRequest.from.setCoord(latlng.lat, latlng.lng);
                resolveFrom();
            } else {
                ghRequest.to.setCoord(latlng.lat, latlng.lng);
                resolveTo();
            }
            // do not wait for resolving and avoid zooming when dragging
            ghRequest.do_zoom = false;
            routeLatLng(ghRequest, false);
        });
    }
}

function resolveFrom() {
    setFlag(ghRequest.from, true);
    return resolve("from", ghRequest.from);
}

function resolveTo() {
    setFlag(ghRequest.to, false);
    return resolve("to", ghRequest.to);
}

function resolve(fromOrTo, locCoord) {
    $("#" + fromOrTo + "Flag").hide();
    $("#" + fromOrTo + "Indicator").show();
    $("#" + fromOrTo + "Input").val(locCoord.input);

    return createAmbiguityList(locCoord).done(function(arg1) {
        var errorDiv = $("#" + fromOrTo + "ResolveError");
        errorDiv.empty();

        if (locCoord.error)
            errorDiv.text(locCoord.error);

        $("#" + fromOrTo + "Indicator").hide();
        $("#" + fromOrTo + "Flag").show();
        return locCoord;
    });
}

/**
 * Returns a defer object containing the location pointing to a resolvedList with all the found
 * coordinates.
 */
function createAmbiguityList(locCoord) {
    // make example working even if nominatim service is down
    if (locCoord.input.toLowerCase() === "madrid") {
        locCoord.lat = 40.416698;
        locCoord.lng = -3.703551;
        locCoord.locationDetails = formatLocationEntry({city: "Madrid", country: "Spain"});
        locCoord.resolvedList = [locCoord];
    }
    if (locCoord.input.toLowerCase() === "moscow") {
        locCoord.lat = 55.751608;
        locCoord.lng = 37.618775;
        locCoord.locationDetails = formatLocationEntry({road: "Borowizki-Straße", city: "Moscow", country: "Russian Federation"});
        locCoord.resolvedList = [locCoord];
    }

    if (locCoord.isResolved()) {
        var tmpDefer = $.Deferred();
        tmpDefer.resolve([locCoord]);
        return tmpDefer;
    }

    locCoord.error = "";
    locCoord.resolvedList = [];
    var timeout = 3000;
    if (locCoord.lat && locCoord.lng) {
        var url = nominatim_reverse + "?lat=" + locCoord.lat + "&lon="
                + locCoord.lng + "&format=json&zoom=16";
        return $.ajax({
            url: url,
            type: "GET",
            dataType: "json",
            timeout: timeout
        }).fail(function(err) {
            // not critical => no alert
            locCoord.error = "Error while looking up coordinate";
            console.log(err);
        }).pipe(function(json) {
            if (!json) {
                locCoord.error = "No description found for coordinate";
                return [locCoord];
            }
            var address = json.address;
            var point = {};
            point.lat = locCoord.lat;
            point.lng = locCoord.lng;
            point.bbox = json.boundingbox;
            point.positionType = json.type;
            point.locationDetails = formatLocationEntry(address);
            // point.address = json.address;
            locCoord.resolvedList.push(point);
            return [locCoord];
        });
    } else {
        return doGeoCoding(locCoord.input, 10, timeout).pipe(function(jsonArgs) {
            if (!jsonArgs || jsonArgs.length == 0) {
                locCoord.error = "No area description found";
                return [locCoord];
            }
            var prevImportance = jsonArgs[0].importance;
            var address;
            for (var index in jsonArgs) {
                var json = jsonArgs[index];
                // if we have already some results ignore unimportant
                if (prevImportance - json.importance > 0.4)
                    break;

                // de-duplicate via ignoring boundary stuff => not perfect as 'Freiberg' would no longer be correct
                // if (json.type === "administrative")
                //    continue;

                // if no different properties => skip!
                if (address && JSON.stringify(address) === JSON.stringify(json.address))
                    continue;

                address = json.address;
                prevImportance = json.importance;
                var point = {};
                point.lat = round(json.lat);
                point.lng = round(json.lon);
                point.locationDetails = formatLocationEntry(address);
                point.bbox = json.boundingbox;
                point.positionType = json.type;
                locCoord.resolvedList.push(point);
            }
            if (locCoord.resolvedList.length === 0) {
                locCoord.error = "No area description found";
                return [locCoord];
            }
            var list = locCoord.resolvedList;
            locCoord.lat = list[0].lat;
            locCoord.lng = list[0].lng;
            // locCoord.input = dataToText(list[0]);
            return [locCoord];
        });
    }
}

function insComma(textA, textB) {
    if (textA.length > 0)
        return textA + ", " + textB;
    return textB;
}

function formatLocationEntry(address) {
    var locationDetails = {};
    var text = "";
    if (address.road) {
        text = address.road;
        if (address.house_number) {
            if (text.length > 0)
                text += " ";
            text += address.house_number;
        }
        locationDetails.road = text;
    }

    locationDetails.postcode = address.postcode;
    locationDetails.country = address.country;

    if (address.city || address.suburb || address.town
            || address.village || address.hamlet || address.locality) {
        text = "";
        if (address.locality)
            text = insComma(text, address.locality);
        if (address.hamlet)
            text = insComma(text, address.hamlet);
        if (address.village)
            text = insComma(text, address.village);
        if (address.suburb)
            text = insComma(text, address.suburb);
        if (address.city)
            text = insComma(text, address.city);
        if (address.town)
            text = insComma(text, address.town);
        locationDetails.city = text;
    }

    text = "";
    if (address.state)
        text += address.state;

    if (address.continent)
        text = insComma(text, address.continent);

    locationDetails.more = text;
    return locationDetails;
}

function doGeoCoding(input, limit, timeout) {
    // see https://trac.openstreetmap.org/ticket/4683 why limit=3 and not 1
    if (!limit)
        limit = 10;
    var url = nominatim + "?format=json&addressdetails=1&q=" + encodeURIComponent(input) + "&limit=" + limit;
    if (bounds.initialized) {
        // minLon, minLat, maxLon, maxLat => left, top, right, bottom
        url += "&bounded=1&viewbox=" + bounds.minLon + "," + bounds.maxLat + "," + bounds.maxLon + "," + bounds.minLat;
    }

    return $.ajax({
        url: url,
        type: "GET",
        dataType: "json",
        timeout: timeout
    }).fail(createCallback("[nominatim] Problem while looking up location " + input));
}

function createCallback(errorFallback) {
    return function(err) {
        console.log(errorFallback + " " + JSON.stringify(err));
    };
}

function focusWithBounds(coord, bbox, isFrom) {
    routingLayer.clearLayers();
    // bbox needs to be in the none-geojson format!?
    // [[lat, lng], [lat2, lng2], ...]
    map.fitBounds(new L.LatLngBounds(bbox));
    setFlag(coord, isFrom);
}

function focus(coord, zoom, isFrom) {
    if (coord.lat && coord.lng) {
        if (!zoom)
            zoom = 11;
        routingLayer.clearLayers();
        map.setView(new L.LatLng(coord.lat, coord.lng), zoom);
        setFlag(coord, isFrom);
    }
}
function routeLatLng(request, doQuery) {
    // do_zoom should not show up in the URL but in the request object to avoid zooming for history change
    var doZoom = request.do_zoom;
    request.do_zoom = true;

    var urlForHistory = request.createFullURL();
    // not enabled e.g. if no cookies allowed (?)
    // if disabled we have to do the query and cannot rely on the statechange history event    
    if (!doQuery && History.enabled) {
        // 2. important workaround for encoding problems in history.js
        var params = parseUrl(urlForHistory);
        console.log(params);
        params.do_zoom = doZoom;
        // force a new request even if we have the same parameters
        params.mathRandom = Math.random();
        History.pushState(params, browserTitle, urlForHistory);
        return;
    }

    $("#info").empty();
    $("#info").show();
    var descriptionDiv = $("<div/>");
    $("#info").append(descriptionDiv);

    var from = request.from.toString();
    var to = request.to.toString();
    if (!from || !to) {
        descriptionDiv.html('<small>' + tr('locationsNotFound') + '</small>');
        return;
    }

    if (elevationControl)
        elevationControl.clear();

    routingLayer.clearLayers();
    setFlag(request.from, true);
    setFlag(request.to, false);

    $("#vehicles button").removeClass("selectvehicle");
    $("button#" + request.vehicle.toLowerCase()).addClass("selectvehicle");

    var urlForAPI = request.createURL("point=" + from + "&point=" + to);
    descriptionDiv.html('<img src="img/indicator.gif"/> Search Route ...');
    request.doRequest(urlForAPI, function(json) {
        descriptionDiv.html("");
        if (json.info.errors) {
            var tmpErrors = json.info.errors;
            console.log(tmpErrors);
            for (var m = 0; m < tmpErrors.length; m++) {
                descriptionDiv.append("<div class='error'>" + tmpErrors[m].message + "</div>");
            }
            return;
        }
        var path = json.paths[0];
        var geojsonFeature = {
            "type": "Feature",
            // "style": myStyle,                
            "geometry": path.points
        };

        if (path.points_dimension === 3) {
            if (elevationControl === null) {
                elevationControl = L.control.elevation({
                    position: "bottomright",
                    theme: "white-theme", //default: lime-theme
                    width: 450,
                    height: 125,
                    yAxisMin: 0, // set min domain y axis
                    // yAxisMax: 550, // set max domain y axis
                    forceAxisBounds: false,
                    margins: {
                        top: 10,
                        right: 20,
                        bottom: 30,
                        left: 50
                    },
                    useHeightIndicator: true, //if false a marker is drawn at map position
                    interpolation: "linear", //see https://github.com/mbostock/d3/wiki/SVG-Shapes#wiki-area_interpolate
                    hoverNumber: {
                        decimalsX: 3, //decimals on distance (always in km)
                        decimalsY: 0, //deciamls on height (always in m)
                        formatter: undefined //custom formatter function may be injected
                    },
                    xTicks: undefined, //number of ticks in x axis, calculated by default according to width
                    yTicks: undefined, //number of ticks on y axis, calculated by default according to height
                    collapsed: false    //collapsed mode, show chart on click or mouseover
                });
                elevationControl.addTo(map);
            }

            elevationControl.addData(geojsonFeature);
        }

        routingLayer.addData(geojsonFeature);
        if (path.bbox && doZoom) {
            var minLon = path.bbox[0];
            var minLat = path.bbox[1];
            var maxLon = path.bbox[2];
            var maxLat = path.bbox[3];
            var tmpB = new L.LatLngBounds(new L.LatLng(minLat, minLon), new L.LatLng(maxLat, maxLon));
            map.fitBounds(tmpB);
        }

        var tmpTime = createTimeString(path.time);
        var tmpDist = createDistanceString(path.distance);
        descriptionDiv.append(tr("routeInfo", [tmpDist, tmpTime]));

        $('.defaulting').each(function(index, element) {
            $(element).css("color", "black");
        });

        if (path.instructions) {
            var instructionsElement = $("<table id='instructions'>");

            var partialInstr = path.instructions.length > 100;
            var len = Math.min(path.instructions.length, 100);
            for (var m = 0; m < len; m++) {
                var instr = path.instructions[m];
                var lngLat = path.points.coordinates[instr.interval[0]];
                addInstruction(instructionsElement, instr, m, lngLat);
            }
            $("#info").append(instructionsElement);

            if (partialInstr) {
                var moreDiv = $("<button id='moreButton'>More...</button>");
                moreDiv.click(function() {
                    moreDiv.remove();
                    for (var m = len; m < path.instructions.length; m++) {
                        var instr = path.instructions[m];
                        var lngLat = path.points.coordinates[instr.interval[0]];
                        addInstruction(instructionsElement, instr, m, lngLat);
                    }
                });
                instructionsElement.append(moreDiv);
            }

            var hiddenDiv = $("<div id='routeDetails'/>");
            hiddenDiv.hide();

            var toggly = $("<button id='expandDetails'>+</button>");
            toggly.click(function() {
                hiddenDiv.toggle();
            });
            $("#info").append(toggly);
            var infoStr = "took: " + round(json.info.took / 1000, 1000) + "s"
                    + ", points: " + path.points.coordinates.length;

            hiddenDiv.append("<span>" + infoStr + "</span>");

            var exportLink = $("#exportLink a");
            exportLink.attr('href', urlForHistory);
            var startOsmLink = $("<a>start</a>");
            startOsmLink.attr("href", "http://www.openstreetmap.org/?zoom=14&mlat=" + request.from.lat + "&mlon=" + request.from.lng);
            var endOsmLink = $("<a>end</a>");
            endOsmLink.attr("href", "http://www.openstreetmap.org/?zoom=14&mlat=" + request.to.lat + "&mlon=" + request.to.lng);
            hiddenDiv.append("<br/><span>View on OSM: </span>").append(startOsmLink).append(endOsmLink);

            var osrmLink = $("<a>OSRM</a>");
            osrmLink.attr("href", "http://map.project-osrm.org/?loc=" + from + "&loc=" + to);
            hiddenDiv.append("<br/><span>Compare with: </span>");
            hiddenDiv.append(osrmLink);
            var googleLink = $("<a>Google</a> ");
            var addToGoogle = "";
            var addToBing = "";
            if (request.vehicle.toUpperCase() === "FOOT") {
                addToGoogle = "&dirflg=w";
                addToBing = "&mode=W";
            } else if ((request.vehicle.toUpperCase() === "BIKE") ||
                    (request.vehicle.toUpperCase() === "RACINGBIKE") ||
                    (request.vehicle.toUpperCase() === "MTB")) {
                addToGoogle = "&dirflg=b";
                // ? addToBing = "&mode=B";
            }
            googleLink.attr("href", "http://maps.google.com/?q=from:" + from + "+to:" + to + addToGoogle);
            hiddenDiv.append(googleLink);
            var bingLink = $("<a>Bing</a> ");
            bingLink.attr("href", "http://www.bing.com/maps/default.aspx?rtp=adr." + from + "~adr." + to + addToBing);
            hiddenDiv.append(bingLink);
            $("#info").append(hiddenDiv);
        }
    });
}

function createDistanceString(dist) {
    if (dist < 900)
        return round(dist, 1) + tr2("mAbbr");

    dist = round(dist / 1000, 100);
    if (dist > 100)
        dist = round(dist, 1);
    return dist + tr2("kmAbbr");
}

function createTimeString(time) {
    var tmpTime = round(time / 60 / 1000, 1000);
    var resTimeStr;
    if (tmpTime > 60) {
        if (tmpTime / 60 > 24) {
            resTimeStr = floor(tmpTime / 60 / 24, 1) + tr2("dayAbbr");
            tmpTime = floor(((tmpTime / 60) % 24), 1);
            if (tmpTime > 0)
                resTimeStr += " " + tmpTime + tr2("hourAbbr");
        } else {
            resTimeStr = floor(tmpTime / 60, 1) + tr2("hourAbbr");
            tmpTime = floor(tmpTime % 60, 1);
            if (tmpTime > 0)
                resTimeStr += " " + tmpTime + tr2("minAbbr");
        }
    } else
        resTimeStr = round(tmpTime % 60, 1) + tr2("minAbbr");
    return resTimeStr;
}

function addInstruction(main, instr, instrIndex, lngLat) {
    var sign = instr.sign;
    if (instrIndex === 0)
        sign = "marker-icon-green";
    else if (sign === -3)
        sign = "sharp_left";
    else if (sign === -2)
        sign = "left";
    else if (sign === -1)
        sign = "slight_left";
    else if (sign === 0)
        sign = "continue";
    else if (sign === 1)
        sign = "slight_right";
    else if (sign === 2)
        sign = "right";
    else if (sign === 3)
        sign = "sharp_right";
    else if (sign === 4)
        sign = "marker-icon-red";
    else
        throw "did not found sign " + sign;
    var title = instr.text;
    if(instr.annotationText) {
        if(!title)
            title = instr.annotationText;
        else
            title = title + ", " + instr.annotationText;
    }
    var distance = instr.distance;
    var str = "<td class='instr_title'>" + title + "</td>";

    if (distance > 0) {
        str += " <td class='instr_distance'><span>"
                + createDistanceString(distance) + "<br/>"
                + createTimeString(instr.time) + "</span></td>";
    }

    if (sign !== "continue") {
        var indiPic = "<img class='pic' style='vertical-align: middle' src='" +
                window.location.pathname + "img/" + sign + ".png'/>";
        str = "<td class='instr_pic'>" + indiPic + "</td>" + str;
    } else
        str = "<td class='instr_pic'/>" + str;
    var instructionDiv = $("<tr class='instruction'/>");
    instructionDiv.html(str);
    if (lngLat) {
        instructionDiv.click(function() {
            if (routeSegmentPopup)
                map.removeLayer(routeSegmentPopup);

            routeSegmentPopup = L.popup().
                    setLatLng([lngLat[1], lngLat[0]]).
                    setContent(title).
                    openOn(map);
        });
    }
    main.append(instructionDiv);
}

function getCenter(bounds) {
    var center = {
        lat: 0,
        lng: 0
    };
    if (bounds.initialized) {
        center.lat = (bounds.minLat + bounds.maxLat) / 2;
        center.lng = (bounds.minLon + bounds.maxLon) / 2;
    }
    return center;
}

function parseUrlWithHisto() {
    if (window.location.hash)
        return parseUrl(window.location.hash);

    return parseUrl(window.location.search);
}

function parseUrlAndRequest() {
    return parseUrl(window.location.search);
}

function parseUrl(query) {
    var index = query.indexOf('?');
    if (index >= 0)
        query = query.substring(index + 1);
    var res = {};
    var vars = query.split("&");
    for (var i = 0; i < vars.length; i++) {
        var indexPos = vars[i].indexOf("=");
        if (indexPos < 0)
            continue;

        var key = vars[i].substring(0, indexPos);
        var value = vars[i].substring(indexPos + 1);
        value = decodeURIComponent(value.replace(/\+/g, ' '));

        if (typeof res[key] === "undefined")
            res[key] = value;
        else if (typeof res[key] === "string") {
            var arr = [res[key], value];
            res[key] = arr;
        } else
            res[key].push(value);

    }
    return res;
}

function mySubmit() {
    var fromStr = $("#fromInput").val();
    var toStr = $("#toInput").val();
    if (toStr == "To" && fromStr == "From") {
        // TODO print warning
        return;
    }
    if (fromStr == "From") {
        // no special function
        return;
    }
    if (toStr == "To") {
        // lookup area
        ghRequest.from = new GHInput(fromStr);
        $.when(resolveFrom()).done(function() {
            focus(ghRequest.from);
        });
        return;
    }
    // route!
    resolveCoords(fromStr, toStr);
}

function floor(val, precision) {
    if (!precision)
        precision = 1e6;
    return Math.floor(val * precision) / precision;
}

function round(val, precision) {
    if (precision === undefined)
        precision = 1e6;
    return Math.round(val * precision) / precision;
}

function tr(key, args) {
    return tr2("web." + key, args);
}

function tr2(key, args) {
    if (key == null) {
        console.log("ERROR: key was null?");
        return "";
    }
    if (defaultTranslationMap == null) {
        console.log("ERROR: defaultTranslationMap was not initialized?");
        return key;
    }
    key = key.toLowerCase();
    var val = defaultTranslationMap[key];
    if (val == null && enTranslationMap)
        val = enTranslationMap[key];
    if (val == null)
        return key;

    return stringFormat(val, args);
}

function stringFormat(str, args) {
    if (typeof args === 'string')
        args = [args];

    if (str.indexOf("%1$s") >= 0) {
        // with position arguments ala %2$s
        return str.replace(/\%(\d+)\$s/g, function(match, matchingNum) {
            matchingNum--;
            return typeof args[matchingNum] != 'undefined' ? args[matchingNum] : match;
        });
    } else {
        // no position so only values ala %s
        var matchingNum = 0;
        return str.replace(/\%s/g, function(match) {
            var val = typeof args[matchingNum] != 'undefined' ? args[matchingNum] : match;
            matchingNum++;
            return val;
        });
    }
}

function initI18N() {
    $('#searchButton').attr("value", tr("searchButton"));
    $('#fromInput').attr("placeholder", tr("fromHint"));
    $('#toInput').attr("placeholder", tr("toHint"));
}

function exportGPX() {
    if (ghRequest.from.isResolved() && ghRequest.to.isResolved())
        window.open(ghRequest.createGPXURL());
    return false;
}

function getAutoCompleteDiv(fromOrTo) {
    return $('#' + fromOrTo + 'Input');
}

function hideAutoComplete() {
    getAutoCompleteDiv("from").autocomplete().hide();
    getAutoCompleteDiv("to").autocomplete().hide();
}

function formatValue(orig, query) {
    var pattern = '(' + $.Autocomplete.utils.escapeRegExChars(query) + ')';
    return orig.replace(new RegExp(pattern, 'gi'), '<strong>$1<\/strong>');
}

function setAutoCompleteList(fromOrTo, ghRequestLoc) {
    var isFrom = fromOrTo === "from";
    var pointIndex = isFrom ? 1 : 2;
    var myAutoDiv = getAutoCompleteDiv(fromOrTo);    

    var options = {
        containerClass: "complete-" + pointIndex,
        /* as we use jsonp we need to set the timeout to a small value */
        timeout: 1000,
        /* avoid too many requests when typing quickly */
        deferRequestBy: 5,
        minChars: 2,
        maxHeight: 510,
        noCache: true,
        /* this default could be problematic: preventBadQueries: true, */
        triggerSelectOnValidInput: false,
        autoSelectFirst: false,
        paramName: "q",
        dataType: "jsonp",
        onSearchStart: function(params) {
            // query server only if not a parsable point (i.e. format lat,lon)
            var val = new GHInput(params.q).lat;
            return val === undefined;
        },
        serviceUrl: function() {
            return ghRequest.createGeocodeURL();
        },
        transformResult: function(response, originalQuery) {
            response.suggestions = [];
            for (var i = 0; i < response.hits.length; i++) {
                var hit = response.hits[i];
                response.suggestions.push({value: dataToText(hit), data: hit});
            }
            return response;
        },
        onSearchError: function(element, q, jqXHR, textStatus, errorThrown) {
            // too many errors if interrupted console.log(element + ", " + JSON.stringify(q) + ", textStatus " + textStatus + ", " + errorThrown);
        },
        formatResult: function(suggestion, currInput) {
            // avoid highlighting for now as this breaks the html sometimes
            return dataToHtml(suggestion.data, currInput);
        },
        onSelect: function(suggestion) {
            options.onPreSelect(suggestion);
        },
        onPreSelect: function(suggestion) {
            var req = ghRequest.to;
            if (isFrom)
                req = ghRequest.from;

            myAutoDiv.autocomplete().disable();

            var point = suggestion.data.point;
            req.setCoord(point.lat, point.lng);

            req.input = suggestion.value;
            if (ghRequest.from.isResolved() && ghRequest.to.isResolved())
                routeLatLng(ghRequest);
            else
                focus(req, 15, isFrom);

            myAutoDiv.autocomplete().enable();
        }
    };

    myAutoDiv.autocomplete(options);
    $("#"+fromOrTo+"Input").focusout(function() {
        myAutoDiv.autocomplete().hide();
    });
}

function dataToHtml(data, query) {
    var element = "";
    if (data.name)
        element += "<div class='nameseg'>" + formatValue(data.name, query) + "</div>";
    var addStr = "";
    if (data.postcode)
        addStr = data.postcode;
    if (data.city)
        addStr = insComma(addStr, data.city);
    if (data.country)
        addStr = insComma(addStr, data.country);

    if (addStr)
        element += "<div class='cityseg'>" + formatValue(addStr, query) + "</div>";

    if (data.osm_key === "highway") {
        // ignore
    }
    if (data.osm_key === "place") {
        element += "<span class='moreseg'>" + data.osm_value + "</span>";
    } else
        element += "<span class='moreseg'>" + data.osm_key + "</span>";
    return element;
}

function dataToText(data) {
    var text = "";
    if (data.name)
        text += data.name;

    if (data.postcode)
        text = insComma(text, data.postcode);

    // make sure name won't be duplicated
    if (data.city && text.indexOf(data.city) < 0)
        text = insComma(text, data.city);

    if (data.country && text.indexOf(data.country) < 0)
        text = insComma(text, data.country);
    return text;
}
