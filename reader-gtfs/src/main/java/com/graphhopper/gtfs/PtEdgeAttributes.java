package com.graphhopper.gtfs;

import com.google.transit.realtime.GtfsRealtime;

public class PtEdgeAttributes {

    public final GtfsStorage.Validity validity;
    public GtfsStorage.EdgeType type;
    public int time;
    public int route_type;
    public GtfsStorage.FeedIdWithTimezone feedIdWithTimezone;
    public int transfers;
    public int stop_sequence;
    public GtfsRealtime.TripDescriptor tripDescriptor;
    public GtfsStorage.PlatformDescriptor platformDescriptor;

    @Override
    public String toString() {
        return "PtEdgeAttributes{" +
                "type=" + type +
                ", time=" + time +
                ", transfers=" + transfers +
                '}';
    }

    public PtEdgeAttributes(GtfsStorage.EdgeType type, int time, GtfsStorage.Validity validity, int route_type, GtfsStorage.FeedIdWithTimezone feedIdWithTimezone, int transfers, int stop_sequence, GtfsRealtime.TripDescriptor tripDescriptor, GtfsStorage.PlatformDescriptor platformDescriptor) {
        this.type = type;
        this.time = time;
        this.validity = validity;
        this.route_type = route_type;
        this.feedIdWithTimezone = feedIdWithTimezone;
        this.transfers = transfers;
        this.stop_sequence = stop_sequence;
        this.tripDescriptor = tripDescriptor;
        this.platformDescriptor = platformDescriptor;
    }

}
