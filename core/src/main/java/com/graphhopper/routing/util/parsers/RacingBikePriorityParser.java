package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.PriorityCode;

import java.util.TreeMap;

import static com.graphhopper.routing.util.PriorityCode.*;

public class RacingBikePriorityParser extends BikeCommonPriorityParser {

    public RacingBikePriorityParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehiclePriority.key("racingbike")),
                lookup.getDecimalEncodedValue(VehicleSpeed.key("racingbike")));
    }

    protected RacingBikePriorityParser(DecimalEncodedValue priorityEnc, DecimalEncodedValue speedEnc) {
        super(priorityEnc, speedEnc);

        addPushingSection("path");

        preferHighwayTags.add("secondary");
        preferHighwayTags.add("secondary_link");

//        preferHighwayTags.add("road");
//        preferHighwayTags.add("unclassified");
//        preferHighwayTags.add("tertiary");
//        preferHighwayTags.add("tertiary_link");

        avoidHighwayTags.put("service", SLIGHT_AVOID);
        avoidHighwayTags.put("residential", SLIGHT_AVOID);

        avoidHighwayTags.put("motorway", BAD);
        avoidHighwayTags.put("motorway_link", BAD);
        avoidHighwayTags.put("trunk", BAD);
        avoidHighwayTags.put("trunk_link", BAD);
        avoidHighwayTags.put("primary", AVOID_MORE);
        avoidHighwayTags.put("primary_link", AVOID_MORE);

        setSpecificClassBicycle("roadcycling");

        avoidSpeedLimit = 81;
    }

    @Override
    void collect(ReaderWay way, double wayTypeSpeed, boolean bikeDesignated, TreeMap<Double, PriorityCode> weightToPrioMap) {
        super.collect(way, wayTypeSpeed, bikeDesignated, weightToPrioMap);

        String highway = way.getTag("highway");
        if ("track".equals(highway)) {
            String trackType = way.getTag("tracktype");
            if ("grade1".equals(trackType) || goodSurface.contains(way.getTag("surface", "")))
                weightToPrioMap.put(110d, VERY_NICE);
            else if (trackType == null || trackType.startsWith("grade"))
                weightToPrioMap.put(110d, AVOID_MORE);
        }
    }
}
