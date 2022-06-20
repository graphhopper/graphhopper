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
import com.graphhopper.routing.util.parsers.OSMBikeNetworkTagParser;
import com.graphhopper.routing.util.parsers.OSMSmoothnessParser;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class Bike2WeightTagParserTest extends BikeTagParserTest {

    @Override
    protected EncodingManager createEncodingManager() {
        return EncodingManager.create("bike2");
    }

    @Override
    protected BikeCommonTagParser createBikeTagParser(EncodedValueLookup lookup, PMap pMap) {
        Bike2WeightTagParser parser = new Bike2WeightTagParser(lookup, pMap);
        parser.init(new DateRangeParser());
        return parser;
    }

    @Override
    protected OSMParsers createOSMParsers(BikeCommonTagParser parser, EncodedValueLookup lookup) {
        return new OSMParsers()
                .addRelationTagParser(relConfig -> new OSMBikeNetworkTagParser(lookup.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class), relConfig))
                .addWayTagParser(new OSMSmoothnessParser(lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness.class)))
                .addVehicleTagParser(parser);
    }

    private Graph initExampleGraph() {
        BaseGraph gs = new BaseGraph.Builder(encodingManager).set3D(true).create();
        NodeAccess na = gs.getNodeAccess();
        // 50--(0.0001)-->49--(0.0004)-->55--(0.0005)-->60
        na.setNode(0, 51.1, 12.001, 50);
        na.setNode(1, 51.1, 12.002, 60);
        EdgeIteratorState edge = gs.edge(0, 1).
                setDistance(100).
                setWayGeometry(Helper.createPointList3D(51.1, 12.0011, 49, 51.1, 12.0015, 55));
        edge.set(avgSpeedEnc, 10, 15);
        edge.set(parser.getAccessEnc(), true, true);
        return gs;
    }

    @Test
    public void testApplyWayTags() {
        Graph graph = initExampleGraph();
        EdgeIteratorState edge = GHUtility.getEdge(graph, 0, 1);
        ReaderWay way = new ReaderWay(1);
        parser.applyWayTags(way, edge);

        IntsRef flags = edge.getFlags();
        // decrease speed
        assertEquals(2, avgSpeedEnc.getDecimal(false, flags), 1e-1);
        // increase speed but use maximum speed (calculated was 24)
        assertEquals(18, avgSpeedEnc.getDecimal(true, flags), 1e-1);
    }

    @Test
    public void testUnchangedForStepsBridgeAndTunnel() {
        Graph graph = initExampleGraph();
        EdgeIteratorState edge = GHUtility.getEdge(graph, 0, 1);
        IntsRef oldFlags = IntsRef.deepCopyOf(edge.getFlags());
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "steps");
        parser.applyWayTags(way, edge);

        assertEquals(oldFlags, edge.getFlags());
    }

    @Test
    public void testSetSpeed0_issue367() {
        IntsRef edgeFlags = encodingManager.createEdgeFlags();
        avgSpeedEnc.setDecimal(false, edgeFlags, 10);
        avgSpeedEnc.setDecimal(true, edgeFlags, 10);
        parser.getAccessEnc().setBool(false, edgeFlags, true);
        parser.getAccessEnc().setBool(true, edgeFlags, true);

        parser.setSpeed(false, edgeFlags, 0);

        assertEquals(0, avgSpeedEnc.getDecimal(false, edgeFlags), .1);
        assertEquals(10, avgSpeedEnc.getDecimal(true, edgeFlags), .1);
        assertFalse(parser.getAccessEnc().getBool(false, edgeFlags));
        assertTrue(parser.getAccessEnc().getBool(true, edgeFlags));
    }

    @Test
    public void testRoutingFailsWithInvalidGraph_issue665() {
        BaseGraph graph = new BaseGraph.Builder(encodingManager).set3D(true).create();
        ReaderWay way = new ReaderWay(0);
        way.setTag("route", "ferry");
        way.setTag("edge_distance", 500.0);

        assertNotEquals(WayAccess.CAN_SKIP, parser.getAccess(way));
        IntsRef edgeFlags = encodingManager.createEdgeFlags();
        edgeFlags = osmParsers.handleWayTags(edgeFlags, way, encodingManager.createRelationFlags());
        graph.edge(0, 1).setDistance(247).setFlags(edgeFlags);

        assertTrue(isGraphValid(graph, parser.getAccessEnc()));
    }

    private boolean isGraphValid(Graph graph, BooleanEncodedValue accessEnc) {
        EdgeExplorer explorer = graph.createEdgeExplorer();
        // iterator at node 0 considers the edge 0-1 to be undirected
        EdgeIterator iter0 = explorer.setBaseNode(0);
        iter0.next();
        boolean iter0flag
                = iter0.getBaseNode() == 0 && iter0.getAdjNode() == 1
                && iter0.get(accessEnc) && iter0.getReverse(accessEnc);

        // iterator at node 1 considers the edge 1-0 to be directed
        EdgeIterator iter1 = explorer.setBaseNode(1);
        iter1.next();
        boolean iter1flag
                = iter1.getBaseNode() == 1 && iter1.getAdjNode() == 0
                && iter1.get(accessEnc) && iter1.getReverse(accessEnc);

        return iter0flag && iter1flag;
    }
}
