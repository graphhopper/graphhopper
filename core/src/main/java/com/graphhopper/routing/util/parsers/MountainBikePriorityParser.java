package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;

import java.util.TreeMap;

public class MountainBikePriorityParser extends BikeCommonPriorityParser {

    public MountainBikePriorityParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehicleSpeed.key("mtb")),
                lookup.getDecimalEncodedValue(VehiclePriority.key("mtb")));
    }

    protected MountainBikePriorityParser(DecimalEncodedValue speedEnc, DecimalEncodedValue priorityEnc) {
        super(priorityEnc, speedEnc);

        preferHighwayTags.add("road");
        preferHighwayTags.add("track");
        preferHighwayTags.add("path");
        preferHighwayTags.add("service");
        preferHighwayTags.add("tertiary");
        preferHighwayTags.add("tertiary_link");
        preferHighwayTags.add("residential");
        preferHighwayTags.add("unclassified");

        setSpecificClassBicycle("mtb");
    }

    @Override
    void collect(ReaderWay way, double wayTypeSpeed, boolean bikeDesignated, TreeMap<Double, Double> weightToPrioMap) {
        super.collect(way, wayTypeSpeed, bikeDesignated, weightToPrioMap);

        String highway = way.getTag("highway");
        if ("track".equals(highway)) {
            String trackType = way.getTag("tracktype");
            if ("grade1".equals(trackType) || goodSurface.contains(way.getTag("surface","")))
                weightToPrioMap.put(50d, 1.1);
            else if (trackType == null)
                weightToPrioMap.put(90d, 1.2);
            else if (trackType.startsWith("grade"))
                weightToPrioMap.put(100d, 1.3);
        }
    }
}
