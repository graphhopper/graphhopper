/*
 * If you want to query another API append this something like
 * &host=http://graphhopper.com/routing
 * to the URL or overwrite the 'host' variable.
 */
var tmpArgs = parseUrlWithHisto();
var host = tmpArgs["host"];
//var host = "http://graphhopper.com/routing";
if (host == null) {
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
                    vehicle = vehicle.toLowerCase();
                    var button = $("<button class='vehicle-btn' title='" + tr(vehicle) + "'/>")
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
                    var vehicles = json.supported_vehicles.split(",");
                    if (vehicles.length > 1)
                        ghRequest.vehicle = vehicles[0];
                    for (var i = 0; i < vehicles.length; i++) {
                        vehiclesDiv.append(createButton(vehicles[i]));
                    }
                }

                initMap();

//        var data = JSON.parse("[[10.4049076,48.2802518],[10.405231,48.2801396],...]");
//        var tempFeature = {
//            "type": "Feature", "geometry": { "type": "LineString", "coordinates": data }
//        };        
//        routingLayer.addData(tempFeature);

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
});

function initFromParams(params, doQuery) {
    ghRequest.init(params);
    var fromAndTo = params.from && params.to;
    var routeNow = params.point && params.point.length == 2 || fromAndTo;
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

function initMap() {
    var mapDiv = $("#map");
    var width = $(window).width() - 300;
    if (width < 100)
        width = $(window).width() - 5;
    var height = $(window).height() - 5;
    mapDiv.width(width).height(height);
    if (height > 350)
        height -= 265;
    $("#info").css("max-height", height);
    console.log("init map at " + JSON.stringify(bounds));

    // mapquest provider
    var moreAttr = 'Data &copy; <a href="http://www.openstreetmap.org/copyright">OSM</a>,'
            + 'JS: <a href="http://leafletjs.com/">Leaflet</a>';

    var tp = "ls";
    if (L.Browser.retina)
        tp = "lr";

    var lyrk = L.tileLayer('http://{s}.tiles.lyrk.org/' + tp + '/{z}/{x}/{y}?apikey=6e8cfef737a140e2a58c8122aaa26077', {
        attribution: '<a href="http://geodienste.lyrk.de/">Lyrk</a>,' + moreAttr,
        subdomains: ['a', 'b', 'c']
    });

    var mapquest = L.tileLayer('http://{s}.mqcdn.com/tiles/1.0.0/osm/{z}/{x}/{y}.png', {
        attribution: '<a href="http://open.mapquest.co.uk">MapQuest</a>,' + moreAttr,
        subdomains: ['otile1', 'otile2', 'otile3', 'otile4']
    });

    var mapquestAerial = L.tileLayer('http://{s}.mqcdn.com/tiles/1.0.0/sat/{z}/{x}/{y}.png', {
        attribution: '<a href="http://open.mapquest.co.uk">MapQuest</a>,' + moreAttr,
        subdomains: ['otile1', 'otile2', 'otile3', 'otile4']
    });

    var thunderTransport = L.tileLayer('http://{s}.tile.thunderforest.com/transport/{z}/{x}/{y}.png', {
        attribution: '<a href="http://www.thunderforest.com/transport/">Thunderforest Transport</a>,' + moreAttr,
        subdomains: ['a', 'b', 'c']
    });

    var thunderCycle = L.tileLayer('http://{s}.tile.thunderforest.com/cycle/{z}/{x}/{y}.png', {
        attribution: '<a href="http://www.thunderforest.com/opencyclemap/">Thunderforest Cycle</a>,' + moreAttr,
        subdomains: ['a', 'b', 'c']
    });

    var thunderOutdoors = L.tileLayer('http://{s}.tile.thunderforest.com/outdoors/{z}/{x}/{y}.png', {
        attribution: '<a href="http://www.thunderforest.com/outdoors/">Thunderforest Outdoors</a>,' + moreAttr,
        subdomains: ['a', 'b', 'c']
    });

    //    var mapbox = L.tileLayer('http://a.tiles.mapbox.com/v3/mapbox.world-bright/{z}/{x}/{y}.png', {
    //        attribution: '<a href="http://www.mapbox.com">MapBox</a>,' + moreAttr, 
    //        subdomains: ['a','b','c']
    //    });    

    var wrk = L.tileLayer('http://{s}.wanderreitkarte.de/topo/{z}/{x}/{y}.png', {
        attribution: '<a href="http://wanderreitkarte.de">WanderReitKarte</a>,' + moreAttr,
        subdomains: ['topo4', 'topo', 'topo2', 'topo3']
    });

    var osm = L.tileLayer('http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: moreAttr
    });

    var osmde = L.tileLayer('http://{s}.tile.openstreetmap.de/tiles/osmde/{z}/{x}/{y}.png', {
        attribution: moreAttr
    });

    // only work if you zoom a bit deeper
    var lang = "en_US";
    var apple = L.tileLayer('http://gsp2.apple.com/tile?api=1&style=slideshow&layers=default&lang=' + lang + '&z={z}&x={x}&y={y}&v=9', {
        maxZoom: 17,
        attribution: 'Map data and Imagery &copy; <a href="http://www.apple.com/ios/maps/">Apple</a>,' + moreAttr
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
        // didn't found a usage policy for this "Apple": apple,
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
        var foundDiv = $("#" + fromOrTo + "ResolveFound");
        // deinstallation of completion if there was one
        // if (getAutoCompleteDiv(fromOrTo).autocomplete())
        //    getAutoCompleteDiv(fromOrTo).autocomplete().dispose();

        foundDiv.empty();
        var list = locCoord.resolvedList;
        if (locCoord.error) {
            errorDiv.text(locCoord.error);
        } else if (list) {
            var anchor = String.fromCharCode(0x25BC);
            var linkPart = $("<a>" + anchor + "<small>" + list.length + "</small></a>");
            foundDiv.append(linkPart.click(function(e) {
                setAutoCompleteList(fromOrTo, locCoord);
            }));
        }

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
        descriptionDiv.html(tr("routeInfo", [tmpDist, tmpTime]));

        var hiddenDiv = $("<div id='routeDetails'/>");
        hiddenDiv.hide();

        var toggly = $("<button style='font-size:14px; float: right; font-weight: bold; padding: 0px'>+</button>");
        toggly.click(function() {
            hiddenDiv.toggle();
        });
        $("#info").prepend(toggly);
        var infoStr = "took: " + round(json.info.took, 1000) + "s"
                + ", points: " + path.points.length;

        hiddenDiv.append("<span>" + infoStr + "</span>");
        $("#info").append(hiddenDiv);

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
        if (request.vehicle.toUpperCase() == "FOOT") {
            addToGoogle = "&dirflg=w";
            addToBing = "&mode=W";
        } else if ((request.vehicle.toUpperCase() == "BIKE") ||
                (request.vehicle.toUpperCase() == "RACINGBIKE") ||
                (request.vehicle.toUpperCase() == "MTB")) {
            addToGoogle = "&dirflg=b";
            // ? addToBing = "&mode=B";
        }
        googleLink.attr("href", "http://maps.google.com/?q=from:" + from + "+to:" + to + addToGoogle);
        hiddenDiv.append(googleLink);
        var bingLink = $("<a>Bing</a> ");
        bingLink.attr("href", "http://www.bing.com/maps/default.aspx?rtp=adr." + from + "~adr." + to + addToBing);
        hiddenDiv.append(bingLink);

        if (host.indexOf("gpsies.com") > 0)
            hiddenDiv.append("<div id='hosting'>The routing API is hosted by <a href='http://gpsies.com'>GPSies.com</a></div>");

        $('.defaulting').each(function(index, element) {
            $(element).css("color", "black");
        });

        if (path.instructions) {
            var instructionsElement = $("<table id='instructions'><colgroup>"
                    + "<col width='10%'><col width='65%'><col width='25%'></colgroup>");
            $("#info").append(instructionsElement);
            for (var m = 0; m < path.instructions.length; m++) {
                var instr = path.instructions[m];
                var sign = instr.sign;
                if (m == 0)
                    sign = "marker-from";
                else if (sign == -3)
                    sign = "sharp_left";
                else if (sign == -2)
                    sign = "left";
                else if (sign == -1)
                    sign = "slight_left";
                else if (sign == 0)
                    sign = "continue";
                else if (sign == 1)
                    sign = "slight_right";
                else if (sign == 2)
                    sign = "right";
                else if (sign == 3)
                    sign = "sharp_right";
                else if (sign == 4)
                    sign = "marker-to";
                else
                    throw "did not found indication " + sign;

                var lngLat = path.points.coordinates[instr.interval[0]];
                addInstruction(instructionsElement, sign, instr.text, instr.distance, instr.time, lngLat);
            }
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

function addInstruction(main, indi, title, distance, milliEntry, lngLat) {
    var indiPic = "<img class='instr_pic' style='vertical-align: middle' src='" +
            window.location.pathname + "img/" + indi + ".png'/>";
    var str = "<td class='instr_title'>" + title + "</td>";

    if (distance > 0) {
        str += " <td class='instr_distance_td'><span class='instr_distance'>"
                + createDistanceString(distance) + "<br/>"
                + createTimeString(milliEntry) + "</span></td>";
    }

    if (indi !== "continue")
        str = "<td>" + indiPic + "</td>" + str;
    else
        str = "<td/>" + str;
    var instructionDiv = $("<tr class='instruction'/>");
    instructionDiv.html(str);
    if (lngLat) {
        instructionDiv.click(function() {
            if (routeSegmentPopup)
                map.removeLayer(routeSegmentPopup);
            
            routeSegmentPopup = L.popup().
                    setLatLng([lngLat[1], lngLat[0]]).
                    setContent(indiPic + " " + title).
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
    if (fromOrTo === "from")
        return $('#fromInput')
    else
        return $('#toInput');
}

function setAutoCompleteList(fromOrTo, ghRequestLoc) {
    function formatValue(suggestionValue, currentValue) {
        var pattern = '(' + $.Autocomplete.utils.escapeRegExChars(currentValue) + ')';
        return suggestionValue.replace(new RegExp(pattern, 'gi'), '<strong>$1<\/strong>');
    }
    var isFrom = fromOrTo === "from";
    var pointIndex = isFrom ? 1 : 2;
    var fakeCurrentInput = ghRequestLoc.input.toLowerCase();
    var valueDataList = [];
    var list = ghRequestLoc.resolvedList;
    for (var index in list) {
        var dataItem = list[index];
        valueDataList.push({value: dataToText(dataItem), data: dataItem});
    }

    var options = {
        maxHeight: 510,
        triggerSelectOnValidInput: false,
        autoSelectFirst: false,
        lookup: valueDataList,
        onSearchError: function(element, q, jqXHR, textStatus, errorThrown) {
            console.log(element + ", " + q + ", textStatus " + textStatus + ", " + errorThrown);
        },
        formatResult: function(suggestion, currInput) {
            // avoid highlighting for now as this breaks the html sometimes
            return dataToHtml(suggestion.data);
        },
        lookupFilter: function(suggestion, originalQuery, queryLowerCase) {
            if (queryLowerCase === fakeCurrentInput)
                return true;
            return suggestion.value.toLowerCase().indexOf(queryLowerCase) !== -1;
        }
    };
    options.onSelect = function(suggestion) {
        options.onPreSelect(suggestion);
    };
    options.onPreSelect = function(suggestion) {
        var data = suggestion.data;
        ghRequestLoc.setCoord(data.lat, data.lng);
        ghRequestLoc.input = dataToText(suggestion.data);
        if (ghRequest.from.isResolved() && ghRequest.to.isResolved())
            routeLatLng(ghRequest);
        else if (suggestion.data.boundingbox) {
            var bbox = suggestion.data.box;
            focusWithBounds(ghRequestLoc, [[bbox[0], bbox[2]], [bbox[1], bbox[3]]], isFrom);
        } else
            focus(ghRequestLoc, 15, isFrom);
    };

    options.containerClass = "complete-" + pointIndex;
    var myAutoDiv = getAutoCompleteDiv(fromOrTo);
    myAutoDiv.autocomplete(options);
    myAutoDiv.autocomplete().forceSuggest("");
    myAutoDiv.focus();
}

function dataToHtml(data) {
    var data = data.locationDetails;
    var text = "";
    if (data.road)
        text += "<div class='roadseg'>" + data.road + "</div>";
    if (data.city) {
        text += "<div class='cityseg'>" + insComma(data.city, data.country) + "</div>";
    }
    if (data.country)
        text += "<div class='moreseg'>" + data.more + "</div>";
    return text;
}

// do not print everything as nominatim slows down or doesn't properly handle if continent etc is included
function dataToText(data) {
    var data = data.locationDetails;
    var text = "";
    if (data.road)
        text += data.road;

    if (data.city) {
        if (text.length > 0)
            text += ", ";
        text += data.city;
    }

    if (data.postcode) {
        if (text.length > 0)
            text += ", ";
        text += data.postcode;
    }

    if (data.country) {
        if (text.length > 0)
            text += ", ";
        var tmp = $.trim(data.country.replace(data.city, '').replace(data.city + ", ", ''));
        text += tmp;
    }
    return text;
}
