// usage: log('inside coolFunc',this,arguments);
// http://paulirish.com/2009/log-a-lightweight-wrapper-for-consolelog/
var debug = false;
window.log = function () {
    log.history = log.history || [];   // store logs to an array for reference
    log.history.push(arguments);
    if (this.console && debug) {
        console.log(Array.prototype.slice.call(arguments));
    }
};

// compatiblity script taken from http://stackoverflow.com/a/11054570/194609
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
                    return fToBind.apply(this instanceof fNOP && oThis
                            ? this
                            : oThis,
                            aArgs.concat(Array.prototype.slice.call(arguments)));
                };

        fNOP.prototype = this.prototype;
        fBound.prototype = new fNOP();

        return fBound;
    };
}

GHRequest = function (host) {
    this.way_point_max_distance = 1;
    this.host = host;
    this.route = new GHroute(new GHInput(), new GHInput());
    this.from = this.route.first();
    this.to = this.route.last();
    this.vehicle = "car";
    this.weighting = "fastest";
    this.points_encoded = true;
    this.instructions = true;
    this.elevation = false;
    this.features = {};
    this.debug = false;
    this.locale = "en";
    this.do_zoom = true;
    // use jsonp here if host allows CORS
    this.dataType = "json";
    // all URL parameters starting with "api." will be forwarded to GraphHopper directly    
    this.api_params = [];

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // We know that you love 'free', we love it too :)! And so our entire software stack is free and even Open Source!      
    // Our routing service is also free for certain applications or smaller volume. Be fair, grab an API key and support us:
    // https://graphhopper.com/#directions-api Misuse of API keys that you don't own is prohibited and you'll be blocked.                    
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    this.key = "Cmmtvx01R56rdHcQQo7VjI6rgPgxuFLvqI8cR31u";

    // register events
    this.route.addListener('route.add', function (evt) {
        this.to = this.route.last();
        log("Foo just added.");
    }.bind(this));
    this.route.addListener('route.remove', function (evt) {
        this.from = this.route.first();
        this.to = this.route.last();
        log("Foo just removed.");
    }.bind(this));
    this.route.addListener('route.move', function (evt) {
        this.from = this.route.first();
        this.to = this.route.last();
        log("Foo just moved.");
    }.bind(this));
//    this.route.addListener('route.set', function (evt) {
//        this.from = this.route.first();
//        this.to = this.route.last();
//        log("Foo just moved.");
//    }.bind(this));
    this.route.addListener('route.reverse', function (evt) {
        this.from = this.route.first();
        this.to = this.route.last();
        log("Foo just reversed.");
    }.bind(this));
};

GHroute = function () {
    var route = Object.create(Array.prototype);
    route = (Array.apply(route, arguments) || route);
    GHroute.injectClassMethods(route);
    route._listeners = {};
    return (route);
};

GHroute.injectClassMethods = function (route) {
    for (var method in GHroute.prototype) {
        if (GHroute.prototype.hasOwnProperty(method)) {
            route[method] = GHroute.prototype[method];
        }
    }
    return (route);
};

GHroute.fromArray = function (array) {
    var route = GHroute.apply(null, array);
    return (route);
};

GHroute.isArray = function (value) {
    var stringValue = Object.prototype.toString.call(value);
    return (stringValue.toLowerCase() === "[object array]");
};

