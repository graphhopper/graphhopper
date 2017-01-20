package com.graphhopper;

import com.graphhopper.util.EdgeIteratorState;

import java.util.Date;
import java.util.List;

public class Trip {
    public static class Leg {
        public final List<EdgeIteratorState> edges;

        public Leg(List<EdgeIteratorState> edges) {
            this.edges = edges;
        }
    }
    public static class PtLeg extends Leg {

        public final String trip_headsign;
        public final Date departureTime;  // TODO: Java 8: Should be LocalDateTime
        public PtLeg(List<EdgeIteratorState> edges, Date departureTime) {
            super(edges);
            this.trip_headsign = edges.get(0).getName();
            this.departureTime = departureTime;
        }

    }

}
