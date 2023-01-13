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
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.core.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class MotorcycleTagParserTest {
    private final BooleanEncodedValue motorcycleAccessEnc = VehicleAccess.create("motorcycle");
    private final DecimalEncodedValue motorcycleSpeedEnc = VehicleSpeed.create("motorcycle", 5, 5, true);
    private final DecimalEncodedValue motorcyclePriorityEnc = VehiclePriority.create("motorcycle", 4, PriorityCode.getFactor(1), false);
    private final DecimalEncodedValue motorcycleCurvatureEnc = new DecimalEncodedValueImpl("motorcycle_curvature", 4, 0.1, false);
    private final BooleanEncodedValue footAccessEnc = VehicleAccess.create("foot");
    private final DecimalEncodedValue footSpeedEnc = VehicleSpeed.create("foot", 4, 1, false);
    private final EncodingManager em = EncodingManager.start()
            .add(motorcycleAccessEnc).add(motorcycleSpeedEnc).add(motorcyclePriorityEnc).add(motorcycleCurvatureEnc)
            .add(footAccessEnc).add(footSpeedEnc)
            .build();
    private final MotorcycleTagParser parser;

    public MotorcycleTagParserTest() {
        parser = new MotorcycleTagParser(em, new PMap());
        parser.init(new DateRangeParser());
    }

    private Graph initExampleGraph() {
        BaseGraph gs = new BaseGraph.Builder(em).set3D(true).create();
        NodeAccess na = gs.getNodeAccess();
        // 50--(0.0001)-->49--(0.0004)-->55--(0.0005)-->60
        na.setNode(0, 51.1, 12.001, 50);
        na.setNode(1, 51.1, 12.002, 60);
        EdgeIteratorState edge = gs.edge(0, 1).
                setWayGeometry(Helper.createPointList3D(51.1, 12.0011, 49, 51.1, 12.0015, 55));
        edge.setDistance(100);

        edge.set(motorcycleAccessEnc, true, true).set(motorcycleSpeedEnc, 10.0, 15.0);
        return gs;
    }

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);
        assertTrue(parser.getAccess(way).canSkip());
        way.setTag("highway", "service");
        assertTrue(parser.getAccess(way).isWay());
        way.setTag("access", "no");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "track");
        assertTrue(parser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "delivery");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "unclassified");
        way.setTag("ford", "yes");
        assertTrue(parser.getAccess(way).isWay());
        way.setTag("motorcycle", "no");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("route", "ferry");
        assertTrue(parser.getAccess(way).isFerry());
        way.setTag("motorcycle", "no");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "yes");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("access", "yes");
        way.setTag("motor_vehicle", "no");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "yes");
        way.setTag("motor_vehicle", "no");
        assertTrue(parser.getAccess(way).canSkip());
        way.setTag("motor_vehicle", "agricultural;forestry");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "emergency");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("motor_vehicle", "emergency");
        assertTrue(parser.getAccess(way).canSkip());

        DateFormat simpleDateFormat = Helper.createFormatter("yyyy MMM dd");

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access:conditional", "no @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access", "no");
        way.setTag("access:conditional", "yes @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertTrue(parser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("service", "emergency_access");
        assertTrue(parser.getAccess(way).canSkip());
    }

    @Test
    public void testHandleWayTags() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "service");
        assertTrue(parser.getAccess(way).isWay());
        IntsRef edgeFlags = parser.handleWayTags(em.createEdgeFlags(), way);
        assertEquals(20, parser.avgSpeedEnc.getDecimal(false, edgeFlags), .1);
        assertEquals(20, parser.avgSpeedEnc.getDecimal(true, edgeFlags), .1);
    }

    @Test
    public void testSetSpeed0_issue367() {
        IntsRef edgeFlags = em.createEdgeFlags();
        motorcycleAccessEnc.setBool(false, edgeFlags, true);
        motorcycleAccessEnc.setBool(true, edgeFlags, true);
        parser.getAverageSpeedEnc().setDecimal(false, edgeFlags, 10);
        parser.getAverageSpeedEnc().setDecimal(true, edgeFlags, 10);

        assertEquals(10, parser.getAverageSpeedEnc().getDecimal(false, edgeFlags), .1);
        assertEquals(10, parser.getAverageSpeedEnc().getDecimal(true, edgeFlags), .1);

        parser.setSpeed(false, edgeFlags, 0);
        assertEquals(0, parser.avgSpeedEnc.getDecimal(false, edgeFlags), .1);
        assertEquals(10, parser.avgSpeedEnc.getDecimal(true, edgeFlags), .1);
        assertFalse(motorcycleAccessEnc.getBool(false, edgeFlags));
        assertTrue(motorcycleAccessEnc.getBool(true, edgeFlags));
    }

    @ParameterizedTest
    @ValueSource(strings = {"mofa", "moped", "motorcar", "motor_vehicle", "motorcycle"})
    void footway_etc_not_allowed_despite_vehicle_yes(String vehicle) {
        // these highways are blocked, even when we set one of the vehicles to yes
        for (String highway : Arrays.asList("footway", "cycleway", "steps", "pedestrian")) {
            ReaderWay way = new ReaderWay(1);
            way.setTag("highway", highway);
            way.setTag(vehicle, "yes");
            assertEquals(WayAccess.CAN_SKIP, parser.getAccess(way));
        }
    }
}
