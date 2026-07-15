package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.PriorityCode;

import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.graphhopper.routing.util.PriorityCode.*;
import static com.graphhopper.routing.util.parsers.AbstractAccessParser.INTENDED;

public class RacingBikePriorityParser extends BikeCommonPriorityParser {

    public RacingBikePriorityParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehiclePriority.key("racingbike")));
    }

    protected RacingBikePriorityParser(DecimalEncodedValue priorityEnc) {
        super(priorityEnc);

        addPushingSection("path");

        preferHighwayTags.add("road");
        preferHighwayTags.add("secondary");
        preferHighwayTags.add("secondary_link");
        preferHighwayTags.add("tertiary");
        preferHighwayTags.add("tertiary_link");

        avoidHighwayTags.put("motorway", BAD);
        avoidHighwayTags.put("motorway_link", BAD);
        avoidHighwayTags.put("trunk", BAD);
        avoidHighwayTags.put("trunk_link", BAD);
        avoidHighwayTags.put("service", SLIGHT_AVOID);
        avoidHighwayTags.put("residential", SLIGHT_AVOID);
        avoidHighwayTags.put("unclassified", SLIGHT_AVOID);
    }

    @Override
    void collect(ReaderWay way, boolean bikeDesignated, TreeMap<Double, PriorityCode> weightToPrioMap) {
        String highway = way.getTag("highway");
        double maxSpeed = Math.max(OSMMaxSpeedParser.parseMaxSpeed(way, false), OSMMaxSpeedParser.parseMaxSpeed(way, true));

        if (bikeDesignated)
            weightToPrioMap.put(100d, PREFER);

        if ("track".equals(highway)) {
            String trackType = way.getTag("tracktype");
            if ("grade1".equals(trackType) || goodSurface.contains(way.getTag("surface", "")))
                weightToPrioMap.put(110d, UNCHANGED);
            else if (trackType == null || trackType.startsWith("grade"))
                weightToPrioMap.put(110d, AVOID_MORE);
        } else if (preferHighwayTags.contains(highway) || maxSpeed <= 30) {
            weightToPrioMap.put(40d, SLIGHT_PREFER);
            if (way.hasTag("tunnel", INTENDED))
                weightToPrioMap.put(40d, UNCHANGED);
        } else if (avoidHighwayTags.containsKey(highway) || way.hasTag("foot", INTENDED)) {
            PriorityCode priorityCode = avoidHighwayTags.getOrDefault(highway, SLIGHT_AVOID);
            weightToPrioMap.put(50d, priorityCode);
            // tunnels are only dangerous on the high-speed roads we strongly avoid
            if (way.hasTag("tunnel", INTENDED) && priorityCode == BAD) {
                PriorityCode worse = priorityCode.worse().worse();
                weightToPrioMap.put(50d, worse == EXCLUDE ? REACH_DESTINATION : worse);
            }
        }

        if (way.hasTag("bicycle", "use_sidepath")) {
            weightToPrioMap.put(100d, REACH_DESTINATION);
        }

        Set<String> cyclewayValues = Stream.of("cycleway", "cycleway:left", "cycleway:both", "cycleway:right").map(key -> way.getTag(key, "")).collect(Collectors.toSet());
        if (cyclewayValues.contains("track")) {
            weightToPrioMap.put(100d, VERY_NICE);
        } else if (Stream.of("lane", "opposite_track", "shared_lane", "share_busway", "shoulder").anyMatch(cyclewayValues::contains)) {
            PriorityCode current = weightToPrioMap.lastEntry().getValue();
            if (current.getValue() < PREFER.getValue())
                weightToPrioMap.put(100d, current.better());
        } else if (pushingSectionsHighways.contains(highway) || "parking_aisle".equals(way.getTag("service"))) {
            PriorityCode pushingSectionPrio = SLIGHT_AVOID;
            if (way.hasTag("highway", "steps"))
                pushingSectionPrio = BAD;
            else if (way.hasTag("foot", "yes"))
                pushingSectionPrio = pushingSectionPrio.worse();

            weightToPrioMap.put(100d, pushingSectionPrio);
        }

        if (way.hasTag("railway", "tram") && !bikeDesignated)
            weightToPrioMap.put(100d, AVOID_MORE);

        String classBicycleValue = way.getTag("class:bicycle:roadcycling");

        // We assume that humans are better in classifying preferences compared to our algorithm above
        if (classBicycleValue != null) {
            PriorityCode prio = convertClassValueToPriority(classBicycleValue);
            // do not overwrite if e.g. designated
            weightToPrioMap.compute(100d, (key, existing) ->
                    existing == null || existing.getValue() < prio.getValue() ? prio : existing
            );
        }
    }
}
