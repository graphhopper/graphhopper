package com.graphhopper.gtfs.fare;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Trip {

    public static class Segment {

        private final String route;
        private long startTime;
        private String originId;
        private String destinationId;
        private Set<String> zones;

        public Segment(String route, long startTime, String originId, String destinationId, Set<String> zones) {
            this.route = route;
            this.startTime = startTime;
            this.originId = originId;
            this.destinationId = destinationId;
            this.zones = zones;
        }

        String getRoute() {
            return route;
        }

        long getStartTime() {
            return startTime;
        }

        String getOriginId() {
            return originId;
        }

        String getDestinationId() {
            return destinationId;
        }

        Set<String> getZones() {
            return zones;
        }

    }

    public final List<Segment> segments = new ArrayList<>();


}
