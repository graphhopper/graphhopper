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
import com.graphhopper.routing.util.EncodingManager;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class CarAverageSpeedParserTest {
    private final EncodingManager em = createEncodingManager("car");
    final CarAverageSpeedParser speedParser = new CarAverageSpeedParser(em);
    private final BooleanEncodedValue accessEnc = em.getBooleanEncodedValue(VehicleAccess.key("car"));
    private final DecimalEncodedValue avSpeedEnc = speedParser.getAverageSpeedEnc();

    private EncodingManager createEncodingManager(String carName) {
        return new EncodingManager.Builder()
                .add(VehicleAccess.create(carName))
                .add(VehicleSpeed.create(carName, 7, 2, true))
                .addTurnCostEncodedValue(TurnCost.create(carName, 1))
                .add(Smoothness.create())
                .add(Roundabout.create())
                .add(FerrySpeed.create())
                .build();
    }

    @Test
    public void testMaxSpeed() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "trunk");
        way.setTag("maxspeed", "500");
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        int edgeId = 0;
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(136, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("maxspeed:backward", "10");
        way.setTag("maxspeed:forward", "20");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(18, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);
        assertEquals(10, avSpeedEnc.getDecimal(true, edgeId, edgeIntAccess), 1e-1);

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("maxspeed:forward", "20");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(18, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("maxspeed:backward", "20");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(66, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);
        assertEquals(18, avSpeedEnc.getDecimal(true, edgeId, edgeIntAccess), 1e-1);

        way = new ReaderWay(1);
        way.setTag("highway", "motorway");
        way.setTag("maxspeed", "none");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(136, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), .1);

        way = new ReaderWay(1);
        way.setTag("highway", "motorway_link");
        way.setTag("maxspeed", "70 mph");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(102, avSpeedEnc.getDecimal(true, edgeId, edgeIntAccess), 1e-1);
    }

    @Test
    public void testSpeed() {
        // limit bigger than default road speed
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "trunk");
        way.setTag("maxspeed", "110");
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        int edgeId = 0;
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(100, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("surface", "cobblestone");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(30, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);

        way.clearTags();
        way.setTag("highway", "track");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(16, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("tracktype", "grade1");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(20, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("surface", "compacted");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);

        assertEquals(50, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("motorroad", "yes"); // motorroad should not influence speed. only access for non-motor vehicles
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);

        assertEquals(60, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);

        way.clearTags();
        way.setTag("highway", "motorway");
        way.setTag("motorroad", "yes");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(100, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);

        way.clearTags();
        way.setTag("highway", "motorway_link");
        way.setTag("motorroad", "yes");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(70, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);

        try {
            avSpeedEnc.setDecimal(false, edgeId, edgeIntAccess, -1);
            assertTrue(false);
        } catch (IllegalArgumentException ex) {
        }
    }

    @Test
    public void testSetSpeed() {
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        int edgeId = 0;
        avSpeedEnc.setDecimal(false, edgeId, edgeIntAccess, 10);
        assertEquals(10, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);
    }

    @Test
    public void testSetSpeed0_issue367_issue1234() {
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        int edgeId = 0;
        accessEnc.setBool(false, edgeId, edgeIntAccess, true);
        accessEnc.setBool(true, edgeId, edgeIntAccess, true);
        speedParser.setSpeed(false, edgeId, edgeIntAccess, 30);
        speedParser.setSpeed(true, edgeId, edgeIntAccess, 40);

        // exception for very low speed values
        assertThrows(IllegalArgumentException.class, () -> speedParser.setSpeed(false, edgeId, edgeIntAccess, 0.09));

        // this is independent from the speed
        assertTrue(accessEnc.getBool(false, edgeId, edgeIntAccess));

        // and does not affect the reverse direction:
        assertEquals(40, avSpeedEnc.getDecimal(true, edgeId, edgeIntAccess), .1);
        assertTrue(accessEnc.getBool(true, edgeId, edgeIntAccess));

        // for low speed values (and low precision of the EncodedValue) it can happen that the speed is increased:
        speedParser.setSpeed(false, edgeId, edgeIntAccess, 1);
        assertEquals(avSpeedEnc.getSmallestNonZeroValue(), avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), .1);

        assertTrue(accessEnc.getBool(true, edgeId, edgeIntAccess));
    }

    @Test
    public void testMaxValue() {
        DecimalEncodedValueImpl smallFactorSpeedEnc = new DecimalEncodedValueImpl("car_average_speed", 10, 0.5, true);
        EncodingManager em = new EncodingManager.Builder()
                .add(new SimpleBooleanEncodedValue("car_access", true))
                .add(smallFactorSpeedEnc)
                .add(FerrySpeed.create())
                .addTurnCostEncodedValue(TurnCost.create("car", 1))
                .build();
        CarAverageSpeedParser speedParser = new CarAverageSpeedParser(em);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "motorway_link");
        way.setTag("maxspeed", "60 mph");
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        int edgeId = 0;
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);

        // double speed = AbstractFlagEncoder.parseSpeed("60 mph");
        // => 96.56 * 0.9 => 86.9
        assertEquals(86.9, smallFactorSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);
        assertEquals(86.9, smallFactorSpeedEnc.getDecimal(true, edgeId, edgeIntAccess), 1e-1);

        // test that maxPossibleValue  is not exceeded
        way = new ReaderWay(2);
        way.setTag("highway", "motorway_link");
        way.setTag("maxspeed", "70 mph");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(101.5, smallFactorSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), .1);
    }

    @Test
    public void testApplyBadSurfaceSpeed() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("surface", "unpaved");
        assertEquals(30, speedParser.applyBadSurfaceSpeed(way, 90), 1e-1);
        assertEquals(20, speedParser.applyBadSurfaceSpeed(way, 20), 1e-1);

        way.setTag("surface", "sett");
        assertEquals(30, speedParser.applyBadSurfaceSpeed(way, 90), 1e-1);
        way.setTag("surface", "gravel:lanes");
        assertEquals(40, speedParser.applyBadSurfaceSpeed(way, 90), 1e-1);
        way.setTag("surface", "asphalt");
        assertEquals(90, speedParser.applyBadSurfaceSpeed(way, 90), 1e-1);
        way.setTag("surface", "something_unknown");
        assertEquals(30, speedParser.applyBadSurfaceSpeed(way, 90), 1e-1);
        way.removeTag("surface");
        assertEquals(90, speedParser.applyBadSurfaceSpeed(way, 90), 1e-1);
    }

    @Test
    public void testBadSurfaceWithMaxSpeed() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "unclassified");
        way.setTag("maxspeed", "80");
        way.setTag("surface", "gravel");
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        int edgeId = 0;
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(40, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);

        way.setTag("surface", "asphalt");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(72, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);
    }

    @Test
    void nonHighwaysFallbackSpeed_issue2845() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("man_made", "pier");
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(0, edgeIntAccess, way);
        assertEquals(10, avSpeedEnc.getDecimal(false, 0, edgeIntAccess), 1e-1);

        way.clearTags();
        way.setTag("railway", "platform");
        speedParser.handleWayTags(0, edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags()), way);
        assertEquals(10, avSpeedEnc.getDecimal(false, 0, edgeIntAccess), 1e-1);

        way.clearTags();
        way.setTag("route", "ski");
        speedParser.handleWayTags(0, edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags()), way);
        assertEquals(10, avSpeedEnc.getDecimal(false, 0, edgeIntAccess), 1e-1);

        way.clearTags();
        way.setTag("highway", "abandoned");
        speedParser.handleWayTags(0, edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags()), way);
        assertEquals(10, avSpeedEnc.getDecimal(false, 0, edgeIntAccess), 1e-1);

        way.clearTags();
        way.setTag("highway", "construction");
        way.setTag("maxspeed", "100");
        speedParser.handleWayTags(0, edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags()), way);
        // unknown highways can be quite fast in combination with maxspeed!?
        assertEquals(90, avSpeedEnc.getDecimal(false, 0, edgeIntAccess), 1e-1);
    }
}
