package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.VehiclePriority;

import static com.graphhopper.routing.util.PriorityCode.BAD;

public class BikePriorityParser extends BikeCommonPriorityParser {

    public BikePriorityParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehiclePriority.key("bike")));
    }

    public BikePriorityParser(DecimalEncodedValue priorityEnc) {
        super(priorityEnc);

        addPushingSection("path");

        avoidHighwayTags.put("primary", BAD);
        avoidHighwayTags.put("primary_link", BAD);

        preferHighwayTags.add("service");
        preferHighwayTags.add("residential");
        preferHighwayTags.add("unclassified");
        preferHighwayTags.add("cycleway");

        setSpecificClassBicycle("touring");
    }
}
