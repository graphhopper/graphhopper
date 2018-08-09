module.exports.getCenter = function (bounds) {
    var center = {
        lat: 0,
        lng: 0
    };
    if (bounds.initialized) {
        center.lat = (bounds.minLat + bounds.maxLat) / 2;
        center.lng = (bounds.minLon + bounds.maxLon) / 2;
    }
    return center;
};

module.exports.floor = function (val, precision) {
    if (!precision)
        precision = 1e6;
    return Math.floor(val * precision) / precision;
};

module.exports.round = function (val, precision) {
    if (precision === undefined)
        precision = 1e6;
    return Math.round(val * precision) / precision;
};


