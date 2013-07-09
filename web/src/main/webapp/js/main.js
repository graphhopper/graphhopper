// fixing cross domain support e.g in Opera
jQuery.support.cors = true;

//var nominatim = "http://open.mapquestapi.com/nominatim/v1/search.php";
//var nominatim_reverse = "http://open.mapquestapi.com/nominatim/v1/reverse.php";
var nominatim = "http://nominatim.openstreetmap.org/search";
var nominatim_reverse = "http://nominatim.openstreetmap.org/reverse";
var routingLayer;
var map;
var browserTitle = "GraphHopper Maps";
var firstClickToRoute;
var iconTo = L.icon({
    iconUrl: './img/marker-to.png', 
    iconAnchor: [10, 16]
});
var iconFrom = L.icon({
    iconUrl: './img/marker-from.png', 
    iconAnchor: [10, 16]
});

var bounds = {};
LOCAL=false;
var host;
if(LOCAL)
    host = "http://localhost:8989";
else {
    // cross origin:
    host = "http://graphhopper.gpsies.com";
}
var ghRequest = new GHRequest(host);
ghRequest.algoType = "fastest";
//ghRequest.algorithm = "dijkstra";
var everPushedSomething = false;

$(document).ready(function(e) {    
    var initialUrl = location.href;

    var History = window.History;
    if (History.enabled) {
        History.Adapter.bind(window, 'statechange', function(){
            // First important workaround:
            // Chrome and Safari always emit a popstate event on page load, but Firefox doesn’t
            // https://github.com/defunkt/jquery-pjax/issues/143#issuecomment-6194330
            var onloadPop = !everPushedSomething && location.href == initialUrl;
            if (onloadPop) return;
            var state = History.getState();
            console.log(state);            
            //             initFromParams(parseUrl(state.url), true);
            initFromParams(state.data, true);
        });
    }
    initForm();
    ghRequest.getInfo(function(json) {
        // OK bounds            
        var tmp = json.bbox;              
        bounds.initialized = true;
        bounds.minLon = tmp[0];
        bounds.minLat = tmp[1];
        bounds.maxLon = tmp[2];
        bounds.maxLat = tmp[3];
        var vehiclesDiv = $("#vehicles");
        function createButton(text) {
            var button = $("<button/>")            
            button.attr('id', text);
            button.html(text.charAt(0) + text.substr(1).toLowerCase());
            button.click(function() {
                ghRequest.vehicle = text;
                resolveFrom();
                resolveTo();
                routeLatLng(ghRequest);
            });
            return button;
        }
        
        if(json.supportedVehicles) {
            var vehicles = json.supportedVehicles.split(",");
            if(vehicles.length > 1)
                for(var i = 0; i < vehicles.length; i++) {
                    vehiclesDiv.append(createButton(vehicles[i]));
                }
        }
    }, function(err) {
        // error bounds
        console.log(err);
        $('#error').html('GraphHopper API offline? ' + host);
    }).done(function() {
        var params = parseUrlWithHisto();        
        initMap();
        // force same behaviour for all browsers: on page load no history event will be fired
        // 
        // put into history, (first popstate is muted -> see above)
        initFromParams(params, false);
        // force to true even if history.js is disabled due to cookie disallow etc
        everPushedSomething = true;
        // execute query
        initFromParams(params, true);        
    }).error(function() {
        bounds = {
            "minLon" : -180, 
            "minLat" : -90, 
            "maxLon" : 180, 
            "maxLat" : 90
        };
        initMap();
    });
});

function initFromParams(params, doQuery) {
    ghRequest.init(params);
    var fromAndTo = params.from && params.to;    
    var routeNow = params.point && params.point.length == 2 || fromAndTo;
    if(routeNow) {
        if(fromAndTo)
            resolveCoords(params.from, params.to, doQuery);
        else
            resolveCoords(params.point[0], params.point[1], doQuery);
    }
}

