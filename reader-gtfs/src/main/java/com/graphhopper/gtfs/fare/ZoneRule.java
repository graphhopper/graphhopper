package com.graphhopper.gtfs.fare;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

final class ZoneRule extends SanitizedFareRule {
    private final Set<String> zones;

    ZoneRule(Collection<String> zones) {
        this.zones = new HashSet<>(zones);
    }

    @Override
    boolean appliesTo(Trip.Segment segment) {
        if (zones.isEmpty()) {
            return false;
        } else {
            return zones.equals(segment.getZones());
        }
    }
}
