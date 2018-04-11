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

        public abstract Date getDepartureTime();
        public abstract Date getArrivalTime();
    }

    public static class Stop {
        public final String stop_id;
        public final String stop_name;
        public final Point geometry;

        public final Date arrivalTime;
        public final Date plannedArrivalTime;
        public final Date predictedArrivalTime;
        public final boolean arrivalCancelled;

        public final Date departureTime;
        public final Date plannedDepartureTime;
        public final Date predictedDepartureTime;
        public final boolean departureCancelled;

        public Stop(String stop_id, String name, Point geometry, Date arrivalTime, Date plannedArrivalTime, Date predictedArrivalTime, boolean arrivalCancelled, Date departureTime, Date plannedDepartureTime, Date predictedDepartureTime, boolean departureCancelled) {
            this.stop_id = stop_id;
            this.stop_name = name;
            this.geometry = geometry;
            this.arrivalTime = arrivalTime;
            this.plannedArrivalTime = plannedArrivalTime;
            this.predictedArrivalTime = predictedArrivalTime;
            this.arrivalCancelled = arrivalCancelled;
            this.departureTime = departureTime;
            this.plannedDepartureTime = plannedDepartureTime;
            this.predictedDepartureTime = predictedDepartureTime;
            this.departureCancelled = departureCancelled;
        }

        @Override
        public String toString() {
            return "Stop{" +
                    "stop_id='" + stop_id + '\'' +
                    ", arrivalTime=" + arrivalTime +
                    ", departureTime=" + departureTime +
                    '}';
        }
    }
    public static class WalkLeg extends Leg {
        public final InstructionList instructions;
        private final Date departureTime;
        private final Date arrivalTime;

        public WalkLeg(String departureLocation, Date departureTime, List<EdgeIteratorState> edges, Geometry geometry, double distance, InstructionList instructions, Date arrivalTime) {
            super("walk", departureLocation, edges, geometry, distance);
            this.instructions = instructions;
            this.departureTime = departureTime;
            this.arrivalTime = arrivalTime;
        }

        @Override
        public Date getDepartureTime() {
            return departureTime;
        }

        @Override
        public Date getArrivalTime() {
            return arrivalTime;
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

        public PtLeg(String feedId, boolean isInSameVehicleAsPrevious, String tripId, String routeId, List<EdgeIteratorState> edges, List<Stop> stops, double distance, long travelTime, Geometry geometry) {
            super("pt", stops.get(0).stop_name, edges, geometry, distance);
            this.feed_id = feedId;
            this.isInSameVehicleAsPrevious = isInSameVehicleAsPrevious;
            this.trip_id = tripId;
            this.route_id = routeId;
            this.trip_headsign = edges.get(0).getName();
            this.travelTime = travelTime;
            this.stops = stops;
        }

        @Override
        public Date getDepartureTime() {
            return stops.get(0).departureTime;
        }

        @Override
        public Date getArrivalTime() {
            return stops.get(stops.size()-1).arrivalTime;
        }
    }

}
