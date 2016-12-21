package com.graphhopper.gtfs.fare;

public class Ticket {

    private String fareId;

    public Ticket(String fareId) {
        this.fareId = fareId;
    }

    public String getFareId() {
        return fareId;
    }

}
