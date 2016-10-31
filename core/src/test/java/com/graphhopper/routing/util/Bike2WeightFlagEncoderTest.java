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
import com.graphhopper.util.*;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class Bike2WeightFlagEncoderTest extends BikeFlagEncoderTest {
    private final EncodingManager em = new EncodingManager("bike,bike2");

    @Override
    protected BikeCommonFlagEncoder createBikeEncoder() {
        return (BikeCommonFlagEncoder) em.getEncoder("bike2");
    }

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
    public void testApplyWayTags() {
        Graph graph = initExampleGraph();
        EdgeIteratorState edge = GHUtility.getEdge(graph, 0, 1);
        ReaderWay way = new ReaderWay(1);
        encoder.applyWayTags(way, edge);

        long flags = edge.getFlags();
        // decrease speed
        assertEquals(2, encoder.getSpeed(flags), 1e-1);
        // increase speed but use maximum speed (calculated was 24)
        assertEquals(18, encoder.getReverseSpeed(flags), 1e-1);
    }

    @Test
    public void testUnchangedForStepsBridgeAndTunnel() {
        Graph graph = initExampleGraph();
        EdgeIteratorState edge = GHUtility.getEdge(graph, 0, 1);
        long oldFlags = edge.getFlags();
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "steps");
        encoder.applyWayTags(way, edge);

        assertEquals(oldFlags, edge.getFlags());
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
    public void testRoutingFailsWithInvalidGraph_issue665() {
        GraphHopperStorage graph = new GraphHopperStorage(
                new RAMDirectory(), em, true, new GraphExtension.NoOpExtension());
        graph.create(100);

        ReaderWay way = new ReaderWay(0);
        way.setTag("route", "ferry");

        long includeWay = em.acceptWay(way);
        long relationFlags = 0;
        long wayFlags = em.handleWayTags(way, includeWay, relationFlags);
        graph.edge(0, 1).setDistance(247).setFlags(wayFlags);

        assertTrue(isGraphValid(graph, encoder));
    }

    private boolean isGraphValid(Graph graph, FlagEncoder encoder) {
        EdgeExplorer explorer = graph.createEdgeExplorer();

        // iterator at node 0 considers the edge 0-1 to be undirected
        EdgeIterator iter0 = explorer.setBaseNode(0);
        iter0.next();
        boolean iter0flag
                = iter0.getBaseNode() == 0 && iter0.getAdjNode() == 1
                && iter0.isForward(encoder) && iter0.isBackward(encoder);

        // iterator at node 1 considers the edge 1-0 to be directed
        EdgeIterator iter1 = explorer.setBaseNode(1);
        iter1.next();
        boolean iter1flag
                = iter1.getBaseNode() == 1 && iter1.getAdjNode() == 0
                && iter1.isForward(encoder) && iter1.isBackward(encoder);

        return iter0flag && iter1flag;
    }
}
