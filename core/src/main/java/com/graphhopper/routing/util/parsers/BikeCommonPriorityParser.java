package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.MaxSpeed;
import com.graphhopper.routing.util.FerrySpeedCalculator;
import com.graphhopper.storage.IntsRef;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.graphhopper.routing.util.parsers.AbstractAccessParser.INTENDED;

public abstract class BikeCommonPriorityParser implements TagParser {
    private static final Set<String> CYCLEWAY_KEYS = Set.of("cycleway", "cycleway:left", "cycleway:both", "cycleway:right");

    // rare use case when a bicycle lane has access tag
    private static final List<String> CYCLEWAY_BICYCLE_KEYS = List.of("cycleway:bicycle", "cycleway:both:bicycle", "cycleway:left:bicycle", "cycleway:right:bicycle");
    private final Set<String> bikeNotAllowed = Set.of("footway", "pedestrian", "platform");
    private final Set<String> bikeNotAllowedAndBadSurface = Set.of("path", "track", "bridleway");
    protected final Set<String> goodSurface = Set.of("paved", "asphalt", "concrete");


    // Conversion of class value to priority. See http://wiki.openstreetmap.org/wiki/Class:bicycle
    private final Map<Integer, Double> bicycleClass = Map.of(3, 1.5,
            2, 1.3,
            1, 1.2,
            -1, 0.8,
            -2, 0.5,
            -3, 0.1);

    protected final HashMap<String, Double> highways = new HashMap<>();

    protected final DecimalEncodedValue avgSpeedEnc;
    protected final DecimalEncodedValue priorityEnc;
    // Car speed limit which switches the preference from UNCHANGED to AVOID_IF_POSSIBLE
    int avoidSpeedLimit;

    // This is the specific bicycle class
    private String classBicycleKey;

    protected BikeCommonPriorityParser(DecimalEncodedValue priorityEnc, DecimalEncodedValue avgSpeedEnc) {
        this.priorityEnc = priorityEnc;
        this.avgSpeedEnc = avgSpeedEnc;

        highways.put("motorway", 0.1);
        highways.put("motorway_link", 0.1);
        highways.put("trunk", 0.1);
        highways.put("trunk_link", 0.1);
        highways.put("primary", 0.5);
        highways.put("primary_link", 0.5);
        highways.put("secondary", 0.8);
        highways.put("secondary_link", 0.8);

        highways.put("footway", 0.8);
        highways.put("pedestrian", 0.8);
        highways.put("steps", 0.8);
        highways.put("platform", 0.8);
        highways.put("bridleway", 0.8);

        // TODO NOW remove as default is 1.0
        //  why is 0.9 used in tests
        highways.put("path", 0.8);
        highways.put("track", 0.8);

        highways.put("living_street", 1.0);

        // See #3015 as why it shouldn't be preferred
        highways.put("tertiary", 1.0);
        highways.put("tertiary_link", 1.0);
        highways.put("road", 1.0);
        highways.put("unclassified", 1.0);
        highways.put("residential", 1.0);
        highways.put("service", 1.0);

        avoidSpeedLimit = 71;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        if (!way.hasTag("highway") || FerrySpeedCalculator.isFerry(way)) {
            priorityEnc.setDecimal(false, edgeId, edgeIntAccess, 1);
            return;
        }

        double maxSpeed = Math.max(avgSpeedEnc.getDecimal(false, edgeId, edgeIntAccess),
                avgSpeedEnc.getDecimal(true, edgeId, edgeIntAccess));
        double prio = collect(way, maxSpeed, isBikeDesignated(way));
        priorityEnc.setDecimal(false, edgeId, edgeIntAccess, Math.min(1.5, Math.max(0, prio)));
    }

    double collect(ReaderWay way, double wayTypeSpeed, boolean bikeDesignated) {
        double prio;
        String highway = way.getTag("highway");

        Set<String> cyclewayValues = Stream.of("cycleway", "cycleway:left", "cycleway:both", "cycleway:right").map(key -> way.getTag(key, "")).collect(Collectors.toSet());
        double maxSpeed = Math.max(OSMMaxSpeedParser.parseMaxSpeed(way, false), OSMMaxSpeedParser.parseMaxSpeed(way, true));
        boolean shareWithFoot = way.hasTag("foot", "yes") && !way.hasTag("segregated", "yes");

        // TODO according to wiki: bicycle=designated is not necessary on highway=footway + bicycle=yes or highway=pedestrian + bicycle=yes => do we handle this via preferHighwayTags?
        boolean isGoodSurface = way.getTag("tracktype", "").equals("grade1") || goodSurface.contains(way.getTag("surface", ""));

        if ("steps".equals(highway)) {
            prio = 0.5;
        } else if ("cycleway".equals(highway) || cyclewayValues.contains("track") || bikeDesignated) {
            prio = shareWithFoot || bikeNotAllowedAndBadSurface.contains(highway) && !isGoodSurface ? 1.2 : 1.3;
        } else if (Stream.of("lane", "opposite_track", "shared_lane", "share_busway", "shoulder").anyMatch(cyclewayValues::contains)) {
            prio = 1.1;
        } else if (way.hasTag("railway", "tram")) {
            prio = 0.6;
        } else if (way.hasTag("bicycle", "use_sidepath")) {
            prio = 0.1;
        } else if (bikeNotAllowed.contains(highway) || "parking_aisle".equals(way.getTag("service")) || way.hasTag("bicycle", "dismount")) {
            // TODO LATER more finegrained
            //  prio = way.hasTag("bicycle", INTENDED) ? (shareWithFoot ? 1.1 : 1.2) : 0.9;
            prio = way.hasTag("bicycle", INTENDED) ? 1.2 : 0.8;

        } else {
            double prioFromHighwayTag = highways.getOrDefault(highway, 1.0);

            if (bikeNotAllowedAndBadSurface.contains(highway)) {
                if (way.hasTag("bicycle", INTENDED))
                    prioFromHighwayTag = Math.max(prioFromHighwayTag, 1.1) + (isGoodSurface ? 0.1 : 0);
                else if (isGoodSurface)
                    prioFromHighwayTag *= 1.1; // make slightly better but not necessarily above 1.0
            }

            if (way.hasTag("tunnel", INTENDED)) {
                prio = Math.min(1, Math.max(0.1, prioFromHighwayTag * 0.8));
            } else if (maxSpeed < 30) {
                prio = Math.max(prioFromHighwayTag * 1.1, 1.1);
            } else if (maxSpeed != MaxSpeed.MAXSPEED_MISSING && maxSpeed > avoidSpeedLimit && prioFromHighwayTag > 0.9) {
                prio = 0.8; // keep avoiding even if highways map says otherwise
            } else {
                prio = prioFromHighwayTag;
            }
        }

        // We assume that humans are better in classifying preferences compared to our algorithm above
        String classBicycleValue = way.getTag(classBicycleKey);
        if (classBicycleValue == null) classBicycleValue = way.getTag("class:bicycle");
        if (classBicycleValue != null) {
            try {
                Double tmpPrio = bicycleClass.get(Integer.parseInt(classBicycleValue));
                // do not overwrite if e.g. already designated
                if (tmpPrio != null && (tmpPrio > prio || prio == 1)) prio = tmpPrio;
            } catch (NumberFormatException e) {
            }
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

    void setSpecificClassBicycle(String subkey) {
        classBicycleKey = "class:bicycle:" + subkey;
    }

    DecimalEncodedValue getPriorityEnc() {
        return priorityEnc;
    }
}
