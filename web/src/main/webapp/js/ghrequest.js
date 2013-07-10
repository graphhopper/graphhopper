GHRequest = function(host) {
    this.minPathPrecision = 1;
    this.host = host;
    this.from = new GHInput("");
    this.to = new GHInput("");    
    this.vehicle = "car";
    this.encodedPolyline = true;
    this.instructions = true;
    this.debug = false;
    this.locale = "en";
    this.doZoom = true;
};

GHRequest.prototype.init = function(params) {
    //    for(var key in params) {
    //        var val = params[key];
    //        if(val === "false")
    //            val = false;
    //        else if(val === "true")
    //            val = true;
    //        else {            
    //            if(parseFloat(val) != NaN)
    //                val = parseFloat(val)
    //        }
    //        this[key] = val;
    //    } 
    if(params.minPathPrecision)
        this.minPathPrecision = params.minPathPrecision;
    if(params.vehicle)
        this.vehicle = params.vehicle;
    if(params.algoType)
        this.algoType = params.algoType;
    if(params.algorithm)
        this.algorithm = params.algorithm;
    if(params.locale)
        this.locale = params.locale;
    
    this.handleBoolean("doZoom", params);
    this.handleBoolean("instructions", params);
    this.handleBoolean("encodedPolyline", params);
}

GHRequest.prototype.handleBoolean = function(key, params) {
    if(key in params)
        this.doZoom = params[key] == "true" || params[key] == true;
}

GHRequest.prototype.createURL = function(demoUrl) {    
    return this.createPath(this.host + "/api/route?" + demoUrl + "&type=jsonp");
}

GHRequest.prototype.createFullURL = function() {
    var str = "?point=" + encodeURIComponent(this.from.input) + "&point=" + encodeURIComponent(this.to.input);    
    return this.createPath(str);
}
    
GHRequest.prototype.createPath = function(url) {    
    if(this.vehicle && this.vehicle != "car")
        url += "&vehicle=" + this.vehicle;    
    // fastest or shortest
    if(this.algoType && this.algoType != "fastest")
        url += "&algoType=" + this.algoType;
    if(this.locale && this.locale != "en")
        url += "&locale=" + this.locale;
    // dijkstra, dijkstrabi, astar, astarbi
    if(this.algorithm && this.algorithm != "dijkstrabi")
        url += "&algorithm=" + this.algorithm;
    if (!this.instructions)
        url += "&instructions=false";
    if (!this.encodedPolyline)
        url += "&encodedPolyline=false";
    if(this.minPathPrecision != 1)
        url += "&minPathPrecision=" + this.minPathPrecision;
    if (this.debug)
        url += "&debug=true";
    return url;
}

function decodePath(encoded, geoJson) {
    var start = new Date().getTime();
    var len = encoded.length;        
    var index = 0;
    var array = [];
    var lat = 0;
    var lng = 0;

    while (index < len) {
        var b;
        var shift = 0;
        var result = 0;
        do {
            b = encoded.charCodeAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        var deltaLat = ((result & 1) ? ~(result >> 1) : (result >> 1));
        lat += deltaLat;

        shift = 0;
        result = 0;
        do {
            b = encoded.charCodeAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        var deltaLon = ((result & 1) ? ~(result >> 1) : (result >> 1));
        lng += deltaLon;

        if(geoJson)
            array.push([lng * 1e-5, lat * 1e-5]);
        else
            array.push([lat * 1e-5, lng * 1e-5]);
    }
    var end = new Date().getTime();    
    console.log("decoded " + len + " coordinates in " + ((end - start)/1000)+ "s");
    return array;
}

GHRequest.prototype.doRequest = function(url, callback) {   
    var tmpEncodedPolyline = this.encodedPolyline;
    $.ajax({
        "timeout" : 30000,
        "url": url,
        "success": function(json) {
            if(tmpEncodedPolyline && json.route) {
                if(!json.route.coordinates)
                    console.log("something wrong on server? as we have encodedPolyline=" + tmpEncodedPolyline + " but no encoded data was return?");
                // convert encoded polyline stuff to normal json
                if (json.route.coordinates) {
                    var tmpArray = decodePath(json.route.coordinates, true);
                    json.route.coordinates = null;
                    json.route.data = {
                        "type": "LineString",
                        "coordinates": tmpArray
                    };
                }
            }
            callback(json);
        },
        "error": function(err) {
            var msg = "API did not response! ";
            if (err && err.statusText && err.statusText != "OK")
                msg += err.statusText;
            
            console.log(msg + " " + JSON.stringify(err));            
            var details = "Error for " + url;
            var json = {
                "info" : {
                    "errors" : {
                        "message" : msg, 
                        "details" : details
                    }
                }
            };
            callback(json);
        },
        "type": "GET",
        "dataType": "jsonp"
    });
};

GHRequest.prototype.getInfo = function(success, error) {
    var url = this.host + "/api/info?type=jsonp";
    console.log(url);    
    return $.ajax({
        "url": url,
        "success": success,
        "error" : error,
        "timeout" : 3000,
        "type" : "GET",
        "dataType": 'jsonp'
    });
}

GHInput = function(str) {
    // either text or coordinates
    this.input = str;
    this.resolvedText = "";
    try {
        var index = str.indexOf(",");
        if (index >= 0) {
            this.lat = round(parseFloat(str.substr(0, index)));
            this.lng = round(parseFloat(str.substr(index + 1)));
            if(!isNaN(this.lat) && !isNaN(this.lng)) {
                this.input = this.toString();
            } else {
                this.lat = false;
                this.lng = false;
            }
        }
    } catch (ex) {
    }
};

GHInput.prototype.isResolved = function() {
    return this.lat && this.lng;
}

GHInput.prototype.setCoord = function(lat, lng) {
    this.resolvedText = "";
    this.lat = round(lat);
    this.lng = round(lng);
    this.input = this.lat + "," + this.lng;
};

GHInput.prototype.toString = function() {
    if (this.lat && this.lng)
        return this.lat + "," + this.lng;
    return undefined;
};
