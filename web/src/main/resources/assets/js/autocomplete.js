var formatTools = require('./tools/format.js');
var GHInput = require('./graphhopper/GHInput.js');
var GHRoute = require('./graphhopper/GHRoute.js');
var mapLayer = require('./map.js');

var dataToHtml = function (data, query) {
    var element = "";
    if (data.name) {
        var name = data.name;
        if (data.housenumber)
            name = formatTools.insComma(name, data.housenumber);

        element += "<div class='nameseg'>" + formatTools.formatValue(name, query) + "</div>";
    }

    var addStr = "";
    if (data.postcode)
        addStr = data.postcode;
    if (data.city)
        addStr = formatTools.insComma(addStr, data.city);
    if (data.country)
        addStr = formatTools.insComma(addStr, data.country);

    if (addStr)
        element += "<div class='cityseg'>" + formatTools.formatValue(addStr, query) + "</div>";

    if (data.osm_key === "highway") {
        // ignore
    }
    if (data.osm_key === "place") {
        element += "<span class='moreseg'>" + data.osm_value + "</span>";
    } else
        element += "<span class='moreseg'>" + data.osm_key + "</span>";
    return element;
};

var dataToText = function (data) {
    var text = "";
    if (data.name) {
        text = data.name;
    } else if (data.street) {
        text = data.street;
        if (data.housenumber)
            text = formatTools.insComma(text, data.housenumber);
    }

    if (data.postcode)
        text = formatTools.insComma(text, data.postcode);

    // make sure name won't be duplicated
    if (data.city && text.indexOf(data.city) < 0)
        text = formatTools.insComma(text, data.city);

    if (data.country && text.indexOf(data.country) < 0)
        text = formatTools.insComma(text, data.country);
    return text;
};

var AutoComplete = function (host, key) {
    this.host = host;
    this.key = key;
    this.dataType = "json";
    this.api_params = {"locale": "en"};
};

AutoComplete.prototype.createPath = function (url) {
    for (var key in this.api_params) {
        var val = this.api_params[key];
        if (GHRoute.isArray(val)) {
            for (var keyIndex in val) {
                url += "&" + encodeURIComponent(key) + "=" + encodeURIComponent(val[keyIndex]);
            }
        } else {
            url += "&" + encodeURIComponent(key) + "=" + encodeURIComponent(val);
        }
    }
    return url;
};

AutoComplete.prototype.createGeocodeURL = function (ghRequest, prevIndex) {
    var path = this.createPath(this.host + "/geocode?limit=6&type=" + this.dataType + "&key=" + this.key);
    if (prevIndex >= 0 && prevIndex < ghRequest.route.size()) {
        var point = ghRequest.route.getIndex(prevIndex);
        if (point.isResolved()) {
            path += "&point=" + point.lat + "," + point.lng;
        }
    }
    return path;
};

AutoComplete.prototype.getAutoCompleteDiv = function (index) {
    return $('#locationpoints > div.pointDiv').eq(index).find(".pointInput");
};

AutoComplete.prototype.hide = function () {
    $(':input[id$="_Input"]').autocomplete().hide();
};

AutoComplete.prototype.showListForIndex = function (ghRequest, routeIfAllResolved, index) {
    var myAutoDiv = this.getAutoCompleteDiv(index);
    var url = this.createGeocodeURL(ghRequest, index - 1);

    var options = {
        containerClass: "autocomplete",
        timeout: 1000,
        /* avoid too many requests when typing quickly */
        deferRequestBy: 200,
        minChars: 2,
        maxHeight: 510,
        noCache: true,
        /* this default could be problematic: preventBadQueries: true, */
        triggerSelectOnValidInput: false,
        autoSelectFirst: false,
        paramName: "q",
        dataType: ghRequest.dataType,
        onSearchStart: function (params) {
            // query server only if not a parsable point (i.e. format lat,lon)
            var val = new GHInput(params.q).lat;
            return val === undefined;
        },
        serviceUrl: function () {
            return url;
        },
        transformResult: function (response, originalQuery) {
            response.suggestions = [];
            if (response.hits)
                for (var i = 0; i < response.hits.length; i++) {
                    var hit = response.hits[i];
                    response.suggestions.push({value: dataToText(hit), data: hit});
                }
            return response;
        },
        onSearchError: function (element, q, jqXHR, textStatus, errorThrown) {
            // too many errors if interrupted console.log(element + ", " + JSON.stringify(q) + ", textStatus " + textStatus + ", " + errorThrown);
        },
        formatResult: function (suggestion, currInput) {
            // avoid highlighting for now as this breaks the html sometimes
            return dataToHtml(suggestion.data, currInput);
        },
        onSelect: function (suggestion) {
            options.onPreSelect(suggestion);
        },
        onPreSelect: function (suggestion) {
            var req = ghRequest.route.getIndex(index);

            myAutoDiv.autocomplete().disable();

            var point = suggestion.data.point;
            req.setCoord(point.lat, point.lng);

            req.input = suggestion.value;
            if (!routeIfAllResolved(true))
                mapLayer.focus(req, 15, index);

            myAutoDiv.autocomplete().enable();
        }
    };

    myAutoDiv.autocomplete(options);
};

AutoComplete.prototype.createStub = function () {
    complete = new AutoComplete();
    complete.showListForIndex = function () {};
    complete.hide = function () {};
    return complete;
};

AutoComplete.prototype.setLocale = function (locale) {
    if (locale)
        this.api_params.locale = locale;
};

module.exports = AutoComplete;
