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
package com.graphhopper.reader.dem;

import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Alexey Valikov
 */
public class TunnelElevationInterpolatorTest extends EdgeElevationInterpolatorTest {

    @Override
    protected ReaderWay createInterpolatableWay() {
        ReaderWay tunnelWay = new ReaderWay(0);
        tunnelWay.setTag("highway", "primary");
        tunnelWay.setTag("tunnel", "yes");
        return tunnelWay;
    }

    @Override
    protected EdgeElevationInterpolator createEdgeElevationInterpolator() {
        return new EdgeElevationInterpolator(graph, roadEnvEnc, RoadEnvironment.TUNNEL);
    }

    @Test
    public void doesNotInterpolateElevationOfTunnelWithZeroOuterNodes() {

        // @formatter:off
        /*
         * Graph structure:
         * 0--T--1--T--2     3--T--4
         * Tunnel 0-1-2 has a single outer node 2.
         */
        // @formatter:on
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0, 0, 0);
        na.setNode(1, 10, 0, 0);
        na.setNode(2, 20, 0, 10);
        na.setNode(3, 30, 0, 20);
        na.setNode(4, 40, 0, 0);

        FlagEncoder encoder = encodingManager.getEncoder("car");
        EdgeIteratorState edge01 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(10));
        EdgeIteratorState edge12 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 2).setDistance(10));
        EdgeIteratorState edge34 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 4).setDistance(10));

        edge01.setFlags(encodingManager.handleWayTags(interpolatableWay, ACCEPT_WAY, relFlags));
        edge12.setFlags(encodingManager.handleWayTags(interpolatableWay, ACCEPT_WAY, relFlags));
        edge34.setFlags(encodingManager.handleWayTags(interpolatableWay, ACCEPT_WAY, relFlags));

        final GHIntHashSet outerNodeIds = new GHIntHashSet();
        final GHIntHashSet innerNodeIds = new GHIntHashSet();
        gatherOuterAndInnerNodeIdsOfStructure(edge01, outerNodeIds, innerNodeIds);

        assertEquals(GHIntHashSet.from(), outerNodeIds);
        assertEquals(GHIntHashSet.from(0, 1, 2), innerNodeIds);

        edgeElevationInterpolator.execute();
        assertEquals(0, na.getEle(0), PRECISION);
        assertEquals(0, na.getEle(1), PRECISION);
        assertEquals(10, na.getEle(2), PRECISION);
        assertEquals(20, na.getEle(3), PRECISION);
        assertEquals(0, na.getEle(4), PRECISION);
    }

    @Test
    public void interpolatesElevationOfTunnelWithSingleOuterNode() {

        // @formatter:off
        /*
         * Graph structure:
         * 0--T--1--T--2-----3--T--4
         */
        // @formatter:on
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0, 0, 0);
        na.setNode(1, 10, 0, 00);
        na.setNode(2, 20, 0, 10);
        na.setNode(3, 30, 0, 20);
        na.setNode(4, 40, 0, 00);

        FlagEncoder encoder = encodingManager.getEncoder("car");
        EdgeIteratorState edge01 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(10));
        EdgeIteratorState edge12 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 2).setDistance(10));
        EdgeIteratorState edge23 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 3).setDistance(10));
        EdgeIteratorState edge34 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 4).setDistance(10));

        edge01.setFlags(encodingManager.handleWayTags(interpolatableWay, ACCEPT_WAY, relFlags));
        edge12.setFlags(encodingManager.handleWayTags(interpolatableWay, ACCEPT_WAY, relFlags));
        edge23.setFlags(encodingManager.handleWayTags(normalWay, ACCEPT_WAY, relFlags));
        edge34.setFlags(encodingManager.handleWayTags(interpolatableWay, ACCEPT_WAY, relFlags));

        final GHIntHashSet outerNodeIds = new GHIntHashSet();
        final GHIntHashSet innerNodeIds = new GHIntHashSet();
        gatherOuterAndInnerNodeIdsOfStructure(edge01, outerNodeIds, innerNodeIds);

        assertEquals(GHIntHashSet.from(2), outerNodeIds);
        assertEquals(GHIntHashSet.from(0, 1), innerNodeIds);

        edgeElevationInterpolator.execute();
        assertEquals(10, na.getEle(0), PRECISION);
        assertEquals(10, na.getEle(1), PRECISION);
        assertEquals(10, na.getEle(2), PRECISION);
        assertEquals(20, na.getEle(3), PRECISION);
        assertEquals(20, na.getEle(4), PRECISION);
    }

    @Test
    public void interpolatesElevationOfTunnelWithTwoOuterNodes() {

        // @formatter:off
        /*
         * Graph structure:
         * 0-----1--T--2--T--3-----4
         */
        // @formatter:on
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0, 0, 0);
        na.setNode(1, 10, 0, 10);
        na.setNode(2, 20, 0, 1000);
        na.setNode(3, 30, 0, 30);
        na.setNode(4, 40, 0, 40);

        FlagEncoder encoder = encodingManager.getEncoder("car");
        EdgeIteratorState edge01 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(10));
        EdgeIteratorState edge12 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 2).setDistance(10));
        EdgeIteratorState edge23 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 3).setDistance(10));
        EdgeIteratorState edge34 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 4).setDistance(10));

        edge01.setFlags(encodingManager.handleWayTags(normalWay, ACCEPT_WAY, relFlags));
        edge12.setFlags(encodingManager.handleWayTags(interpolatableWay, ACCEPT_WAY, relFlags));
        edge23.setFlags(encodingManager.handleWayTags(interpolatableWay, ACCEPT_WAY, relFlags));
        edge34.setFlags(encodingManager.handleWayTags(normalWay, ACCEPT_WAY, relFlags));

        final GHIntHashSet outerNodeIds = new GHIntHashSet();
        final GHIntHashSet innerNodeIds = new GHIntHashSet();
        gatherOuterAndInnerNodeIdsOfStructure(edge12, outerNodeIds, innerNodeIds);

        assertEquals(GHIntHashSet.from(1, 3), outerNodeIds);
        assertEquals(GHIntHashSet.from(2), innerNodeIds);

        edgeElevationInterpolator.execute();
        assertEquals(0, na.getEle(0), PRECISION);
        assertEquals(10, na.getEle(1), PRECISION);
        assertEquals(20, na.getEle(2), PRECISION);
        assertEquals(30, na.getEle(3), PRECISION);
        assertEquals(40, na.getEle(4), PRECISION);
    }

    @Test
    public void interpolatesElevationOfTunnelWithThreeOuterNodes() {

        // @formatter:off
        /*
         * Graph structure:
         * 0-----1--T--2--T--3-----4
         *             |
         *             |
         *             T
         *             |
         *             |
         *             5--T--6-----7
         */
        // @formatter:on
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0, 0, 0);
        na.setNode(1, 10, 0, 10);
        na.setNode(2, 20, 0, 1000);
        na.setNode(3, 30, 0, 30);
        na.setNode(4, 40, 0, 40);
        na.setNode(5, 20, 10, 1000);
        na.setNode(6, 30, 10, 30);
        na.setNode(7, 40, 10, 40);

        FlagEncoder encoder = encodingManager.getEncoder("car");
        EdgeIteratorState edge01 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(10));
        EdgeIteratorState edge12 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 2).setDistance(10));
        EdgeIteratorState edge23 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 3).setDistance(10));
        EdgeIteratorState edge34 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 4).setDistance(10));
        EdgeIteratorState edge25 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 5).setDistance(10));
        EdgeIteratorState edge56 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(5, 6).setDistance(10));
        EdgeIteratorState edge67 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(6, 7).setDistance(10));

        edge01.setFlags(encodingManager.handleWayTags(normalWay, ACCEPT_WAY, relFlags));
        edge12.setFlags(encodingManager.handleWayTags(interpolatableWay, ACCEPT_WAY, relFlags));
        edge23.setFlags(encodingManager.handleWayTags(interpolatableWay, ACCEPT_WAY, relFlags));
        edge34.setFlags(encodingManager.handleWayTags(normalWay, ACCEPT_WAY, relFlags));
        edge25.setFlags(encodingManager.handleWayTags(interpolatableWay, ACCEPT_WAY, relFlags));
        edge56.setFlags(encodingManager.handleWayTags(interpolatableWay, ACCEPT_WAY, relFlags));
        edge67.setFlags(encodingManager.handleWayTags(normalWay, ACCEPT_WAY, relFlags));

        final GHIntHashSet outerNodeIds = new GHIntHashSet();
        final GHIntHashSet innerNodeIds = new GHIntHashSet();
        gatherOuterAndInnerNodeIdsOfStructure(edge12, outerNodeIds, innerNodeIds);

        assertEquals(GHIntHashSet.from(1, 3, 6), outerNodeIds);
        assertEquals(GHIntHashSet.from(2, 5), innerNodeIds);

        edgeElevationInterpolator.execute();
        assertEquals(0, na.getEle(0), PRECISION);
        assertEquals(10, na.getEle(1), PRECISION);
        assertEquals(20, na.getEle(2), PRECISION);
        assertEquals(30, na.getEle(3), PRECISION);
        assertEquals(40, na.getEle(4), PRECISION);
        assertEquals(20, na.getEle(5), PRECISION);
        assertEquals(30, na.getEle(6), PRECISION);
        assertEquals(40, na.getEle(7), PRECISION);
    }

    @Test
    public void interpolatesElevationOfTunnelWithFourOuterNodes() {

        // @formatter:off
        /*
         * Graph structure:
         * 0-----1--T--2--T--3-----4
         *             |
         *             |
         *             T
         *             |
         *             |
         * 5-----6--T--7--T--8-----9
         */
        // @formatter:on
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0, 0, 0);
        na.setNode(1, 10, 0, 10);
        na.setNode(2, 20, 0, 1000);
        na.setNode(3, 30, 0, 30);
        na.setNode(4, 40, 0, 40);
        na.setNode(5, 0, 10, 40);
        na.setNode(6, 10, 10, 30);
        na.setNode(7, 20, 10, 1000);
        na.setNode(8, 30, 10, 10);
        na.setNode(9, 40, 10, 0);

        FlagEncoder encoder = encodingManager.getEncoder("car");
        EdgeIteratorState edge01, edge12, edge23, edge34, edge56, edge67, edge78, edge89, edge27;
        GHUtility.setSpeed(60, 60, encoder,
                edge01 = graph.edge(0, 1).setDistance(10),
                edge12 = graph.edge(1, 2).setDistance(10),
                edge23 = graph.edge(2, 3).setDistance(10),
                edge34 = graph.edge(3, 4).setDistance(10),
                edge56 = graph.edge(5, 6).setDistance(10),
                edge67 = graph.edge(6, 7).setDistance(10),
                edge78 = graph.edge(7, 8).setDistance(10),
                edge89 = graph.edge(8, 9).setDistance(10),
                edge27 = graph.edge(2, 7).setDistance(10));

        edge01.setFlags(encodingManager.handleWayTags(normalWay, ACCEPT_WAY, relFlags));
        edge12.setFlags(encodingManager.handleWayTags(interpolatableWay, ACCEPT_WAY, relFlags));
        edge23.setFlags(encodingManager.handleWayTags(interpolatableWay, ACCEPT_WAY, relFlags));
        edge34.setFlags(encodingManager.handleWayTags(normalWay, ACCEPT_WAY, relFlags));

        edge56.setFlags(encodingManager.handleWayTags(normalWay, ACCEPT_WAY, relFlags));
        edge67.setFlags(encodingManager.handleWayTags(interpolatableWay, ACCEPT_WAY, relFlags));
        edge78.setFlags(encodingManager.handleWayTags(interpolatableWay, ACCEPT_WAY, relFlags));
        edge89.setFlags(encodingManager.handleWayTags(normalWay, ACCEPT_WAY, relFlags));

        edge27.setFlags(encodingManager.handleWayTags(interpolatableWay, ACCEPT_WAY, relFlags));

        final GHIntHashSet outerNodeIds = new GHIntHashSet();
        final GHIntHashSet innerNodeIds = new GHIntHashSet();
        gatherOuterAndInnerNodeIdsOfStructure(edge12, outerNodeIds, innerNodeIds);

        assertEquals(GHIntHashSet.from(1, 3, 6, 8), outerNodeIds);
        assertEquals(GHIntHashSet.from(2, 7), innerNodeIds);

        edgeElevationInterpolator.execute();
        assertEquals(0, na.getEle(0), PRECISION);
        assertEquals(10, na.getEle(1), PRECISION);
        assertEquals(20, na.getEle(2), PRECISION);
        assertEquals(30, na.getEle(3), PRECISION);
        assertEquals(40, na.getEle(4), PRECISION);
        assertEquals(40, na.getEle(5), PRECISION);
        assertEquals(30, na.getEle(6), PRECISION);
        assertEquals(20, na.getEle(7), PRECISION);
        assertEquals(10, na.getEle(8), PRECISION);
        assertEquals(0, na.getEle(9), PRECISION);
    }
}
