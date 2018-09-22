package com.graphhopper.routing.profiles;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;

import java.util.HashMap;
import java.util.Map;

public enum RoadClass {
    DEFAULT("_default", 0),
    MOTORWAY("motorway", 100d), MOTORWAY_LINK("motorway_link", 70d), MOTORROAD("motorroad", 90d),
    TRUNK("trunk", 70d), TRUNK_LINK("trunk_link", 65d),
    PRIMARY("primary", 65d), PRIMARY_LINK("primary_link", 60d),
    SECONDARY("secondary", 60d), SECONDARY_LINK("secondary_link", 50d),
    TERTIARY("tertiary", 50d), TERTIARY_LINK("tertiary_link", 40d),
    RESIDENTIAL("residential", 30d),
    UNCLASSIFIED("unclassified", 30d),
    SERVICE("service", 20d),
    ROAD("road", 20d),
    TRACK("track", 15d),
    FORESTRY("forestry", 15d),
    LIVING_STREET("living_street", 5d);

    //    private static final Map<String, Double> CAR_SPEEDS = new LinkedHashMap<String, Double>() {
//        {
//            put("motorway", 100d);
//            put("motorway_link", 70d);
//            put("motorroad", 90d);
//            put("trunk", 70d);
//            put("trunk_link", 65d);
//            put("primary", 65d);
//            put("primary_link", 60d);
//            put("secondary", 60d);
//            put("secondary_link", 50d);
//            put("tertiary", 50d);
//            put("tertiary_link", 40d);
//            put("residential", 30d);
//            put("unclassified", 30d);
//            put("service", 20d);
//            put("road", 20d);
//            put("track", 15d);
//            put("forestry", 15d);
//            put("living_street", 5d);
//            // TODO how to handle roads that are not allowed per default but could be allowed via explicit tagging?
//            // put("cycleway", 15d);
//            // put("bridleway", 10d);
//            // put("path", 10d);
//        }
//    };

    String name;
    double speed;

    RoadClass(String name, double speed) {
        this.name = name;
        this.speed = speed;
    }

    public double getSpeed() {
        return speed;
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
            if (e != RoadClass.DEFAULT)
                map.put(e.toString(), pMap.getDouble(e.toString(), e.getSpeed()));
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
