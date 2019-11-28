package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.profiles.GetOffBike;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;

import java.util.HashSet;
import java.util.List;

/**
 * This parser scans different OSM tags to identify ways where a cyclist has to get off her bike.
 */
public class OSMGetOffBikeParser implements TagParser {

    // roads where you get off your bike and push it
    private final HashSet<String> pushBikeTags = new HashSet<>();
    private final HashSet<String> accepted = new HashSet<>();
    private final BooleanEncodedValue offBikeEnc;

    public OSMGetOffBikeParser() {
        offBikeEnc = GetOffBike.create();
        pushBikeTags.add("path");
        pushBikeTags.add("footway");
        pushBikeTags.add("pedestrian");
        pushBikeTags.add("platform");

        accepted.add("designated");
        accepted.add("yes");
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        registerNewEncodedValue.add(offBikeEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, EncodingManager.Access access, IntsRef relationFlags) {
        String highway = way.getTag("highway");
        // String trackType = way.getTag("tracktype");
        if ((pushBikeTags.contains(highway) || way.hasTag("railway", "platform")) && !way.hasTag("bicycle", accepted)
                || "steps".equals(highway)
                || way.hasTag("bicycle", "dismount"))
            // || "track".equals(highway) && !"grade1".equals(trackType))
            offBikeEnc.setBool(false, edgeFlags, true);
        return edgeFlags;
    }
}
