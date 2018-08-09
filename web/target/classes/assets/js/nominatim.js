var nominatimURL = "https://nominatim.openstreetmap.org/search";
var nominatimReverseURL = "https://nominatim.openstreetmap.org/reverse";

var bounds;
var mathTools = require('./tools/math.js');
var format = require('./tools/format.js');

/**
 * Returns a defer object containing the location pointing to a resolvedList with all the found
 * coordinates.
 */
function createAmbiguityList(locCoord) {
    locCoord.error = "";
    locCoord.resolvedList = [];
    var timeout = 3000;

    if (locCoord.isResolved()) {
        // if we changed only another location no need to look this up again
        var tmpDefer = $.Deferred();
        tmpDefer.resolve([locCoord]);
        return tmpDefer;
    } else if (locCoord.lat && locCoord.lng) {
        var url = nominatimReverseURL + "?lat=" + locCoord.lat + "&lon=" + locCoord.lng + "&format=json&zoom=16";
        return $.ajax({
            url: url,
            type: "GET",
            dataType: "json",
            timeout: timeout
        }).then(
                function (json) {
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
                    point.locationDetails = format.formatLocationEntry(address);
                    // point.address = json.address;
                    locCoord.resolvedList.push(point);
                    return [locCoord];
                },
                function (err) {
                    console.log("[nominatim_reverse] Error while looking up coordinate lat=" + locCoord.lat + "&lon=" + locCoord.lng);
                    locCoord.error = "Problem while looking up location.";
                    return [locCoord];
                }
        );
    } else {
        return doGeoCoding(locCoord.input, 10, timeout).then(
                function (jsonArgs) {
                    if (!jsonArgs || jsonArgs.length === 0) {
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
                        point.lat = mathTools.round(json.lat);
                        point.lng = mathTools.round(json.lon);
                        point.locationDetails = format.formatLocationEntry(address);
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
                },
                function () {
                    locCoord.error = "Problem while looking up address";
                    return [locCoord];
                }
        );
    }
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
    }).fail(createCallback("[nominatim] Problem while looking up location " + input));
}

function createCallback(errorFallback) {
    return function (err) {
        console.log(errorFallback + " " + JSON.stringify(err));
    };
}

function resolve(index, locCoord) {
    var div = $('#locationpoints > div.pointDiv').eq(index);
    $(div).find(".pointFlag").hide();
    $(div).find(".pointIndicator").show();
    $(div).find(".pointInput").val(locCoord.input);

    return createAmbiguityList(locCoord).always(function () {
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

module.exports.resolve = resolve;
module.exports.setBounds = function (newBounds) {
    bounds = newBounds;
};
