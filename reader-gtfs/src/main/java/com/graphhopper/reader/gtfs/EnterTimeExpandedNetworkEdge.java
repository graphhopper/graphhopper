package com.graphhopper.reader.gtfs;

class EnterTimeExpandedNetworkEdge extends AbstractPtEdge {
    final int time;

    EnterTimeExpandedNetworkEdge(int time) {
        super();
        this.time = time;
    }

    static double traveltime(int edgeTimeValue, long earliestStartTime) {
        int timeOfDay = (int) (earliestStartTime % (24*60*60));
        if (timeOfDay <= edgeTimeValue) {
            return (edgeTimeValue - timeOfDay);
        } else {
            return Double.POSITIVE_INFINITY;
        }
    }

}
