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
package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.FerrySpeedCalculator;
import com.graphhopper.util.Helper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CarAverageSpeedParser extends AbstractAverageSpeedParser implements TagParser {

    protected final Map<String, Integer> trackTypeSpeedMap = new HashMap<>();
    protected final Set<String> badSurfaceSpeedMap = new HashSet<>();
    // This value determines the maximal possible on roads with bad surfaces
    private final int badSurfaceSpeed;

    /**
     * A map which associates string to speed. Get some impression:
     * http://www.itoworld.com/map/124#fullscreen
     * http://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed
     */
    protected final Map<String, Integer> defaultSpeedMap = new HashMap<>();

    public CarAverageSpeedParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehicleSpeed.key("car")));
    }

    public CarAverageSpeedParser(DecimalEncodedValue speedEnc) {
        super(speedEnc);

        badSurfaceSpeedMap.add("cobblestone");
        badSurfaceSpeedMap.add("unhewn_cobblestone");
        badSurfaceSpeedMap.add("sett");
        badSurfaceSpeedMap.add("grass_paver");
        badSurfaceSpeedMap.add("gravel");
        badSurfaceSpeedMap.add("fine_gravel");
        badSurfaceSpeedMap.add("pebblestone");
        badSurfaceSpeedMap.add("sand");
        badSurfaceSpeedMap.add("paving_stones");
        badSurfaceSpeedMap.add("dirt");
        badSurfaceSpeedMap.add("earth");
        badSurfaceSpeedMap.add("ground");
        badSurfaceSpeedMap.add("wood");
        badSurfaceSpeedMap.add("grass");
        badSurfaceSpeedMap.add("unpaved");
        badSurfaceSpeedMap.add("compacted");

        // autobahn
        defaultSpeedMap.put("motorway", 100);
        defaultSpeedMap.put("motorway_link", 70);
        // bundesstraße
        defaultSpeedMap.put("trunk", 70);
        defaultSpeedMap.put("trunk_link", 65);
        // linking bigger town
        defaultSpeedMap.put("primary", 65);
        defaultSpeedMap.put("primary_link", 60);
        // linking towns + villages
        defaultSpeedMap.put("secondary", 60);
        defaultSpeedMap.put("secondary_link", 50);
        // streets without middle line separation
        defaultSpeedMap.put("tertiary", 50);
        defaultSpeedMap.put("tertiary_link", 40);
        defaultSpeedMap.put("unclassified", 30);
        defaultSpeedMap.put("residential", 30);
        defaultSpeedMap.put("living_street", 6);
        defaultSpeedMap.put("pedestrian", 6);
        defaultSpeedMap.put("service", 20);
        // unknown road
        defaultSpeedMap.put("road", 20);
        // forestry stuff
        defaultSpeedMap.put("track", 15);

        trackTypeSpeedMap.put("grade1", 20); // paved
        trackTypeSpeedMap.put("grade2", 15); // now unpaved - gravel mixed with ...
        trackTypeSpeedMap.put("grade3", 10); // ... hard and soft materials
        trackTypeSpeedMap.put(null, defaultSpeedMap.get("track"));

        // limit speed on bad surfaces to 30 km/h
        badSurfaceSpeed = 30;
    }

    protected double getSpeed(ReaderWay way) {
        String highwayValue = way.getTag("highway", "");
        Integer speed = defaultSpeedMap.get(highwayValue);

        // even inaccessible edges get a speed assigned
        if (speed == null) speed = 10;

        if (highwayValue.equals("track")) {
            String tt = way.getTag("tracktype");
            if (!Helper.isEmpty(tt)) {
                Integer tInt = trackTypeSpeedMap.get(tt);
                if (tInt != null)
                    speed = tInt;
            }
        }

        return speed;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way) {
        if (FerrySpeedCalculator.isFerry(way))
            return;

        // get assumed speed from highway type
        double speed = getSpeed(way);
        speed = applyBadSurfaceSpeed(way, speed);

        setSpeed(false, edgeId, edgeIntAccess, applyMaxSpeed(way, speed, false));
        setSpeed(true, edgeId, edgeIntAccess, applyMaxSpeed(way, speed, true));
    }

    /**
     * @param way   needed to retrieve tags
     * @param speed speed guessed e.g. from the road type or other tags
     * @return The assumed speed.
     */
    protected double applyMaxSpeed(ReaderWay way, double speed, boolean bwd) {
        double maxSpeed = OSMMaxSpeedParser.parseMaxSpeed(way, bwd);
        return maxSpeed != MaxSpeed.MAXSPEED_MISSING ? Math.max(1, maxSpeed * 0.9) : speed;
    }

    /**
     * @param way   needed to retrieve tags
     * @param speed speed guessed e.g. from the road type or other tags
     * @return The assumed speed
     */
    protected double applyBadSurfaceSpeed(ReaderWay way, double speed) {
        // limit speed if bad surface
        if (badSurfaceSpeed > 0 && speed > badSurfaceSpeed) {
            String surface = way.getTag("surface", "");
            int colonIndex = surface.indexOf(":");
            if (colonIndex != -1)
                surface = surface.substring(0, colonIndex);
            if (badSurfaceSpeedMap.contains(surface))
                speed = badSurfaceSpeed;
        }
        return speed;
    }
}
