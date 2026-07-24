package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.PriorityCode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.graphhopper.routing.util.PriorityCode.*;
import static com.graphhopper.routing.util.parsers.AbstractAccessParser.INTENDED;

public class RacingBikePriorityParser extends BikeCommonPriorityParser {

    // usually too narrow for racing bikes, so e.g. bicycle=designated must not boost them
    private static final Set<String> NARROW_WAYS = new HashSet<>(List.of("cycleway", "path", "footway", "pedestrian", "platform"));

    private final Map<String, PriorityCode> highwayToPrio = new HashMap<>();

    public RacingBikePriorityParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehiclePriority.key("racingbike")));
    }

    protected RacingBikePriorityParser(DecimalEncodedValue priorityEnc) {
        super(priorityEnc);

        highwayToPrio.put("road", SLIGHT_PREFER);
        highwayToPrio.put("secondary", SLIGHT_PREFER);
        highwayToPrio.put("secondary_link", SLIGHT_PREFER);
        highwayToPrio.put("tertiary", SLIGHT_PREFER);
        highwayToPrio.put("tertiary_link", SLIGHT_PREFER);
        highwayToPrio.put("service", SLIGHT_AVOID);
        highwayToPrio.put("residential", SLIGHT_AVOID);
        highwayToPrio.put("unclassified", SLIGHT_AVOID);
        highwayToPrio.put("path", SLIGHT_AVOID);
        highwayToPrio.put("footway", SLIGHT_AVOID);
        highwayToPrio.put("pedestrian", SLIGHT_AVOID);
        highwayToPrio.put("platform", SLIGHT_AVOID);
        highwayToPrio.put("track", AVOID_MORE);
        highwayToPrio.put("bridleway", AVOID);
        highwayToPrio.put("motorway", BAD);
        highwayToPrio.put("motorway_link", BAD);
        highwayToPrio.put("trunk", BAD);
        highwayToPrio.put("trunk_link", BAD);
    }

    @Override
    void collect(ReaderWay way, boolean bikeDesignated, TreeMap<Double, PriorityCode> weightToPrioMap) {
        String highway = way.getTag("highway");
        double maxSpeed = Math.max(OSMMaxSpeedParser.parseMaxSpeed(way, false), OSMMaxSpeedParser.parseMaxSpeed(way, true));
        PriorityCode prio = highwayToPrio.getOrDefault(highway, UNCHANGED);

        if ("steps".equals(highway)) {
            prio = BAD;
        } else if ("track".equals(highway)) {
            if ("grade1".equals(way.getTag("tracktype")) || goodSurface.contains(way.getTag("surface", "")))
                prio = UNCHANGED;
        } else if (way.hasTag("bicycle", "use_sidepath")) {
            prio = REACH_DESTINATION;
        } else if (bikeDesignated && !NARROW_WAYS.contains(highway)) {
            prio = PREFER;
        } else if ("cycleway".equals(highway) && way.hasTag("foot", INTENDED)) {
            // too narrow when shared with pedestrians; wide roads keep their priority
            prio = SLIGHT_AVOID;
        } else if (way.hasTag("tunnel", INTENDED)) {
            // tunnels are only dangerous on the high-speed roads that we strongly avoid anyway
            if (prio == BAD) prio = REACH_DESTINATION;
            else if (prio == SLIGHT_PREFER) prio = UNCHANGED;
        } else if (maxSpeed <= 30 && prio == UNCHANGED && !"cycleway".equals(highway)) {
            // a slow but otherwise neutral road is pleasant, but this must not lift
            // e.g. residential in a 30 zone above the parallel main road
            prio = SLIGHT_PREFER;
        }

        if (way.hasTag("railway", "tram") && !bikeDesignated)
            prio = AVOID_MORE;

        String classBicycleValue = way.getTag("class:bicycle:roadcycling");
        if (classBicycleValue != null) {
            // We assume that humans are better in classifying preferences compared to our algorithm above,
            // but do not degrade e.g. designated
            PriorityCode classPrio = convertClassValueToPriority(classBicycleValue);
            if (classPrio.getValue() > prio.getValue())
                prio = classPrio;
        }

        weightToPrioMap.put(100d, prio);
    }
}
