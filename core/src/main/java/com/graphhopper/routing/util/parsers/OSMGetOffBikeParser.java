package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.storage.IntsRef;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * This parser scans different OSM tags to identify ways where a cyclist has to get off her bike. Like on footway but
 * also in reverse oneway direction.
 */
public class OSMGetOffBikeParser implements TagParser {

    private final List<String> accepted = Arrays.asList("designated", "yes", "official", "permissive");
    private final HashSet<String> pushBikeHighwayTags;
    private final BooleanEncodedValue getOffBikeEnc;
    private final BooleanEncodedValue onewayEnc;

    public OSMGetOffBikeParser(BooleanEncodedValue getOffBikeEnc, BooleanEncodedValue onewayEnc) {
        // steps -> special handling
        this(getOffBikeEnc, onewayEnc, Arrays.asList("path", "footway", "pedestrian", "platform"));
    }

    public OSMGetOffBikeParser(BooleanEncodedValue getOffBikeEnc, BooleanEncodedValue onewayEnc, List<String> pushBikeTags) {
        this.getOffBikeEnc = getOffBikeEnc;
        this.onewayEnc = onewayEnc;
        this.pushBikeHighwayTags = new HashSet<>(pushBikeTags);
    }

    @Override
    public void handleWayTags(IntsRef edgeFlags, ReaderWay way, IntsRef relationFlags) {
        String highway = way.getTag("highway");
        if (!way.hasTag("bicycle", accepted) && (pushBikeHighwayTags.contains(highway) || way.hasTag("railway", "platform"))
                || "steps".equals(highway) || way.hasTag("bicycle", "dismount")) {
            getOffBikeEnc.setBool(false, edgeFlags, true);
            getOffBikeEnc.setBool(true, edgeFlags, true);
        }
        if (!onewayEnc.getBool(false, edgeFlags)) getOffBikeEnc.setBool(false, edgeFlags, true);
        if (!onewayEnc.getBool(true, edgeFlags)) getOffBikeEnc.setBool(true, edgeFlags, true);
    }
}
