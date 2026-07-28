package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.PriorityCode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.graphhopper.routing.util.PriorityCode.*;
import static com.graphhopper.routing.util.parsers.AbstractAccessParser.INTENDED;

public class RacingBikePriorityParser extends BikeCommonPriorityParser {

    // usually too narrow for racing bikes, so e.g. bicycle=designated must not boost them
    private static final Set<String> NARROW_WAYS = Set.of("cycleway", "path", "footway", "pedestrian", "platform");

    private static final List<String> CYCLEWAY_KEYS = List.of("cycleway", "cycleway:left", "cycleway:both", "cycleway:right");
    private static final Set<String> CYCLEWAY_LANES = Set.of("lane", "shoulder");

    private final Map<String, PriorityCode> highwayToPrio = new HashMap<>();

    public RacingBikePriorityParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehiclePriority.key("racingbike")));
    }

    protected RacingBikePriorityParser(DecimalEncodedValue priorityEnc) {
        super(priorityEnc);

        highwayToPrio.put("secondary", SLIGHT_PREFER);
        highwayToPrio.put("secondary_link", SLIGHT_PREFER);
        highwayToPrio.put("tertiary", SLIGHT_PREFER);
        highwayToPrio.put("tertiary_link", SLIGHT_PREFER);

        highwayToPrio.put("unclassified", UNCHANGED);
        highwayToPrio.put("primary", UNCHANGED);
        highwayToPrio.put("primary_link", UNCHANGED);
        highwayToPrio.put("cycleway", AVOID);

        highwayToPrio.put("road", AVOID);
        highwayToPrio.put("service", AVOID);
        highwayToPrio.put("residential", SLIGHT_AVOID);
        highwayToPrio.put("path", AVOID);
        highwayToPrio.put("footway", AVOID);
        highwayToPrio.put("pedestrian", AVOID);
        highwayToPrio.put("platform", AVOID);
        highwayToPrio.put("living_street", AVOID);
        highwayToPrio.put("bridleway", AVOID);
        highwayToPrio.put("track", AVOID_MORE);
        highwayToPrio.put("motorway", BAD);
        highwayToPrio.put("motorway_link", BAD);
        highwayToPrio.put("trunk", BAD);
        highwayToPrio.put("trunk_link", BAD);
    }

    @Override
    void collect(ReaderWay way, boolean bikeDesignated, TreeMap<Double, PriorityCode> weightToPrioMap) {
        String highway = way.getTag("highway", "");
        double maxSpeed = Math.max(OSMMaxSpeedParser.parseMaxSpeed(way, false), OSMMaxSpeedParser.parseMaxSpeed(way, true));
        PriorityCode prio = highwayToPrio.getOrDefault(highway, UNCHANGED);

        if ("steps".equals(highway)) {
            prio = BAD;
        } else if ("track".equals(highway)) {
            if ("grade1".equals(way.getTag("tracktype")) || goodSurface.contains(way.getTag("surface", "")))
                prio = UNCHANGED;
        } else if (way.hasTag("bicycle", "use_sidepath")) {
            prio = REACH_DESTINATION;
        } else if (bikeDesignated && !NARROW_WAYS.contains(highway) && !"parking_aisle".equals(way.getTag("service"))) {
            prio = PREFER;
        } else if (prio.getValue() < SLIGHT_PREFER.getValue() && way.hasTag(CYCLEWAY_KEYS, CYCLEWAY_LANES)) {
            // a painted lane boosts one step, but always stays below cycleway=track (PREFER via designated)
            prio = prio.better();
        } else if ("cycleway".equals(highway) && way.hasTag("foot", INTENDED)) {
            // too narrow when shared with pedestrians; wide roads keep their priority
            prio = AVOID;
        } else if (way.hasTag("tunnel", INTENDED)) {
            // tunnels are only dangerous on the high-speed roads that we strongly avoid anyway
            if (prio == BAD) prio = REACH_DESTINATION;
            else if (prio == SLIGHT_PREFER) prio = UNCHANGED;
        } else if (maxSpeed <= 30 && highway.startsWith("primary")) {
            // a slow primary is as pleasant as a secondary
            prio = SLIGHT_PREFER;
        }

        if (way.hasTag("railway", "tram") && !bikeDesignated)
            prio = AVOID_MORE;

        String classBicycleValue = way.getTag("class:bicycle:roadcycling");
        if (classBicycleValue != null) {
            // We assume that humans are better in classifying preferences compared to our algorithm above
            prio = convertClassValueToPriority(classBicycleValue);
        }

        weightToPrioMap.put(100d, prio);
    }
}
