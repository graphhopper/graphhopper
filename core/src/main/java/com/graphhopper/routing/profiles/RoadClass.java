/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.profiles;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;

import java.util.*;

public class RoadClass extends AbstractIndexBased {
    public static final RoadClass DEFAULT = new RoadClass("_default", 0),
            MOTORWAY = new RoadClass("motorway", 1), MOTORWAY_LINK = new RoadClass("motorway_link", 2),
            MOTORROAD = new RoadClass("motorroad", 3),
            TRUNK = new RoadClass("trunk", 4), TRUNK_LINK = new RoadClass("trunk_link", 5),
            PRIMARY = new RoadClass("primary", 6), PRIMARY_LINK = new RoadClass("primary_link", 7),
            SECONDARY = new RoadClass("secondary", 8), SECONDARY_LINK = new RoadClass("secondary_link", 9),
            TERTIARY = new RoadClass("tertiary", 10), TERTIARY_LINK = new RoadClass("tertiary_link", 11),
            RESIDENTIAL = new RoadClass("residential", 12),
            UNCLASSIFIED = new RoadClass("unclassified", 13),
            SERVICE = new RoadClass("service", 14),
            ROAD = new RoadClass("road", 15),
            TRACK = new RoadClass("track", 16),
            FORESTRY = new RoadClass("forestry", 17),
            STEPS = new RoadClass("steps", 18),
            CYCLEWAY = new RoadClass("cycleway", 19),
            PATH = new RoadClass("path", 20),
            LIVING_STREET = new RoadClass("living_street", 21);

    private static final List<RoadClass> values = Arrays.asList(DEFAULT, MOTORWAY, MOTORWAY_LINK, MOTORROAD, TRUNK, TRUNK_LINK,
            PRIMARY, PRIMARY_LINK, SECONDARY, SECONDARY_LINK, TERTIARY, TERTIARY_LINK, RESIDENTIAL, UNCLASSIFIED,
            SERVICE, ROAD, TRACK, FORESTRY, STEPS, CYCLEWAY, PATH, LIVING_STREET);

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

    public static ObjectEncodedValue create() {
        return new MappedObjectEncodedValue(EncodingManager.ROAD_CLASS, values);
    }

    public RoadClass(String name, int ordinal) {
        super(name, ordinal);
    }

    @JsonCreator
    static RoadClass deserialize(String name) {
        for (RoadClass rc : values) {
            if (rc.toString().equals(name))
                return rc;
        }
        throw new IllegalArgumentException("Cannot find RoadClass " + name);
    }

    /**
     * This method creates a Config map out of the PMap. Later on this conversion should not be
     * necessary when we read JSON.
     */
    public static SpeedConfig createSpeedConfig(ObjectEncodedValue enumEnc, PMap pMap) {
        HashMap<String, Double> map = new HashMap<>(values.size());
        for (int i = 1; i < values.size(); i++) {
            RoadClass e = values.get(i);
            Double speed = CAR_SPEEDS.get(e);
            if (speed != null)
                map.put(e.toString(), pMap.getDouble(e.toString(), speed));
        }

        return new SpeedConfig(getHighwaySpeedMap(enumEnc, map), enumEnc);
    }

    static double[] getHighwaySpeedMap(ObjectEncodedValue enumEnc, Map<String, Double> map) {
        if (map == null)
            throw new IllegalArgumentException("Map cannot be null when calling getHighwaySpeedMap");

        double[] res = new double[enumEnc.getObjects().length];
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
        private final ObjectEncodedValue enc;

        public SpeedConfig(double[] speedArray, ObjectEncodedValue enc) {
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

    public static List<RoadClass> create(String... values) {
        List<RoadClass> list = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            list.add(new RoadClass(values[i], i));
        }
        return Collections.unmodifiableList(list);
    }
}
