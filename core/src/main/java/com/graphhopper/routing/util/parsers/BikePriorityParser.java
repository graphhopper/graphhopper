package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.VehiclePriority;
import com.graphhopper.routing.ev.VehicleSpeed;

public class BikePriorityParser extends BikeCommonPriorityParser {

    public BikePriorityParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehiclePriority.key("bike")),
                lookup.getDecimalEncodedValue(VehicleSpeed.key("bike")));
    }

    public BikePriorityParser(DecimalEncodedValue priorityEnc, DecimalEncodedValue speedEnc) {
        super(priorityEnc, speedEnc);

        addPushingSection("path");

        preferHighwayTags.add("service");
        preferHighwayTags.add("residential");
        preferHighwayTags.add("unclassified");

        setSpecificClassBicycle("touring");
    }
}
