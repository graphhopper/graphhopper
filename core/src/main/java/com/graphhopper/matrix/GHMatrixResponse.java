package com.graphhopper.matrix;

import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the result of a distance matrix query.
 *
 * @author Pascal Büttiker
 */
public class GHMatrixResponse {

    private final List<GHMatrixDistanceRow> rows = new ArrayList<>();


    /**
     * Adds a new distance row for the given origin point.
     */
    public GHMatrixDistanceRow addRow(GHPoint origin){
        GHMatrixDistanceRow row = new GHMatrixDistanceRow(origin);
        rows.add(row);
        return row;
    }

    /**
     * Gets all rows of the distance matrix
     */
    public List<GHMatrixDistanceRow> getRows() {
        return rows;
    }


    public static class GHMatrixDistanceRow {
        public final GHPoint origin;
        public final List<GHMatrixDestinationInfo> destinations = new ArrayList<>();

        public GHMatrixDistanceRow(GHPoint origin){
            this.origin = origin;
        }

        public GHMatrixDestinationInfo addDestination(GHPoint destination, double distance, long time){
            GHMatrixDestinationInfo dest = new GHMatrixDestinationInfo(destination, distance, time);
            destinations.add(dest);
            return dest;
        }

        public List<GHMatrixDestinationInfo> getDestinations() {
            return destinations;
        }
    }

    public static class GHMatrixDestinationInfo {

        public final GHPoint destination;
        public final double distance;
        public final long time;

        /**
         * Creates a new distance info from the origin to the given destination with the given distance/time info
         * @param destination
         * @param distance
         * @param time
         */
        public GHMatrixDestinationInfo(GHPoint destination, double distance, long time){
            this.destination = destination;
            this.distance = distance;
            this.time = time;
        }
    }
}
