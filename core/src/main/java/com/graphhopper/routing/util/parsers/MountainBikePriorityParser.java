package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.VehiclePriority;
import com.graphhopper.routing.ev.VehicleSpeed;

public class MountainBikePriorityParser extends BikeCommonPriorityParser {

    public MountainBikePriorityParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehicleSpeed.key("mtb")),
                lookup.getDecimalEncodedValue(VehiclePriority.key("mtb")));
    }

    protected MountainBikePriorityParser(DecimalEncodedValue speedEnc, DecimalEncodedValue priorityEnc) {
        super(priorityEnc, speedEnc);

        highways.put("track", 1.2);
        highways.put("path", 1.2);

        // TODO NOW shouldn't this be the default?
//        highways.put("road", 1.2);
//        highways.put("tertiary", 1.2);
//        highways.put("tertiary_link", 1.2);

        setSpecificClassBicycle("mtb");
    }

    @Override
    double collect(ReaderWay way, double wayTypeSpeed, boolean bikeDesignated) {
        double prio = super.collect(way, wayTypeSpeed, bikeDesignated);

        // worse grade should not mean we prefer it because it is also less fun
        if ("track".equals(way.getTag("highway"))) prio = Math.max(1.1, prio);

        return prio;
    }
}
