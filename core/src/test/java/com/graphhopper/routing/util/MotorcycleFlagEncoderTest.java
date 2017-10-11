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
package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import org.junit.Test;

import java.text.DateFormat;
import java.util.Date;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class MotorcycleFlagEncoderTest {
    private final EncodingManager em = new EncodingManager("motorcycle,foot");
    private final MotorcycleFlagEncoder encoder = (MotorcycleFlagEncoder) em.getEncoder("motorcycle");

    private Graph initExampleGraph() {
        GraphHopperStorage gs = new GraphHopperStorage(new RAMDirectory(), em, true, new GraphExtension.NoOpExtension()).create(1000);
        NodeAccess na = gs.getNodeAccess();
        // 50--(0.0001)-->49--(0.0004)-->55--(0.0005)-->60
        na.setNode(0, 51.1, 12.001, 50);
        na.setNode(1, 51.1, 12.002, 60);
        EdgeIteratorState edge = gs.edge(0, 1).
                setWayGeometry(Helper.createPointList3D(51.1, 12.0011, 49, 51.1, 12.0015, 55));
        edge.setDistance(100);

        edge.setFlags(encoder.setReverseSpeed(encoder.setProperties(10, true, true), 15));
        return gs;
    }

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);
        assertFalse(encoder.acceptWay(way) > 0);
        way.setTag("highway", "service");
        assertTrue(encoder.acceptWay(way) > 0);
        way.setTag("access", "no");
        assertFalse(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "track");
        assertTrue(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "delivery");
        assertFalse(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "unclassified");
        way.setTag("ford", "yes");
        assertFalse(encoder.acceptWay(way) > 0);
        way.setTag("motorcycle", "yes");
        assertTrue(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("route", "ferry");
        assertTrue(encoder.acceptWay(way) > 0);
        assertTrue(encoder.isFerry(encoder.acceptWay(way)));
        way.setTag("motorcycle", "no");
        assertFalse(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "yes");
        assertFalse(encoder.acceptWay(way) > 0);
        assertFalse(encoder.isFerry(encoder.acceptWay(way)));

        way.clearTags();
        way.setTag("access", "yes");
        way.setTag("motor_vehicle", "no");
        assertFalse(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "yes");
        way.setTag("motor_vehicle", "no");
        assertFalse(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "emergency");
        assertFalse(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("motor_vehicle", "emergency");
        assertFalse(encoder.acceptWay(way) > 0);

        DateFormat simpleDateFormat = Helper.createFormatter("yyyy MMM dd");

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access:conditional", "no @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertFalse(encoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access", "no");
        way.setTag("access:conditional", "yes @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertTrue(encoder.acceptWay(way) > 0);
    }

    @Test
    public void testHandleWayTags() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "service");
        long flags = encoder.acceptWay(way);
        assertTrue(flags > 0);
        long result = encoder.handleWayTags(way, flags, 0);
        assertEquals(20, encoder.getSpeed(result), .1);
        assertEquals(20, encoder.getReverseSpeed(result), .1);
    }

    @Test
    public void testRoundabout() {
        long flags = encoder.setAccess(0, true, true);
        long resFlags = encoder.setBool(flags, FlagEncoder.K_ROUNDABOUT, true);
        assertTrue(encoder.isBool(resFlags, FlagEncoder.K_ROUNDABOUT));
        assertTrue(encoder.isForward(resFlags));
        assertTrue(encoder.isBackward(resFlags));

        resFlags = encoder.setBool(flags, FlagEncoder.K_ROUNDABOUT, false);
        assertFalse(encoder.isBool(resFlags, FlagEncoder.K_ROUNDABOUT));
        assertTrue(encoder.isForward(resFlags));
        assertTrue(encoder.isBackward(resFlags));

        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "motorway");
        flags = encoder.handleWayTags(way, encoder.acceptBit, 0);
        assertTrue(encoder.isForward(flags));
        assertTrue(encoder.isBackward(flags));
        assertFalse(encoder.isBool(flags, FlagEncoder.K_ROUNDABOUT));

        way.setTag("junction", "roundabout");
        flags = encoder.handleWayTags(way, encoder.acceptBit, 0);
        assertTrue(encoder.isForward(flags));
        assertFalse(encoder.isBackward(flags));
        assertTrue(encoder.isBool(flags, FlagEncoder.K_ROUNDABOUT));

        way.clearTags();
        way.setTag("highway", "motorway");
        way.setTag("junction", "circular");
        flags = encoder.handleWayTags(way, encoder.acceptBit, 0);
        assertTrue(encoder.isForward(flags));
        assertFalse(encoder.isBackward(flags));
        assertTrue(encoder.isBool(flags, FlagEncoder.K_ROUNDABOUT));
    }

    @Test
    public void testSetSpeed0_issue367() {
        long flags = encoder.setProperties(10, true, true);
        flags = encoder.setSpeed(flags, 0);

        assertEquals(0, encoder.getSpeed(flags), .1);
        assertEquals(10, encoder.getReverseSpeed(flags), .1);
        assertFalse(encoder.isForward(flags));
        assertTrue(encoder.isBackward(flags));
    }

    @Test
    public void testCurvature() {
        Graph graph = initExampleGraph();
        EdgeIteratorState edge = GHUtility.getEdge(graph, 0, 1);

        double bendinessOfStraightWay = getBendiness(edge, 100.0);
        double bendinessOfCurvyWay = getBendiness(edge, 10.0);

        assertTrue("The bendiness of the straight road is smaller than the one of the curvy road", bendinessOfCurvyWay < bendinessOfStraightWay);
    }

    private double getBendiness(EdgeIteratorState edge, double estimatedDistance) {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("estimated_distance", estimatedDistance);
        long includeWay = encoder.acceptWay(way);
        long flags = encoder.handleWayTags(way, includeWay, 0l);
        edge.setFlags(flags);
        encoder.applyWayTags(way, edge);
        return encoder.getDouble(edge.getFlags(), MotorcycleFlagEncoder.CURVATURE_KEY);
    }
}
