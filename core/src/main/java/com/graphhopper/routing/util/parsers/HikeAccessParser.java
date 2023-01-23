package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.util.PMap;

import static com.graphhopper.routing.ev.RouteNetwork.*;
import static com.graphhopper.routing.util.PriorityCode.BEST;
import static com.graphhopper.routing.util.PriorityCode.VERY_NICE;

public class HikeAccessParser extends FootAccessParser {

    public HikeAccessParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getBooleanEncodedValue(properties.getString("name", VehicleAccess.key("hike"))));
        blockPrivate(properties.getBool("block_private", true));
        blockFords(properties.getBool("block_fords", false));
    }

    protected HikeAccessParser(BooleanEncodedValue accessEnc) {
        super(accessEnc);

        routeMap.put(INTERNATIONAL, BEST.getValue());
        routeMap.put(NATIONAL, BEST.getValue());
        routeMap.put(REGIONAL, VERY_NICE.getValue());
        routeMap.put(LOCAL, VERY_NICE.getValue());

        // hiking allows all sac_scale values
        allowedSacScale.add("alpine_hiking");
        allowedSacScale.add("demanding_alpine_hiking");
        allowedSacScale.add("difficult_alpine_hiking");
    }
}
