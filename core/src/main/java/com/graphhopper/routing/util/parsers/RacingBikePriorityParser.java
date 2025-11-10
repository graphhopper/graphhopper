package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.VehiclePriority;
import com.graphhopper.routing.ev.VehicleSpeed;

import java.util.TreeMap;

public class RacingBikePriorityParser extends BikeCommonPriorityParser {

    public RacingBikePriorityParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehiclePriority.key("racingbike")),
                lookup.getDecimalEncodedValue(VehicleSpeed.key("racingbike")));
    }

    protected RacingBikePriorityParser(DecimalEncodedValue priorityEnc, DecimalEncodedValue speedEnc) {
        super(priorityEnc, speedEnc);

        addPushingSection("path");

        preferHighwayTags.add("road");
        preferHighwayTags.add("secondary");
        preferHighwayTags.add("secondary_link");
        preferHighwayTags.add("tertiary");
        preferHighwayTags.add("tertiary_link");
        preferHighwayTags.add("residential");

        avoidHighwayTags.put("motorway", 0.5);
        avoidHighwayTags.put("motorway_link", 0.5);
        avoidHighwayTags.put("trunk", 0.5);
        avoidHighwayTags.put("trunk_link", 0.5);
        avoidHighwayTags.put("primary", 0.6);
        avoidHighwayTags.put("primary_link", 0.6);

        setSpecificClassBicycle("roadcycling");

        avoidSpeedLimit = 81;
    }

    @Override
    void collect(ReaderWay way, double wayTypeSpeed, boolean bikeDesignated, TreeMap<Double, Double> weightToPrioMap) {
        super.collect(way, wayTypeSpeed, bikeDesignated, weightToPrioMap);

        String highway = way.getTag("highway");
        if ("service".equals(highway) || "residential".equals(highway)) {
            weightToPrioMap.put(40d, 0.9);
        } else if ("track".equals(highway)) {
            String trackType = way.getTag("tracktype");
            if ("grade1".equals(trackType) || goodSurface.contains(way.getTag("surface", "")))
                weightToPrioMap.put(110d, 1.3);
            else if (trackType == null || trackType.startsWith("grade"))
                weightToPrioMap.put(110d, 0.6);
        }
    }
}
