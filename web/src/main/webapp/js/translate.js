var defaultTranslationMap = null;
var enTranslationMap = null;
var i18nIsInitialized;

var mathTools = require('./tools/math.js');

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

function tr(key, args) {
    if(key !== key.toLowerCase())
        console.log("key " + key + " has to be lower case");
    
    return tr2("web." + key, args);
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
    if (global.$) {
        $('#searchButton').attr("value", tr("search_button"));
        var location_points = $("#locationpoints > div.pointDiv > input.pointInput");
        var l = location_points.size();
        $(location_points).each(function (index) {
            if (index === 0)
                $(this).attr("placeholder", tr("from_hint"));
            else if (index === (l - 1))
                $(this).attr("placeholder", tr("to_hint"));
            else
                $(this).attr("placeholder", tr("via_hint"));
        });
        $('#gpxExportButton').attr("title", tr("gpx_export_button"));
    }
}

module.exports.createDistanceString = function (dist) {
    if (dist < 900)
        return mathTools.round(dist, 1) + tr2("m_abbr");

    dist = mathTools.round(dist / 1000, 100);
    if (dist > 100)
        dist = mathTools.round(dist, 1);
    return dist + tr2("km_abbr");
};

module.exports.createEleInfoString = function (ascend, descend) {
    var str = "";
    if (ascend > 0 || descend > 0) {
        str = "<br/> ";
        if (ascend > 0)
            str += "&#8599;" + mathTools.round(ascend, 1) + tr2("m_abbr");

        if (descend > 0)
            str += " &#8600;" + mathTools.round(descend, 1) + tr2("m_abbr");
    }

    return str;
};

module.exports.createTimeString = function (time) {
    var tmpTime = mathTools.round(time / 60 / 1000, 1000);
    var resTimeStr;
    if (tmpTime > 60) {
        if (tmpTime / 60 > 24) {
            resTimeStr = mathTools.floor(tmpTime / 60 / 24, 1) + tr2("day_abbr");
            tmpTime = mathTools.floor(((tmpTime / 60) % 24), 1);
            if (tmpTime > 0)
                resTimeStr += " " + tmpTime + tr2("hour_abbr");
        } else {
            resTimeStr = mathTools.floor(tmpTime / 60, 1) + tr2("hour_abbr");
            tmpTime = mathTools.floor(tmpTime % 60, 1);
            if (tmpTime > 0)
                resTimeStr += " " + tmpTime + tr2("min_abbr");
        }
    } else
        resTimeStr = mathTools.round(tmpTime % 60, 1) + tr2("min_abbr");
    return resTimeStr;
};

module.exports.tr = tr;

module.exports.nanoTemplate = function (template, data) {
    return template.replace(/\{([\w\.]*)\}/g, function (str, key) {
        var keys = key.split("."), v = data[keys.shift()];
        for (i = 0, l = keys.length; i < l; _i++)
            v = v[this];
        return (typeof v !== "undefined" && v !== null) ? v : "";
    });
};

module.exports.init = function (translations) {
    // init language
    // 1. determined by Accept-Language header, falls back to 'en' if no translation map available
    // 2. can be overwritten by url parameter
    defaultTranslationMap = translations["default"];
    enTranslationMap = translations.en;
    if (!defaultTranslationMap)
        defaultTranslationMap = enTranslationMap;

    i18nIsInitialized = true;
    initI18N();
};

module.exports.isI18nIsInitialized = function () {
    return i18nIsInitialized;
};
