package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.PriorityCode;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import static com.graphhopper.routing.util.PriorityCode.AVOID;
import static com.graphhopper.routing.util.PriorityCode.VERY_NICE;

public class WheelchairPriorityParser extends FootPriorityParser {
    private final Set<String> excludeSurfaces = new HashSet<>();
    private final Set<String> excludeSmoothness = new HashSet<>();
    private final int maxInclinePercent = 6;

    public WheelchairPriorityParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getDecimalEncodedValue(VehiclePriority.key("wheelchair")),
                lookup.getEnumEncodedValue(FootNetwork.KEY, RouteNetwork.class));
    }

    protected WheelchairPriorityParser(DecimalEncodedValue priorityEnc, EnumEncodedValue<RouteNetwork> footRouteEnc) {
        super(priorityEnc, footRouteEnc);

        safeHighwayTags.add("footway");
        safeHighwayTags.add("pedestrian");
        safeHighwayTags.add("living_street");
        safeHighwayTags.add("residential");
        safeHighwayTags.add("service");
        safeHighwayTags.add("platform");

        safeHighwayTags.remove("steps");
        safeHighwayTags.remove("track");


        excludeSurfaces.add("cobblestone");
        excludeSurfaces.add("gravel");
        excludeSurfaces.add("sand");

        excludeSmoothness.add("bad");
        excludeSmoothness.add("very_bad");
        excludeSmoothness.add("horrible");
        excludeSmoothness.add("very_horrible");
        excludeSmoothness.add("impassable");
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, IntsRef relationFlags) {
        Integer priorityFromRelation = routeMap.get(footRouteEnc.getEnum(false, edgeFlags));
        priorityWayEncoder.setDecimal(false, edgeFlags, PriorityCode.getValue(handlePriority(way, priorityFromRelation)));
        return edgeFlags;
    }

    /**
     * First get priority from {@link FootPriorityParser#handlePriority(ReaderWay, Integer)} then evaluate wheelchair specific
     * tags.
     *
     * @return a priority for the given way
     */
    @Override
    protected int handlePriority(ReaderWay way, Integer priorityFromRelation) {
        TreeMap<Double, Integer> weightToPrioMap = new TreeMap<>();

        weightToPrioMap.put(100d, super.handlePriority(way, priorityFromRelation));

        if (way.hasTag("wheelchair", "designated")) {
            weightToPrioMap.put(102d, VERY_NICE.getValue());
        } else if (way.hasTag("wheelchair", "limited")) {
            weightToPrioMap.put(102d, AVOID.getValue());
        }

        return weightToPrioMap.lastEntry().getValue();
    }
}
