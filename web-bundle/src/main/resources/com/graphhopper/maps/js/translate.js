var defaultTranslationMap = null;
var enTranslationMap = null;
var i18nIsInitialized;

var mathTools = require('./tools/math.js');

function tr2(key, args) {
    if (key === null) {
        console.log("ERROR: key was null?");
        return "";
    }
    if (defaultTranslationMap === null) {
        console.log("ERROR: defaultTranslationMap was not initialized?");
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
    if (key !== key.toLowerCase())
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
        var l = location_points.size;
        $(location_points).each(function (index) {
            if (index === 0) {
                $(this).attr("placeholder", tr("from_hint"));
                $(this).focus();
            } else if (index === (l - 1)) {
                $(this).attr("placeholder", tr("to_hint"));
            } else {
                $(this).attr("placeholder", tr("via_hint"));
            }
        });
        $('.pointFlag').each(function () {
            $(this).attr('title', tr('drag_to_reorder'));
        });
        $('.pointDelete').each(function () {
            $(this).attr("title", tr("delete_from_route"));
        });
        $('#export-link').attr("title", tr("staticlink"));
        $('#gpxExportButton').attr("title", tr("gpx_export_button"));
    }
}

function mToKm(m) {
    return m / 1000;
}

function mToFt(m) {
    return m / 0.3048;
}

function mToMi(m) {
    return m / 1609.344;
}

module.exports.createDistanceString = function (dist, useMiles) {
    if (!useMiles) {
        if (dist < 900)
            return mathTools.round(dist, 1) + tr2("m_abbr");

        dist = mathTools.round(mToKm(dist), 100);
        if (dist > 100)
            dist = mathTools.round(dist, 1);
        return dist + tr2("km_abbr");
    } else {
        if (dist < 152)
            return mathTools.round(mToFt(dist), 1) + tr2("ft_abbr");

        dist = mathTools.round(mToMi(dist), 100);
        if (dist > 100)
            dist = mathTools.round(dist, 1);
        return dist + tr2("mi_abbr");
    }
};

module.exports.createEleInfoString = function (ascend, descend, useMiles) {
    var str = "";
    if (ascend > 0 || descend > 0) {
        str = "<br/> ";
        if (ascend > 0) {
            if (!useMiles)
                str += "&#8599;" + mathTools.round(ascend, 1) + tr2("m_abbr");
            else
                str += "&#8599;" + mathTools.round(mToFt(ascend), 1) + tr2("ft_abbr");
        }

        if (descend > 0) {
            if (!useMiles)
                str += " &#8600;" + mathTools.round(descend, 1) + tr2("m_abbr");
            else
                str += " &#8600;" + mathTools.round(mToFt(descend), 1) + tr2("ft_abbr");
        }
    }

    return str;
};

module.exports.createTimeString = function (time) {
    var seconds = mathTools.round(time / 1000, 1);
    var minutes = mathTools.round(seconds / 60, 1);
    var restSeconds = mathTools.round(seconds % 60, 1)
    var resTimeStr;
    if (minutes > 60) {
        if (minutes / 60 > 24) {
            resTimeStr = mathTools.floor(minutes / 60 / 24, 1) + tr2("day_abbr");
            minutes = mathTools.floor(((minutes / 60) % 24), 1);
            if (minutes > 0)
                resTimeStr += " " + minutes + tr2("hour_abbr");
        } else {
            resTimeStr = mathTools.floor(minutes / 60, 1) + tr2("hour_abbr");
            minutes = mathTools.floor(minutes % 60, 1);
            if (minutes > 0)
                resTimeStr += " " + minutes + tr2("min_abbr");
        }
    } else if (minutes < 10 && restSeconds > 0) {
        resTimeStr = minutes + tr2("min_abbr") + restSeconds.toString().padStart(2, "0");
    } else
        resTimeStr = minutes + tr2("min_abbr");
    return resTimeStr;
};

module.exports.tr = tr;
module.exports.tr2 = tr2;

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
