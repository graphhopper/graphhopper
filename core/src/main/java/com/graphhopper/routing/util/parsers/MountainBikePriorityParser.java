package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.util.PMap;

import java.util.TreeMap;

import static com.graphhopper.routing.ev.RouteNetwork.*;
import static com.graphhopper.routing.util.PriorityCode.*;

public class MountainBikePriorityParser extends BikeCommonPriorityParser {

    public MountainBikePriorityParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getDecimalEncodedValue(properties.getString("average_speed", VehicleSpeed.key("mtb"))),
                lookup.getDecimalEncodedValue(properties.getString("name", VehiclePriority.key("mtb"))),
                lookup.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class));
    }

    protected MountainBikePriorityParser(DecimalEncodedValue speedEnc, DecimalEncodedValue priorityEnc,
                                         EnumEncodedValue<RouteNetwork> bikeRouteEnc) {
        super(priorityEnc, speedEnc, bikeRouteEnc);

        routeMap.put(INTERNATIONAL, PREFER.getValue());
        routeMap.put(NATIONAL, PREFER.getValue());
        routeMap.put(REGIONAL, PREFER.getValue());
        routeMap.put(LOCAL, BEST.getValue());

        avoidHighwayTags.add("primary");
        avoidHighwayTags.add("primary_link");
        avoidHighwayTags.add("secondary");
        avoidHighwayTags.add("secondary_link");

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
    void collect(ReaderWay way, double wayTypeSpeed, TreeMap<Double, Integer> weightToPrioMap) {
        super.collect(way, wayTypeSpeed, weightToPrioMap);

        String highway = way.getTag("highway");
        if ("track".equals(highway)) {
            String trackType = way.getTag("tracktype");
            if ("grade1".equals(trackType))
                weightToPrioMap.put(50d, UNCHANGED.getValue());
            else if (trackType == null)
                weightToPrioMap.put(90d, PREFER.getValue());
            else if (trackType.startsWith("grade"))
                weightToPrioMap.put(100d, VERY_NICE.getValue());
        }
    }
}
