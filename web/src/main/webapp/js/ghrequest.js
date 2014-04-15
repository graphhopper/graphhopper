// IE fix
if (!window.console) {
    var console = {
        log: function() {
        },
        warn: function() {
        },
        error: function() {
        },
        time: function() {
        },
        timeEnd: function() {
        }
    };
}

GHRequest = function(host) {
    this.min_path_precision = 1;
    this.host = host;
    this.from = new GHInput("");
    this.via = new Array();
    this.to = new GHInput("");
    this.vehicle = "car";
    this.weighting = "fastest";
    this.points_encoded = true;
    this.instructions = true;
    this.debug = false;
    this.locale = "en";
    this.do_zoom = true;
    // use jsonp here if host allows CORS
    this.dataType = "jsonp";
    this.key = "tcV28oCCNIzu4GD1Hsp8dYGAHqFBXvYrBvBwthGE";
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
    if (params.minPathPrecision)
        this.minPathPrecision = params.minPathPrecision;
    if (params.vehicle)
        this.vehicle = params.vehicle;
    if (params.weighting)
        this.weighting = params.weighting;
    if (params.algorithm)
        this.algorithm = params.algorithm;
    if (params.locale)
        this.locale = params.locale;

    this.handleBoolean("do_zoom", params);
    this.handleBoolean("instructions", params);
    this.handleBoolean("points_encoded", params);

    if (params.q) {
        var qStr = params.q;
        if (!params.point)
            params.point = [];
        var indexFrom = qStr.indexOf("from:");
        var indexTo = qStr.indexOf("to:");
        if (indexFrom >= 0 && indexTo >= 0) {
            // google-alike query schema            
            if (indexFrom < indexTo) {
                params.point.push(qStr.substring(indexFrom + 5, indexTo).trim());
                params.point.push(qStr.substring(indexTo + 3).trim());
            } else {
                params.point.push(qStr.substring(indexTo + 3, indexFrom).trim());
                params.point.push(qStr.substring(indexFrom + 5).trim());
            }
        } else {
            var points = qStr.split("p:");
            for (var i = 0; i < points.length; i++) {
                var str = points[i].trim();
                if (str.length === 0)
                    continue;

                params.point.push(str);
            }
        }
    }
};

GHRequest.prototype.handleBoolean = function(key, params) {
    if (key in params)
        this[key] = params[key] === "true" || params[key] === true;
};

GHRequest.prototype.createGeocodeURL = function() {
    return this.createPath(this.host + "/geocode?limit=8&type=" + this.dataType + "&key=" + this.key);
};

GHRequest.prototype.createURL = function(demoUrl) {
    return this.createPath(this.host + "/route?" + demoUrl + "&type=" + this.dataType + "&key=" + this.key);
};

GHRequest.prototype.createViaParams = function() {
  var vialist="";
  for(i=0;i<this.via.length;i++)
  {
     vialist=vialist + "&point=" + encodeURIComponent(this.via[i].toString());
  }
  return vialist;
};

GHRequest.prototype.createFullViaParams = function() {
  var vialist="";
  for(i=0;i<this.via.length;i++)
  {
     vialist=vialist + "&point=" + encodeURIComponent(this.via[i].input);
  }
  return vialist;
};

GHRequest.prototype.createGPXURL = function() {
    // use points instead of strings
    var str = "point=" + encodeURIComponent(this.from.toString()) + this.createViaParams() + "&point=" + encodeURIComponent(this.to.toString());
    return this.createPath(this.host + "/route?" + str + "&type=gpx");
};

GHRequest.prototype.createFullURL = function() {
    var str = "?point=" + encodeURIComponent(this.from.input) + this.createFullViaParams() + "&point=" + encodeURIComponent(this.to.input);
    return this.createPath(str);
};

