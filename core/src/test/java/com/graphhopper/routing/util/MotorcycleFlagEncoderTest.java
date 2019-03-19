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
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
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
    private final EncodingManager em = EncodingManager.create("motorcycle,foot");
    private final MotorcycleFlagEncoder encoder = (MotorcycleFlagEncoder) em.getEncoder("motorcycle");
    private final BooleanEncodedValue accessEnc = encoder.getAccessEnc();

    private Graph initExampleGraph() {
        GraphHopperStorage gs = new GraphHopperStorage(new RAMDirectory(), em, true, new GraphExtension.NoOpExtension()).create(1000);
        NodeAccess na = gs.getNodeAccess();
        // 50--(0.0001)-->49--(0.0004)-->55--(0.0005)-->60
        na.setNode(0, 51.1, 12.001, 50);
        na.setNode(1, 51.1, 12.002, 60);
        EdgeIteratorState edge = gs.edge(0, 1).
                setWayGeometry(Helper.createPointList3D(51.1, 12.0011, 49, 51.1, 12.0015, 55));
        edge.setDistance(100);

        edge.set(accessEnc, true).setReverse(accessEnc, true).set(encoder.getAverageSpeedEnc(), 10.0).setReverse(encoder.getAverageSpeedEnc(), 15.0);
        return gs;
    }

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);
        assertTrue(encoder.getAccess(way).canSkip());
        way.setTag("highway", "service");
        assertTrue(encoder.getAccess(way).isWay());
        way.setTag("access", "no");
        assertTrue(encoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "track");
        assertTrue(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "delivery");
        assertTrue(encoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "unclassified");
        way.setTag("ford", "yes");
        assertTrue(encoder.getAccess(way).canSkip());
        way.setTag("motorcycle", "yes");
        assertTrue(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("route", "ferry");
        assertTrue(encoder.getAccess(way).isFerry());
        way.setTag("motorcycle", "no");
        assertTrue(encoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "yes");
        assertTrue(encoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("access", "yes");
        way.setTag("motor_vehicle", "no");
        assertTrue(encoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "yes");
        way.setTag("motor_vehicle", "no");
        assertTrue(encoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "emergency");
        assertTrue(encoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("motor_vehicle", "emergency");
        assertTrue(encoder.getAccess(way).canSkip());

        DateFormat simpleDateFormat = Helper.createFormatter("yyyy MMM dd");

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access:conditional", "no @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertTrue(encoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access", "no");
        way.setTag("access:conditional", "yes @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertTrue(encoder.getAccess(way).isWay());
    }

    @Test
    public void testHandleWayTags() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "service");
        assertTrue(encoder.getAccess(way).isWay());
        IntsRef edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, EncodingManager.Access.WAY, 0);
        assertEquals(20, encoder.getSpeed(edgeFlags), .1);
        assertEquals(20, encoder.getSpeed(true, edgeFlags), .1);
    }

    @Test
    public void testSetSpeed0_issue367() {
        IntsRef edgeFlags = em.createEdgeFlags();
        accessEnc.setBool(false, edgeFlags, true);
        accessEnc.setBool(true, edgeFlags, true);
        encoder.getAverageSpeedEnc().setDecimal(false, edgeFlags, 10);
        encoder.getAverageSpeedEnc().setDecimal(true, edgeFlags, 10);

        assertEquals(10, encoder.getAverageSpeedEnc().getDecimal(false, edgeFlags), .1);
        assertEquals(10, encoder.getAverageSpeedEnc().getDecimal(true, edgeFlags), .1);

        encoder.setSpeed(false, edgeFlags, 0);
        assertEquals(0, encoder.getSpeed(edgeFlags), .1);
        assertEquals(10, encoder.getSpeed(true, edgeFlags), .1);
        assertFalse(accessEnc.getBool(false, edgeFlags));
        assertTrue(accessEnc.getBool(true, edgeFlags));
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
        assertTrue(encoder.getAccess(way).isWay());
        IntsRef flags = encoder.handleWayTags(em.createEdgeFlags(), way, EncodingManager.Access.WAY, 0);
        edge.setFlags(flags);
        encoder.applyWayTags(way, edge);
        DecimalEncodedValue curvatureEnc = encoder.getDecimalEncodedValue(EncodingManager.getKey(encoder, "curvature"));
        return curvatureEnc.getDecimal(false, edge.getFlags());
    }
}
