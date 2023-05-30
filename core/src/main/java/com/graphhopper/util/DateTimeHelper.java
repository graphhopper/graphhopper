package com.graphhopper.util;

import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * @author Andrzej Oles
 */
public class DateTimeHelper {
    private final NodeAccess nodeAccess;

    public DateTimeHelper(GraphHopperStorage graph) {
        this.nodeAccess = graph.getNodeAccess();
    }

    public ZonedDateTime getZonedDateTime(EdgeIteratorState iter, long time) {
        int node = iter.getBaseNode();
        double lat = nodeAccess.getLat(node);
        double lon = nodeAccess.getLon(node);
        ZoneId edgeZoneId = getZoneId(lat, lon);
        Instant edgeEnterTime = Instant.ofEpochMilli(time);
        return ZonedDateTime.ofInstant(edgeEnterTime, edgeZoneId);
    }

    public ZonedDateTime getZonedDateTime(double lat, double lon, String time) {
        LocalDateTime localDateTime = LocalDateTime.parse(time);
        ZoneId zoneId = getZoneId(lat, lon);
        return localDateTime.atZone(zoneId);
    }

    public ZoneId getZoneId(double lat, double lon) {
        String zoneId = "Europe/Berlin";
        return ZoneId.of(zoneId);
    }
}