function resolveCoords(fromStr, toStr, doQuery) { 
    routingLayer.clearLayers();
    if(fromStr !== ghRequest.from.input || !ghRequest.from.isResolved())
        ghRequest.from = new GHInput(fromStr);
    
    if(toStr !== ghRequest.to.input || !ghRequest.to.isResolved())
        ghRequest.to = new GHInput(toStr);
    
    if(ghRequest.from.lat && ghRequest.to.lat) {
        // do not wait for resolve
        resolveFrom();
        resolveTo();
        routeLatLng(ghRequest, doQuery);
    } else {
        // wait for resolve as we need the coord for routing     
        $.when(resolveFrom(), resolveTo()).done(function(fromArgs, toArgs) {                
            routeLatLng(ghRequest, doQuery);
        });    
    }
}

function initMap() {
    var mapDiv = $("#map");
    var width = $(window).width() - 300;
    if(width < 100)
        width = $(window).width() - 5;
    var height = $(window).height() - 5;
    mapDiv.width(width).height(height);
    if(height > 350)
        height -= 255;
    $("#info").css("max-height", height);
    console.log("init map at " + JSON.stringify(bounds));
    
    // mapquest provider
    var moreAttr = 'Data &copy; <a href="http://www.openstreetmap.org/">OSM</a>,'
    + 'JS: <a href="http://leafletjs.com/">Leaflet</a>';
    var mapquest = L.tileLayer('http://{s}.mqcdn.com/tiles/1.0.0/osm/{z}/{x}/{y}.png', {
        attribution: '<a href="http://open.mapquest.co.uk">MapQuest</a>,' + moreAttr, 
        subdomains: ['otile1','otile2','otile3','otile4']
    });
    
    var mapquestAerial = L.tileLayer('http://{s}.mqcdn.com/tiles/1.0.0/sat/{z}/{x}/{y}.png', {
        attribution: '<a href="http://open.mapquest.co.uk">MapQuest</a>,' + moreAttr, 
        subdomains: ['otile1','otile2','otile3','otile4']
    });
    
    //    var mapbox = L.tileLayer('http://a.tiles.mapbox.com/v3/mapbox.world-bright/{z}/{x}/{y}.png', {
    //        attribution: '<a href="http://www.mapbox.com">MapBox</a>,' + moreAttr, 
    //        subdomains: ['a','b','c']
    //    });    
    
    var wrk = L.tileLayer('http://{s}.wanderreitkarte.de/topo/{z}/{x}/{y}.png', {
        attribution: '<a href="http://wanderreitkarte.de">WanderReitKarte</a>,' + moreAttr, 
        subdomains: ['topo4','topo','topo2','topo3']
    });
    
    var cloudmade = L.tileLayer('http://{s}.tile.cloudmade.com/{key}/{styleId}/256/{z}/{x}/{y}.png', {
        attribution: '<a href="http://cloudmade.com">Cloudmade</a>,' + moreAttr,
        key: '43b079df806c4e03b102055c4e1a8ba8',
        styleId: 997
    });
    
    var osm = L.tileLayer('http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: moreAttr
    });
    
    var osmde = L.tileLayer('http://{s}.tile.openstreetmap.de/tiles/osmde/{z}/{x}/{y}.png', {
        attribution: moreAttr
    });
    
    // default
    map = L.map('map' , {
        layers: [mapquest]
    });
    
    var baseMaps = {
        "MapQuest": mapquest,        
        "MapQuest Aerial": mapquestAerial,
        //        "MapBox": mapbox,
        "WanderReitKarte": wrk,
        "Cloudmade": cloudmade,
        "OpenStreetMap": osm,
        "OpenStreetMap.de": osmde
    };
    
    //    var overlays = {
    //        "MapQuest Hybrid": mapquest
    //    };
    
    // no layers for small browser windows
    if($(window).width() > 400) {
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
            "coordinates":[
            [bounds.minLon, bounds.minLat], 
            [bounds.maxLon, bounds.minLat], 
            [bounds.maxLon, bounds.maxLat], 
            [bounds.minLon, bounds.maxLat],
            [bounds.minLon, bounds.minLat]]
        }
    };
    
    if(bounds.initialized)
        L.geoJson(geoJson, {
            "style": myStyle
        }).addTo(map); 
    
    routingLayer = L.geoJson().addTo(map);    
    firstClickToRoute = true;
    function onMapClick(e) {       
        var latlng = e.latlng;
        if(firstClickToRoute) {
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
        }
    }

    map.on('click', onMapClick);       
}

