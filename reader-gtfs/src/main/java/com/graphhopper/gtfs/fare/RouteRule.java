package com.graphhopper.gtfs.fare;

final class RouteRule extends SanitizedFareRule {
    private final String route_id;

    RouteRule(String route_id) {
        this.route_id = route_id;
    }

    @Override
    boolean appliesTo(Trip.Segment segment) {
        return route_id.equals(segment.getRoute());
    }
}
