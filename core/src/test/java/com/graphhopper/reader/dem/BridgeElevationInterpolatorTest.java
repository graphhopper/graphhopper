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

import com.graphhopper.core.util.Helper;
import com.graphhopper.core.util.PointList;
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Alexey Valikov
 */
public class BridgeElevationInterpolatorTest extends EdgeElevationInterpolatorTest {

    @Override
    protected RoadEnvironment getInterpolatableRoadEnvironment() {
        return RoadEnvironment.BRIDGE;
    }

    @Override
    protected EdgeElevationInterpolator createEdgeElevationInterpolator() {
        return new EdgeElevationInterpolator(graph, roadEnvEnc, RoadEnvironment.BRIDGE);
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

        EdgeIteratorState edge01, edge12, edge23, edge34, edge56, edge67, edge78, edge89, edge17, edge27, edge37;
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc,
                edge01 = graph.edge(0, 1).setDistance(10),
                edge12 = graph.edge(1, 2).setDistance(10),
                edge23 = graph.edge(2, 3).setDistance(10),
                edge34 = graph.edge(3, 4).setDistance(10),
                edge56 = graph.edge(5, 6).setDistance(10),
                edge67 = graph.edge(6, 7).setDistance(10),
                edge78 = graph.edge(7, 8).setDistance(10),
                edge89 = graph.edge(8, 9).setDistance(10),
                edge17 = graph.edge(1, 7).setDistance(10),
                edge27 = graph.edge(2, 7).setDistance(10),
                edge37 = graph.edge(3, 7).setDistance(10));

        edge17.setWayGeometry(Helper.createPointList3D(12, 2, 200, 14, 4, 400, 16, 6, 600, 18, 8, 800));

        edge01.set(roadEnvEnc, normalRoadEnvironment);
        edge12.set(roadEnvEnc, normalRoadEnvironment);
        edge23.set(roadEnvEnc, normalRoadEnvironment);
        edge34.set(roadEnvEnc, normalRoadEnvironment);

        edge56.set(roadEnvEnc, normalRoadEnvironment);
        edge67.set(roadEnvEnc, interpolatableRoadEnvironment);
        edge78.set(roadEnvEnc, interpolatableRoadEnvironment);
        edge89.set(roadEnvEnc, normalRoadEnvironment);

        edge17.set(roadEnvEnc, interpolatableRoadEnvironment);
        edge27.set(roadEnvEnc, interpolatableRoadEnvironment);
        edge37.set(roadEnvEnc, interpolatableRoadEnvironment);

        final GHIntHashSet outerNodeIds = new GHIntHashSet();
        final GHIntHashSet innerNodeIds = new GHIntHashSet();
        gatherOuterAndInnerNodeIdsOfStructure(edge27, outerNodeIds, innerNodeIds);

        assertEquals(GHIntHashSet.from(1, 2, 3, 6, 8), outerNodeIds);
        assertEquals(GHIntHashSet.from(7), innerNodeIds);

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

        final PointList edge17PointList = edge17.fetchWayGeometry(FetchMode.ALL);
        assertEquals(6, edge17PointList.size());
        assertEquals(10, edge17PointList.getEle(0), PRECISION);
        assertEquals(12, edge17PointList.getEle(1), PRECISION);
        assertEquals(14, edge17PointList.getEle(2), PRECISION);
        assertEquals(16, edge17PointList.getEle(3), PRECISION);
        assertEquals(18, edge17PointList.getEle(4), PRECISION);
        assertEquals(20, edge17PointList.getEle(5), PRECISION);
    }
}
