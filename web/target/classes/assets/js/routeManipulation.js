var L = require('leaflet');

module.exports.getIntermediatePointIndex = function(routeSegments, clickedLocation) {
    // for each point of the route determine its distance to the clicked location and the index
    // of the waypoint that comes next if we keep following the route
    var nextWayPointIndex = 0;
    var wayPoints = routeSegments[0].wayPoints;
    var distancesAndNextWayPointIndices = [];
    for(var i=0; i<routeSegments.length; ++i) {
        var routeCoords = routeSegments[i].coordinates;
        for(var j=0; j<routeCoords.length; ++j) {
            // whenever we hit a waypoint we start looking for the next one
            if (routeCoords[j].equals(wayPoints[nextWayPointIndex], 1.e-5)) {
                nextWayPointIndex++;
            }
            distancesAndNextWayPointIndices.push({
                distance: routeCoords[j].distanceTo(clickedLocation),
                nextWayPointIndex: nextWayPointIndex === wayPoints.length ? nextWayPointIndex-1 : nextWayPointIndex
            });
        }
    }

    // get the waypoint index for the point closest to the clicked location, if two distances are
    // equal prefer the point further down the route for a slightly nicer user experience
    var result = distancesAndNextWayPointIndices.reduce(function(prev, curr) {
       return (curr.distance - prev.distance) < 1.e-6 ? curr : prev;
    }).nextWayPointIndex;

    result = (result > 0 && result < wayPoints.length) ? result : 1; //just for safety
    return result;
};
