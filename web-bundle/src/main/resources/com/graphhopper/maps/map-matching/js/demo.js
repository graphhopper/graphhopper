var iconObject = L.icon({
    iconUrl: './img/marker-icon.png',
    shadowSize: [50, 64],
    shadowAnchor: [4, 62],
    iconAnchor: [12, 40]
});

$(document).ready(function (e) {
    jQuery.support.cors = true;

    var mmMap = createMap('map-matching-map');
    var mmClient = new GraphHopperMapMatching(/*{host: "https://graphhopper.com/api/1/", key: ""}*/);
    setup(mmMap, mmClient);
});

function setup(map, mmClient) {
    // TODO fetch bbox from /info
    map.setView([50.9, 13.4], 9);

    var routeLayer = L.geoJson().addTo(map);
    routeLayer.options = {
        // use style provided by the 'properties' entry of the geojson added by addDataToRoutingLayer
        style: function (feature) {
            return feature.properties && feature.properties.style;
        }};

    function readSingleFile(e) {
        var file = e.target.files[0];
        if (!file) {
            return;
        }
        var reader = new FileReader();
        reader.onload = function (e) {
            var content = e.target.result;

            var dom = (new DOMParser()).parseFromString(content, 'text/xml');
            var pathOriginal = toGeoJSON.gpx(dom);

            routeLayer.clearLayers();
            if (pathOriginal.features[0]) {

                pathOriginal.features[0].properties = {style: {color: "black", weight: 2, opacity: 0.9}};
                routeLayer.addData(pathOriginal);
                $("#map-matching-response").text("calculate route match ...");
                $("#map-matching-error").text("");
            } else {
                $("#map-matching-error").text("Cannot display original gpx file. No trk/trkseg/trkpt elements found?");
            }

            var vehicle = $("#vehicle-input").val();
            if (!vehicle)
                vehicle = "car";

            var gpsAccuracy = $("#accuracy-input").val();
            if (!gpsAccuracy)
                gpsAccuracy = 20;

            mmClient.vehicle = vehicle;
            mmClient.doRequest(content, function (json) {
                if (json.message) {
                    $("#map-matching-response").text("");
                    $("#map-matching-error").text(json.message);
                } else if (json.paths && json.paths.length > 0) {
                    var mm = json.map_matching;
                    var error = (100 * Math.abs(1 - mm.distance / mm.original_distance));
                    error = Math.floor(error * 100) / 100.0;
                    $("#map-matching-response").text("success with " + error + "% difference, "
                            + "distance " + Math.floor(mm.distance) + " vs. original distance " + Math.floor(mm.original_distance));
                    var matchedPath = json.paths[0];
                    var geojsonFeature = {
                        type: "Feature",
                        geometry: matchedPath.points,
                        properties: {style: {color: "#00cc33", weight: 6, opacity: 0.4}}
                    };
                    routeLayer.addData(geojsonFeature);

                    if (matchedPath.bbox) {
                        var minLon = matchedPath.bbox[0];
                        var minLat = matchedPath.bbox[1];
                        var maxLon = matchedPath.bbox[2];
                        var maxLat = matchedPath.bbox[3];
                        var tmpB = new L.LatLngBounds(new L.LatLng(minLat, minLon), new L.LatLng(maxLat, maxLon));
                        map.fitBounds(tmpB);
                    }
                } else {
                    $("#map-matching-error").text("unknown error");
                }
            }, {gps_accuracy: gpsAccuracy});
        };
        reader.readAsText(file);
    }

    document.getElementById('matching-file-input').addEventListener('change', readSingleFile, false);
}

GraphHopperMapMatching = function (args) {
    this.host = "/";
    this.basePath = "match";
    this.vehicle = "car";
    this.gps_accuracy = 20;
    this.data_type = "json";

    graphhopper.util.copyProperties(args, this);
};

GraphHopperMapMatching.prototype.doRequest = function (content, callback, reqArgs) {
    var that = this;
    var args = graphhopper.util.clone(that);
    if (reqArgs)
        args = graphhopper.util.copyProperties(reqArgs, args);

    var url = args.host + args.basePath + "?profile=" + args.vehicle
            + "&gps_accuracy=" + args.gps_accuracy
            + "&type=" + args.data_type;

    if (args.key)
        url += "&key=" + args.key;

    $.ajax({
        timeout: 20000,
        url: url,
        contentType: "application/xml",
        type: "POST",
        data: content
    }).done(function (json) {
        if (json.paths) {
            for (var i = 0; i < json.paths.length; i++) {
                var path = json.paths[i];
                // convert encoded polyline to geo json
                if (path.points_encoded) {
                    var tmpArray = graphhopper.util.decodePath(path.points, that.elevation);
                    path.points = {
                        "type": "LineString",
                        "coordinates": tmpArray
                    };
                }
            }
        }
        callback(json);

    }).fail(function (jqXHR) {

        if (jqXHR.responseJSON && jqXHR.responseJSON.message) {
            callback(jqXHR.responseJSON);

        } else {
            callback({
                "message": "Unknown error",
                "details": "Error for " + url
            });
        }
    });
};


function createMap(divId) {
    var osmAttr = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

    var omniscale = L.tileLayer.wms('https://maps.omniscale.net/v1/mapmatching-23a1e8ea/tile', {
        layers: 'osm',
        attribution: osmAttr + ', &copy; <a href="http://maps.omniscale.com/">Omniscale</a>'
    });

    var osm = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: osmAttr
    });

    var map = L.map(divId, {layers: [omniscale]});
    L.control.layers({"Omniscale": omniscale,
        "OpenStreetMap": osm, }).addTo(map);
    L.control.scale().addTo(map);
    return map;
}