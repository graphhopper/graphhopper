package com.graphhopper.matrix;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the resulting distance matrix
 *
 * @author Pascal Büttiker
 */
public final class DistanceMatrix {

    private final List<DistanceRow> rows = new ArrayList<>();


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


    public static class DistanceRow {
        public final int originNode;
        public final List<DestinationInfo> destinations = new ArrayList<>();

        public DistanceRow(int originNode){
            this.originNode = originNode;
        }

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
