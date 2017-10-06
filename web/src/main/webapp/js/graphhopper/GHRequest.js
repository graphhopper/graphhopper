var GHRoute = require('./GHRoute.js');
var GHInput = require('./GHInput.js');
var graphhopperTools = require('./tools.js');

// compatibility script taken from http://stackoverflow.com/a/11054570/194609
if (!Function.prototype.bind) {
    Function.prototype.bind = function (oThis) {
        if (typeof this !== 'function') {
            // closest thing possible to the ECMAScript 5
            // internal IsCallable function
            throw new TypeError('Function.prototype.bind - what is trying to be bound is not callable');
        }

        var aArgs = Array.prototype.slice.call(arguments, 1),
                fToBind = this,
                fNOP = function () {
                },
                fBound = function () {
                    return fToBind.apply(this instanceof fNOP && oThis ? this : oThis,
                            aArgs.concat(Array.prototype.slice.call(arguments)));
                };

        fNOP.prototype = this.prototype;
        fBound.prototype = new fNOP();

        return fBound;
    };
}

var GHRequest = function (host, api_key) {
    this.host = host;
    this.route = new GHRoute(new GHInput(), new GHInput());
    this.from = this.route.first();
    this.to = this.route.last();
    this.features = {};

    this.do_zoom = true;
    this.useMiles = false;
    // use jsonp here if host allows CORS
    this.dataType = "json";
    this.api_params = {"locale": "en", "vehicle": "car", "weighting": "fastest", "elevation": false,
        "key": api_key, "pt": {}};

    // register events
    this.route.addListener('route.add', function (evt) {
        this.to = this.route.last();
    }.bind(this));
    this.route.addListener('route.remove', function (evt) {
        this.from = this.route.first();
        this.to = this.route.last();
    }.bind(this));
    this.route.addListener('route.move', function (evt) {
        this.from = this.route.first();
        this.to = this.route.last();
    }.bind(this));

    this.route.addListener('route.reverse', function (evt) {
        this.from = this.route.first();
        this.to = this.route.last();
    }.bind(this));
};

