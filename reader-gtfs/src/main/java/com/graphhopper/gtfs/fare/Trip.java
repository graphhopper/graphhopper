package com.graphhopper.gtfs.fare;

import java.util.ArrayList;
import java.util.List;

public class Trip {

    static class Segment {

        private final String route;
        private long startTime;

        public Segment(String route, long startTime) {
            this.route = route;
            this.startTime = startTime;
        }

        public String getRoute() {
            return route;
        }

        public long getStartTime() {
            return startTime;
        }
    }

    final List<Segment> segments = new ArrayList<>();


}
