var host;

// Deployment-scripts can insert host here.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// We know that you love 'free', we love it too :)! And so our entire software stack is free and even Open Source!      
// Our routing service is also free for certain applications or smaller volume. Be fair, grab an API key and support us:
// https://graphhopper.com/#directions-api Misuse of API keys that you don't own is prohibited and you'll be blocked.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
if (!host) {
    if (location.port === '') {
        host = location.protocol + '//' + location.hostname;
    } else {
        host = location.protocol + '//' + location.hostname + ":" + location.port;
    }
}

var ghRequest = new GHRequest(host);
var tmpArgs = parseUrlWithHisto();
var bounds = {};

var nominatimURL = "https://nominatim.openstreetmap.org/search";
var nominatimReverseURL = "https://nominatim.openstreetmap.org/reverse";
var routingLayer;
var map;
var menuStart;
var menuIntermediate;
var menuEnd;
var browserTitle = "GraphHopper Maps - Driving Directions";
var defaultTranslationMap = null;
var enTranslationMap = null;
var routeSegmentPopup = null;
var elevationControl = null;
var activeLayer = 'Lyrk';
var i18nIsInitialized;

var iconFrom = L.icon({
    iconUrl: 'img/marker-icon-green.png',
    shadowSize: [50, 64],
    shadowAnchor: [4, 62],
    iconAnchor: [12, 40]
});

var iconTo = L.icon({
    iconUrl: 'img/marker-icon-red.png',
    shadowSize: [50, 64],
    shadowAnchor: [4, 62],
    iconAnchor: [12, 40]
});

var iconInt = L.icon({
    iconUrl: 'img/marker-icon-blue.png',
    shadowSize: [50, 64],
    shadowAnchor: [4, 62],
    iconAnchor: [12, 40]
});

