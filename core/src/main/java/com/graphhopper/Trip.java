package com.graphhopper;

import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.InstructionList;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

import java.util.Date;
import java.util.List;

public class Trip {
    public static abstract class Leg {
        public final String type;
        public final String departureLocation;
        public final Date departureTime;
        public final List<EdgeIteratorState> edges;
        public final Geometry geometry;
        public final double distance;
        public final Date arrivalTime;

        public Leg(String type, String departureLocation, Date departureTime, List<EdgeIteratorState> edges, Geometry geometry, double distance, Date arrivalTime) {
            this.type = type;
            this.departureLocation = departureLocation;
            this.edges = edges;
            this.geometry = geometry;
            this.distance = distance;
            this.departureTime = departureTime;
            this.arrivalTime = arrivalTime;
        }

        public double getDistance() {
            return distance;
        }
    }

    public static class Stop {
        public final String stop_id;
        public final String stop_name;
        public final Point geometry;

        public final Date arrivalTime;
        public final Date departureTime;

        public Stop(String stop_id, String name, Point geometry, Date arrivalTime, Date departureTime) {
            this.stop_id = stop_id;
            this.stop_name = name;
            this.geometry = geometry;
            this.arrivalTime = arrivalTime;
            this.departureTime = departureTime;
        }
    }
    public static class WalkLeg extends Leg {
        public final InstructionList instructions;

        public WalkLeg(String departureLocation, Date departureTime, List<EdgeIteratorState> edges, Geometry geometry, double distance, InstructionList instructions, Date arrivalTime) {
            super("walk", departureLocation, departureTime, edges, geometry, distance, arrivalTime);
            this.instructions = instructions;
        }
    }
    public static class PtLeg extends Leg {
        public final String feed_id;
        public final boolean isInSameVehicleAsPrevious;
        public final String trip_headsign;
        public final long travelTime;
        public final List<Stop> stops;
        public final String trip_id;
        public final String route_id;

        public PtLeg(String feedId, boolean isInSameVehicleAsPrevious, String tripId, String routeId, List<EdgeIteratorState> edges, Date departureTime, List<Stop> stops, double distance, long travelTime, Date arrivalTime, Geometry geometry) {
            super("pt", stops.get(0).stop_name, departureTime, edges, geometry, distance, arrivalTime);
            this.feed_id = feedId;
            this.isInSameVehicleAsPrevious = isInSameVehicleAsPrevious;
            this.trip_id = tripId;
            this.route_id = routeId;
            this.trip_headsign = edges.get(0).getName();
            this.travelTime = travelTime;
            this.stops = stops;
        }

    }

}
