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

import com.graphhopper.json.GHJson;
import com.graphhopper.json.GHJsonFactory;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.TagParserFactory;
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
    protected GHJson json = new GHJsonFactory().create();
    private final EncodingManager em = new EncodingManager.Builder().addGlobalEncodedValues().addAllFlagEncoders("motorcycle,foot").build();
    private final MotorcycleFlagEncoder encoder = (MotorcycleFlagEncoder) em.getEncoder("motorcycle");
    private BooleanEncodedValue mcAccessEnc;
    private DecimalEncodedValue mcAverageSpeedEnc;
    private DecimalEncodedValue mcCurvatureEnc;

    private Graph initExampleGraph() {
        GraphHopperStorage gs = new GraphHopperStorage(new RAMDirectory(), em, json, true, new GraphExtension.NoOpExtension()).create(1000);
        NodeAccess na = gs.getNodeAccess();
        // 50--(0.0001)-->49--(0.0004)-->55--(0.0005)-->60
        na.setNode(0, 51.1, 12.001, 50);
        na.setNode(1, 51.1, 12.002, 60);
        EdgeIteratorState edge = gs.edge(0, 1).
                setWayGeometry(Helper.createPointList3D(51.1, 12.0011, 49, 51.1, 12.0015, 55));
        edge.setDistance(100);

        mcAccessEnc = em.getBooleanEncodedValue("motorcycle.access");
        mcAverageSpeedEnc = em.getDecimalEncodedValue("motorcycle.average_speed");
        mcCurvatureEnc = em.getDecimalEncodedValue(TagParserFactory.CURVATURE);

        edge.set(mcAverageSpeedEnc, 10d);
        edge.setReverse(mcAverageSpeedEnc, 15d);
        edge.set(mcAccessEnc, true);
        edge.setReverse(mcAccessEnc, true);
        return gs;
    }

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);
        assertFalse(encoder.getAccess(way).isWay());
        way.setTag("highway", "service");
        assertTrue(encoder.getAccess(way).isWay());
        way.setTag("access", "no");
        assertFalse(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "track");
        assertTrue(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "delivery");
        assertFalse(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "unclassified");
        way.setTag("ford", "yes");
        assertFalse(encoder.getAccess(way).isWay());
        way.setTag("motorcycle", "yes");
        assertTrue(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("route", "ferry");
        assertTrue(encoder.getAccess(way).isFerry());
        way.setTag("motorcycle", "no");
        assertFalse(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "yes");
        assertFalse(encoder.getAccess(way).isWay());
        assertFalse(encoder.getAccess(way).isFerry());

        way.clearTags();
        way.setTag("access", "yes");
        way.setTag("motor_vehicle", "no");
        assertFalse(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "yes");
        way.setTag("motor_vehicle", "no");
        assertFalse(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "emergency");
        assertFalse(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("motor_vehicle", "emergency");
        assertFalse(encoder.getAccess(way).isWay());

        DateFormat simpleDateFormat = Helper.createFormatter("yyyy MMM dd");

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access:conditional", "no @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertFalse(encoder.getAccess(way).isWay());

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
        IntsRef result = encoder.handleWayTags(em.createIntsRef(), way, encoder.getAccess(way), 0);
        assertEquals(20, encoder.getSpeed(result), .1);
        assertEquals(20, encoder.getReverseSpeed(result), .1);
    }

    @Test
    public void testSetSpeed0_issue367() {
        initExampleGraph();
        IntsRef flags = em.createIntsRef();
        mcAccessEnc.setBool(false, flags, true);
        mcAccessEnc.setBool(true, flags, true);
        mcAverageSpeedEnc.setDecimal(false, flags, 10d);
        mcAverageSpeedEnc.setDecimal(true, flags, 10d);
        encoder.setSpeed(flags, 0);

        assertEquals(0, encoder.getSpeed(flags), .1);
        assertEquals(10, encoder.getReverseSpeed(flags), .1);
        assertFalse(mcAccessEnc.getBool(false, flags));
        assertTrue(mcAccessEnc.getBool(true, flags));
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
        IntsRef ints = em.createIntsRef();
        encoder.handleWayTags(ints, way, encoder.getAccess(way), 0l);
        edge.setData(ints);
        encoder.applyWayTags(way, edge);
        return edge.get(mcCurvatureEnc);
    }
}
