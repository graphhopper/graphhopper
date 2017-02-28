package com.graphhopper;

import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

import java.util.Date;
import java.util.List;

public class Trip {
    public static abstract class Leg {
        public final String type;
        public final String departureLocation;
        public final List<EdgeIteratorState> edges;
        public final Geometry geometry;

        public final double distance;

        public Leg(String type, String departureLocation, List<EdgeIteratorState> edges, Geometry geometry, double distance) {
            this.type = type;
            this.departureLocation = departureLocation;
            this.edges = edges;
            this.geometry = geometry;
            this.distance = distance;
        }

        public double getDistance() {
            return distance;
        }
    }

    public static class Stop {
        public final String name;
        public final Point geometry;

        public Stop(String name, Point geometry) {
            this.name = name;
            this.geometry = geometry;
        }
    }
    public static class WalkLeg extends Leg {
        public final InstructionList instructions;

        public WalkLeg(String departureLocation, List<EdgeIteratorState> edges, Geometry geometry, double distance, InstructionList instructions) {
            super("walk", departureLocation, edges, geometry, distance);
            this.instructions = instructions;
        }
    }
    public static class PtLeg extends Leg {

        public final String trip_headsign;
        public final Date departureTime;  // TODO: Java 8: Should be LocalDateTime
        public final long travelTime;
        public final List<Stop> stops;
        public final Stop boardStop;
        public final String routeId;

        public PtLeg(Stop stop, String routeId, List<EdgeIteratorState> edges, Date departureTime, List<Stop> stops, double distance, long travelTime, Geometry geometry) {
            super("pt", stop.name, edges, geometry, distance);
            this.boardStop = stop;
            this.routeId = routeId;
            this.trip_headsign = edges.get(0).getName();
            this.departureTime = departureTime;
            this.travelTime = travelTime;
            this.stops = stops;
        }

    }

}
