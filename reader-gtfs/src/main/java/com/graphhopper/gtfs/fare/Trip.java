package com.graphhopper.gtfs.fare;

import java.util.ArrayList;
import java.util.List;

public class Trip {

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

    Trip() {

    }

    public long duration() {
        return 6000;
    }

}