GHroute.prototype = {
    first: function () {
        return this.getIndex(0);
    },
    last: function () {
        return this.getIndex((this.length - 1));
    },
    getIndex: function (index) {
        var index = (isNaN(index)) ? 0 : index;
        if (this[index] instanceof GHInput) {
            return this[index];
        } else
            return false;
    },
    getIndexByCoord: function (value) {
        var point,
                index = false,
                coord = new GHInput(value),
                i,
                l;

        for (i = 0, l = this.length; i < l; i++) {
            point = this[i];
            if (point.toString() === coord.toString()) {
                index = i;
                break;
            }
        }
        return index;
    },
    getIndexFromCoord: function (value) {
        return this.getIndex(this.getIndexByCoord(value));
    },
    size: function () {
        return this.length;
    },
    add: function (value, to) {
        if (GHroute.isArray(value)) {
            for (var i = 0; i < value.length; i++) {
                Array.prototype.push.call(this, (value[i] instanceof GHInput) ? value[i] : new GHInput(value[i]));
                if (to !== undefined) {
                    this.move(-1, to, true);
                    to++;
                } else
                    to = this.lenght - 1;
                this.fire('route.add', {
                    point: this[to],
                    to: to
                });
            }
            return (this);
        } else {
            Array.prototype.push.call(this, (value instanceof GHInput) ? value : new GHInput(value));
            if (to !== undefined)
                this.move(-1, to, true);
            else
                to = this.lenght - 1;
            this.fire('route.add', {
                point: this[to],
                to: to
            });
        }
        return (this[to]);
    },
    removeSingle: function (value) {
        var index = false;
        if (!(isNaN(value) || value >= this.length) && this[value] !== undefined) {
            index = value;
        } else {
            if (value instanceof GHInput) {
                value = value.toString();
            }
            index = this.getIndexByCoord(value);
        }
        if (index !== false) {
            this.remove(index);
        }
        return (this);
    },
    remove: function (from, to) {
        var tmpTo = to || 1;
        Array.prototype.splice.call(this, from, tmpTo);
        if (this.length === 1)
            Array.prototype.push.call(this, new GHInput());
        this.fire('route.remove', {
            from: from,
            to: tmpTo
        });
        return (this);
    },
    addAll: function () {
        for (var i = 0; i < arguments.length; i++) {
            this.add(arguments[i]);
        }
        return (this);
    },
    set: function (value, to, create) {
        if (value instanceof GHInput)
            this[to] = value;
        else if (this[to] instanceof GHInput) {
            this[to].set(value);
        } else if (create)
            return this.add(value, to);
        else
            return false;
        this.fire('route.set', {
            point: this[to],
            to: to
        });
        return (this[to]);
    },
    move: function (old_index, new_index, supress_event) {
        while (old_index < 0) {
            old_index += this.length;
        }
        while (new_index < 0) {
            new_index += this.length;
        }
        if (new_index >= this.length) {
            var k = new_index - this.length;
            while ((k--) + 1) {
                Array.prototype.push.call(this, undefined);
            }
        }
        Array.prototype.splice.call(this, new_index, 0, Array.prototype.splice.call(this, old_index, 1)[0]);
        if (!supress_event)
            this.fire('route.move', {
                old_index: old_index,
                new_index: new_index
            });
        return (this);
    },
    reverse: function () {
        Array.prototype.reverse.call(this);
        this.fire('route.reverse', {});
        return (this);
    },
    isResolved: function () {
        for (var i = 0, l = this.length; i < l; i++) {
            var point = this[i];
            if (!point.isResolved()) {
                return false;
            }
        }
        return true;
    },
    addListener: function (type, listener) {
        if (typeof this._listeners[type] === "undefined") {
            this._listeners[type] = [];
        }
        this._listeners[type].push(listener);
        return this;
    },
    fire: function (event, options) {
        if (typeof event === "string") {
            event = {type: event};
        }
        if (typeof options === "object") {
            for (var attrname in options) {
                event[attrname] = options[attrname];
            }
        }
        if (!event.route) {
            event.route = this;
        }
        if (!event.type) {  //falsy
            throw new Error("Event object missing 'type' property.");
        }
        if (this._listeners[event.type] instanceof Array) {
            var listeners = this._listeners[event.type];
            for (var i = 0, len = listeners.length; i < len; i++) {
                listeners[i].call(this, event);
            }
        }
    },
    removeListener: function (type, listener) {
        if (this._listeners[type] instanceof Array) {
            var listeners = this._listeners[type];
            for (var i = 0, len = listeners.length; i < len; i++) {
                if (listeners[i] === listener) {
                    listeners.splice(i, 1);
                    break;
                }
            }
        }
    }
};

