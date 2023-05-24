package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RouteNetwork;
import com.graphhopper.routing.util.PriorityCode;
import com.graphhopper.storage.IntsRef;

import java.util.*;

import static com.graphhopper.routing.ev.RouteNetwork.*;
import static com.graphhopper.routing.util.PriorityCode.*;
import static com.graphhopper.routing.util.parsers.AbstractAccessParser.FERRIES;
import static com.graphhopper.routing.util.parsers.AbstractAccessParser.INTENDED;
import static com.graphhopper.routing.util.parsers.AbstractAverageSpeedParser.getMaxSpeed;
import static com.graphhopper.routing.util.parsers.AbstractAverageSpeedParser.isValidSpeed;

public abstract class BikeCommonPriorityParser implements TagParser {

    // Bicycle tracks subject to compulsory use in Germany and Poland (https://wiki.openstreetmap.org/wiki/DE:Key:cycleway)
    private static final List<String> CYCLEWAY_ACCESS_KEYS = Arrays.asList("cycleway:bicycle", "cycleway:both:bicycle", "cycleway:left:bicycle", "cycleway:right:bicycle");

    // Pushing section highways are parts where you need to get off your bike and push it (German: Schiebestrecke)
    protected final HashSet<String> pushingSectionsHighways = new HashSet<>();
    protected final Set<String> preferHighwayTags = new HashSet<>();
    protected final Map<String, PriorityCode> avoidHighwayTags = new HashMap<>();
    protected final Set<String> unpavedSurfaceTags = new HashSet<>();
    protected final Set<String> ferries = new HashSet<>(FERRIES);
    protected final Set<String> intendedValues = new HashSet<>(INTENDED);

    protected final DecimalEncodedValue avgSpeedEnc;
    protected final DecimalEncodedValue priorityEnc;
    // Car speed limit which switches the preference from UNCHANGED to AVOID_IF_POSSIBLE
    int avoidSpeedLimit;
    EnumEncodedValue<RouteNetwork> bikeRouteEnc;
    Map<RouteNetwork, Integer> routeMap = new HashMap<>();

    // This is the specific bicycle class
    private String classBicycleKey;

