package com.graphhopper.routing.profiles.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.IntEncodedValue;
import com.graphhopper.routing.profiles.StringEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;

import java.util.*;

/**
 * Stores the class of the road like motorway or primary. Previously called "highway" in DataFlagEncoder.
 */
public class RoadClassParser extends AbstractTagParser {

    private static final List<String> FERRIES = Arrays.asList("shuttle_train", "ferry");
    private static final Map<String, Double> CAR_SPEEDS = new LinkedHashMap<String, Double>() {
        {
            put("motorway", 100d);
            put("motorway_link", 70d);
            put("motorroad", 90d);
            put("trunk", 70d);
            put("trunk_link", 65d);
            put("primary", 65d);
            put("primary_link", 60d);
            put("secondary", 60d);
            put("secondary_link", 50d);
            put("tertiary", 50d);
            put("tertiary_link", 40d);
            put("residential", 30d);
            put("unclassified", 30d);
            put("service", 20d);
            put("road", 20d);
            put("track", 15d);
            put("forestry", 15d);
            put("living_street", 5d);
            // TODO how to handle roads that are not allowed per default but could be allowed via explicit tagging?
            // put("cycleway", 15d);
            // put("bridleway", 10d);
            // put("path", 10d);
        }
    };

    private final StringEncodedValue enc;

    public RoadClassParser() {
        super(EncodingManager.ROAD_CLASS);
        List<String> roadClasses = Arrays.asList("_default", "motorway", "motorway_link", "motorroad", "trunk", "trunk_link",
                "primary", "primary_link", "secondary", "secondary_link", "tertiary", "tertiary_link",
                "residential", "unclassified", "service", "track", "road", "cycleway", "bridleway", "forestry",
                "footway", "path", "steps", "pedestrian", "living_street");
        enc = new StringEncodedValue(EncodingManager.ROAD_CLASS, roadClasses, "_default");
    }

    public StringEncodedValue getEnc() {
        return enc;
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, long allowed, long relationFlags) {
        int hwValue = getHighwayValue(way);
        if (hwValue == 0)
            return edgeFlags;

        enc.setInt(false, edgeFlags, hwValue);
        return edgeFlags;
    }

    /**
     * This method converts the specified way into a storable integer value.
     *
     * @return 0 for default
     */
    public int getHighwayValue(ReaderWay way) {
        String highwayValue = way.getTag("highway");
        int hwValue = enc.indexOf(highwayValue);
        if (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable")) {
            hwValue = 0;
        } else if (hwValue == 0) {
            if (way.hasTag("route", FERRIES)) {
                String ferryValue = way.getFirstPriorityTag(FERRIES);
                if (ferryValue == null)
                    ferryValue = "service";
                hwValue = enc.indexOf(ferryValue);
            }
        }
        return hwValue;
    }

    double[] getHighwaySpeedMap(Map<String, Double> map) {
        if (map == null)
            throw new IllegalArgumentException("Map cannot be null when calling getHighwaySpeedMap");

        double[] res = new double[enc.getMapSize()];
        for (Map.Entry<String, Double> e : map.entrySet()) {
            int integ = enc.indexOf(e.getKey());
            if (integ == 0)
                throw new IllegalArgumentException("Graph not prepared for highway=" + e.getKey());

            if (e.getValue() < 0)
                throw new IllegalArgumentException("Negative speed " + e.getValue() + " not allowed. highway=" + e.getKey());

            res[integ] = e.getValue();
        }
        return res;
    }

    /**
     * This method creates a Config map out of the PMap. Later on this conversion should not be
     * necessary when we read JSON.
     */
    public WeightingConfig createWeightingConfig(PMap pMap) {
        HashMap<String, Double> map = new HashMap<>(CAR_SPEEDS.size());
        for (Map.Entry<String, Double> e : CAR_SPEEDS.entrySet()) {
            map.put(e.getKey(), pMap.getDouble(e.getKey(), e.getValue()));
        }

        return new WeightingConfig(getHighwaySpeedMap(map));
    }

    public class WeightingConfig {
        private final double[] speedArray;

        public WeightingConfig(double[] speedArray) {
            this.speedArray = speedArray;
        }

        public double getSpeed(EdgeIteratorState edgeState) {
            int highwayKey = edgeState.get((IntEncodedValue) enc);
            // ensure before (in createResult) that all highways that were specified in the request are known
            double speed = speedArray[highwayKey];
            if (speed < 0)
                throw new IllegalStateException("speed was negative? " + edgeState.getEdge()
                        + ", highway:" + highwayKey);
            return speed;
        }

        public double getMaxSpecifiedSpeed() {
            double tmpSpeed = 0;
            for (double speed : speedArray) {
                if (speed > tmpSpeed)
                    tmpSpeed = speed;
            }
            return tmpSpeed;
        }
    }
}
