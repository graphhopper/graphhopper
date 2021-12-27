package com.graphhopper.gtfs;

public class PtEdgeAttributes {

    public final GtfsStorage.Validity validity;
    public GtfsStorage.EdgeType type;
    public int time;
    public int route_type;
    public GtfsStorage.FeedIdWithTimezone feedIdWithTimezone;
    public int transfers;

    @Override
    public String toString() {
        return "PtEdgeAttributes{" +
                "type=" + type +
                ", time=" + time +
                ", transfers=" + transfers +
                '}';
    }

    public PtEdgeAttributes(GtfsStorage.EdgeType type, int time, GtfsStorage.Validity validity, int route_type, GtfsStorage.FeedIdWithTimezone feedIdWithTimezone, int transfers) {
        this.type = type;
        this.time = time;
        this.validity = validity;
        this.route_type = route_type;
        this.feedIdWithTimezone = feedIdWithTimezone;
        this.transfers = transfers;
    }

}
