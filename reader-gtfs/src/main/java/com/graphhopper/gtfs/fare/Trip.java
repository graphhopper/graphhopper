package com.graphhopper.gtfs.fare;

import java.util.ArrayList;
import java.util.List;

public class Trip {

    private final long duration;

    static class Segment {

        private final String route;

        public Segment(String route) {
            this.route = route;
        }

        public String getRoute() {
            return route;
        }

    }

    final List<Segment> segments = new ArrayList<>();

    Trip(long duration) {
        this.duration = duration;
    }

    public long duration() {
        return duration;
    }

}