GHRequest.prototype.init = function (params) {
    for (var key in params) {
        var val = params[key];
        if (val === "false")
            val = false;
        else if (val === "true")
            val = true;
        else {
            if (parseFloat(val) != NaN)
                val = parseFloat(val)
        }

        // todo
        // this[key] = val;

        if (key.indexOf('api.') === 0) {
            this.api_params[key.substring(4)] = val;
        }
    }

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
    if (params.key)
        this.key = params.key;

    if ('do_zoom' in params)
        this.do_zoom = params.do_zoom;
    if ('instructions' in params)
        this.instructions = params.instructions;
    if ('points_encoded' in params)
        this.points_encoded = params.points_encoded;

    this.elevation = false;
    var featureSet = this.features[this.vehicle];
    if (featureSet && featureSet.elevation) {
        if ('elevation' in params)
            this.elevation = params.elevation;
        else
            this.elevation = true;
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

GHRequest.prototype.initVehicle = function (vehicle) {
    this.vehicle = vehicle;
    var featureSet = this.features[this.vehicle];
    if (featureSet && featureSet.elevation)
        this.elevation = true;
    else
        this.elevation = false;
};

GHRequest.prototype.hasElevation = function () {
    return this.elevation;
};

GHRequest.prototype.createGeocodeURL = function (host, prevIndex) {
    var tmpHost = this.host;
    if (host)
        tmpHost = host;

    var path = this.createPath(tmpHost + "/geocode?limit=6&type=" + this.dataType + "&key=" + this.key);
    if (prevIndex >= 0 && prevIndex < this.route.size()) {
        var point = this.route.getIndex(prevIndex);
        path += "&lat=" + point.lat + "&lon=" + point.lng;
    }
    return path;
};

GHRequest.prototype.createURL = function () {
    return this.createPath(this.host + "/route?" + this.createPointParams(false) + "&type=" + this.dataType + "&key=" + this.key);
};

GHRequest.prototype.createGPXURL = function () {
    return this.createPath(this.host + "/route?" + this.createPointParams(false) + "&type=gpx&key=" + this.key);
};

GHRequest.prototype.createHistoryURL = function () {
    return this.createPath("?" + this.createPointParams(true));
};

GHRequest.prototype.createPointParams = function (useRawInput) {
    var str = "", point, i, l;

    for (i = 0, l = this.route.size(); i < l; i++) {
        point = this.route.getIndex(i);
        if (i > 0)
            str += "&";
        if (useRawInput)
            str += "point=" + encodeURIComponent(point.input);
        else
            str += "point=" + encodeURIComponent(point.toString());
    }
    return (str);
};

GHRequest.prototype.createPath = function (url) {
    if (this.vehicle && this.vehicle !== "car")
        url += "&vehicle=" + this.vehicle;
    // fastest or shortest
    var checkedValue = ""; 
    var inputElements = document.getElementsByClassName('hazCheck');
    for(var i=0; inputElements[i]; ++i){
          if(inputElements[i].checked){
        	  if(checkedValue.length>0) 
        		    checkedValue += ","
               checkedValue += inputElements[i].value;
          }
    }
    if(document.routeoptions.weighting[1].checked == true){
    	this.weighting = "shortest";
    } else {
    	this.weighting = "fastest";
    }
    if(checkedValue.length>0) {
    	if(this.weighting==="fastest") {
    		this.weighting = "fastavoid";
    	}
    	else {
    		this.weighting="shortavoid";
    	}
    	url += "&avoidances=" + checkedValue;
    }
    
    if (this.weighting && this.weighting !== "fastest")
        url += "&weighting=" + this.weighting;
    if (this.locale && this.locale !== "en")
        url += "&locale=" + this.locale;
    // dijkstra, dijkstrabi, astar, astarbi
    if (this.algorithm && this.algorithm !== "dijkstrabi")
        url += "&algorithm=" + this.algorithm;
    if (this.way_point_max_distance !== 1)
        url += "&way_point_max_distance=" + this.way_point_max_distance;
    if (!this.instructions)
        url += "&instructions=false";
    if (!this.points_encoded)
        url += "&points_encoded=false";

    if (this.elevation)
        url += "&elevation=true";
    if (this.debug)
        url += "&debug=true";

    for (var key in this.api_params) {
        url += "&" + key + "=" + this.api_params[key];
    }
    
    return url;
};

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
                        var tmpArray = decodePath(path.points, that.hasElevation());
                        path.points = {
                            "type": "LineString",
                            "coordinates": tmpArray
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
            if (err && err.responseText && err.responseText.indexOf('{') >= 0) {
                var jsonError = JSON.parse(err.responseText);
                msg += jsonError.message;
            } else if (err && err.statusText && err.statusText !== "OK")
                msg += err.statusText;

            log(msg + " " + JSON.stringify(err));
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
        type: "GET",
        dataType: this.dataType,
        crossDomain: true
    });
};

GHRequest.prototype.getInfo = function () {
    var url = this.host + "/info?type=" + this.dataType + "&key=" + this.key;
    log(url);
    return $.ajax({
        url: url,
        timeout: 3000,
        type: "GET",
        dataType: this.dataType,
        crossDomain: true
    });
};

GHInput = function (input) {
    this.set(input);
};

GHInput.isObject = function (value) {
    var stringValue = Object.prototype.toString.call(value);
    return (stringValue.toLowerCase() === "[object object]");
};

GHInput.isString = function (value) {
    var stringValue = Object.prototype.toString.call(value);
    return (stringValue.toLowerCase() === "[object string]");
};

GHInput.prototype.isResolved = function () {
    return !isNaN(this.lat) && !isNaN(this.lng);
};

GHInput.prototype.setCoord = function (lat, lng) {
    this.lat = round(lat);
    this.lng = round(lng);
    this.input = this.toString();
};

GHInput.prototype.setUnresolved = function () {
    this.lat = undefined;
    this.lng = undefined;
};

GHInput.prototype.set = function (strOrObject) {
    // either text or coordinates or object
    this.input = strOrObject;
    // reset to unresolved


    if (GHInput.isObject(strOrObject)) {
        this.setCoord(strOrObject.lat, strOrObject.lng);
    } else if (GHInput.isString(strOrObject)) {
        var index = strOrObject.indexOf(",");
        if (index >= 0) {
            this.lat = round(parseFloat(strOrObject.substr(0, index)));
            this.lng = round(parseFloat(strOrObject.substr(index + 1)));

            if (this.isResolved()) {
                this.input = this.toString();
            } else {
                this.setUnresolved();
            }
        } else {
            this.setUnresolved();
        }
    }
};

GHInput.prototype.toString = function () {
    if (this.lat !== undefined && this.lng !== undefined)
        return this.lat + "," + this.lng;
    return undefined;
};

GHRequest.prototype.setLocale = function (locale) {
    if (locale)
        this.locale = locale;
};

GHRequest.prototype.fetchTranslationMap = function (urlLocaleParam) {
    if (!urlLocaleParam)
        // let servlet figure out the locale from the Accept-Language header
        urlLocaleParam = "";
    var url = this.host + "/i18n/" + urlLocaleParam + "?type=" + this.dataType + "&key=" + this.key;
    log(url);
    return $.ajax({
        url: url,
        timeout: 3000,
        type: "GET",
        dataType: this.dataType,
        crossDomain: true
    });
};