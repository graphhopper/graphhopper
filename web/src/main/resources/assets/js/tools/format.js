function insComma(textA, textB) {
    if (textA.length > 0)
        return textA + ", " + textB;
    return textB;
}

// TODO unused: currently just dataToHtml is used
function formatLocationEntry(address) {
    var locationDetails = {};
    var text = "";
    if (!address)
        return locationDetails;
    if (address.road) {
        text = address.road;
        if (address.house_number) {
            if (text.length > 0)
                text += " ";
            text += address.house_number;
        }
        locationDetails.road = text;
    }

    if (address.postcode)
        locationDetails.postcode = address.postcode;
    locationDetails.country = address.country;

    if (address.city || address.suburb || address.town || address.village || address.hamlet || address.locality) {
        text = "";
        if (address.locality)
            text = insComma(text, address.locality);
        if (address.hamlet)
            text = insComma(text, address.hamlet);
        if (address.village)
            text = insComma(text, address.village);
        if (address.suburb)
            text = insComma(text, address.suburb);
        if (address.city)
            text = insComma(text, address.city);
        if (address.town)
            text = insComma(text, address.town);
        locationDetails.city = text;
    }

    text = "";
    if (address.state)
        text += address.state;

    if (address.continent)
        text = insComma(text, address.continent);

    locationDetails.more = text;
    return locationDetails;
}

module.exports.formatLocationEntry = formatLocationEntry;

// TODO unused
module.exports.formatAddress = function (address) {
    return ((address.road) ? address.road + ', ' : '') +
        ((address.postcode) ? address.postcode + ', ' : '') +
        ((address.city) ? address.city + ', ' : '') +
        ((address.country) ? address.country : '');
};

module.exports.insComma = insComma;

module.exports.formatValue = function (orig, query) {
    var pattern = '(' + $.Autocomplete.utils.escapeRegExChars(query) + ')';
    return orig.replace(/[<>]/g, "_").replace(new RegExp(pattern, 'gi'), '<strong>$1<\/strong>');
};
