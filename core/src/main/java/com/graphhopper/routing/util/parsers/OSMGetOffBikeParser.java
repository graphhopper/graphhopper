package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.GetOffBike;
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

    public OSMGetOffBikeParser() {
        // steps -> special handling
        this(GetOffBike.create(), Arrays.asList("path", "footway", "pedestrian", "platform"));
    }

    public OSMGetOffBikeParser(BooleanEncodedValue enc, List<String> pushBikeTags) {
        offBikeEnc = enc;
        pushBikeHighwayTags.addAll(pushBikeTags);
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        registerNewEncodedValue.add(offBikeEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, boolean ferry, IntsRef relationFlags) {
        String highway = way.getTag("highway");
        if (!way.hasTag("bicycle", accepted) && (pushBikeHighwayTags.contains(highway) || way.hasTag("railway", "platform"))
                || "steps".equals(highway) || way.hasTag("bicycle", "dismount")) {
            offBikeEnc.setBool(false, edgeFlags, true);
        }
        return edgeFlags;
    }
}
