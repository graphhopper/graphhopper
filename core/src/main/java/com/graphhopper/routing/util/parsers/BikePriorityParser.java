package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.*;

public class BikePriorityParser extends BikeCommonPriorityParser {

    public BikePriorityParser(EncodedValueLookup lookup) {
        this(
                lookup.getDecimalEncodedValue(VehiclePriority.key("bike")),
                lookup.getDecimalEncodedValue(VehicleSpeed.key("bike")),
                lookup.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class)
        );
    }

    public BikePriorityParser(DecimalEncodedValue priorityEnc, DecimalEncodedValue speedEnc, EnumEncodedValue<RouteNetwork> bikeRouteEnc) {
        super(priorityEnc, speedEnc, bikeRouteEnc);

        addPushingSection("path");

        preferHighwayTags.add("service");
        preferHighwayTags.add("tertiary");
        preferHighwayTags.add("tertiary_link");
        preferHighwayTags.add("residential");
        preferHighwayTags.add("unclassified");

        setSpecificClassBicycle("touring");
    }
}