    protected BikeCommonPriorityParser(DecimalEncodedValue priorityEnc, DecimalEncodedValue avgSpeedEnc,
                                       EnumEncodedValue<RouteNetwork> bikeRouteEnc) {
        this.bikeRouteEnc = bikeRouteEnc;
        this.priorityEnc = priorityEnc;
        this.avgSpeedEnc = avgSpeedEnc;

        // duplicate code as also in BikeCommonAverageSpeedParser
        addPushingSection("footway");
        addPushingSection("pedestrian");
        addPushingSection("steps");
        addPushingSection("platform");

        unpavedSurfaceTags.add("unpaved");
        unpavedSurfaceTags.add("gravel");
        unpavedSurfaceTags.add("ground");
        unpavedSurfaceTags.add("dirt");
        unpavedSurfaceTags.add("grass");
        unpavedSurfaceTags.add("compacted");
        unpavedSurfaceTags.add("earth");
        unpavedSurfaceTags.add("fine_gravel");
        unpavedSurfaceTags.add("grass_paver");
        unpavedSurfaceTags.add("ice");
        unpavedSurfaceTags.add("mud");
        unpavedSurfaceTags.add("salt");
        unpavedSurfaceTags.add("sand");
        unpavedSurfaceTags.add("wood");

        avoidHighwayTags.put("motorway", REACH_DESTINATION);
        avoidHighwayTags.put("motorway_link", REACH_DESTINATION);
        avoidHighwayTags.put("trunk", REACH_DESTINATION);
        avoidHighwayTags.put("trunk_link", REACH_DESTINATION);
        avoidHighwayTags.put("primary", BAD);
        avoidHighwayTags.put("primary_link", BAD);
        avoidHighwayTags.put("secondary", AVOID);
        avoidHighwayTags.put("secondary_link", AVOID);
        avoidHighwayTags.put("bridleway", AVOID);

        routeMap.put(INTERNATIONAL, BEST.getValue());
        routeMap.put(NATIONAL, BEST.getValue());
        routeMap.put(REGIONAL, VERY_NICE.getValue());
        routeMap.put(LOCAL, PREFER.getValue());

        avoidSpeedLimit = 71;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String highwayValue = way.getTag("highway");
        Integer priorityFromRelation = routeMap.get(bikeRouteEnc.getEnum(false, edgeId, edgeIntAccess));
        if (highwayValue == null) {
            if (way.hasTag("route", ferries)) {
                priorityFromRelation = SLIGHT_AVOID.getValue();
            } else {
                return;
            }
        }

        double maxSpeed = Math.max(avgSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), avgSpeedEnc.getDecimal(true, edgeId, edgeIntAccess));
        priorityEnc.setDecimal(false, edgeId, edgeIntAccess, PriorityCode.getValue(handlePriority(way, maxSpeed, priorityFromRelation)));
    }

    /**
     * In this method we prefer cycleways or roads with designated bike access and avoid big roads
     * or roads with trams or pedestrian.
     *
     * @return new priority based on priorityFromRelation and on the tags in ReaderWay.
     */
    int handlePriority(ReaderWay way, double wayTypeSpeed, Integer priorityFromRelation) {
        TreeMap<Double, PriorityCode> weightToPrioMap = new TreeMap<>();
        if (priorityFromRelation == null)
            weightToPrioMap.put(0d, UNCHANGED);
        else
            weightToPrioMap.put(110d, PriorityCode.valueOf(priorityFromRelation));

        collect(way, wayTypeSpeed, weightToPrioMap);

        // pick priority with biggest order value
        return weightToPrioMap.lastEntry().getValue().getValue();
    }

    // Conversion of class value to priority. See http://wiki.openstreetmap.org/wiki/Class:bicycle
    private PriorityCode convertClassValueToPriority(String tagvalue) {
        int classvalue;
        try {
            classvalue = Integer.parseInt(tagvalue);
        } catch (NumberFormatException e) {
            return UNCHANGED;
        }

        switch (classvalue) {
            case 3:
                return BEST;
            case 2:
                return VERY_NICE;
            case 1:
                return PREFER;
            case -1:
                return SLIGHT_AVOID;
            case -2:
                return AVOID;
            case -3:
                return AVOID_MORE;
            default:
                return UNCHANGED;
        }
    }

    /**
     * @param weightToPrioMap associate a weight with every priority. This sorted map allows
     *                        subclasses to 'insert' more important priorities as well as overwrite determined priorities.
     */
    void collect(ReaderWay way, double wayTypeSpeed, TreeMap<Double, PriorityCode> weightToPrioMap) {
        String highway = way.getTag("highway");
        if (isDesignated(way)) {
            if ("path".equals(highway))
                weightToPrioMap.put(100d, VERY_NICE);
            else
                weightToPrioMap.put(100d, PREFER);
        }

        if ("cycleway".equals(highway)) {
            if (way.hasTag("foot", intendedValues) && !way.hasTag("segregated", "yes"))
                weightToPrioMap.put(100d, PREFER);
            else
                weightToPrioMap.put(100d, VERY_NICE);
        }

        double maxSpeed = Math.max(getMaxSpeed(way, false), getMaxSpeed(way, true));
        if (preferHighwayTags.contains(highway) || (isValidSpeed(maxSpeed) && maxSpeed <= 30)) {
            if (!isValidSpeed(maxSpeed) || maxSpeed < avoidSpeedLimit) {
                weightToPrioMap.put(40d, PREFER);
                if (way.hasTag("tunnel", intendedValues))
                    weightToPrioMap.put(40d, UNCHANGED);
            }
        } else if (avoidHighwayTags.containsKey(highway)
                || isValidSpeed(maxSpeed) && maxSpeed >= avoidSpeedLimit && !"track".equals(highway)) {
            PriorityCode priorityCode = avoidHighwayTags.get(highway);
            weightToPrioMap.put(50d, priorityCode == null ? AVOID : priorityCode);
            if (way.hasTag("tunnel", intendedValues) || way.hasTag("hazmat", intendedValues)) {
                PriorityCode worse = priorityCode == null ? BAD : priorityCode.worse().worse();
                weightToPrioMap.put(50d,  worse == EXCLUDE ? REACH_DESTINATION : worse);
            }
        }

        String cycleway = way.getFirstPriorityTag(Arrays.asList("cycleway", "cycleway:left", "cycleway:right", "cycleway:both"));
        if (Arrays.asList("lane", "opposite_track", "shared_lane", "share_busway", "shoulder").contains(cycleway)) {
            weightToPrioMap.put(100d, SLIGHT_PREFER);
        } else if ("track".equals(cycleway)) {
            weightToPrioMap.put(100d, PREFER);
        }

        if (way.hasTag("bicycle", "use_sidepath")) {
            weightToPrioMap.put(100d, REACH_DESTINATION);
        }

        if (pushingSectionsHighways.contains(highway) || "parking_aisle".equals(way.getTag("service"))) {
            PriorityCode pushingSectionPrio = SLIGHT_AVOID;
            if (way.hasTag("bicycle", "yes") || way.hasTag("bicycle", "permissive"))
                pushingSectionPrio = PREFER;
            if (isDesignated(way) && (!way.hasTag("highway","steps")))
                pushingSectionPrio = VERY_NICE;
            if (way.hasTag("foot", "yes")) {
                pushingSectionPrio = pushingSectionPrio.worse();
                if (way.hasTag("segregated", "yes"))
                    pushingSectionPrio = pushingSectionPrio.better();
            }
            if (way.hasTag("highway","steps")) {
                pushingSectionPrio = BAD;
            }
            weightToPrioMap.put(100d, pushingSectionPrio);
        }

        if (way.hasTag("railway", "tram"))
            weightToPrioMap.put(50d, AVOID_MORE);

        if (way.hasTag("lcn", "yes"))
            weightToPrioMap.put(100d, PREFER);

        String classBicycleValue = way.getTag(classBicycleKey);
        if (classBicycleValue != null) {
            // We assume that humans are better in classifying preferences compared to our algorithm above -> weight = 100
            weightToPrioMap.put(100d, convertClassValueToPriority(classBicycleValue));
        } else {
            String classBicycle = way.getTag("class:bicycle");
            if (classBicycle != null)
                weightToPrioMap.put(100d, convertClassValueToPriority(classBicycle));
        }

        // Increase the priority for scenic routes or in case that maxspeed limits our average speed as compensation. See #630
        if (way.hasTag("scenic", "yes") || maxSpeed > 0 && maxSpeed <= wayTypeSpeed) {
            PriorityCode lastEntryValue = weightToPrioMap.lastEntry().getValue();
            if (lastEntryValue.getValue() < BEST.getValue()) weightToPrioMap.put(110d, lastEntryValue.better());
        }
    }

    boolean isDesignated(ReaderWay way) {
        return way.hasTag("bicycle", "designated") || way.hasTag(CYCLEWAY_ACCESS_KEYS, "designated")
                || way.hasTag("bicycle_road", "yes") || way.hasTag("cyclestreet", "yes") || way.hasTag("bicycle", "official");
    }

    // TODO duplicated in average speed
    void addPushingSection(String highway) {
        pushingSectionsHighways.add(highway);
    }

    void setSpecificClassBicycle(String subkey) {
        classBicycleKey = "class:bicycle:" + subkey;
    }

    public final DecimalEncodedValue getPriorityEnc() {
        return priorityEnc;
    }
}
