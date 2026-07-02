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
package com.graphhopper.routing.ev;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the constant NAMES AND ORDER of every enum stored in graphs via EnumEncodedValue -
 * ordinals are persisted, so reordering or renaming constants silently corrupts stored graphs.
 * Expected values were extracted from the pre-migration java implementation.
 * See docs/pinned-behavior.md. Appending new constants at the END is safe; if this test fails
 * for any other reason, do NOT just update the literals.
 */
public class EvEnumOrderPinnedTest {

    private static String names(Class<? extends Enum<?>> c) {
        return Arrays.stream(c.getEnumConstants()).map(Enum::name).collect(Collectors.joining(","));
    }

    @Test
    public void orderBikeRoadAccess() {
        assertEquals("MISSING,YES,DESIGNATED,DISMOUNT,DESTINATION,PRIVATE,MILITARY,USE_SIDEPATH,NO", names(BikeRoadAccess.class));
    }
    @Test
    public void orderBikeTemporalAccess() {
        assertEquals("MISSING,YES,NO", names(BikeTemporalAccess.class));
    }
    @Test
    public void orderCarTemporalAccess() {
        assertEquals("MISSING,YES,NO", names(CarTemporalAccess.class));
    }
    @Test
    public void orderCrossing() {
        assertEquals("MISSING,RAILWAY_BARRIER,RAILWAY,TRAFFIC_SIGNALS,UNCONTROLLED,MARKED,UNMARKED,NO", names(Crossing.class));
    }
    @Test
    public void orderCycleway() {
        assertEquals("MISSING,TRACK,LANE,SHARED_LANE,SHOULDER,SEPARATE,NO", names(Cycleway.class));
    }
    @Test
    public void orderFootRoadAccess() {
        assertEquals("MISSING,YES,DESIGNATED,DESTINATION,PRIVATE,MILITARY,USE_SIDEPATH,NO", names(FootRoadAccess.class));
    }
    @Test
    public void orderFootTemporalAccess() {
        assertEquals("MISSING,YES,NO", names(FootTemporalAccess.class));
    }
    @Test
    public void orderFootway() {
        assertEquals("MISSING,SIDEWALK,CROSSING,ACCESS_AISLE,LINK,TRAFFIC_ISLAND,ALLEY", names(Footway.class));
    }
    @Test
    public void orderHazmat() {
        assertEquals("YES,NO", names(Hazmat.class));
    }
    @Test
    public void orderHazmatTunnel() {
        assertEquals("A,B,C,D,E", names(HazmatTunnel.class));
    }
    @Test
    public void orderHazmatWater() {
        assertEquals("YES,PERMISSIVE,NO", names(HazmatWater.class));
    }
    @Test
    public void orderHgv() {
        assertEquals("MISSING,YES,DESIGNATED,DESTINATION,DELIVERY,DISCOURAGED,AGRICULTURAL,NO", names(Hgv.class));
    }
    @Test
    public void orderMaxWeightExcept() {
        assertEquals("MISSING,DELIVERY,DESTINATION,FORESTRY", names(MaxWeightExcept.class));
    }
    @Test
    public void orderRoadAccess() {
        assertEquals("YES,DESTINATION,CUSTOMERS,DELIVERY,PRIVATE,MILITARY,AGRICULTURAL,FORESTRY,NO", names(RoadAccess.class));
    }
    @Test
    public void orderRoadClass() {
        assertEquals("OTHER,MOTORWAY,TRUNK,PRIMARY,SECONDARY,TERTIARY,RESIDENTIAL,UNCLASSIFIED,SERVICE,ROAD,TRACK,BRIDLEWAY,STEPS,CYCLEWAY,PATH,LIVING_STREET,FOOTWAY,PEDESTRIAN,PLATFORM,CORRIDOR,CONSTRUCTION,BUSWAY", names(RoadClass.class));
    }
    @Test
    public void orderRoadEnvironment() {
        assertEquals("OTHER,ROAD,FERRY,TUNNEL,BRIDGE,FORD", names(RoadEnvironment.class));
    }
    @Test
    public void orderRouteNetwork() {
        assertEquals("MISSING,INTERNATIONAL,NATIONAL,REGIONAL,LOCAL,OTHER", names(RouteNetwork.class));
    }
    @Test
    public void orderSidewalk() {
        assertEquals("MISSING,YES,SEPARATE,NO", names(Sidewalk.class));
    }
    @Test
    public void orderSmoothness() {
        assertEquals("MISSING,EXCELLENT,GOOD,INTERMEDIATE,BAD,VERY_BAD,HORRIBLE,VERY_HORRIBLE,IMPASSABLE,OTHER", names(Smoothness.class));
    }
    @Test
    public void orderSurface() {
        assertEquals("MISSING,PAVED,ASPHALT,CONCRETE,PAVING_STONES,COBBLESTONE,UNPAVED,COMPACTED,FINE_GRAVEL,GRAVEL,GROUND,DIRT,GRASS,SAND,WOOD,OTHER", names(Surface.class));
    }
    @Test
    public void orderToll() {
        assertEquals("MISSING,NO,HGV,ALL", names(Toll.class));
    }
    @Test
    public void orderTrackType() {
        assertEquals("MISSING,GRADE1,GRADE2,GRADE3,GRADE4,GRADE5", names(TrackType.class));
    }
    @Test
    public void orderUrbanDensity() {
        assertEquals("RURAL,RESIDENTIAL,CITY", names(UrbanDensity.class));
    }
    @Test
    public void countryAndStateShape() {
        // full name lists would be huge; counts + reference constants pin the critical shape
        assertEquals(220, Country.values().length);
        assertEquals(77, State.values().length);
        assertEquals("DEU", Country.find("DE").toString());
        assertEquals("---", Country.MISSING.toString());
        assertNull(Country.find("--"));
        assertNull(Country.find("XX"));
        assertEquals("US-CA", State.find("US-CA").toString());
        assertEquals("-", State.MISSING.toString());
        assertEquals(State.MISSING, State.find("XX-YY"));
    }

    @Test
    public void toStringAsymmetries() {
        // most ev enums lowercase their name - these deliberately do not:
        assertEquals("A", HazmatTunnel.A.toString());
        assertEquals("motorway", RoadClass.MOTORWAY.toString());
        assertEquals("missing", Surface.MISSING.toString());
    }

    @Test
    public void findDefaultAsymmetries() {
        assertEquals(RoadAccess.YES, RoadAccess.find("unknown_value"));
        assertEquals(RoadAccess.PRIVATE, RoadAccess.find("permit"));
        assertEquals(Surface.COBBLESTONE, Surface.find("cobblestone:flattened"));
        assertEquals(Surface.PAVED, Surface.find("metal"));
        assertEquals(Smoothness.OTHER, Smoothness.find("unknown_value"));
        assertEquals(Smoothness.MISSING, Smoothness.find(""));
    }
}
