package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.FerrySpeedCalculator;
import com.graphhopper.storage.IntsRef;

import java.util.*;

import static com.graphhopper.routing.util.parsers.AbstractAccessParser.INTENDED;

public class FootPriorityParser implements TagParser {
    final Set<String> safeHighwayTags = new HashSet<>();
    final Map<String, Double> avoidHighwayTags = new HashMap<>();
    protected HashSet<String> sidewalksNoValues = new HashSet<>(5);
    protected final DecimalEncodedValue priorityWayEncoder;

    public FootPriorityParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehiclePriority.key("foot")));
    }

    protected FootPriorityParser(DecimalEncodedValue priorityEnc) {
        priorityWayEncoder = priorityEnc;

        sidewalksNoValues.add("no");
        sidewalksNoValues.add("none");
        // see #712
        sidewalksNoValues.add("separate");

        safeHighwayTags.add("footway");
        safeHighwayTags.add("path");
        safeHighwayTags.add("steps");
        safeHighwayTags.add("pedestrian");
        safeHighwayTags.add("living_street");
        safeHighwayTags.add("track");
        safeHighwayTags.add("residential");
        safeHighwayTags.add("service");
        safeHighwayTags.add("platform");

        avoidHighwayTags.put("motorway", 0.2); // could be allowed when they have sidewalks
        avoidHighwayTags.put("motorway_link", 0.2);
        avoidHighwayTags.put("trunk", 0.2);
        avoidHighwayTags.put("trunk_link", 0.2);
        avoidHighwayTags.put("primary", 0.5);
        avoidHighwayTags.put("primary_link", 0.5);
        avoidHighwayTags.put("secondary", 0.5);
        avoidHighwayTags.put("secondary_link", 0.5);
        avoidHighwayTags.put("tertiary", 0.7);
        avoidHighwayTags.put("tertiary_link", 0.7);
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null && !FerrySpeedCalculator.isFerry(way)) return;

        TreeMap<Double, Double> weightToPrioMap = new TreeMap<>();
        weightToPrioMap.put(0d, 1.0);
        collect(way, weightToPrioMap);

        // pick priority with the biggest order value
        double priority = weightToPrioMap.lastEntry().getValue();
        priorityWayEncoder.setDecimal(false, edgeId, edgeIntAccess, priority);
    }

    /**
     * @param weightToPrioMap associate a weight with every priority. This sorted map allows
     *                        subclasses to 'insert' more important priorities as well as overwrite determined priorities.
     */
    void collect(ReaderWay way, TreeMap<Double, Double> weightToPrioMap) {
        String highway = way.getTag("highway");
        if (way.hasTag("foot", "designated"))
            weightToPrioMap.put(100d, 1.2);

        if (way.hasTag("foot", "use_sidepath")) {
            weightToPrioMap.put(100d, 0.3); // see #3035
        }

        double maxSpeed = Math.max(OSMMaxSpeedParser.parseMaxSpeed(way, false), OSMMaxSpeedParser.parseMaxSpeed(way, true));
        if (safeHighwayTags.contains(highway) || maxSpeed <= 20) {
            weightToPrioMap.put(40d, 1.2);
            if (way.hasTag("tunnel", INTENDED)) {
                if (way.hasTag("sidewalk", sidewalksNoValues))
                    weightToPrioMap.put(40d, 0.8);
                else
                    weightToPrioMap.put(40d, 1.0);
            }
        } else if ((maxSpeed != MaxSpeed.MAXSPEED_MISSING && maxSpeed > 50) || avoidHighwayTags.containsKey(highway)) {
            Double priorityCode = avoidHighwayTags.get(highway);
            if (way.hasTag("sidewalk", sidewalksNoValues))
                weightToPrioMap.put(40d, priorityCode == null ? 0.5 : priorityCode);
            else // this is a bit ugly that the default values get a boost without sidewalk tag (can happen also with sidewalk==yes)
                weightToPrioMap.put(40d, priorityCode == null ? 0.5 : Math.min(1.5, priorityCode + 0.3));
        } else if (way.hasTag("sidewalk", sidewalksNoValues))
            weightToPrioMap.put(40d, 0.8);

        if (way.hasTag("bicycle", "official") || way.hasTag("bicycle", "designated"))
            weightToPrioMap.put(44d, 0.9);
    }
}