$(document).ready(function (e) {
    // fixing cross domain support e.g in Opera
    jQuery.support.cors = true;

    if (isProduction())
        $('#hosting').show();

    var History = window.History;
    if (History.enabled) {
        History.Adapter.bind(window, 'statechange', function () {
            // No need for workaround?
            // Chrome and Safari always emit a popstate event on page load, but Firefox doesnt
            // https://github.com/defunkt/jquery-pjax/issues/143#issuecomment-6194330

            var state = History.getState();
            log(state);
            initFromParams(state.data, true);
        });
    }

    $('#locationform').submit(function (e) {
        // no page reload
        e.preventDefault();
        mySubmit();
    });

    $('#gpxExportButton a').click(function (e) {
        // no page reload
        e.preventDefault();
        exportGPX();
    });
    
    $('#settings-btn').click(function(e){
        $('.collapse').collapse('toggle');
    });

    var urlParams = parseUrlWithHisto();
    $.when(ghRequest.fetchTranslationMap(urlParams.locale), ghRequest.getInfo())
            .then(function (arg1, arg2) {
                // init translation retrieved from first call (fetchTranslationMap)
                var translations = arg1[0];

                // init language
                // 1. determined by Accept-Language header, falls back to 'en' if no translation map available
                // 2. can be overwritten by url parameter        
                ghRequest.setLocale(translations["locale"]);
                defaultTranslationMap = translations["default"];
                enTranslationMap = translations["en"];
                if (!defaultTranslationMap)
                    defaultTranslationMap = enTranslationMap;

                i18nIsInitialized = true;
                initI18N();

                // init bounding box from getInfo result
                var json = arg2[0];
                var tmp = json.bbox;
                bounds.initialized = true;
                bounds.minLon = tmp[0];
                bounds.minLat = tmp[1];
                bounds.maxLon = tmp[2];
                bounds.maxLat = tmp[3];
                // Additional configuration of the nominatum URL for the bounding box
                nominatum.remote.url = nominatum.remote.url + "&bounded=1&viewbox=" + bounds.minLon + "," + bounds.maxLat + "," + bounds.maxLon + "," + bounds.minLat;
                
                var vehiclesDiv = $("#vehicles");
                function createButton(vehicle) {
                    var button = $("<button class='vehicle-btn' title='" + tr(vehicle) + "'/>");
                    button.attr('id', vehicle);
                    button.html("<img src='img/" + vehicle + ".png' alt='" + tr(vehicle) + "'></img>");
                    button.click(function () {
                        ghRequest.initVehicle(vehicle);
                        resolveAll()
                        routeLatLng(ghRequest);
                    });
                    return button;
                }

                if (json.features) {
                    ghRequest.features = json.features;

                    var vehicles = Object.keys(json.features);
                    if (vehicles.length > 0)
                        ghRequest.initVehicle(vehicles[0]);

                    for (var key in json.features) {
                        vehiclesDiv.append(createButton(key.toLowerCase()));
                    }
                }

                initMap(urlParams.layer);

                // execute query
                initFromParams(urlParams, true);
            }, function (err) {
                log(err);
                $('#error').html('GraphHopper API offline? <a href="http://graphhopper.com/maps">Refresh</a>'
                        + '<br/>Status: ' + err.statusText + '<br/>' + host);

                bounds = {
                    "minLon": -180,
                    "minLat": -90,
                    "maxLon": 180,
                    "maxLat": 90
                };
                initMap();
            });

    $(window).resize(function () {
        adjustMapSize();
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

function initFromParams(params, doQuery) {
    ghRequest.init(params);
    var fromAndTo = params.from && params.to,
            routeNow = params.point && params.point.length >= 2 || fromAndTo;

    if (routeNow) {
        if (fromAndTo)
            resolveCoords([params.from, params.to], doQuery);
        else
            resolveCoords(params.point, doQuery);
    } else if (params.point && params.point.length === 1) {
        ghRequest.from = new GHInput(params.point[0]);
        resolve("from", ghRequest.from);
        focus(ghRequest.from, 15, true);
    }
}

function resolveCoords(pointsAsStr, doQuery, inputsStr) {
	for (var i = 0, l = pointsAsStr.length; i < l; i++) {
        var pointStr = pointsAsStr[i];
        var coords = ghRequest.route.getIndex(i);
        if (!coords || pointStr !== coords.input || !coords.isResolved())
            ghRequest.route.set(pointStr, i, true);
    }

    checkInput();

    if (ghRequest.route.isResolved()) {
        resolveAll(inputsStr);
        routeLatLng(ghRequest, doQuery);
    } else {
        // at least one text input from user -> wait for resolve as we need the coord for routing     
        $.when.apply($, resolveAll(inputsStr)).done(function () {
            routeLatLng(ghRequest, doQuery);
        });
    }
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

    // console.log("#### new checkInput #### ");
    for (var i = 0; i < len; i++) {
        var div = $('#locationpoints > div.pointDiv').eq(i);
        if (div.length === 0) {
            $('#locationpoints > div.pointAdd').before(nanoTemplate(template, {id: i}));
            div = $('#locationpoints > div.pointDiv').eq(i);
        }
        var toFrom = getToFrom(i);
        div.data("index", i);
        div.find(".pointFlag").attr("src",
                (toFrom === FROM) ? 'img/marker-small-green.png' :
                ((toFrom === TO) ? 'img/marker-small-red.png' : 'img/marker-small-blue.png'));
        if (len > 2) {
            div.find(".pointDelete").click(function () {
                var index = $(this).parent().data('index');
                ghRequest.route.removeSingle(index);
                routingLayer.clearLayers();
                routeLatLng(ghRequest, false);
            }).show();
        } else {
            div.find(".pointDelete").hide();
        }

       setupTypeAhead(i);
        
    }

    adjustMapSize();
}
function setupTypeAhead(i) {
	$('#'+i+'_searchBox')
	existing_typeahead = $("#locationpoints > #"+i+"_Div > span.twitter-typeahead");
	if (existing_typeahead.size()==0) {
		$('#'+i+'_searchBox').typeahead(
				{
					highlight : true
				},
				{
					name : 'Address',
					displayKey : 'address_line',
					source : places.ttAdapter(),
					templates : {
						empty : [ '<div class="tt-empty-message">',
								'No Results', '</div>' ].join('\n'),
						header : '<h4 class="tt-tag-heading tt-tag-heading2">OS Places - Address</h4>'
					}
//				},
//				{
//					name : 'Nominatum',
//					displayKey : 'address_line',
//					source : nominatum.ttAdapter(),
//					templates : {
//						empty : [ '<div class="tt-empty-message">',
//								'No Results', '</div>' ].join('\n'),
//						header : '<h4 class="tt-tag-heading tt-tag-heading2">OpenStreetMap Nominatum</h4>'
//					}

				}
				);
	$('#'+i+'_searchBox').on(
				'typeahead:selected',
				function(e, datum) {
					e.target.data = {
							x: datum.longitude, 
							y: datum.latitude,
							latitude: datum.latitude,
							longitude: datum.longitude,
							address_line: datum.address_line};
				});
	}

}
function nanoTemplate(template, data) {
    return template.replace(/\{([\w\.]*)\}/g, function (str, key) {
        var keys = key.split("."), v = data[keys.shift()];
        for (i = 0, l = keys.length; i < l; _i++)
            v = v[this];
        return (typeof v !== "undefined" && v !== null) ? v : "";
    });
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
    // reduce info size depending on how heigh the input_header is and reserve space for footer
    $("#info").css("max-height", height - $("#input_header").height() - 58);
}

function initMap(selectLayer) {
    adjustMapSize();
    log("init map at " + JSON.stringify(bounds));

    var osmAttr = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';
    // provider
    //@see http://leaflet-extras.github.io/leaflet-providers/preview/index.html
    var osmAttr = '&copy; <a href="http://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap</a> contributors';

    var tp = "ls";
    if (L.Browser.retina)
        tp = "lr";
        
        
    /** Define the layer type
 *  GEOGRAPHICALGRIDSYSTEMS.MAPS
 *  GEOGRAPHICALGRIDSYSTEMS.MAPS.SCAN-EXPRESS.CLASSIQUE
 *  GEOGRAPHICALGRIDSYSTEMS.MAPS.SCAN-EXPRESS.STANDARD
 */
var layerOSZoom = "Zoom Map Auto";

// The WMTS URL 
var url = "http://gwccluster2-env.elasticbeanstalk.com/service/wmts";


function getTopLeftCorners() {
	/**
	 * the matrixIdsBNG represents the projection
	 * for in the OS WMTS for BNG coordinates.
	 */
	var topLeftCorners = new Array(14);
	var i=0;
	topLeftCorners[i++] = new L.LatLng(1376256.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1376256.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1376256.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1376256.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1376256.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1376256.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1376256.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1376256.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1376256.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1376256.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1376256.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1376256.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1376256.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1376256.0, 0.0);
	/*
	topLeftCorners[i++] = new L.LatLng(1376256.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1376256.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1376256.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1347584.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1347584.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1347584.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1344000.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1344000.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1344000.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1344000.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1344000.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1344000.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1344000.0, 0.0);
	topLeftCorners[i++] = new L.LatLng(1344000.0, 0.0);		
	*/		
	return topLeftCorners;
}

	// The WMTS URL 
	var url = "https://gwccluster2-env.elasticbeanstalk.com/service/wmts";

    var osClassic = new L.TileLayer(url + '?height=256&width=256&tilematrixSet=EPSG%3A27700&version=1.0.0&style=&layer=Zoom%20Map%20Auto&SERVICE=WMTS&REQUEST=GetTile&format=image/png&TileMatrix=EPSG:27700:{z}&TileRow={y}&TileCol={x}',{
       tileSize: 256,
       continuousWorld: true,
       attribution: '&copy; <a href="http://os.uk/">Ordnance Survey</a> '
    });
    
    var osConsumer = new L.TileLayer(url + '?height=256&width=256&tilematrixSet=EPSG%3A27700&version=1.0.0&style=&layer=Zoom%20Map%20Tactical%2027700&SERVICE=WMTS&REQUEST=GetTile&format=image/png&TileMatrix=EPSG:27700:{z}&TileRow={y}&TileCol={x}',{
        tileSize: 256,
        continuousWorld: true,
        attribution: '&copy; <a href="http://os.uk/">Ordnance Survey</a> '
     });

    var lyrk = L.tileLayer('https://tiles.lyrk.org/' + tp + '/{z}/{x}/{y}?apikey=6e8cfef737a140e2a58c8122aaa26077', {
        attribution: osmAttr + ', <a href="https://geodienste.lyrk.de/">Lyrk</a>',
        subdomains: ['a', 'b', 'c']
    });

    var mapquest = L.tileLayer('http://{s}.mqcdn.com/tiles/1.0.0/osm/{z}/{x}/{y}.png', {
        attribution: osmAttr + ', <a href="http://open.mapquest.co.uk" target="_blank">MapQuest</a>',
        subdomains: ['otile1', 'otile2', 'otile3', 'otile4']
    });

    var mapquestAerial = L.tileLayer('http://{s}.mqcdn.com/tiles/1.0.0/sat/{z}/{x}/{y}.png', {
        attribution: osmAttr + ', <a href="http://open.mapquest.co.uk" target="_blank">MapQuest</a>',
        subdomains: ['otile1', 'otile2', 'otile3', 'otile4']
    });

    var openMapsSurfer = L.tileLayer('http://openmapsurfer.uni-hd.de/tiles/roads/x={x}&y={y}&z={z}', {
        attribution: osmAttr + ', <a href="http://openmapsurfer.uni-hd.de/contact.html">GIScience Heidelberg</a>'
    });

    var thunderTransport = L.tileLayer('http://{s}.tile.thunderforest.com/transport/{z}/{x}/{y}.png', {
        attribution: osmAttr + ', <a href="http://www.thunderforest.com/transport/" target="_blank">Thunderforest Transport</a>',
        subdomains: ['a', 'b', 'c']
    });

    var thunderCycle = L.tileLayer('http://{s}.tile.thunderforest.com/cycle/{z}/{x}/{y}.png', {
        attribution: osmAttr + ', <a href="http://www.thunderforest.com/opencyclemap/" target="_blank">Thunderforest Cycle</a>',
        subdomains: ['a', 'b', 'c']
    });

    var thunderOutdoors = L.tileLayer('http://{s}.tile.thunderforest.com/outdoors/{z}/{x}/{y}.png', {
        attribution: osmAttr + ', <a href="http://www.thunderforest.com/outdoors/" target="_blank">Thunderforest Outdoors</a>',
        subdomains: ['a', 'b', 'c']
    });

    var wrk = L.tileLayer('http://{s}.wanderreitkarte.de/topo/{z}/{x}/{y}.png', {
        attribution: osmAttr + ', <a href="http://wanderreitkarte.de" target="_blank">WanderReitKarte</a>',
        subdomains: ['topo4', 'topo', 'topo2', 'topo3']
    });

    var osm = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: osmAttr
    });

    var osmde = L.tileLayer('http://{s}.tile.openstreetmap.de/tiles/osmde/{z}/{x}/{y}.png', {
        attribution: osmAttr,
        subdomains: ['a', 'b', 'c']
    });

    var baseMaps = {
    	"OS Classic": osClassic,
    	"OS Consumer": osConsumer
    };

    var defaultLayer = baseMaps[selectLayer];
    if (!defaultLayer)
        defaultLayer = osClassic;
	var epsg27700 = "+proj=tmerc +lat_0=49 +lon_0=-2 +k=0.999601 +x_0=400000 +y_0=-100000 +ellps=airy +towgs84=446.448,-125.157,542.060,0.1502,0.2470,0.8421,-20.4894 +datum=OSGB36 +units=m +no_defs";    
    var crs = new L.Proj.WMTSCRS(
    		'EPSG:27700',
    		epsg27700,
    		{
    			topLeftCorners: getTopLeftCorners(),
    			resolutions : [ 896.0, 448.0, 224.0, 112.0, 56.0, 28.0, 14.0, 7.0, 3.5, 1.75, 0.875, 0.4375, 0.21875, 0.109375 ]
    		});

    // default
    map = L.map('map', {
    	crs: crs,
        layers: [defaultLayer],
        contextmenu: true,
        contextmenuWidth: 140,
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
        loadingControl: false,
        maxZoom: 13,
        minZoom: 0
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

    L.control.layers(baseMaps/*, overlays*/).addTo(map);

    map.on('baselayerchange', function (a) {
        if (a.name)
            activeLayer = a.name;
    });

    L.control.scale().addTo(map);

    map.fitBounds(new L.LatLngBounds(new L.LatLng(bounds.minLat, bounds.minLon),
            new L.LatLng(bounds.maxLat, bounds.maxLon)));

    if (isProduction())
        map.setView(new L.LatLng(0, 0), 2);

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
        style: {color: "#ED1A5E", "weight": 5, "opacity": 0.6}, // route color and style
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

function setToStart(e) {
    var latlng = e.target.getLatLng(),
            index = ghRequest.route.getIndexByCoord(latlng);
    ghRequest.route.move(index, 0);
    routeIfAllResolved();
}

function setToEnd(e) {
    var latlng = e.target.getLatLng(),
            index = ghRequest.route.getIndexByCoord(latlng);
    ghRequest.route.move(index, -1);
    routeIfAllResolved();
}

function setStartCoord(e) {
    ghRequest.route.set(e.latlng, 0);
    resolveFrom();
    routeIfAllResolved();
}

function setIntermediateCoord(e) {
    var index = ghRequest.route.size() - 1;
    ghRequest.route.add(e.latlng, index);
    resolveIndex(index);
    routeIfAllResolved();
}

function deleteCoord(e) {
    var latlng = e.target.getLatLng();
    ghRequest.route.removeSingle(latlng);
    routingLayer.clearLayers();
    routeLatLng(ghRequest, false);
}

function setEndCoord(e) {
    var index = ghRequest.route.size() - 1;
    ghRequest.route.set(e.latlng, index);
    resolveTo();
    routeIfAllResolved();
}

function routeIfAllResolved() {
    if (ghRequest.route.isResolved()) {
        routeLatLng(ghRequest);
        return true;
    }
    return false;
}

function makeValidLng(lon) {
    if (lon < 180 && lon > -180)
        return lon;
    if (lon > 180)
        return (lon + 180) % 360 - 180;
    return (lon - 180) % 360 + 180;
}

var FROM = 'from', TO = 'to';
function getToFrom(index) {
    if (index === 0)
        return FROM;
    else if (index === (ghRequest.route.size() - 1))
        return TO;
    return -1;
}

function setFlag(coord, index) {
    if (coord.lat) {
        var toFrom = getToFrom(index),
                marker = L.marker([coord.lat, coord.lng], {
                    icon: ((toFrom === FROM) ? iconFrom : ((toFrom === TO) ? iconTo : iconInt)),
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
        // intercept openPopup
        marker._openPopup = marker.openPopup;
        marker.openPopup = function () {
            var latlng = this.getLatLng(),
                    locCoord = ghRequest.route.getIndexFromCoord(latlng),
                    content;
            if (locCoord.resolvedList && locCoord.resolvedList[0] && locCoord.resolvedList[0].locationDetails) {
                var address = locCoord.resolvedList[0].locationDetails;
                content =
                        ((address.road) ? address.road + ', ' : '') +
                        ((address.postcode) ? address.postcode + ', ' : '') +
                        ((address.city) ? address.city + ', ' : '') +
                        ((address.country) ? address.country : '')
                        ;
                // at last update the content and update
                this._popup.setContent(content).update();
            }
            this._openPopup();
        };
        var _tempItem = {
            text: 'Set as Start',
            callback: setToStart,
            index: 1,
            state: 2
        };
        if (toFrom === -1)
            marker.options.contextmenuItems.push(_tempItem);// because the Mixin.ContextMenu isn't initialized
        marker.on('dragend', function (e) {
            routingLayer.clearLayers();
            // inconsistent leaflet API: event.target.getLatLng vs. mouseEvent.latlng?
            var latlng = e.target.getLatLng();
            hideAutoComplete();
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

function resolveIndex(index, inputsStr) {
    setFlag(ghRequest.route.getIndex(index), index);
    if (index === 0) {
        if (!ghRequest.to.isResolved())
            map.contextmenu.setDisabled(menuStart, true);
        else
            map.contextmenu.setDisabled(menuStart, false);
    } else if (index === (ghRequest.route.size() - 1)) {
        if (!ghRequest.from.isResolved())
            map.contextmenu.setDisabled(menuEnd, true);
        else
            map.contextmenu.setDisabled(menuEnd, false);
    }

    return resolve(index, ghRequest.route.getIndex(index), inputsStr);
}

function resolveAll(inputsStr) {
    var ret = [];
    for (var i = 0, l = ghRequest.route.size(); i < l; i++) {
        ret[i] = resolveIndex(i, inputsStr);
    }
    return ret;
}

function flagAll() {
    for (var i = 0, l = ghRequest.route.size(); i < l; i++) {
        setFlag(ghRequest.route.getIndex(i), i);
    }
}

function resolve(index, locCoord, inputsStr) {
    var div = $('#locationpoints > div.pointDiv').eq(index);
    $(div).find(".pointFlag").hide();
    $(div).find(".pointIndicator").show();
//    if (inputsStr) {
//    	$(div).find(".pointInput").val(inputsStr[index]);
//    }
//    else {
//	$(div).find(".pointInput").val(locCoord.input);
    var ttinput = $(div).find(".tt-input"); 
    ttinput.val(locCoord.input);
    var tthint = $(div).find(".tt-hint"); 
    tthint.val('');

    return createAmbiguityList(locCoord, ttinput).always(function () {
        var errorDiv = $(div).find(".pointResolveError");
        errorDiv.empty();

        if (locCoord.error) {
            errorDiv.show();
            errorDiv.text(locCoord.error).fadeOut(5000);
            locCoord.error = '';
        }

        $(div).find(".pointIndicator").hide();
        $(div).find(".pointFlag").show();
        return locCoord;
    });
}

/**
 * Returns a defer object containing the location pointing to a resolvedList with all the found
 * coordinates.
 */
function createAmbiguityList(locCoord, ttinput) {
    locCoord.error = "";
    locCoord.resolvedList = [];
    var timeout = 3000;

    if (locCoord.isResolved()) {
        // if we changed only another location no need to look this up again
        var tmpDefer = $.Deferred();
        tmpDefer.resolve([locCoord]);
        return tmpDefer;
    } else if (locCoord.lat && locCoord.lng) {
        locCoord.error = "Nominatum reverse geocode not supported";
        var tmpDefer = $.Deferred();
        tmpDefer.resolve([locCoord]);
        return tmpDefer;
    } else {
        var tmpDefer = $.Deferred();
        var enteredValue = ttinput.val();
    	places.get(enteredValue, function(suggestions) {
    		var resolved = false;
    		jQuery.each(suggestions, function (index, item) {
    			if (item.address_line===enteredValue) {
    				locCoord.lat = item.latitude;
    				locCoord.lng = item.longitude;
    	            resolved = true;
    				return false;
    			}
    	    });
    		if(!resolved) {
	            locCoord.error = "Failed to resolve entered value";
				tmpDefer.resolve([locCoord]);
    		}
    		else {
    			tmpDefer.resolve([locCoord]);
    		}
    	});
        return tmpDefer;
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
    if (!address)
        return locationDetails;
    if (address.road) {
        text = address.road;
        if (address.house_number) {
            if (text.length > 0)
                text += " ";
            text += address.house_number;
        }
        locationDetails.road = text;
    }

    if (address.postcode)
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
    var url = nominatimURL + "?format=json&addressdetails=1&q=" + encodeURIComponent(input) + "&limit=" + limit;
    if (bounds.initialized) {
        // minLon, minLat, maxLon, maxLat => left, top, right, bottom
        url += "&bounded=1&viewbox=" + bounds.minLon + "," + bounds.maxLat + "," + bounds.maxLon + "," + bounds.minLat;
    }

    return $.ajax({
        url: url,
        type: "GET",
        dataType: "json",
        timeout: timeout
    }).fail(
            createCallback("[nominatim] Problem while looking up location " + input)
            );
}

function createCallback(errorFallback) {
    return function (err) {
        log(errorFallback + " " + JSON.stringify(err));
    };
}

function focusWithBounds(coord, bbox, index) {
    routingLayer.clearLayers();
    // bbox needs to be in the none-geojson format!?
    // [[lat, lng], [lat2, lng2], ...]
    map.fitBounds(new L.LatLngBounds(bbox));
    setFlag(coord, index);
}

function focus(coord, zoom, index) {
    if (coord.lat && coord.lng) {
        if (!zoom)
            zoom = 11;
        routingLayer.clearLayers();
        map.setView(new L.LatLng(coord.lat, coord.lng), zoom);
        setFlag(coord, index);
    }
}
function routeLatLng(request, doQuery) {
    // do_zoom should not show up in the URL but in the request object to avoid zooming for history change
    var doZoom = request.do_zoom;
    request.do_zoom = true;

    var urlForHistory = request.createHistoryURL() + "&layer=" + activeLayer;

    // not enabled e.g. if no cookies allowed (?)
    // if disabled we have to do the query and cannot rely on the statechange history event    
    if (!doQuery && History.enabled) {
        // 2. important workaround for encoding problems in history.js
        var params = parseUrl(urlForHistory);
        log(params);
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

    if (elevationControl)
        elevationControl.clear();

    routingLayer.clearLayers();
    flagAll();

    map.contextmenu.setDisabled(menuIntermediate, false);

    $("#vehicles button").removeClass("selectvehicle");
    $("button#" + request.vehicle.toLowerCase()).addClass("selectvehicle");

    var urlForAPI = request.createURL();
    descriptionDiv.html('<img src="img/indicator.gif"/> Search Route ...');
    request.doRequest(urlForAPI, function (json) {
        descriptionDiv.html("");
        if (json.info.errors) {
            var tmpErrors = json.info.errors;
            log(tmpErrors);
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

        if (request.hasElevation()) {
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

        $('.defaulting').each(function (index, element) {
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
                var moreDiv = $("<button id='moreButton'>" + tr("moreButton") + "..</button>");
                moreDiv.click(function () {
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
            toggly.click(function () {
                hiddenDiv.toggle();
            });
            $("#info").append(toggly);
            var infoStr = "took: " + round(json.info.took / 1000, 1000) + "s"
                    + ", points: " + path.points.coordinates.length;

            hiddenDiv.append("<span>" + infoStr + "</span>");

            var exportLink = $("#export-link a");
            exportLink.attr('href', urlForHistory);
            var osmRouteLink = $("<br/><a>view on OSM</a>");

            var osmVehicle = "bicycle";
            if (request.vehicle.toUpperCase() === "FOOT") {
                osmVehicle = "foot";
            }
            osmRouteLink.attr("href", "http://www.openstreetmap.org/directions?engine=graphhopper_" + osmVehicle + "&route=" + encodeURIComponent(request.from.lat + "," + request.from.lng + ";" + request.to.lat + "," + request.to.lng));
            hiddenDiv.append(osmRouteLink);

            var osrmLink = $("<a>OSRM</a>");
            osrmLink.attr("href", "http://map.project-osrm.org/?loc=" + request.from + "&loc=" + request.to);
            hiddenDiv.append("<br/><span>Compare with: </span>");
            hiddenDiv.append(osrmLink);
            var googleLink = $("<a>Google</a> ");
            var addToGoogle = "";
            var addToBing = "";
            if (request.vehicle.toUpperCase() === "FOOT") {
                addToGoogle = "&dirflg=w";
                addToBing = "&mode=W";
            } else if ((request.vehicle.toUpperCase().indexOf("BIKE") >= 0) ||
                    (request.vehicle.toUpperCase() === "MTB")) {
                addToGoogle = "&dirflg=b";
                // ? addToBing = "&mode=B";
            }

            googleLink.attr("href", "https://maps.google.com/?saddr=" + request.from + "&daddr=" + request.to + addToGoogle);
            hiddenDiv.append(googleLink);
            var bingLink = $("<a>Bing</a> ");
            bingLink.attr("href", "https://www.bing.com/maps/default.aspx?rtp=adr." + request.from + "~adr." + request.to + addToBing);
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
    else if (sign === 5)
        sign = "marker-icon-blue";
    else if (sign === 6)
        sign = "roundabout";
    else
        throw "did not found sign " + sign;
    var title = instr.text;
    if (instr.annotation_text) {
        if (!title)
            title = instr.annotation_text;
        else
            title = title + ", " + instr.annotation_text;
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
        instructionDiv.click(function () {
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
        if (value === "")
            continue;

        if (key === "point" && !res[key]) {
            res[key] = [value];
        } else if (typeof res[key] === "string") {
            var arr = [res[key], value];
            res[key] = arr;
        } else if (typeof res[key] === "undefined") {
            if (value === 'true') {
                res[key] = true;
            } else if (value === 'false') {
                res[key] = false;
            } else {
                var tmp = Number(value);
                if (isNaN(tmp))
                    res[key] = value;
                else
                    res[key] = Number(value);
            }
        } else {
            res[key].push(value);
        }
    }
    return res;
}

function mySubmit() {
    var fromStr,
            toStr,
            viaStr,
            allStr = [],
            inputsStr = [],
            inputOk = true;
    
    if (osplacesurl && osplacesurl.indexOf('OS_PLACES_KEY')>-1) {
    	alert('Configuration error. You must configure your OS Places license key.');
    	return;
    }
    
//    var location_points = $("#locationpoints > div.pointDiv > input.pointInput");
    var location_points = $("#locationpoints > div.pointDiv > span.twitter-typeahead > input.tt-input");
    
    var len = location_points.size();
    $.each(location_points, function (index) {
    	var latLonString;
    	var address;
    	if (this.data) {
    		latLonString = this.data.latitude + ', ' + this.data.longitude;
    		address = this.data.address_line;
    	}
    	
        if (index === 0) {
            fromStr = $(this).val();
            // We haven't looked up an address so assume it is a lat lon
        	if (!this.data) {
        		latLonString = fromStr;
        		address = latLonString;
        	}
            if (fromStr !== tr("fromHint") && fromStr !== "") {
                allStr.push(latLonString);
                inputsStr.push(address);
            }
            else {
                inputOk = false;
            }
        } else if (index === (len - 1)) {
            toStr = $(this).val();
            // We haven't looked up an address so assume it is a lat lon
        	if (!this.data) {
        		latLonString = toStr;
        		address = latLonString;
        	}
            if (toStr !== tr("toHint") && toStr !== "") {
                allStr.push(latLonString);
                inputsStr.push(address);
            }
            else {
                inputOk = false;
            }
        } else {
            viaStr = $(this).val();
            // We haven't looked up an address so assume it is a lat lon
        	if (!this.data) {
        		latLonString = viaStr;
        		address = latLonString;
        	}
            if (viaStr !== tr("viaHint") && viaStr !== "") {
                allStr.push(latLonString);
                inputsStr.push(address);
            }
            else {
                inputOk = false;
            }
        }
    });
    if (!inputOk) {
        // TODO print warning
        return;
    }
    if (fromStr === tr("fromHint")) {
        // no special function
        return;
    }
    
    if (toStr === tr("toHint")) {
        // lookup area
        ghRequest.from.setStr(fromStr);
        $.when(resolveFrom()).done(function () {
            focus(ghRequest.from, null, 0);
        });
        return;
    }
    // route!
    if (inputOk)
        resolveCoords(allStr, false, inputsStr);
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
    if (key === null) {
        log("ERROR: key was null?");
        return "";
    }
    if (defaultTranslationMap === null) {
        log("ERROR: defaultTranslationMap was not initialized?");
        return key;
    }
    key = key.toLowerCase();
    var val = defaultTranslationMap[key];
    if (!val && enTranslationMap)
        val = enTranslationMap[key];
    if (!val)
        return key;

    return stringFormat(val, args);
}

function stringFormat(str, args) {
    if (typeof args === 'string')
        args = [args];

    if (str.indexOf("%1$s") >= 0) {
        // with position arguments ala %2$s
        return str.replace(/\%(\d+)\$s/g, function (match, matchingNum) {
            matchingNum--;
            return typeof args[matchingNum] !== 'undefined' ? args[matchingNum] : match;
        });
    } else {
        // no position so only values ala %s
        var matchingNum = 0;
        return str.replace(/\%s/g, function (match) {
            var val = typeof args[matchingNum] !== 'undefined' ? args[matchingNum] : match;
            matchingNum++;
            return val;
        });
    }
}

function initI18N() {
    $('#searchButton').attr("value", tr("searchButton"));
    location_points = $("#locationpoints > div.pointDiv > input.pointInput");
    var j = location_points.size();
    $(location_points).each(function (index) {
        if (index === 0)
            $(this).attr("placeholder", tr("fromHint"));
        else if (index === (j - 1))
            $(this).attr("placeholder", tr("toHint"));
        else
            $(this).attr("placeholder", tr("viaHint"));
    });
    $('#gpxExportButton').attr("title", tr("gpxExportButton"));
    // Populate the placeholder text for the typeahead inputs
    typeahead_points = $("#locationpoints > div.pointDiv > span.twitter-typeahead > input.tt-input");
    var l = typeahead_points.size();
    $(typeahead_points).each(function (index) {
        if (index === 0)
            $(this).attr("placeholder", tr("fromHint"));
        else if (index === (l - 1))
            $(this).attr("placeholder", tr("toHint"));
        else
            $(this).attr("placeholder", tr("viaHint"));
    });
    

}

function exportGPX() {
    if (ghRequest.route.isResolved())
        window.open(ghRequest.createGPXURL());
    return false;
}

function getAutoCompleteDiv(index) {
    return null;//$('#locationpoints > div.pointDiv').eq(index).find(".pointInput");
}

function hideAutoComplete() {
//    $(':input[id$="_Input"]').autocomplete().hide();
}

function formatValue(orig, query) {
    var pattern = '(' + $.Autocomplete.utils.escapeRegExChars(query) + ')';
    return orig.replace(new RegExp(pattern, 'gi'), '<strong>$1<\/strong>');
}

function setAutoCompleteList(index) {
    var myAutoDiv = getAutoCompleteDiv(index);

    var options = {
        containerClass: "autocomplete",
        /* as we use can potentially use jsonp we need to set the timeout to a small value */
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
        dataType: ghRequest.dataType,
        onSearchStart: function (params) {
            // query server only if not a parsable point (i.e. format lat,lon)
            var val = new GHInput(params.q).lat;
            return val === undefined;
        },
        serviceUrl: function () {
            // see http://graphhopper.com/#enterprise
            return ghRequest.createGeocodeURL(host, index -1);
        },
        transformResult: function (response, originalQuery) {
            response.suggestions = [];
            if (response.hits)
                for (var i = 0; i < response.hits.length; i++) {
                    var hit = response.hits[i];
                    response.suggestions.push({value: dataToText(hit), data: hit});
                }
            return response;
        },
        onSearchError: function (element, q, jqXHR, textStatus, errorThrown) {
            // too many errors if interrupted console.log(element + ", " + JSON.stringify(q) + ", textStatus " + textStatus + ", " + errorThrown);
        },
        formatResult: function (suggestion, currInput) {
            // avoid highlighting for now as this breaks the html sometimes
            return dataToHtml(suggestion.data, currInput);
        },
        onSelect: function (suggestion) {
            options.onPreSelect(suggestion);
        },
        onPreSelect: function (suggestion) {
            var req = ghRequest.route.getIndex(index);

            myAutoDiv.autocomplete().disable();

            var point = suggestion.data.point;
            req.setCoord(point.lat, point.lng);

            req.input = suggestion.value;
            if (!routeIfAllResolved(true))
                focus(req, 15, index);

            myAutoDiv.autocomplete().enable();
        }
    };

    myAutoDiv.autocomplete(options);

    // with the following more stable code we cannot click on suggestions anylonger
//    $("#" + fromOrTo + "Input").focusout(function() {
//        myAutoDiv.autocomplete().disable();
//        myAutoDiv.autocomplete().hide();
//    });
//    $("#" + fromOrTo + "Input").focusin(function() {
//        myAutoDiv.autocomplete().enable();
//    });
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

function isProduction() {
    return host.indexOf("graphhopper.com") > 0;
}