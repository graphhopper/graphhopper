package com.graphhopper.reader.gtfs;

class EnterTimeExpandedNetworkEdge extends AbstractPtEdge {
    final int time;

    EnterTimeExpandedNetworkEdge(int time) {
        super();
        this.time = time;
    }

    double traveltime(long earliestStartTime) {
        int timeOfDay = (int) (earliestStartTime % (24*60*60));
        if (timeOfDay <= this.time) {
            return (time - timeOfDay);
        } else {
            return Double.POSITIVE_INFINITY;
        }
    }

}
