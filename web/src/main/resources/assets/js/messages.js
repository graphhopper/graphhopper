module.exports.extractMetaVersionInfo = function (json) {
    metaVersionInfo = "";
    if (json.data_date)
        metaVersionInfo += "<br/>Data date: " + json.data_date;
    if (json.import_date)
        metaVersionInfo += "<br/>Import date: " + json.import_date;
    if (json.prepare_date)
        metaVersionInfo += "<br/>Prepare date: " + json.prepare_date;
    if (json.version)
        metaVersionInfo += "<br/>GH version: " + json.version;
    if (json.build_date)
        metaVersionInfo += "<br/>Jar date: " + json.build_date;

    return metaVersionInfo;
};

module.exports.getSignName = function (sign) {
    if (sign === -7)
        return "keep_left";
    if (sign === -3)
        return "sharp_left";
    else if (sign === -2)
        return "left";
    else if (sign === -1)
        return "slight_left";
    else if (sign === 0)
        return "continue";
    else if (sign === 1)
        return "slight_right";
    else if (sign === 2)
        return "right";
    else if (sign === 3)
        return "sharp_right";
    else if (sign === 4)
        return "marker-icon-red";
    else if (sign === 5)
        return "marker-icon-blue";
    else if (sign === 6)
        return "roundabout";
    else if (sign === 7)
        return "keep_right";
    else if (sign === 101)
        return "pt_start_trip";
    else if (sign === 102)
        return "pt_transfer_to";
    else if (sign === 103)
        return "pt_end_trip";
    else
        // throw "did not find sign " + sign;
        return "unknown";
};

module.exports.browserTitle = "GraphHopper Maps - Driving Directions";
