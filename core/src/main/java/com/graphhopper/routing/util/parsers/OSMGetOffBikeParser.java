package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EdgeBytesAccess;
import com.graphhopper.storage.BytesRef;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * This parser scans different OSM tags to identify ways where a cyclist has to get off her bike. Like on footway but
 * also in reverse oneway direction.
 */
public class OSMGetOffBikeParser implements TagParser {

    private final List<String> INTENDED = Arrays.asList("designated", "yes", "official", "permissive");
    // steps -> special handling, path -> see #2777
    private final HashSet<String> GET_OFF_BIKE = new HashSet<>(Arrays.asList("footway", "pedestrian", "platform"));
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
    public void handleWayTags(int edgeId, EdgeBytesAccess edgeAccess, ReaderWay way, BytesRef relationFlags) {
        String highway = way.getTag("highway");
        boolean notIntended = !way.hasTag("bicycle", INTENDED) &&
                (GET_OFF_BIKE.contains(highway)
                        || way.hasTag("railway", "platform")
                        || "path".equals(highway) && way.hasTag("foot", "designated") && !way.hasTag("segregated", "yes"));
        if ("steps".equals(highway) || way.hasTag("bicycle", "dismount") || notIntended) {
            getOffBikeEnc.setBool(false, edgeId, edgeAccess, true);
            getOffBikeEnc.setBool(true, edgeId, edgeAccess, true);
        }
        boolean fwd = bikeAccessEnc.getBool(false, edgeId, edgeAccess);
        boolean bwd = bikeAccessEnc.getBool(true, edgeId, edgeAccess);
        // get off bike for reverse oneways
        if (fwd != bwd) {
            if (!fwd) getOffBikeEnc.setBool(false, edgeId, edgeAccess, true);
            if (!bwd) getOffBikeEnc.setBool(true, edgeId, edgeAccess, true);
        }
    }
}
