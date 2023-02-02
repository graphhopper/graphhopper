package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.IntAccess;
import com.graphhopper.storage.IntsRef;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * This parser scans different OSM tags to identify ways where a cyclist has to get off her bike.
 */
public class OSMGetOffBikeParser implements TagParser {
    private final HashSet<String> pushBikeHighwayTags = new HashSet<>();
    private final List<String> accepted = Arrays.asList("designated", "yes", "official", "permissive");
    private final BooleanEncodedValue offBikeEnc;

    public OSMGetOffBikeParser(BooleanEncodedValue getOffBikeEnc) {
        // steps -> special handling
        this(getOffBikeEnc, Arrays.asList("path", "footway", "pedestrian", "platform"));
    }

    public OSMGetOffBikeParser(BooleanEncodedValue getOffBikeEnc, List<String> pushBikeTags) {
        offBikeEnc = getOffBikeEnc;
        pushBikeHighwayTags.addAll(pushBikeTags);
    }

    @Override
    public void handleWayTags(int edgeId, IntAccess intAccess, ReaderWay way, IntsRef relationFlags) {
        String highway = way.getTag("highway");
        if (!way.hasTag("bicycle", accepted) && (pushBikeHighwayTags.contains(highway) || way.hasTag("railway", "platform"))
                || "steps".equals(highway) || way.hasTag("bicycle", "dismount")) {
            offBikeEnc.setBool(false, edgeId, intAccess, true);
        }
    }
}
