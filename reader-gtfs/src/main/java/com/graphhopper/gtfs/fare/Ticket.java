package com.graphhopper.gtfs.fare;

import com.conveyal.gtfs.model.Fare;

public class Ticket {

    private Fare fare;

    public Ticket(Fare fare) {
        this.fare = fare;
    }

    public Fare getFare() {
        return fare;
    }

}
