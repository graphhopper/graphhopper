package com.graphhopper.gtfs.fare;

abstract class SanitizedFareRule {

    abstract boolean appliesTo(Trip.Segment segment);

}
