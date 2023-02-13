package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.storage.IntsRef;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This parser scans different OSM tags to identify ways where a cyclist has to get off her bike. Like on footway but
 * also in reverse oneway direction.
 */
public class OSMGetOffBikeParser implements TagParser {
    private static final Set<String> OPP_LANES = new HashSet<>(Arrays.asList("opposite", "opposite_lane", "opposite_track"));
    private static final Set<String> ONEWAYS = new HashSet<>(Arrays.asList("yes", "true", "1", "-1"));
    private static final Set<String> INTENDED = new HashSet<>(Arrays.asList("yes", "designated", "official", "permissive"));
    private static final Set<String> RESTRICTED = new HashSet<>(Arrays.asList("no")); // private, delivery, etc will be handled from access

    private final HashSet<String> pushBikeHighwayTags;
    private final BooleanEncodedValue getOffBikeEnc;
    private final BooleanEncodedValue roundaboutEnc;

    public OSMGetOffBikeParser(BooleanEncodedValue getOffBikeEnc, BooleanEncodedValue roundaboutEnc) {
        // steps -> special handling
        this(getOffBikeEnc, roundaboutEnc, Arrays.asList("path", "footway", "pedestrian", "platform"));
    }

    public OSMGetOffBikeParser(BooleanEncodedValue getOffBikeEnc, BooleanEncodedValue roundaboutEnc, List<String> pushBikeTags) {
        this.getOffBikeEnc = getOffBikeEnc;
        this.roundaboutEnc = roundaboutEnc;
        this.pushBikeHighwayTags = new HashSet<>(pushBikeTags);
    }

    @Override
    public void handleWayTags(IntsRef edgeFlags, ReaderWay way, IntsRef relationFlags) {
        String highway = way.getTag("highway");
        if (!way.hasTag("bicycle", INTENDED) && (pushBikeHighwayTags.contains(highway) || way.hasTag("railway", "platform"))
                || "steps".equals(highway) || way.hasTag("bicycle", "dismount")) {
            getOffBikeEnc.setBool(false, edgeFlags, true);
            getOffBikeEnc.setBool(true, edgeFlags, true);
        }
        handleOneway(edgeFlags, way);
    }

    protected void handleOneway(IntsRef edgeFlags, ReaderWay way) {
        // handle oneways. The value -1 means it is a oneway but for reverse direction of stored geometry.
        // The tagging oneway:bicycle=no or cycleway:right:oneway=no or cycleway:left:oneway=no lifts the generic oneway restriction of the way for bike
        boolean isOneway = way.hasTag("oneway", ONEWAYS) && !way.hasTag("oneway", "-1") && !way.hasTag("bicycle:backward", INTENDED)
                || way.hasTag("oneway", "-1") && !way.hasTag("bicycle:forward", INTENDED)
                || way.hasTag("oneway:bicycle", ONEWAYS)
                || way.hasTag("cycleway:left:oneway", ONEWAYS)
                || way.hasTag("cycleway:right:oneway", ONEWAYS)
                || way.hasTag("vehicle:backward", RESTRICTED) && !way.hasTag("bicycle:forward", INTENDED)
                || way.hasTag("vehicle:forward", RESTRICTED) && !way.hasTag("bicycle:backward", INTENDED)
                || way.hasTag("bicycle:forward", RESTRICTED)
                || way.hasTag("bicycle:backward", RESTRICTED);

        if ((isOneway || roundaboutEnc.getBool(false, edgeFlags))
                && !way.hasTag("oneway:bicycle", "no")
                && !way.hasTag("cycleway", OPP_LANES)
                && !way.hasTag("cycleway:left", OPP_LANES)
                && !way.hasTag("cycleway:right", OPP_LANES)
                && !way.hasTag("cycleway:left:oneway", "no")
                && !way.hasTag("cycleway:right:oneway", "no")) {
            boolean isBackward = way.hasTag("oneway", "-1")
                    || way.hasTag("oneway:bicycle", "-1")
                    || way.hasTag("cycleway:left:oneway", "-1")
                    || way.hasTag("cycleway:right:oneway", "-1")
                    || way.hasTag("vehicle:forward", RESTRICTED)
                    || way.hasTag("bicycle:forward", RESTRICTED);
            getOffBikeEnc.setBool(!isBackward, edgeFlags, true);

        }
    }
}
