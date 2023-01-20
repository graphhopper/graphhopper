package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.util.PMap;

import java.util.TreeMap;

import static com.graphhopper.routing.ev.RouteNetwork.*;
import static com.graphhopper.routing.util.PriorityCode.*;
import static com.graphhopper.routing.util.parsers.GenericAverageSpeedParser.getMaxSpeed;
import static com.graphhopper.routing.util.parsers.GenericAverageSpeedParser.isValidSpeed;

public class HikePriorityParser extends FootPriorityParser {

    public HikePriorityParser(EncodedValueLookup lookup, PMap properties) {
        this(
                lookup.getDecimalEncodedValue(VehiclePriority.key(properties.getString("name", "hike"))),
                lookup.getEnumEncodedValue(FootNetwork.KEY, RouteNetwork.class)
        );
    }

    protected HikePriorityParser(DecimalEncodedValue priorityEnc, EnumEncodedValue<RouteNetwork> footRouteEnc) {
        super(priorityEnc, footRouteEnc);

        routeMap.put(INTERNATIONAL, BEST.getValue());
        routeMap.put(NATIONAL, BEST.getValue());
        routeMap.put(REGIONAL, VERY_NICE.getValue());
        routeMap.put(LOCAL, VERY_NICE.getValue());
    }

    @Override
    void collect(ReaderWay way, TreeMap<Double, Integer> weightToPrioMap) {
        String highway = way.getTag("highway");
        if (way.hasTag("foot", "designated"))
            weightToPrioMap.put(100d, PREFER.getValue());

        double maxSpeed = Math.max(getMaxSpeed(way, false), getMaxSpeed(way, true));
        if (safeHighwayTags.contains(highway) || (isValidSpeed(maxSpeed) && maxSpeed <= 20)) {
            weightToPrioMap.put(40d, PREFER.getValue());
            if (way.hasTag("tunnel", intendedValues)) {
                if (way.hasTag("sidewalk", sidewalksNoValues))
                    weightToPrioMap.put(40d, AVOID.getValue());
                else
                    weightToPrioMap.put(40d, UNCHANGED.getValue());
            }
        } else if ((isValidSpeed(maxSpeed) && maxSpeed > 50) || avoidHighwayTags.contains(highway)) {
            if (way.hasTag("sidewalk", sidewalksNoValues))
                weightToPrioMap.put(45d, BAD.getValue());
            else
                weightToPrioMap.put(45d, AVOID.getValue());
        }

        if (way.hasTag("bicycle", "official") || way.hasTag("bicycle", "designated"))
            weightToPrioMap.put(44d, SLIGHT_AVOID.getValue());
    }
}