GHRequest.prototype.init = function (params) {
    for (var key in params) {
        if (key === "point" || key === "mathRandom" || key === "do_zoom" || key === "layer" || key === "use_miles")
            continue;

        var val = params[key];
        if (val === "false")
            val = false;
        else if (val === "true")
            val = true;

        this.api_params[key] = val;
    }

    if ('do_zoom' in params)
        this.do_zoom = params.do_zoom;

    if ('use_miles' in params)
        this.useMiles = params.use_miles;

    // overwrite elevation e.g. important if not supported from feature set
    this.api_params.elevation = false;
    var featureSet = this.features[this.api_params.vehicle];
    if (featureSet && featureSet.elevation) {
        if ('elevation' in params)
            this.api_params.elevation = params.elevation;
        else
            this.api_params.elevation = true;
    }

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

GHRequest.prototype.setEarliestDepartureTime = function (localdatetime) {
    this.api_params.pt.earliest_departure_time = localdatetime;
};

GHRequest.prototype.getEarliestDepartureTime = function () {
    if (this.api_params.pt.earliest_departure_time)
        return this.api_params.pt.earliest_departure_time;
    return undefined;
};

GHRequest.prototype.initVehicle = function (vehicle) {
    this.api_params.vehicle = vehicle;
    var featureSet = this.features[vehicle];

    if (featureSet && featureSet.elevation)
        this.api_params.elevation = true;
    else
        this.api_params.elevation = false;
};

GHRequest.prototype.hasElevation = function () {
    return this.api_params.elevation;
};

GHRequest.prototype.getVehicle = function () {
    return this.api_params.vehicle;
};

GHRequest.prototype.isPublicTransit = function () {
    return this.getVehicle() === "pt";
};

GHRequest.prototype.createGeocodeURL = function (host, prevIndex) {
    var tmpHost = this.host;
    if (host)
        tmpHost = host;

    var path = this.createPath(tmpHost + "/geocode?limit=6&type=" + this.dataType);
    if (prevIndex >= 0 && prevIndex < this.route.size()) {
        var point = this.route.getIndex(prevIndex);
        if (point.isResolved()) {
            path += "&point=" + point.lat + "," + point.lng;
        }
    }
    return path;
};

GHRequest.prototype.createURL = function () {
    return this.createPath(this.host + "/route?" + this.createPointParams(false) + "&type=" + this.dataType);
};

GHRequest.prototype.createGPXURL = function (withRoute, withTrack, withWayPoints) {
    return this.createPath(this.host + "/route?" + this.createPointParams(false) + "&type=gpx&gpx.route=" + withRoute + "&gpx.track=" + withTrack + "&gpx.waypoints=" + withWayPoints);
};

GHRequest.prototype.createHistoryURL = function () {
    var skip = {"key": true};
    return this.createPath("?" + this.createPointParams(true), skip) + "&use_miles=" + !!this.useMiles;
};

GHRequest.prototype.createPointParams = function (useRawInput) {
    var str = "", point, i, l;

    for (i = 0, l = this.route.size(); i < l; i++) {
        point = this.route.getIndex(i);
        if (i > 0)
            str += "&";
        if (typeof point.input == 'undefined')
            str += "point=";
        else if (useRawInput)
            str += "point=" + encodeURIComponent(point.input);
        else
            str += "point=" + encodeURIComponent(point.toString());
    }
    return (str);
};

GHRequest.prototype.createPath = function (url, skipParameters) {
    for (var key in this.api_params) {
        if(skipParameters && skipParameters[key])
            continue;

        var val = this.api_params[key];
        url += this.flatParameter(key, val);
    }
    return url;
};

GHRequest.prototype.flatParameter = function (key, val) {

    if(GHRoute.isObject(val)) {
        var url = "";
        var arr = Object.keys(val);
        for (var keyIndex in arr) {
           var objKey = arr[keyIndex];
           url += this.flatParameter(key + "." + objKey, val[objKey]);
        }
        return url;

    } else  if (GHRoute.isArray(val)) {
        var url = "";
        var arr = val;
        for (var keyIndex in arr) {
            url += this.flatParameter(key, arr[keyIndex]);
        }
        return url;
    }

    return "&" + encodeURIComponent(key) + "=" + encodeURIComponent(val);
}

GHRequest.prototype.doRequest = function (url, callback) {
    var that = this;
    $.ajax({
        timeout: 30000,
        url: url,
        success: function (json) {
            if (json.paths) {
                for (var i = 0; i < json.paths.length; i++) {
                    var path = json.paths[i];
                    // convert encoded polyline to geo json
                    if (path.points_encoded) {
                        var tmpArray = graphhopperTools.decodePath(path.points, that.hasElevation());
                        path.points = {
                            "type": "LineString",
                            "coordinates": tmpArray
                        };

                        var tmpSnappedArray = graphhopperTools.decodePath(path.snapped_waypoints, that.hasElevation());
                        path.snapped_waypoints = {
                            "type": "MultiPoint",
                            "coordinates": tmpSnappedArray
                        };
                    }
                }
            }
            callback(json);
        },
        error: function (err) {
            // problematic: this callback is not invoked when using JSONP!
            // http://stackoverflow.com/questions/19035557/jsonp-request-error-handling
            var msg = "API did not respond! ";
            var json;

            if (err && err.responseText && err.responseText.indexOf('{') >= 0) {
                json = JSON.parse(err.responseText);
            } else if (err && err.statusText && err.statusText !== "OK") {
                msg += err.statusText;
                var details = "Error for " + url;
                json = {
                    message: msg,
                    hints: [{"message": msg, "details": details}]
                };
            }
            console.log(msg + " " + JSON.stringify(err));

            callback(json);
        },
        type: "GET",
        dataType: this.dataType,
        crossDomain: true
    });
};

GHRequest.prototype.getInfo = function () {
    var url = this.host + "/info?type=" + this.dataType + "&key=" + this.getKey();
    // console.log(url);
    return $.ajax({
        url: url,
        timeout: 3000,
        type: "GET",
        dataType: this.dataType,
        crossDomain: true
    });
};

GHRequest.prototype.setLocale = function (locale) {
    if (locale)
        this.api_params.locale = locale;
};

GHRequest.prototype.getKey = function() {
    return this.api_params.key;
};

GHRequest.prototype.fetchTranslationMap = function (urlLocaleParam) {
    if (!urlLocaleParam)
        // let servlet figure out the locale from the Accept-Language header
        urlLocaleParam = "";
    var url = this.host + "/i18n/" + urlLocaleParam + "?type=" + this.dataType + "&key=" + this.getKey();
    // console.log(url);
    return $.ajax({
        url: url,
        timeout: 3000,
        type: "GET",
        dataType: this.dataType,
        crossDomain: true
    });
};

module.exports = GHRequest;
