package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.PriorityCode;

import java.util.TreeMap;

import static com.graphhopper.routing.ev.RouteNetwork.*;
import static com.graphhopper.routing.util.PriorityCode.*;

public class RacingBikePriorityParser extends BikeCommonPriorityParser {

    public RacingBikePriorityParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehiclePriority.key("racingbike")),
                lookup.getDecimalEncodedValue(VehicleSpeed.key("racingbike")),
                lookup.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class));
    }

    protected RacingBikePriorityParser(DecimalEncodedValue priorityEnc, DecimalEncodedValue speedEnc,
                                       EnumEncodedValue<RouteNetwork> bikeRouteEnc) {
        super(priorityEnc, speedEnc, bikeRouteEnc);

        addPushingSection("path");

        preferHighwayTags.add("road");
        preferHighwayTags.add("secondary");
        preferHighwayTags.add("secondary_link");
        preferHighwayTags.add("tertiary");
        preferHighwayTags.add("tertiary_link");
        preferHighwayTags.add("residential");

        avoidHighwayTags.put("motorway", BAD);
        avoidHighwayTags.put("motorway_link", BAD);
        avoidHighwayTags.put("trunk", BAD);
        avoidHighwayTags.put("trunk_link", BAD);
        avoidHighwayTags.put("primary", AVOID_MORE);
        avoidHighwayTags.put("primary_link", AVOID_MORE);

        routeMap.put(INTERNATIONAL, BEST.getValue());
        routeMap.put(NATIONAL, BEST.getValue());
        routeMap.put(REGIONAL, VERY_NICE.getValue());
        routeMap.put(LOCAL, UNCHANGED.getValue());

        setSpecificClassBicycle("roadcycling");

        avoidSpeedLimit = 81;
    }

    @Override
    void collect(ReaderWay way, double wayTypeSpeed, TreeMap<Double, PriorityCode> weightToPrioMap) {
        super.collect(way, wayTypeSpeed, weightToPrioMap);

        String highway = way.getTag("highway");
        if ("service".equals(highway) || "residential".equals(highway)) {
            weightToPrioMap.put(40d, SLIGHT_AVOID);
        } else if ("track".equals(highway)) {
            boolean isGoodSurface = goodSurface.contains(way.getTag("surface",""));
            String trackType = way.getTag("tracktype");
            if ("grade1".equals(trackType) || isGoodSurface )
                weightToPrioMap.put(110d, VERY_NICE);
            else if (trackType == null || trackType.startsWith("grade"))
                weightToPrioMap.put(110d, AVOID_MORE);
        }
    }
}
