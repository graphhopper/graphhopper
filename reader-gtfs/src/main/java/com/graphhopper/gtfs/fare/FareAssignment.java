package com.graphhopper.gtfs.fare;

import com.conveyal.gtfs.model.Fare;

class FareAssignment {

    Trip.Segment segment;
    Fare fare;

    FareAssignment(Trip.Segment segment) {
        this.segment = segment;
    }

    Fare getFare() {
        return fare;
    }

    void setFare(Fare fare) {
        this.fare = fare;
    }

}