function setFlag(latlng, isFrom) {
    if(latlng.lat) {
        var marker = L.marker([latlng.lat, latlng.lng], {
            icon: (isFrom? iconFrom : iconTo),
            draggable: true
        }).addTo(routingLayer).bindPopup(isFrom? "Start" : "End");                  
        marker.on('dragend', function(e) {
            routingLayer.clearLayers();
            // inconsistent leaflet API: event.target.getLatLng vs. mouseEvent.latlng?
            var latlng = e.target.getLatLng();
            if(isFrom) {
                ghRequest.from.setCoord(latlng.lat, latlng.lng);
                resolveFrom();                                
            } else {
                ghRequest.to.setCoord(latlng.lat, latlng.lng);
                resolveTo();
            }
            // do not wait for resolving and avoid zooming when dragging
            ghRequest.doZoom = false;
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

function resolve(fromOrTo, point) {
    $("#" + fromOrTo + "Flag").hide();
    $("#" + fromOrTo + "Indicator").show();
    return getInfoFromLocation(point).done(function() {        
        $("#" + fromOrTo + "Input").val(point.input);
        if(point.resolvedText)
            $("#" + fromOrTo + "Found").html(point.resolvedText);        
        
        $("#" + fromOrTo + "Flag").show();
        $("#" + fromOrTo + "Indicator").hide();
        return point;
    });
}

var getInfoTmpCounter = 0;
function getInfoFromLocation(locCoord) {
    // make sure that the demo route always works even if external geocoding is down!
    if(locCoord.input.toLowerCase() == "madrid") {
        locCoord.lat = 40.416698;
        locCoord.lng = -3.703551;
        locCoord.resolvedText = "Madrid, Área metropolitana de Madrid y Corredor del Henares, Madrid, Community of Madrid, Spain, European Union";
    }
    if(locCoord.input.toLowerCase() == "moscow") {
        locCoord.lat = 55.751608;
        locCoord.lng = 37.618775;
        locCoord.resolvedText = "Borowizki-Straße Moscow Russian Federation";
    }
    if(locCoord.resolvedText) {
        var tmpDefer = $.Deferred();
        tmpDefer.resolve([locCoord]);
        return tmpDefer;   
    }
        
    // Every call to getInfoFromLocation needs to get its own callback. Sadly we need to overwrite 
    // the callback method name for nominatim and cannot use the default jQuery behaviour.
    getInfoTmpCounter++;
    var url;
    if(locCoord.lat && locCoord.lng) {
        // in every case overwrite name
        locCoord.resolvedText = "Error while looking up coordinate";
        url = nominatim_reverse + "?lat=" + locCoord.lat + "&lon="
        + locCoord.lng + "&format=json&zoom=16&json_callback=reverse_callback" + getInfoTmpCounter;
        return $.ajax({
            url: url,
            type : "GET",
            dataType: "jsonp",
            timeout: 3000,
            jsonpCallback: 'reverse_callback' + getInfoTmpCounter            
        }).fail(function(err) { 
            // not critical => no alert
            console.log(err);
        }).pipe(function(json) {
            if(!json) {
                locCoord.resolvedText = "No description found for coordinate";
                return [locCoord];
            }
            var address = json.address;
            locCoord.resolvedText = "";
            if(address.road)
                locCoord.resolvedText += address.road + " ";
            if(address.city)
                locCoord.resolvedText += address.city + " ";
            if(address.country)
                locCoord.resolvedText += address.country;            
            
            return [locCoord];
        });        
    } else {
        // see https://trac.openstreetmap.org/ticket/4683 why limit=3 and not 1
        url = nominatim + "?format=json&q=" + encodeURIComponent(locCoord.input)
        +"&limit=3&json_callback=search_callback" + getInfoTmpCounter;
        if(bounds.initialized) {
            // minLon, minLat, maxLon, maxLat => left, top, right, bottom
            url += "&bounded=1&viewbox=" + bounds.minLon + ","+bounds.maxLat + ","+bounds.maxLon +","+ bounds.minLat;
        }
        locCoord.resolvedText = "Error while looking up area description";
        return $.ajax({
            url: url,
            type : "GET",
            dataType: "jsonp",
            timeout: 3000,
            jsonpCallback: 'search_callback' + getInfoTmpCounter
        }).fail(createCallback("[nominatim] Problem while looking up location " + locCoord.input)).
        pipe(function(jsonArgs) {
            var json = jsonArgs[0];
            if(!json) {
                locCoord.resolvedText = "No area description found";                
                return [locCoord];
            }        
            locCoord.resolvedText = json.display_name;
            locCoord.lat = round(json.lat);
            locCoord.lng = round(json.lon);            
            return [locCoord];
        });
    }
}

function createCallback(errorFallback) {
    return function(err) {
        if (err.statusText && err.statusText != "OK")
            alert(errorFallback + ", " + err.statusText);
        else
            alert(errorFallback);

        console.log(errorFallback + " " + JSON.stringify(err));
    };
}

function focus(coord) {
    if(coord.lat && coord.lng) {
        routingLayer.clearLayers();
        map.setView(new L.LatLng(coord.lat, coord.lng), 11);
        setFlag(coord, true);
    }
}
function routeLatLng(request, doQuery) {
    // doZoom should not show up in the URL but in the request object to avoid zooming for history change
    var doZoom = request.doZoom;
    request.doZoom = true;
        
    var urlForHistory = request.createFullURL();
    // not enabled e.g. if no cookies allowed (?)
    // if disabled we have to do the query and cannot rely on the statechange history event    
    if(!doQuery && History.enabled) {
        // 2. important workaround for encoding problems in history.js
        var params = parseUrl(urlForHistory);
        console.log(params);
        params.doZoom = doZoom;
        History.pushState(params, browserTitle, urlForHistory);
        return;
    }    
    // BUT if this is the very first query and no history support skip the query
    if(!History.enabled && !everPushedSomething) {
        return;
    }
    
    clickToRoute = true;
    $("#info").empty();
    $("#info").show();
    var descriptionDiv = $("<div/>");    
    $("#info").append(descriptionDiv);
    
    var from = request.from.toString();
    var to = request.to.toString();
    if(!from || !to) {
        descriptionDiv.html('<small>routing not possible. location(s) not found in the area</small>');
        return;
    }
    
    routingLayer.clearLayers();    
    setFlag(request.from, true);
    setFlag(request.to, false);    
    
    $("#vehicles button").removeClass();
    $("button#"+request.vehicle.toUpperCase()).addClass("bold");
    
    var urlForAPI = request.createURL("point=" + from + "&point=" + to);    
    descriptionDiv.html('<img src="img/indicator.gif"/> Search Route ...');
    request.doRequest(urlForAPI, function (json) {        
        if(json.info.errors) {
            var tmpErrors = json.info.errors;
            for (var m = 0; m < tmpErrors.length; m++) {
                descriptionDiv.append("<div class='error'>" + tmpErrors[m].message + "</div>");
            }
            return;
        } else if(json.info.routeFound === false) {
            descriptionDiv.html('Route not found! Disconnected areas?');
            return;
        }
        var geojsonFeature = {
            "type": "Feature",                   
            // "style": myStyle,                
            "geometry": json.route.data
        };
        
        routingLayer.addData(geojsonFeature);        
        if(json.route.bbox && doZoom) {
            var minLon = json.route.bbox[0];
            var minLat = json.route.bbox[1];
            var maxLon = json.route.bbox[2];
            var maxLat = json.route.bbox[3];
            var tmpB = new L.LatLngBounds(new L.LatLng(minLat, minLon), new L.LatLng(maxLat, maxLon));
            map.fitBounds(tmpB);
        }
        
        var tmpTime = round(json.route.time / 60, 1000);        
        if(tmpTime > 60) {
            if(tmpTime / 60 > 24)
                tmpTime = floor(tmpTime / 60 / 24, 1) + "d " + round(((tmpTime / 60) % 24), 1) + "h";
            else
                tmpTime = floor(tmpTime / 60, 1) + "h " + round(tmpTime % 60, 1) + "min";
        } else
            tmpTime = round(tmpTime % 60, 1) + "min";
        var dist = round(json.route.distance, 100);
        if(dist > 100)
            dist = round(dist, 1);
        descriptionDiv.html("<b>"+ dist + "km</b> will take " + tmpTime);        

        var hiddenDiv = $("<div id='routeDetails'/>");
        hiddenDiv.hide();
        
        var toggly = $("<button style='font-size:9px; float: right; padding: 0px'>more</button>");
        toggly.click(function() {
            hiddenDiv.toggle();
        })
        $("#info").prepend(toggly);
        var infoStr = "took: " + round(json.info.took, 1000) + "s"
        +", points: " + json.route.data.coordinates.length;
        if(json.route.instructions)
            infoStr += ", instructions: " + json.route.instructions.descriptions.length;
        hiddenDiv.append("<span>" + infoStr + "</span>");
        $("#info").append(hiddenDiv);
        
        var exportLink = $("#exportLink a");
        exportLink.attr('href', urlForHistory);
        var startOsmLink = $("<a>start</a>");
        startOsmLink.attr("href", "http://www.openstreetmap.org/?zoom=14&mlat="+request.from.lat+"&mlon="+request.from.lng);
        var endOsmLink = $("<a>end</a>");
        endOsmLink.attr("href", "http://www.openstreetmap.org/?zoom=14&mlat="+request.to.lat+"&mlon="+request.to.lng);
        hiddenDiv.append("<br/><span>View on OSM: </span>").append(startOsmLink).append(endOsmLink);
        
        var osrmLink = $("<a>OSRM</a>");
        osrmLink.attr("href", "http://map.project-osrm.org/?loc=" + from + "&loc=" + to);        
        hiddenDiv.append("<br/><span>Compare with: </span>");
        hiddenDiv.append(osrmLink);
        var googleLink = $("<a>Google</a> ");
        var addToGoogle = "";
        var addToBing = "";
        if(request.vehicle == "foot") {
            addToGoogle = "&dirflg=w";
            addToBing = "&mode=W";
        } else if(request.vehicle == "bike") {
            addToGoogle = "&dirflg=b";
        // ? addToBing = "&mode=B";
        }
        googleLink.attr("href", "http://maps.google.com/?q=from:" + from + "+to:" + to + addToGoogle);
        hiddenDiv.append(googleLink);
        var bingLink = $("<a>Bing</a> ");        
        bingLink.attr("href", "http://www.bing.com/maps/default.aspx?rtp=adr." + from + "~adr." + to + addToBing);
        hiddenDiv.append(bingLink);
        
        if(host.indexOf("gpsies.com") > 0)
            hiddenDiv.append("<div id='hosting'>The routing API is hosted by <a href='http://gpsies.com'>Gpsies.com</a></div>");
        
        $('.defaulting').each(function(index, element) {
            $(element).css("color", "black");
        });
               
        if(json.route.instructions) {
            var instructionsElement = $("<table id='instructions'><colgroup>"
                + "<col width='10%'><col width='65%'><col width='25%'></colgroup>");
            $("#info").append(instructionsElement);        
            var descriptions = json.route.instructions.descriptions;
            var distances = json.route.instructions.distances;
            var indications = json.route.instructions.indications;
            for(var m = 0; m < descriptions.length; m++) {                
                var indi = indications[m];                
                if(m == 0)
                    indi = "marker-from";
                else if(indi == -3)
                    indi = "sharp_left";
                else if(indi == -2)
                    indi = "left";
                else if(indi == -1)
                    indi = "slight_left";
                else if(indi == 0)
                    indi = "continue";
                else if(indi == 1)
                    indi = "slight_right";
                else if(indi == 2)
                    indi = "right";
                else if(indi == 3)
                    indi = "sharp_right";
                else
                    throw "did not found indication " + indi;
                    
                addInstruction(instructionsElement, indi, descriptions[m], distances[m]);                
            }
            addInstruction(instructionsElement, "marker-to", "Finish!", "");
        }
    });
}

function addInstruction(main, indi, title, distance) {    
    var indiPic = "<img class='instr_pic' src='" + window.location.pathname + "img/" + indi + ".png'/>";                    
    var str = "<td class='instr_title'>"  + title + "</td>"
    + " <td class='instr_distance_td'><span class='instr_distance'>" + distance + "</span></td>";
    if(indi !== "continue")
        str = "<td>" + indiPic + "</td>"+ str;
    else
        str = "<td/>" + str;
    var instructionDiv = $("<tr class='instruction'/>");
    instructionDiv.html(str);
    main.append(instructionDiv);
}

function getCenter(bounds) {    
    var center = {
        lat : 0, 
        lng : 0
    };
    if(bounds.initialized) {
        center.lat = (bounds.minLat + bounds.maxLat) / 2;
        center.lng = (bounds.minLon + bounds.maxLon) / 2;  
    }
    return center;
}

function parseUrlWithHisto() {
    if(window.location.hash) 
        return parseUrl(window.location.hash);
    
    return parseUrl(window.location.search);
}

function parseUrlAndRequest() {
    return parseUrl(window.location.search);
}
function parseUrl(query) {
    var index = query.indexOf('?');
    if(index >= 0)
        query = query.substring(index + 1);
    var res = {};        
    var vars = query.split("&");
    for (var i = 0; i < vars.length;i++) {
        var indexPos = vars[i].indexOf("=");
        if(indexPos < 0) 
            continue;
        
        var key = vars[i].substring(0, indexPos);
        var value = vars[i].substring(indexPos + 1);
        value = decodeURIComponent(value.replace(/\+/g,' '));
                        
        if (typeof res[key] === "undefined")
            res[key] = value;
        else if (typeof res[key] === "string") {
            var arr = [ res[key], value ];
            res[key] = arr;
        } else
            res[key].push(value);
        
    } 
    return res;
}

function initForm() {
    $('#locationform').submit(function(e) {
        // no page reload        
        e.preventDefault();        
        var fromStr = $("#fromInput").val();
        var toStr = $("#toInput").val();
        if(toStr == "To" && fromStr == "From") {
            // TODO print warning
            return;
        }
        if(fromStr == "From") {
            // no special function
            return;
        }
        if(toStr == "To") {
            // lookup area
            ghRequest.from = new GHInput(fromStr);
            $.when(resolveFrom()).done(function() {                    
                focus(ghRequest.from);
            });                
            return;
        }
        // route!
        resolveCoords(fromStr, toStr);
    });
    
    $('.defaulting').each(function(index, element) {
        var jqElement = $(element);
        var defaultValue = jqElement.attr('defaultValue');        
        jqElement.focus(function() {
            var actualValue = jqElement.val();
            if (actualValue == defaultValue) {
                jqElement.val('');
                jqElement.css('color', 'black');
            }
        });
        jqElement.blur(function() {
            var actualValue = jqElement.val();
            if (!actualValue) {
                jqElement.val(defaultValue);
                jqElement.css('color', 'gray');
            }
        });
    });
}

function floor(val, precision) {
    if(!precision)
        precision = 1e6;
    return Math.floor(val * precision) / precision;
}
function round(val, precision) {
    if(!precision)
        precision = 1e6;
    return Math.round(val * precision) / precision;
}