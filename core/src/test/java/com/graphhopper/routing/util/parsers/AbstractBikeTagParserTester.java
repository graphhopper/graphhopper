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

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.OSMParsers;
import com.graphhopper.routing.util.PriorityCode;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.DateFormat;
import java.util.Date;

import static com.graphhopper.routing.util.PriorityCode.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 * @author ratrun
 */
public abstract class AbstractBikeTagParserTester {
    protected EncodingManager encodingManager;
    protected BikeCommonAccessParser accessParser;
    protected BikeCommonAverageSpeedParser speedParser;
    protected BikeCommonPriorityParser priorityParser;
    protected OSMParsers osmParsers;
    protected DecimalEncodedValue priorityEnc;
    protected DecimalEncodedValue avgSpeedEnc;
    protected BooleanEncodedValue accessEnc;

    @BeforeEach
    public void setUp() {
        encodingManager = createEncodingManager();
        accessParser = createAccessParser(encodingManager, new PMap("block_fords=true"));
        speedParser = createAverageSpeedParser(encodingManager);
        priorityParser = createPriorityParser(encodingManager);
        osmParsers = new OSMParsers()
                .addRelationTagParser(relConfig -> new OSMBikeNetworkTagParser(encodingManager.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class), relConfig))
                .addRelationTagParser(relConfig -> new OSMMtbNetworkTagParser(encodingManager.getEnumEncodedValue(MtbNetwork.KEY, RouteNetwork.class), relConfig))
                .addWayTagParser(new OSMSmoothnessParser(encodingManager.getEnumEncodedValue(Smoothness.KEY, Smoothness.class)))
                .addWayTagParser(accessParser).addWayTagParser(speedParser).addWayTagParser(priorityParser);
        priorityEnc = priorityParser.getPriorityEnc();
        avgSpeedEnc = speedParser.getAverageSpeedEnc();
        accessEnc = accessParser.getAccessEnc();
    }

    protected abstract EncodingManager createEncodingManager();

    protected abstract BikeCommonAccessParser createAccessParser(EncodedValueLookup lookup, PMap pMap);

    protected abstract BikeCommonAverageSpeedParser createAverageSpeedParser(EncodedValueLookup lookup);

    protected abstract BikeCommonPriorityParser createPriorityParser(EncodedValueLookup lookup);

    protected void assertPriority(PriorityCode expectedPrio, ReaderWay way) {
        IntsRef relFlags = osmParsers.handleRelationTags(new ReaderRelation(0), osmParsers.createRelationFlags());
        ArrayEdgeIntAccess intAccess = new ArrayEdgeIntAccess(encodingManager.getIntsForFlags());
        int edgeId = 0;
        osmParsers.handleWayTags(edgeId, intAccess, way, relFlags);
        assertEquals(PriorityCode.getValue(expectedPrio.getValue()), priorityEnc.getDecimal(false, edgeId, intAccess), 0.01);
    }

    protected void assertPriorityAndSpeed(PriorityCode expectedPrio, double expectedSpeed, ReaderWay way) {
        assertPriorityAndSpeed(expectedPrio, expectedSpeed, way, new ReaderRelation(0));
    }

    protected void assertPriorityAndSpeed(PriorityCode expectedPrio, double expectedSpeed, ReaderWay way, ReaderRelation rel) {
        IntsRef relFlags = osmParsers.handleRelationTags(rel, osmParsers.createRelationFlags());
        ArrayEdgeIntAccess intAccess = new ArrayEdgeIntAccess(encodingManager.getIntsForFlags());
        int edgeId = 0;
        osmParsers.handleWayTags(edgeId, intAccess, way, relFlags);
        assertEquals(PriorityCode.getValue(expectedPrio.getValue()), priorityEnc.getDecimal(false, edgeId, intAccess), 0.01);
        assertEquals(expectedSpeed, avgSpeedEnc.getDecimal(false, edgeId, intAccess), 0.1);
        assertEquals(expectedSpeed, avgSpeedEnc.getDecimal(true, edgeId, intAccess), 0.1);
    }

    protected double getSpeedFromFlags(ReaderWay way) {
        IntsRef relFlags = osmParsers.createRelationFlags();
        ArrayEdgeIntAccess intAccess = new ArrayEdgeIntAccess(encodingManager.getIntsForFlags());
        int edgeId = 0;
        osmParsers.handleWayTags(edgeId, intAccess, way, relFlags);
        return avgSpeedEnc.getDecimal(false, edgeId, intAccess);
    }

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);

        way.setTag("highway", "motorway");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.setTag("highway", "motorway");
        way.setTag("bicycle", "yes");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("highway", "footway");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("bicycle", "no");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.setTag("highway", "footway");
        way.setTag("bicycle", "yes");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("highway", "pedestrian");
        way.setTag("bicycle", "no");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.setTag("highway", "pedestrian");
        way.setTag("bicycle", "yes");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("bicycle", "yes");
        way.setTag("highway", "cycleway");
        assertTrue(accessParser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "path");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("highway", "path");
        way.setTag("bicycle", "yes");
        assertTrue(accessParser.getAccess(way).isWay());
        way.clearTags();

        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        assertTrue(accessParser.getAccess(way).isWay());
        way.clearTags();

        way.setTag("highway", "track");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("mtb", "yes");
        assertTrue(accessParser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("foot", "official");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("bicycle", "official");
        assertTrue(accessParser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "no");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("motorroad", "yes");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("ford", "yes");
        assertTrue(accessParser.getAccess(way).canSkip());
        way.setTag("bicycle", "yes");
        assertTrue(accessParser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("access", "no");
        assertTrue(accessParser.getAccess(way).canSkip());
        way.setTag("bicycle", "dismount");
        assertTrue(accessParser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("vehicle", "no");
        assertTrue(accessParser.getAccess(way).canSkip());
        way.setTag("bicycle", "dismount");
        assertTrue(accessParser.getAccess(way).isWay());


        way.clearTags();
        way.setTag("highway", "cycleway");
        way.setTag("cycleway", "track");
        way.setTag("railway", "abandoned");
        assertTrue(accessParser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "platform");
        assertTrue(accessParser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "platform");
        way.setTag("bicycle", "dismount");
        assertTrue(accessParser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "platform");
        way.setTag("bicycle", "no");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("vehicle", "forestry");
        assertTrue(accessParser.getAccess(way).canSkip());
        way.setTag("bicycle", "yes");
        assertTrue(accessParser.getAccess(way).isWay());
    }

    @Test
    public void testRelation() {
        ReaderWay way = new ReaderWay(1);

        way.setTag("highway", "track");
        way.setTag("bicycle", "yes");
        way.setTag("foot", "yes");
        way.setTag("motor_vehicle", "agricultural");
        way.setTag("surface", "gravel");
        way.setTag("tracktype", "grade3");

        ReaderRelation rel = new ReaderRelation(0);
        rel.setTag("type", "route");
        rel.setTag("network", "rcn");
        rel.setTag("route", "bicycle");

        ReaderRelation rel2 = new ReaderRelation(1);
        rel2.setTag("type", "route");
        rel2.setTag("network", "lcn");
        rel2.setTag("route", "bicycle");

        // two relation tags => we currently cannot store a list, so pick the lower ordinal 'regional'
        // Example https://www.openstreetmap.org/way/213492914 => two hike 84544, 2768803 and two bike relations 3162932, 5254650
        IntsRef relFlags = osmParsers.handleRelationTags(rel2, osmParsers.handleRelationTags(rel, osmParsers.createRelationFlags()));
        ArrayEdgeIntAccess intAccess = new ArrayEdgeIntAccess(encodingManager.getIntsForFlags());
        int edgeId = 0;
        osmParsers.handleWayTags(edgeId, intAccess, way, relFlags);
        EnumEncodedValue<RouteNetwork> enc = encodingManager.getEnumEncodedValue(RouteNetwork.key("bike"), RouteNetwork.class);
        assertEquals(RouteNetwork.REGIONAL, enc.getEnum(false, edgeId, intAccess));
    }

    @Test
    public void testTramStations() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "rail");
        assertTrue(accessParser.getAccess(way).isWay());

        way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "station");
        assertTrue(accessParser.getAccess(way).isWay());

        way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "station");
        way.setTag("bicycle", "yes");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("bicycle", "no");
        assertTrue(accessParser.getAccess(way).canSkip());

        way = new ReaderWay(1);
        way.setTag("railway", "platform");
        ArrayEdgeIntAccess intAccess = new ArrayEdgeIntAccess(encodingManager.getIntsForFlags());
        int edgeId = 0;
        accessParser.handleWayTags(edgeId, intAccess, way, null);
        speedParser.handleWayTags(edgeId, intAccess, way, null);
        assertEquals(4.0, avgSpeedEnc.getDecimal(false, edgeId, intAccess));
        assertTrue(accessEnc.getBool(false, edgeId, intAccess));

        way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("railway", "platform");
        accessParser.handleWayTags(edgeId, intAccess, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, intAccess));

        speedParser.handleWayTags(edgeId, intAccess, way, null);
        assertEquals(4, avgSpeedEnc.getDecimal(false, edgeId, intAccess));

        way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("railway", "platform");
        way.setTag("bicycle", "no");

        intAccess = new ArrayEdgeIntAccess(encodingManager.getIntsForFlags());
        accessParser.handleWayTags(edgeId, intAccess, way);
        assertEquals(0.0, avgSpeedEnc.getDecimal(false, edgeId, intAccess));
        assertFalse(accessEnc.getBool(false, edgeId, intAccess));
    }

    @Test
    public void testAvoidTunnel() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "residential");
        assertPriority(PREFER, osmWay);

        osmWay.setTag("tunnel", "yes");
        assertPriority(UNCHANGED, osmWay);

        osmWay.setTag("highway", "secondary");
        osmWay.setTag("tunnel", "yes");
        assertPriority(BAD, osmWay);

        osmWay.setTag("bicycle", "designated");
        assertPriority(PREFER, osmWay);
    }

    @Test
    public void testTram() {
        ReaderWay way = new ReaderWay(1);
        // very dangerous
        way.setTag("highway", "secondary");
        way.setTag("railway", "tram");
        assertPriority(AVOID_MORE, way);

        // should be safe now
        way.setTag("bicycle", "designated");
        assertPriority(PREFER, way);
    }

    @Test
    public void testService() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "service");
        assertPriorityAndSpeed(PREFER, 12, way);

        way.setTag("service", "parking_aisle");
        assertPriorityAndSpeed(SLIGHT_AVOID, 4, way);
    }

    @Test
    public void testSteps() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "steps");
        assertPriorityAndSpeed(BAD, 2, way);

        way.setTag("bicycle", "designated");
        assertPriorityAndSpeed(BAD, 2, way);
    }

    @Test
    public void testSacScale() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "service");
        way.setTag("sac_scale", "hiking");
        // allow
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("sac_scale", "alpine_hiking");
        assertTrue(accessParser.getAccess(way).canSkip());
    }

    @Test
    public void testReduceToMaxSpeed() {
        ReaderWay way = new ReaderWay(12);
        way.setTag("maxspeed", "90");
        assertEquals(12, speedParser.applyMaxSpeed(way, 12, true), 1e-2);
    }

    @Test
    public void testPreferenceForSlowSpeed() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "tertiary");
        assertPriority(PREFER, osmWay);
    }

    @Test
    public void testHandleWayTagsCallsHandlePriority() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "cycleway");

        ArrayEdgeIntAccess intAccess = new ArrayEdgeIntAccess(encodingManager.getIntsForFlags());
        int edgeId = 0;
        priorityParser.handleWayTags(edgeId, intAccess, osmWay, null);
        assertEquals(PriorityCode.getValue(VERY_NICE.getValue()), priorityEnc.getDecimal(false, edgeId, intAccess), 1e-3);
    }

    @Test
    public void testLockedGate() {
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("locked", "yes");
        assertTrue(accessParser.isBarrier(node));
        node.setTag("bicycle", "yes");
        assertFalse(accessParser.isBarrier(node));
    }

    @Test
    public void testNoBike() {
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("bicycle", "no");
        assertTrue(accessParser.isBarrier(node));
    }

    @Test
    public void testBarrierAccess() {
        // by default allow access through the gate for bike & foot!
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        // no barrier!
        assertFalse(accessParser.isBarrier(node));

        node.setTag("bicycle", "yes");
        // no barrier!
        assertFalse(accessParser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        // barrier!
        assertTrue(accessParser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "yes");
        node.setTag("bicycle", "no");
        // barrier!
        assertTrue(accessParser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        node.setTag("foot", "yes");
        // barrier!
        assertTrue(accessParser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        node.setTag("bicycle", "yes");
        // no barrier!
        assertFalse(accessParser.isBarrier(node));
    }

    @Test
    public void testBarrierAccessFord() {
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("ford", "yes");
        // barrier!
        assertTrue(accessParser.isBarrier(node));

        node.setTag("bicycle", "yes");
        // no barrier!
        assertFalse(accessParser.isBarrier(node));
    }

    @Test
    public void testFerries() {
        ReaderWay way = new ReaderWay(1);

        way.clearTags();
        way.setTag("route", "ferry");
        assertTrue(accessParser.getAccess(way).isFerry());
        way.setTag("bicycle", "no");
        assertFalse(accessParser.getAccess(way).isFerry());

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "yes");
        assertFalse(accessParser.getAccess(way).isFerry());

        // #1122
        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("bicycle", "yes");
        way.setTag("access", "private");
        assertTrue(accessParser.getAccess(way).canSkip());

        // #1562, test if ferry route with bicycle
        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("bicycle", "designated");
        assertTrue(accessParser.getAccess(way).isFerry());

        way.setTag("bicycle", "official");
        assertTrue(accessParser.getAccess(way).isFerry());

        way.setTag("bicycle", "permissive");
        assertTrue(accessParser.getAccess(way).isFerry());

        way.setTag("foot", "yes");
        assertTrue(accessParser.getAccess(way).isFerry());

        way.setTag("bicycle", "no");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.setTag("bicycle", "designated");
        way.setTag("access", "private");
        assertTrue(accessParser.getAccess(way).canSkip());

        // test if when foot is set is invalid
        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "yes");
        assertTrue(accessParser.getAccess(way).canSkip());
    }

    @Test
    void privateAndFords() {
        // defaults: do not block fords, block private
        BikeCommonAccessParser bike = createAccessParser(encodingManager, new PMap());
        assertFalse(bike.isBlockFords());
        assertTrue(bike.restrictedValues.contains("private"));
        assertFalse(bike.intendedValues.contains("private"));
        ReaderNode node = new ReaderNode(1, 1, 1);
        node.setTag("access", "private");
        assertTrue(bike.isBarrier(node));

        // block fords, unblock private
        bike = createAccessParser(encodingManager, new PMap("block_fords=true|block_private=false"));
        assertTrue(bike.isBlockFords());
        assertFalse(bike.restrictedValues.contains("private"));
        assertTrue(bike.intendedValues.contains("private"));
        assertFalse(bike.isBarrier(node));
    }

    @Test
    public void testOneway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "tertiary");
        assertAccess(way, true, true);

        way.setTag("oneway", "yes");
        assertAccess(way, true, false);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway:bicycle", "yes");
        assertAccess(way, true, false);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("oneway:bicycle", "no");
        assertAccess(way, true, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("oneway:bicycle", "-1");
        assertAccess(way, false, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("cycleway:right:oneway", "no");
        assertAccess(way, true, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("cycleway:right:oneway", "-1");
        assertAccess(way, false, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        assertAccess(way, true, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("vehicle:forward", "no");
        assertAccess(way, false, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("bicycle:forward", "no");
        assertAccess(way, false, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("vehicle:backward", "no");
        assertAccess(way, true, false);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("motor_vehicle:backward", "no");
        assertAccess(way, true, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("bicycle:backward", "no");
        assertAccess(way, true, false);

        way.setTag("bicycle:backward", "yes");
        assertAccess(way, true, true);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("oneway", "yes");
        way.setTag("bicycle:backward", "yes");
        assertAccess(way, true, true);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("oneway", "-1");
        way.setTag("bicycle:forward", "yes");
        assertAccess(way, true, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("bicycle:forward", "use_sidepath");
        assertAccess(way, true, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("bicycle:forward", "use_sidepath");
        assertAccess(way, true, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("cycleway", "opposite");
        assertAccess(way, true, true);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("oneway", "yes");
        way.setTag("cycleway:left", "opposite_lane");
        assertAccess(way, true, true);

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("cycleway:left:oneway", "-1");
        way.setTag("cycleway:both", "track");
        assertAccess(way, true, true);

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("oneway", "yes");
        way.setTag("cycleway:both", "no");
        assertAccess(way, true, false);
    }

    private void assertAccess(ReaderWay way, boolean fwd, boolean bwd) {
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edge = 0;
        IntsRef relationFlags = new IntsRef(1);
        accessParser.handleWayTags(edge, edgeIntAccess, way, relationFlags);
        if (fwd) assertTrue(accessEnc.getBool(false, edge, edgeIntAccess));
        if (bwd) assertTrue(accessEnc.getBool(true, edge, edgeIntAccess));
    }
}
