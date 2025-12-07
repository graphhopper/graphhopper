package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.VehiclePriority;
import com.graphhopper.routing.ev.VehicleSpeed;

public class RacingBikePriorityParser extends BikeCommonPriorityParser {

    public RacingBikePriorityParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehiclePriority.key("racingbike")),
                lookup.getDecimalEncodedValue(VehicleSpeed.key("racingbike")));
    }

    protected RacingBikePriorityParser(DecimalEncodedValue priorityEnc, DecimalEncodedValue speedEnc) {
        super(priorityEnc, speedEnc);

        highways.put("motorway", 0.5);
        highways.put("motorway_link", 0.5);
        highways.put("trunk", 0.5);
        highways.put("trunk_link", 0.5);
        highways.put("primary", 0.6);
        highways.put("primary_link", 0.6);

        highways.put("secondary", 1.2);
        highways.put("secondary_link", 1.2);
        highways.put("road", 1.2);
        highways.put("tertiary", 1.2);
        highways.put("tertiary_link", 1.2);

        // TODO NOW residential was in preferHighwayTags but at the same time after processing did: weightToPrioMap.put(40d, SLIGHT_AVOID);
        highways.put("service", 0.7);
        highways.put("residential", 1.0);

        setSpecificClassBicycle("roadcycling");

        avoidSpeedLimit = 81;
    }

    @Override
    double collect(ReaderWay way, double wayTypeSpeed, boolean bikeDesignated) {
        double prio = super.collect(way, wayTypeSpeed, bikeDesignated);

        String highway = way.getTag("highway");
        if ("track".equals(highway)) {
            String trackType = way.getTag("tracktype");
            if ("grade1".equals(trackType) || goodSurface.contains(way.getTag("surface", ""))) {
                prio = Math.max(1.3, prio);
            } else if (trackType == null || trackType.startsWith("grade"))
                prio = Math.min(0.6, prio);
        }

        return prio;
    }
}
