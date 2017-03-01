package com.graphhopper.gtfs.fare;

import com.conveyal.gtfs.model.Fare;

class Ticket {

    private Fare fare;

    Ticket(Fare fare) {
        this.fare = fare;
    }

    public Fare getFare() {
        return fare;
    }

}
