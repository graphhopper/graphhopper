package com.graphhopper.reader.gtfs;

class AccessEdge extends AbstractPtEdge {

    private final double minimumTransferTimeSeconds;

    public AccessEdge(double minimumTransferTimeSeconds) {
        this.minimumTransferTimeSeconds = minimumTransferTimeSeconds;
    }

    double getMinimumTransferTimeSeconds() {
        return minimumTransferTimeSeconds;
    }

}
