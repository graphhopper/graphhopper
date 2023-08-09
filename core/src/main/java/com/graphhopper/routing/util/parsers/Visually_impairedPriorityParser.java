package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.FerrySpeedCalculator;
import com.graphhopper.routing.util.PriorityCode;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;

import java.util.*;

import static com.graphhopper.routing.ev.RouteNetwork.*;
import static com.graphhopper.routing.util.PriorityCode.*;
import static com.graphhopper.routing.util.parsers.AbstractAccessParser.INTENDED;
import static com.graphhopper.routing.util.parsers.AbstractAverageSpeedParser.getMaxSpeed;
import static com.graphhopper.routing.util.parsers.AbstractAverageSpeedParser.isValidSpeed;

public class Visually_impairedPriorityParser implements TagParser {
    final Set<String> intendedValues = new HashSet<>(INTENDED);
    final Set<String> safeHighwayTags = new HashSet<>();
    final Set<String> avoidHighwayTags = new HashSet<>();
    protected HashSet<String> sidewalkValues = new HashSet<>(5);
    protected HashSet<String> sidewalksNoValues = new HashSet<>(5);
    protected final DecimalEncodedValue priorityWayEncoder;
    protected EnumEncodedValue<RouteNetwork> visually_impairedRouteEnc;
    protected Map<RouteNetwork, Integer> routeMap = new HashMap<>();
    private boolean traffic_signals = false;

    public Visually_impairedPriorityParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getDecimalEncodedValue(VehiclePriority.key(properties.getString("name", "visually_impaired"))),
                lookup.getEnumEncodedValue(FootNetwork.KEY, RouteNetwork.class)
        );
    }

    protected Visually_impairedPriorityParser(DecimalEncodedValue priorityEnc, EnumEncodedValue<RouteNetwork> visually_impairedRouteEnc) {
        this.visually_impairedRouteEnc = visually_impairedRouteEnc;
        priorityWayEncoder = priorityEnc;

        sidewalksNoValues.add("no");
        sidewalksNoValues.add("none");
        // see #712
        sidewalksNoValues.add("separate");

        sidewalkValues.add("yes");
        sidewalkValues.add("both");
        sidewalkValues.add("left");
        sidewalkValues.add("right");

        safeHighwayTags.add("footway");
        safeHighwayTags.add("path");
        safeHighwayTags.add("steps");
        safeHighwayTags.add("pedestrian");
        safeHighwayTags.add("living_street");
        safeHighwayTags.add("track");
        safeHighwayTags.add("residential");
        safeHighwayTags.add("service");
        safeHighwayTags.add("platform");
        safeHighwayTags.add("steps");

        avoidHighwayTags.add("trunk");
        avoidHighwayTags.add("trunk_link");
        avoidHighwayTags.add("primary");
        avoidHighwayTags.add("primary_link");
        avoidHighwayTags.add("secondary");
        avoidHighwayTags.add("secondary_link");
        avoidHighwayTags.add("tertiary");
        avoidHighwayTags.add("tertiary_link");

        routeMap.put(INTERNATIONAL, UNCHANGED.getValue());
        routeMap.put(NATIONAL, UNCHANGED.getValue());
        routeMap.put(REGIONAL, UNCHANGED.getValue());
        routeMap.put(LOCAL, UNCHANGED.getValue());
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String highwayValue = way.getTag("highway");
        Integer priorityFromRelation = routeMap.get(visually_impairedRouteEnc.getEnum(false, edgeId, edgeIntAccess));
        if (highwayValue == null) {
            if (FerrySpeedCalculator.isFerry(way))
                priorityWayEncoder.setDecimal(false, edgeId, edgeIntAccess, PriorityCode.getValue(handlePriority(way, priorityFromRelation)));
        } else {
            priorityWayEncoder.setDecimal(false, edgeId, edgeIntAccess, PriorityCode.getValue(handlePriority(way, priorityFromRelation)));
        }
    }

    public int handlePriority(ReaderWay way, Integer priorityFromRelation) {
        TreeMap<Double, Integer> weightToPrioMap = new TreeMap<>();
        if (priorityFromRelation == null)
            weightToPrioMap.put(0d, UNCHANGED.getValue());
        else
            weightToPrioMap.put(110d, priorityFromRelation);

        traffic_signals=false;
        Arrays.asList(
            "sound",
            "vibration",
            "arrow",
            "minimap",
            "floor_vibration",
            "countdown",
            "floor_light"
        ).forEach(s -> {
            if(way.hasTag("traffic_signals:"+s,"yes"))
                traffic_signals=true;
        });

        Set<String> crossing = new HashSet<>();
        crossing.add("crossing");
        String tacttile_paving = way.getTag("tactile_paving","no");
        if(!(traffic_signals || way.hasTag("footway",crossing) || way.hasTag("highway","steps") )){
            if (Arrays.asList("no","incolect").contains(tacttile_paving)){
                weightToPrioMap.put(100d, EXCLUDE.getValue());
            }
        }
        else if(traffic_signals)
            weightToPrioMap.put(100d, AVOID.getValue());
        else if (way.hasTag("footway",crossing))
            weightToPrioMap.put(100d, REACH_DESTINATION.getValue());
        else if(way.hasTag("highway","steps"))
            weightToPrioMap.put(100d, REACH_DESTINATION.getValue());
    
        collect(way, weightToPrioMap);

        // pick priority with biggest order value
        return weightToPrioMap.lastEntry().getValue();
    }

    /**
     * @param weightToPrioMap associate a weight with every priority. This sorted map allows
     *                        subclasses to 'insert' more important priorities as well as overwrite determined priorities.
     */
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
                weightToPrioMap.put(40d, VERY_BAD.getValue());
            else if (!way.hasTag("sidewalk", sidewalkValues))
                weightToPrioMap.put(40d, AVOID.getValue());
            else
                weightToPrioMap.put(40d, SLIGHT_AVOID.getValue());
        } else if (way.hasTag("sidewalk", sidewalksNoValues))
            weightToPrioMap.put(40d, AVOID.getValue());

        if (way.hasTag("bicycle", "official") || way.hasTag("bicycle", "designated"))
            weightToPrioMap.put(44d, SLIGHT_AVOID.getValue());
    }
}
