package com.graphhopper.gtfs.fare;

final class OriginDestinationRule extends SanitizedFareRule {
    private final String origin_id;
    private final String destination_id;

    OriginDestinationRule(String origin_id, String destination_id) {
        this.origin_id = origin_id;
        this.destination_id = destination_id;
    }

    @Override
    boolean appliesTo(Trip.Segment segment) {
        return origin_id.equals(segment.getOriginId()) && destination_id.equals(segment.getDestinationId());
    }
}
