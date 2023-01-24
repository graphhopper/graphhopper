package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.*;
import com.graphhopper.util.PMap;

public class BikePriorityParser extends BikeCommonPriorityParser {

    public BikePriorityParser(EncodedValueLookup lookup, PMap properties) {
        this(
                lookup.getDecimalEncodedValue(VehiclePriority.key(properties.getString("name", "bike"))),
                lookup.getDecimalEncodedValue(VehicleSpeed.key(properties.getString("name", "bike"))),
                lookup.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class)
        );
    }

    public BikePriorityParser(DecimalEncodedValue priorityEnc, DecimalEncodedValue speedEnc, EnumEncodedValue<RouteNetwork> bikeRouteEnc) {
        super(priorityEnc, speedEnc, bikeRouteEnc);

        addPushingSection("path");

        avoidHighwayTags.add("trunk");
        avoidHighwayTags.add("trunk_link");
        avoidHighwayTags.add("primary");
        avoidHighwayTags.add("primary_link");
        avoidHighwayTags.add("secondary");
        avoidHighwayTags.add("secondary_link");

        preferHighwayTags.add("service");
        preferHighwayTags.add("tertiary");
        preferHighwayTags.add("tertiary_link");
        preferHighwayTags.add("residential");
        preferHighwayTags.add("unclassified");

        setSpecificClassBicycle("touring");
    }
}
