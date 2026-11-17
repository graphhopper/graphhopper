package com.graphhopper.resources;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.GHPoint;

/**
 * What a shortest path tree needs before it can start: the weighting to search with and the snapped start point.
 */
public record SearchStart(Weighting weighting, Snap snap) {

    public static SearchStart create(GraphHopper graphHopper, String profileName, PMap hints, GHPoint point) {
        Profile profile = graphHopper.getProfile(profileName);
        if (profile == null)
            throw new IllegalArgumentException("The requested profile '" + profileName + "' does not exist");
        Weighting weighting = graphHopper.createWeighting(profile, hints);
        BooleanEncodedValue inSubnetworkEnc = graphHopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(profileName));
        Snap snap = graphHopper.getLocationIndex().findClosest(point.lat, point.lon, new DefaultSnapFilter(weighting, inSubnetworkEnc));
        if (!snap.isValid())
            throw new IllegalArgumentException("Point not found:" + point);
        return new SearchStart(weighting, snap);
    }
}
