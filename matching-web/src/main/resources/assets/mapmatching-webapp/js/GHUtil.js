GHUtil = function () {
};
var graphhopper = {util: new GHUtil()};

GHInput = function (input, input2) {
    this.set(input, input2);
};

GHInput.prototype.round = function (val, precision) {
    if (precision === undefined)
        precision = 1e6;
    return Math.round(val * precision) / precision;
};

GHInput.prototype.setCoord = function (lat, lng) {
    this.lat = this.round(lat);
    this.lng = this.round(lng);
    this.input = this.toString();
};

GHInput.isObject = function (value) {
    var stringValue = Object.prototype.toString.call(value);
    return (stringValue.toLowerCase() === "[object object]");
};

GHInput.isString = function (value) {
    var stringValue = Object.prototype.toString.call(value);
    return (stringValue.toLowerCase() === "[object string]");
};

GHInput.prototype.set = function (strOrObject, input2) {
    if (input2) {
        this.setCoord(strOrObject, input2);
        return;
    }

    // either text or coordinates or object
    this.input = strOrObject;

    if (GHInput.isObject(strOrObject)) {
        this.setCoord(strOrObject.lat, strOrObject.lng);
    } else if (GHInput.isString(strOrObject)) {
        var index = strOrObject.indexOf(",");
        if (index >= 0) {
            this.lat = this.round(parseFloat(strOrObject.substr(0, index)));
            this.lng = this.round(parseFloat(strOrObject.substr(index + 1)));
        }
    }
};

GHInput.prototype.toString = function () {
    if (this.lat !== undefined && this.lng !== undefined)
        return this.lat + "," + this.lng;
    return undefined;
};

GHUtil.prototype.clone = function (obj) {
    var newObj = {};
    for (var prop in obj) {
        if (obj.hasOwnProperty(prop)) {
            newObj[prop] = obj[prop];
        }
    }
    return newObj;
};

GHUtil.prototype.copyProperties = function (args, argsInto) {
    if (!args)
        return argsInto;

    for (var prop in args) {
        if (args.hasOwnProperty(prop) && args[prop] !== undefined) {
            argsInto[prop] = args[prop];
        }
    }

    return argsInto;
};

GHUtil.prototype.decodePath = function (encoded, is3D) {
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
};