package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.PriorityCode;
import static com.graphhopper.routing.util.PriorityCode.*;
import java.util.TreeMap;

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
        preferHighwayTags.add("residential");
        preferHighwayTags.add("unclassified");

        setSpecificClassBicycle("touring");
    }

    @Override
    void collect(ReaderWay way, double wayTypeSpeed, TreeMap<Double, PriorityCode> weightToPrioMap) {
        super.collect(way, wayTypeSpeed, weightToPrioMap);
        String highway = way.getTag("highway");
        if ("track".equals(highway) && (way.getTag("surface") == null) && way.getTag("tracktype") == null)
            weightToPrioMap.put(90d, AVOID);
    }
}
