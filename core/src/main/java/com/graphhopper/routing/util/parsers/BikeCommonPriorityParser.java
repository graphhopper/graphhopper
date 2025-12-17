package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.MaxSpeed;
import com.graphhopper.routing.util.FerrySpeedCalculator;
import com.graphhopper.routing.util.PriorityCode;
import com.graphhopper.storage.IntsRef;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.graphhopper.routing.util.PriorityCode.*;
import static com.graphhopper.routing.util.parsers.AbstractAccessParser.INTENDED;

public abstract class BikeCommonPriorityParser implements TagParser {
    private static final Set<String> CYCLEWAY_KEYS = Set.of("cycleway", "cycleway:left", "cycleway:both", "cycleway:right");

    // rare use case when a bicycle lane has access tag
    private static final List<String> CYCLEWAY_BICYCLE_KEYS = List.of("cycleway:bicycle", "cycleway:both:bicycle", "cycleway:left:bicycle", "cycleway:right:bicycle");

    // pushing section highways are parts where you need to get off your bike and push it
    protected final HashSet<String> pushingSectionsHighways = new HashSet<>();
    protected final Set<String> preferHighwayTags = new HashSet<>();
    protected final Map<String, PriorityCode> avoidHighwayTags = new HashMap<>();
    protected final DecimalEncodedValue avgSpeedEnc;
    protected final DecimalEncodedValue priorityEnc;
    // Car speed limit which switches the preference from UNCHANGED to AVOID_IF_POSSIBLE
    int avoidSpeedLimit;
    protected final Set<String> goodSurface = Set.of("paved", "asphalt", "concrete");

    // This is the specific bicycle class
    private String classBicycleKey;

    protected BikeCommonPriorityParser(DecimalEncodedValue priorityEnc, DecimalEncodedValue avgSpeedEnc) {
        this.priorityEnc = priorityEnc;
        this.avgSpeedEnc = avgSpeedEnc;

        addPushingSection("footway");
        addPushingSection("pedestrian");
        addPushingSection("steps");
        addPushingSection("platform");

        avoidHighwayTags.put("motorway", REACH_DESTINATION);
        avoidHighwayTags.put("motorway_link", REACH_DESTINATION);
        avoidHighwayTags.put("trunk", REACH_DESTINATION);
        avoidHighwayTags.put("trunk_link", REACH_DESTINATION);
        avoidHighwayTags.put("primary", BAD);
        avoidHighwayTags.put("primary_link", BAD);
        avoidHighwayTags.put("secondary", AVOID);
        avoidHighwayTags.put("secondary_link", AVOID);
        avoidHighwayTags.put("bridleway", AVOID);

        avoidSpeedLimit = 71;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null && !FerrySpeedCalculator.isFerry(way)) return;

        TreeMap<Double, PriorityCode> weightToPrioMap = new TreeMap<>();
        weightToPrioMap.put(0d, UNCHANGED);
        double maxSpeed = Math.max(avgSpeedEnc.getDecimal(false, edgeId, edgeIntAccess),
                avgSpeedEnc.getDecimal(true, edgeId, edgeIntAccess));
        collect(way, maxSpeed, isBikeDesignated(way), weightToPrioMap);

