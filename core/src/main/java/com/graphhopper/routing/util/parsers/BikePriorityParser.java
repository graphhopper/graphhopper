package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.VehiclePriority;

public class BikePriorityParser extends BikeCommonPriorityParser {

    public BikePriorityParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehiclePriority.key("bike")));
    }

    public BikePriorityParser(DecimalEncodedValue priorityEnc) {
        super(priorityEnc);

        addPushingSection("path");

        setSpecificClassBicycle("touring");
    }
}
