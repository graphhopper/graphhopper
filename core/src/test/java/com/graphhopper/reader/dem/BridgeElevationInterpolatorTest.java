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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;

/**
 * @author Alexey Valikov
 */
public class BridgeElevationInterpolatorTest extends AbstractEdgeElevationInterpolatorTest {

    @Override
    protected ReaderWay createInterpolatableWay() {
        ReaderWay bridgeWay = new ReaderWay(0);
        bridgeWay.setTag("highway", "primary");
        bridgeWay.setTag("bridge", "yes");
        return bridgeWay;
    }

    @Override
    protected AbstractEdgeElevationInterpolator createEdgeElevationInterpolator() {
        return new BridgeElevationInterpolator(graph, dataFlagEncoder);
    }

    @Test
    public void interpolatesElevationOfPillarNodes() {

        // @formatter:off
        /*
         * Graph structure:
		 * 0-----1-----2-----3-----4
		 *        \    |    /
		 *         \   |   /
		 *          T  T  T
		 *           \ | /
		 *            \|/
		 * 5-----6--T--7--T--8-----9
         */
        // @formatter:on
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0, 0, 0);
        na.setNode(1, 10, 0, 10);
        na.setNode(2, 20, 0, 20);
        na.setNode(3, 30, 0, 30);
        na.setNode(4, 40, 0, 40);
        na.setNode(5, 0, 10, 40);
        na.setNode(6, 10, 10, 30);
        na.setNode(7, 20, 10, 1000);
        na.setNode(8, 30, 10, 10);
        na.setNode(9, 40, 10, 0);

        EdgeIteratorState edge01 = graph.edge(0, 1, 10, true);
        EdgeIteratorState edge12 = graph.edge(1, 2, 10, true);
        EdgeIteratorState edge23 = graph.edge(2, 3, 10, true);
        EdgeIteratorState edge34 = graph.edge(3, 4, 10, true);
        EdgeIteratorState edge56 = graph.edge(5, 6, 10, true);
        EdgeIteratorState edge67 = graph.edge(6, 7, 10, true);
        EdgeIteratorState edge78 = graph.edge(7, 8, 10, true);
        EdgeIteratorState edge89 = graph.edge(8, 9, 10, true);
        EdgeIteratorState edge17 = graph.edge(1, 7, 10, true);
        EdgeIteratorState edge27 = graph.edge(2, 7, 10, true);
        EdgeIteratorState edge37 = graph.edge(3, 7, 10, true);
        edge17.setWayGeometry(
                Helper.createPointList3D(12, 2, 200, 14, 4, 400, 16, 6, 600, 18, 8, 800));

        edge01.setFlags(dataFlagEncoder.handleWayTags(normalWay, 1, 0));
        edge12.setFlags(dataFlagEncoder.handleWayTags(normalWay, 1, 0));
        edge23.setFlags(dataFlagEncoder.handleWayTags(normalWay, 1, 0));
        edge34.setFlags(dataFlagEncoder.handleWayTags(normalWay, 1, 0));

        edge56.setFlags(dataFlagEncoder.handleWayTags(normalWay, 1, 0));
        edge67.setFlags(dataFlagEncoder.handleWayTags(interpolatableWay, 1, 0));
        edge78.setFlags(dataFlagEncoder.handleWayTags(interpolatableWay, 1, 0));
        edge89.setFlags(dataFlagEncoder.handleWayTags(normalWay, 1, 0));

        edge17.setFlags(dataFlagEncoder.handleWayTags(interpolatableWay, 1, 0));
        edge27.setFlags(dataFlagEncoder.handleWayTags(interpolatableWay, 1, 0));
        edge37.setFlags(dataFlagEncoder.handleWayTags(interpolatableWay, 1, 0));

        final GHIntHashSet outerNodeIds = new GHIntHashSet();
        final GHIntHashSet innerNodeIds = new GHIntHashSet();
        gatherOuterAndInnerNodeIdsOfStructure(edge27, outerNodeIds, innerNodeIds);

        assertEquals(GHIntHashSet.from(1, 2, 3, 6, 8), outerNodeIds);
        assertEquals(GHIntHashSet.from(7), innerNodeIds);

        edgeElevationInterpolator.execute();
        assertEquals(0, na.getElevation(0), PRECISION);
        assertEquals(10, na.getElevation(1), PRECISION);
        assertEquals(20, na.getElevation(2), PRECISION);
        assertEquals(30, na.getElevation(3), PRECISION);
        assertEquals(40, na.getElevation(4), PRECISION);
        assertEquals(40, na.getElevation(5), PRECISION);
        assertEquals(30, na.getElevation(6), PRECISION);
        assertEquals(20, na.getElevation(7), PRECISION);
        assertEquals(10, na.getElevation(8), PRECISION);
        assertEquals(0, na.getElevation(9), PRECISION);

        final PointList edge17PointList = edge17.fetchWayGeometry(3);
        assertEquals(6, edge17PointList.size());
        assertEquals(10, edge17PointList.getEle(0), PRECISION);
        assertEquals(12, edge17PointList.getEle(1), PRECISION);
        assertEquals(14, edge17PointList.getEle(2), PRECISION);
        assertEquals(16, edge17PointList.getEle(3), PRECISION);
        assertEquals(18, edge17PointList.getEle(4), PRECISION);
        assertEquals(20, edge17PointList.getEle(5), PRECISION);
    }
}
