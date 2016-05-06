package com.graphhopper.matrix;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the resulting distance matrix for a given set of
 * origin/destination nodes.
 *
 * @author Pascal Büttiker
 */
public final class DistanceMatrix {

    private final List<DistanceRow> rows = new ArrayList<>();

    /**
     * Adds a new row for the given origin node.
     * @param originNode The node id of the origin
     * @return The new distance row
     */
    public DistanceRow addRow(int originNode){
        DistanceRow row = new DistanceRow(originNode);
        rows.add(row);
        return row;
    }

    public List<DistanceRow> getRows() {
        return rows;
    }

    public DistanceRow getRow(int index) {
        return rows.get(index);
    }


    @Override
    public String toString(){
        String matrixStr = "";
        for (DistanceRow row : getRows()) {
            matrixStr += row.toString() + "\n";
        }
        return matrixStr;
    }

    /**
     * Represents a row in the distance matrix.
     *
     * Holds all the distance/duration information from a single starting node to
     * a given set of destinations.
     */
    public static class DistanceRow {
        /**
         * The starting node
         */
        public final int originNode;

        /**
         * Time/duration info to the given destinations
         */
        public final List<DestinationInfo> destinations = new ArrayList<>();

        public DistanceRow(int originNode){
            this.originNode = originNode;
        }

        /**
         * Adds a new destination node with the given distance/duration data
         * @param node The destination node
         * @param distance The distance to the destination node
         * @param time The estimated time to the destination node
         * @return
         */
        public DestinationInfo addDestination(int node, double distance, long time){
            DestinationInfo dest = new DestinationInfo(node, distance, time);
            destinations.add(dest);
            return dest;
        }

        public List<DestinationInfo> getDestinations() {
            return destinations;
        }


        @Override
        public String toString(){

            String destinationStrs = "";

            if(!getDestinations().isEmpty()){
                for (DestinationInfo dest : getDestinations()) {
                    destinationStrs += dest.toString() + ",";
                }
                destinationStrs = destinationStrs.substring(0,destinationStrs.length()-1);
            }

            return "("+originNode+")-->[" +  destinationStrs + "]";
        }
    }

    public static class DestinationInfo {

        public final int destinationNode;
        public final double distance;
        public final long time;

        public DestinationInfo(int node, double distance, long time){
            this.destinationNode = node;
            this.distance = distance;
            this.time = time;
        }

        @Override
        public String toString(){
            return "{("+destinationNode+")#"+distance+"}";
        }
    }
}
