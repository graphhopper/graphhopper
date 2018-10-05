package com.graphhopper.routing.profiles;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public enum RoadClass {
    DEFAULT("_default"),
    MOTORWAY("motorway"), MOTORWAY_LINK("motorway_link"), MOTORROAD("motorroad"),
    TRUNK("trunk"), TRUNK_LINK("trunk_link"),
    PRIMARY("primary"), PRIMARY_LINK("primary_link"),
    SECONDARY("secondary"), SECONDARY_LINK("secondary_link"),
    TERTIARY("tertiary"), TERTIARY_LINK("tertiary_link"),
    RESIDENTIAL("residential"),
    UNCLASSIFIED("unclassified"),
    SERVICE("service"),
    ROAD("road"),
    TRACK("track"),
    FORESTRY("forestry"),
    STEPS("steps"),
    CYCLEWAY("cycleway"),
    PATH("path"),
    LIVING_STREET("living_street");

    private static final Map<RoadClass, Double> CAR_SPEEDS = new LinkedHashMap<RoadClass, Double>() {
        {
            put(MOTORWAY, 100d);
            put(MOTORWAY_LINK, 70d);
            put(MOTORROAD, 90d);
            put(TRUNK, 70d);
            put(TRUNK_LINK, 65d);
            put(PRIMARY, 65d);
            put(PRIMARY_LINK, 60d);
            put(SECONDARY, 60d);
            put(SECONDARY_LINK, 50d);
            put(TERTIARY, 50d);
            put(TERTIARY_LINK, 40d);
            put(RESIDENTIAL, 30d);
            put(UNCLASSIFIED, 30d);
            put(SERVICE, 20d);
            put(ROAD, 20d);
            put(TRACK, 15d);
            put(FORESTRY, 15d);
            put(LIVING_STREET, 5d);
            // TODO how to handle roads that are not allowed per default but could be allowed via explicit tagging?
            // put("cycleway", 15d);
            // put("bridleway", 10d);
            // put("path", 10d);
        }
    };

    String name;

    RoadClass(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static EnumEncodedValue<RoadClass> create() {
        return new EnumEncodedValue<>(EncodingManager.ROAD_CLASS, values(), DEFAULT);
    }


    /**
     * This method creates a Config map out of the PMap. Later on this conversion should not be
     * necessary when we read JSON.
     */
    public static SpeedConfig createSpeedConfig(EnumEncodedValue<RoadClass> enumEnc, PMap pMap) {
        HashMap<String, Double> map = new HashMap<>(RoadClass.values().length);
        for (RoadClass e : RoadClass.values()) {
            if (e != RoadClass.DEFAULT) {
                Double speed = CAR_SPEEDS.get(e);
                if (speed != null)
                    map.put(e.toString(), pMap.getDouble(e.toString(), speed));
            }
        }

        return new SpeedConfig(getHighwaySpeedMap(enumEnc, map), enumEnc);
    }

    static double[] getHighwaySpeedMap(EnumEncodedValue<RoadClass> enumEnc, Map<String, Double> map) {
        if (map == null)
            throw new IllegalArgumentException("Map cannot be null when calling getHighwaySpeedMap");

        double[] res = new double[enumEnc.size()];
        for (Map.Entry<String, Double> e : map.entrySet()) {
            int integ = enumEnc.indexOf(e.getKey());
            if (integ == 0)
                throw new IllegalArgumentException("Graph not prepared for highway=" + e.getKey());

            if (e.getValue() < 0)
                throw new IllegalArgumentException("Negative speed " + e.getValue() + " not allowed. highway=" + e.getKey());

            res[integ] = e.getValue();
        }
        return res;
    }

    public static class SpeedConfig {
        private final double[] speedArray;
        private final EnumEncodedValue<RoadClass> enc;

        public SpeedConfig(double[] speedArray, EnumEncodedValue<RoadClass> enc) {
            this.speedArray = speedArray;
            this.enc = enc;
        }

        public double getSpeed(EdgeIteratorState edgeState) {
            int highwayKey = edgeState.get((IntEncodedValue) enc);
            // ensure before (in createResult) that all highways that were specified in the request are known
            double speed = speedArray[highwayKey];
            if (speed < 0)
                throw new IllegalStateException("speed was negative? " + edgeState.getEdge() + ", highway:" + highwayKey);
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
