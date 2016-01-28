module.exports.getSortedVehicleKeys = function (vehicleHashMap, prefer) {
    var keys = Object.keys(vehicleHashMap);

    keys.sort(function (a, b) {
        var intValA = prefer[a];
        var intValB = prefer[b];

        if (!intValA && !intValB)
            return a.localeCompare(b);

        if (!intValA)
            intValA = 4;
        if (!intValB)
            intValB = 4;

        return intValA - intValB;
    });
    return keys;
};

