package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.MaxSpeed;
import com.graphhopper.routing.util.FerrySpeedCalculator;
import com.graphhopper.storage.IntsRef;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.graphhopper.routing.util.parsers.AbstractAccessParser.INTENDED;

public abstract class BikeCommonPriorityParser implements TagParser {
    private static final Set<String> CYCLEWAY_KEYS = Set.of("cycleway", "cycleway:left", "cycleway:both", "cycleway:right");

    // rare use case when a bicycle lane has access tag
    private static final List<String> CYCLEWAY_BICYCLE_KEYS = List.of("cycleway:bicycle", "cycleway:both:bicycle", "cycleway:left:bicycle", "cycleway:right:bicycle");

    // pushing section highways are parts where you need to get off your bike and push it
    protected final HashSet<String> pushingSectionsHighways = new HashSet<>();
    protected final Set<String> preferHighwayTags = new HashSet<>();
    protected final Map<String, Double> avoidHighwayTags = new HashMap<>();
    protected final Set<String> unpavedSurfaceTags = new HashSet<>();

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

        avoidHighwayTags.put("motorway", 0.1);
        avoidHighwayTags.put("motorway_link", 0.1);
        avoidHighwayTags.put("trunk", 0.1);
        avoidHighwayTags.put("trunk_link", 0.1);
        avoidHighwayTags.put("primary", 0.5);
        avoidHighwayTags.put("primary_link", 0.5);
        avoidHighwayTags.put("secondary", 0.8);
        avoidHighwayTags.put("secondary_link", 0.8);
        avoidHighwayTags.put("bridleway", 0.8);

        avoidSpeedLimit = 71;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null && !FerrySpeedCalculator.isFerry(way)) return;

        double maxSpeed = Math.max(avgSpeedEnc.getDecimal(false, edgeId, edgeIntAccess),
                avgSpeedEnc.getDecimal(true, edgeId, edgeIntAccess));
        double prio = collect(way, maxSpeed, isBikeDesignated(way));
        priorityEnc.setDecimal(false, edgeId, edgeIntAccess, Math.min(1.5, Math.max(0, prio)));
    }

    // Conversion of class value to priority. See http://wiki.openstreetmap.org/wiki/Class:bicycle
    private double convertClassValueToPriority(String tagvalue) {
        try {
            return switch (Integer.parseInt(tagvalue)) {
                case 3 -> 1.5;
                case 2 -> 1.3;
                case 1 -> 1.2;
                case -1 -> 0.8;
                case -2 -> 0.5;
                case -3 -> 0.1;
                default -> 1;
            };
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    double collect(ReaderWay way, double wayTypeSpeed, boolean bikeDesignated) {
        double prio = 1;
        String highway = way.getTag("highway");

        Set<String> cyclewayValues = Stream.of("cycleway", "cycleway:left", "cycleway:both", "cycleway:right").map(key -> way.getTag(key, "")).collect(Collectors.toSet());
        double maxSpeed = Math.max(OSMMaxSpeedParser.parseMaxSpeed(way, false), OSMMaxSpeedParser.parseMaxSpeed(way, true));

        boolean bikeNotSeparate = way.hasTag("foot", "yes") && !way.hasTag("segregated", "yes");

        if (pushingSectionsHighways.contains(highway) || "parking_aisle".equals(way.getTag("service"))) {
            if (way.hasTag("highway", "steps"))
                prio = 0.5;
            else if (bikeDesignated)
                prio = 1.3;
            else if (way.hasTag("bicycle", INTENDED))
                prio = bikeNotSeparate ? 1.2 : 1.1;
            else
                prio = 0.9;

        } else if (preferHighwayTags.contains(highway) || maxSpeed <= 30) {
            if (maxSpeed == MaxSpeed.MAXSPEED_MISSING || maxSpeed < avoidSpeedLimit) {
                if (!way.hasTag("tunnel", INTENDED))
                    prio = 1.2;
            }

        } else if (avoidHighwayTags.containsKey(highway)
                || (maxSpeed != MaxSpeed.MAXSPEED_MISSING && maxSpeed >= avoidSpeedLimit && !"track".equals(highway))) {
            Double priorityCode = avoidHighwayTags.get(highway);
            prio = priorityCode == null ? 0.8 : priorityCode;
            if (way.hasTag("tunnel", INTENDED)) {
                prio = priorityCode == null ? 0.5 : Math.max(0.1, priorityCode - 0.3);
            }
        }

        if ("cycleway".equals(highway)) {
            prio = bikeNotSeparate ? 1.2 : 1.3;
        } else if (bikeDesignated && ("path".equals(highway) || "track".equals(highway) || "bridleway".equals(highway))) {
            boolean isGoodSurface = way.getTag("tracktype", "").equals("grade1") || goodSurface.contains(way.getTag("surface", ""));
            prio = isGoodSurface ? 1.3 : 1.2;
        } else if (bikeDesignated && !"steps".equals(highway) || cyclewayValues.contains("track")) {
            prio = 1.2;
        } else if (Stream.of("lane", "opposite_track", "shared_lane", "share_busway", "shoulder").anyMatch(cyclewayValues::contains)) {
            prio = 1.1;
        } else if (way.hasTag("railway", "tram")) {
            prio = 0.6;
        } else if (way.hasTag("bicycle", "use_sidepath")) {
            prio = 0.1;
        }

        // We assume that humans are better in classifying preferences compared to our algorithm above
        String classBicycleValue = way.getTag(classBicycleKey);
        if (classBicycleValue == null) classBicycleValue = way.getTag("class:bicycle");
        if (classBicycleValue != null) {
            double tmpPrio = convertClassValueToPriority(classBicycleValue);
            // do not overwrite if e.g. already designated
            if (tmpPrio > prio || prio == 1.0) prio = tmpPrio;
        }

        // Increase the priority for scenic routes or in case that maxspeed limits our average speed as compensation. See #630
        if (way.hasTag("scenic", "yes") || maxSpeed > 0 && maxSpeed <= wayTypeSpeed)
            prio *= 1.1;

        return prio;
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

    DecimalEncodedValue getPriorityEnc() {
        return priorityEnc;
    }
}
