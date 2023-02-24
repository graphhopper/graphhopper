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

    private final List<String> INTENDED = Arrays.asList("designated", "yes", "official", "permissive");
    // steps -> special handling
    private final HashSet<String> GET_OFF_BIKE = new HashSet<>(Arrays.asList("path", "footway", "pedestrian", "platform"));
    private final BooleanEncodedValue getOffBikeEnc;
    private final BooleanEncodedValue bikeAccessEnc;

    /**
     * @param bikeAccessEnc used to find out if way is oneway and so it does not matter which bike type is used.
     */
    public OSMGetOffBikeParser(BooleanEncodedValue getOffBikeEnc, BooleanEncodedValue bikeAccessEnc) {
        this.getOffBikeEnc = getOffBikeEnc;
        this.bikeAccessEnc = bikeAccessEnc;
    }

    @Override
    public void handleWayTags(IntsRef edgeFlags, ReaderWay way, IntsRef relationFlags) {
        String highway = way.getTag("highway");
        if (!way.hasTag("bicycle", INTENDED) && (GET_OFF_BIKE.contains(highway) || way.hasTag("railway", "platform"))
                || "steps".equals(highway) || way.hasTag("bicycle", "dismount")) {
            getOffBikeEnc.setBool(false, edgeFlags, true);
            getOffBikeEnc.setBool(true, edgeFlags, true);
        }
        boolean fwd = bikeAccessEnc.getBool(false, edgeFlags);
        boolean bwd = bikeAccessEnc.getBool(true, edgeFlags);
        // get off bike for reverse oneways
        if (fwd != bwd) {
            if (!fwd) getOffBikeEnc.setBool(false, edgeFlags, true);
            if (!bwd) getOffBikeEnc.setBool(true, edgeFlags, true);
        }
    }
}
