var isArray = function (value) {
    var stringValue = Object.prototype.toString.call(value);
    return (stringValue.toLowerCase() === "[object array]");
};

function parseUrlWithHisto() {
    if (window.location.hash)
        return parseUrl(window.location.hash);

    return parseUrl(window.location.search);
}

function parseUrl(query) {
    var index = query.indexOf('?');
    if (index >= 0)
        query = query.substring(index + 1);
    var res = {};
    var vars = query.split("&");
    for (var i = 0; i < vars.length; i++) {
        var indexPos = vars[i].indexOf("=");
        if (indexPos < 0)
            continue;

        var key = vars[i].substring(0, indexPos);
        var value = vars[i].substring(indexPos + 1);
        value = decodeURIComponent(value.replace(/\+/g, ' '));
        mergeParamIntoObject(res, key, value);
    }
    return res;
}

// the key value parameter is merged into the first object 'res'
// it is suboptimal that we need two long parameters for two array entries:
// one.two=1&one.two=2 => one: { two : ["1", "2"] }
function mergeParamIntoObject(res, key, value) {
    var tmpVal;

    var objectIndex = key.indexOf(".");
    if(objectIndex < 0) {
        // force always array for some keys even if just one parameter
        if (typeof res[key] === "undefined" && key !== "heading" && key !== "point" && key !== "details") {
            if (value === 'true')
                value = true;
            else if (value === 'false')
                value = false;

            res[key] = value;
        } else {
            tmpVal = res[key];
            if (isArray(tmpVal)) {
                tmpVal.push(value);
            } else if (tmpVal) {
                res[key] = [tmpVal, value];
            } else {
                res[key] = [value];
            }
        }
        // leaf of recursion reached
        return res;
    }

    var newKey = key.substring(0, objectIndex);
    var subKey = key.substring(objectIndex + 1);

    if(newKey == "__proto__" || newKey == "constructor" || newKey == "prototype") return res;

    tmpVal = res[newKey];
    if(!tmpVal)
        tmpVal = {};

    // recursion
    res[newKey] = mergeParamIntoObject(tmpVal, subKey, value);
    return res;
}

module.exports.parseUrl = parseUrl;
module.exports.mergeParamIntoObject = mergeParamIntoObject;
module.exports.parseUrlWithHisto = parseUrlWithHisto;
