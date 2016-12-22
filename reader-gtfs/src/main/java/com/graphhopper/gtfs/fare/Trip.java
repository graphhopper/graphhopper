package com.graphhopper.gtfs.fare;

import java.util.ArrayList;
import java.util.List;

public class Trip {

    static class Segment {

        private final String route;
        private long startTime;
        private String originId;
        private String destinationId;

        public Segment(String route, long startTime, String originId, String destinationId) {
            this.route = route;
            this.startTime = startTime;
            this.originId = originId;
            this.destinationId = destinationId;
        }

        public String getRoute() {
            return route;
        }

        public long getStartTime() {
            return startTime;
        }

        public String getOriginId() {
            return originId;
        }

        public void setOriginId(String originId) {
            this.originId = originId;
        }

        public String getDestinationId() {
            return destinationId;
        }

        public void setDestinationId(String destinationId) {
            this.destinationId = destinationId;
        }
    }

    final List<Segment> segments = new ArrayList<>();


}