GHRequest.prototype.createPath = function(url) {
    if (this.vehicle && this.vehicle != "car")
        url += "&vehicle=" + this.vehicle;
    // fastest or shortest
    if (this.weighting && this.weighting != "fastest")
        url += "&weighting=" + this.weighting;
    if (this.locale && this.locale != "en")
        url += "&locale=" + this.locale;
    // dijkstra, dijkstrabi, astar, astarbi
    if (this.algorithm && this.algorithm != "dijkstrabi")
        url += "&algorithm=" + this.algorithm;
    if (!this.instructions)
        url += "&instructions=false";
    if (!this.points_encoded)
        url += "&points_encoded=false";
    if (this.min_path_precision !== 1)
        url += "&min_path_precision=" + this.min_path_precision;
    if (this.debug)
        url += "&debug=true";
    return url;
}

function decodePath(encoded, is3D) {
    // var start = new Date().getTime();
    var len = encoded.length;
    var index = 0;
    var array = [];
    var lat = 0;
    var lng = 0;
    var ele = 0;

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

        if (is3D) {
            // elevation
            shift = 0;
            result = 0;
            do
            {
                b = encoded.charCodeAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            var deltaEle = ((result & 1) ? ~(result >> 1) : (result >> 1));
            ele += deltaEle;
            array.push([lng * 1e-5, lat * 1e-5, ele / 100]);
        } else
            array.push([lng * 1e-5, lat * 1e-5]);
    }
    // var end = new Date().getTime();
    // console.log("decoded " + len + " coordinates in " + ((end - start) / 1000) + "s");
    return array;
}

GHRequest.prototype.doRequest = function(url, callback) {
    $.ajax({
        "timeout": 30000,
        "url": url,
        "success": function(json) {
            if (json.paths) {
                for (var i = 0; i < json.paths.length; i++) {
                    var path = json.paths[i];
                    // convert encoded polyline to geo json
                    if (path.points_encoded) {
                        var tmpArray = decodePath(path.points, path.points_dimension === 3);
                        path.points = {
                            "type": "LineString",
                            "coordinates": tmpArray
                        };
                    }
                }
            }
            callback(json);
        },
        "error": function(err) {
            // problematic: this callback is not invoked when using JSONP!
            // http://stackoverflow.com/questions/19035557/jsonp-request-error-handling
            var msg = "API did not respond! ";
            if (err && err.statusText && err.statusText != "OK")
                msg += err.statusText;

            console.log(msg + " " + JSON.stringify(err));
            var details = "Error for " + url;
            var json = {
                "info": {
                    "errors": [{
                            "message": msg,
                            "details": details
                        }]
                }
            };
            callback(json);
        },
        "type": "GET",
        "dataType": this.dataType
    });
};

GHRequest.prototype.getInfo = function() {
    var url = this.host + "/info?type=" + this.dataType + "&key=" + this.key;
    console.log(url);
    return $.ajax({
        "url": url,
        "timeout": 3000,
        "type": "GET",
        "dataType": this.dataType
    });
};

GHInput = function(str) {
    // either text or coordinates
    this.input = str;
    try {
        var index = str.indexOf(",");
        if (index >= 0) {
            this.lat = round(parseFloat(str.substr(0, index)));
            this.lng = round(parseFloat(str.substr(index + 1)));
            if (!isNaN(this.lat) && !isNaN(this.lng)) {
                this.input = this.toString();
            } else {
                this.lat = undefined;
                this.lng = undefined;
            }
        }
    } catch (ex) {
    }
};

GHInput.prototype.isResolved = function() {
    return this.lat && this.lng;
};

GHInput.prototype.setCoord = function(lat, lng) {
    this.lat = round(lat);
    this.lng = round(lng);
    this.input = this.lat + "," + this.lng;
};

GHInput.prototype.toString = function() {
    if (this.lat !== undefined && this.lng !== undefined)
        return this.lat + "," + this.lng;
    return undefined;
};

GHRequest.prototype.setLocale = function(locale) {
    if (locale)
        this.locale = locale;
};

GHRequest.prototype.fetchTranslationMap = function(urlLocaleParam) {
    if (!urlLocaleParam)
        // let servlet figure out the locale from the Accept-Language header
        urlLocaleParam = "";
    var url = this.host + "/i18n/" + urlLocaleParam + "?type=" + this.dataType + "&key=" + this.key;
    console.log(url);
    return $.ajax({
        "url": url,
        "timeout": 3000,
        "type": "GET",
        "dataType": this.dataType
    });
};
