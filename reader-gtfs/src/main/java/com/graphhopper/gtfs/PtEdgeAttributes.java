package com.graphhopper.gtfs;

public class PtEdgeAttributes {

    public GtfsStorage.EdgeType type;
    public int time;
    public int validityId;
    public int transfers;

    public PtEdgeAttributes(GtfsStorage.EdgeType type, int time, int validityId, int transfers) {
        this.type = type;
        this.time = time;
        this.validityId = validityId;
        this.transfers = transfers;
    }

}
