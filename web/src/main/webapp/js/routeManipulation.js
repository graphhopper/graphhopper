var L = require('leaflet');

module.exports.getIntermediatePointIndex = function(routeSegments, clickedLocation) {
    // finds the point of a routeSegment that is closest to the clicked location
    var getClosestPoint = function(routeSegment) {
        return routeSegment.coordinates.map(function(c, i) {
            return {
                index: i,
                distance: L.latLng(c).distanceTo(clickedLocation)
            };
        }).reduce(function(prev, curr) {
            return curr.distance < prev.distance ? curr : prev;
        });
    };

    // find the part of the route that contains the closest point to the clicked location
    // there can be more than one such parts if alternative routes are used
    var closestRouteSegment = routeSegments.map(function(rs) {
       rs.closestPoint = getClosestPoint(rs);
       return rs;
    }).reduce(function(prev, curr) {
        return curr.closestPoint.distance < prev.closestPoint.distance ? curr : prev;
    });

    // start at the closest point and follow the route to find the index of the next waypoint
    var routeCoords = closestRouteSegment.coordinates;
    var wayPoints = closestRouteSegment.wayPoints;
    var index = 1; // if no waypoint is found the index will be one
    for (var i=closestRouteSegment.closestPoint.index; i<routeCoords.length; ++i) {
        var wpIndex = wayPoints.findIndex(function(wp) {
            return routeCoords[i].equals(wp, 1.e-3);
        });
        if (wpIndex !== -1) {
            index = wpIndex === 0 ? 1 : wpIndex;
            break;
        }
    }
    return index;
};