        // pick priority with the biggest order value
        double prio = PriorityCode.getValue(weightToPrioMap.lastEntry().getValue().getValue());
        priorityEnc.setDecimal(false, edgeId, edgeIntAccess, prio);
    }

    // Conversion of class value to priority. See http://wiki.openstreetmap.org/wiki/Class:bicycle
    private PriorityCode convertClassValueToPriority(String tagvalue) {
        try {
            return switch (Integer.parseInt(tagvalue)) {
                case 3 -> BEST;
                case 2 -> VERY_NICE;
                case 1 -> PREFER;
                case -1 -> AVOID;
                case -2 -> BAD;
                case -3 -> REACH_DESTINATION;
                default -> UNCHANGED;
            };
        } catch (NumberFormatException e) {
            return UNCHANGED;
        }
    }

    /**
     * @param weightToPrioMap associate a weight with every priority. This sorted map allows
     *                        subclasses to 'insert' more important priorities as well as overwrite determined priorities.
     */
    void collect(ReaderWay way, double wayTypeSpeed, boolean bikeDesignated, TreeMap<Double, PriorityCode> weightToPrioMap) {
        String highway = way.getTag("highway");
        if (bikeDesignated) {
            boolean isGoodSurface = way.getTag("tracktype", "").equals("grade1") || goodSurface.contains(way.getTag("surface", ""));
            if ("path".equals(highway) || "track".equals(highway) && isGoodSurface)
                weightToPrioMap.put(100d, VERY_NICE);
            else
                weightToPrioMap.put(100d, PREFER);
        }

        if ("cycleway".equals(highway)) {
            if (way.hasTag("foot", INTENDED) && !way.hasTag("segregated", "yes"))
                weightToPrioMap.put(100d, PREFER);
            else
                weightToPrioMap.put(100d, VERY_NICE);
        }

        double maxSpeed = Math.max(OSMMaxSpeedParser.parseMaxSpeed(way, false), OSMMaxSpeedParser.parseMaxSpeed(way, true));
        if (preferHighwayTags.contains(highway) || maxSpeed <= 30) {
            if (maxSpeed == MaxSpeed.MAXSPEED_MISSING || maxSpeed < avoidSpeedLimit) {
                weightToPrioMap.put(40d, PREFER);
                if (way.hasTag("tunnel", INTENDED))
                    weightToPrioMap.put(40d, UNCHANGED);
            }
        } else if (avoidHighwayTags.containsKey(highway)
                || (maxSpeed != MaxSpeed.MAXSPEED_MISSING && maxSpeed >= avoidSpeedLimit && !"track".equals(highway))) {
            PriorityCode priorityCode = avoidHighwayTags.get(highway);
            weightToPrioMap.put(50d, priorityCode == null ? AVOID : priorityCode);
            if (way.hasTag("tunnel", INTENDED)) {
                PriorityCode worse = priorityCode == null ? BAD : priorityCode.worse();
                weightToPrioMap.put(50d, worse == EXCLUDE ? REACH_DESTINATION : worse);
            }
        }

        if (way.hasTag("bicycle", "use_sidepath")) {
            weightToPrioMap.put(100d, REACH_DESTINATION);
        }

        Set<String> cyclewayValues = Stream.of("cycleway", "cycleway:left", "cycleway:both", "cycleway:right").map(key -> way.getTag(key, "")).collect(Collectors.toSet());
        if (cyclewayValues.contains("track")) {
            weightToPrioMap.put(100d, PREFER);
        } else if (Stream.of("lane", "opposite_track", "shared_lane", "share_busway", "shoulder").anyMatch(cyclewayValues::contains)) {
            weightToPrioMap.put(100d, SLIGHT_PREFER);
        } else if (pushingSectionsHighways.contains(highway) || "parking_aisle".equals(way.getTag("service"))) {
            PriorityCode pushingSectionPrio = SLIGHT_AVOID;
            if (way.hasTag("highway", "steps"))
                pushingSectionPrio = BAD;
            else if (way.hasTag("bicycle", "yes") || way.hasTag("bicycle", "permissive"))
                pushingSectionPrio = PREFER;
            else if (bikeDesignated)
                pushingSectionPrio = VERY_NICE;

            if (way.hasTag("foot", "yes") && !way.hasTag("segregated", "yes"))
                pushingSectionPrio = pushingSectionPrio.worse();

            weightToPrioMap.put(100d, pushingSectionPrio);
        }

        if (way.hasTag("railway", "tram"))
            weightToPrioMap.put(50d, AVOID_MORE);

        String classBicycleValue = way.getTag(classBicycleKey);
        if (classBicycleValue == null) classBicycleValue = way.getTag("class:bicycle");

        // We assume that humans are better in classifying preferences compared to our algorithm above
        if (classBicycleValue != null) {
            PriorityCode prio = convertClassValueToPriority(classBicycleValue);
            // do not overwrite if e.g. designated
            weightToPrioMap.compute(100d, (key, existing) ->
                    existing == null || existing.getValue() < prio.getValue() ? prio : existing
            );
        }

        // Increase priority in case that maxspeed limits our average speed as compensation. See #630
        if (maxSpeed > 0 && maxSpeed <= wayTypeSpeed) {
            PriorityCode lastEntryValue = weightToPrioMap.lastEntry().getValue();
            if (lastEntryValue.getValue() < BEST.getValue())
                weightToPrioMap.put(110d, lastEntryValue.better());
        }
    }

    static boolean isBikeDesignated(ReaderWay way) {
        return way.hasTag("bicycle", "designated")
                || way.hasTag("bicycle", "official")
                || way.hasTag("segregated", "yes")
                || way.hasTag("bicycle_road", "yes")
                || way.hasTag("cyclestreet", "yes")
                || CYCLEWAY_KEYS.stream().anyMatch(k -> way.getTag(k, "").equals("track"))
                || way.hasTag(CYCLEWAY_BICYCLE_KEYS, "designated");
    }

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
