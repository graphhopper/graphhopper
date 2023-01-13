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

package com.graphhopper.routing;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.SimpleBooleanEncodedValue;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.core.util.DistanceCalcEuclidean;
import com.graphhopper.core.util.EdgeIteratorState;
import com.graphhopper.core.util.GHUtility;
import com.graphhopper.util.Helper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeadingResolverTest {

    @Test
    public void straightEdges() {
        //    0 1 2
        //     \|/
        // 7 -- 8 --- 3
        //     /|\
        //    6 5 4
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).create();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 49.5073, 1.5545);
        na.setNode(1, 49.5002, 2.3895);
        na.setNode(2, 49.4931, 3.3013);
        na.setNode(3, 48.8574, 3.2025);
        na.setNode(4, 48.2575, 3.0651);
        na.setNode(5, 48.2393, 2.2576);
        na.setNode(6, 48.2246, 1.2249);
        na.setNode(7, 48.8611, 1.2194);
        na.setNode(8, 48.8538, 2.3950);

        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(8, 0).setDistance(10)); // edge 0
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(8, 1).setDistance(10)); // edge 1
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(8, 2).setDistance(10)); // edge 2
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(8, 3).setDistance(10)); // edge 3
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(8, 4).setDistance(10)); // edge 4
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(8, 5).setDistance(10)); // edge 5
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(8, 6).setDistance(10)); // edge 6
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(8, 7).setDistance(10)); // edge 7

        HeadingResolver resolver = new HeadingResolver(graph);
        // using default tolerance
        assertEquals(IntArrayList.from(7, 6, 0), resolver.getEdgesWithDifferentHeading(8, 90));
        assertEquals(IntArrayList.from(7, 6, 0), resolver.setTolerance(100).getEdgesWithDifferentHeading(8, 90));
        assertEquals(IntArrayList.from(7, 6, 5, 4, 2, 1, 0), resolver.setTolerance(10).getEdgesWithDifferentHeading(8, 90));
        assertEquals(IntArrayList.from(7, 6, 5, 1, 0), resolver.setTolerance(60).getEdgesWithDifferentHeading(8, 90));

        assertEquals(IntArrayList.from(1), resolver.setTolerance(170).getEdgesWithDifferentHeading(8, 180));
        assertEquals(IntArrayList.from(2, 1, 0), resolver.setTolerance(130).getEdgesWithDifferentHeading(8, 180));

        assertEquals(IntArrayList.from(5, 4, 3), resolver.setTolerance(90).getEdgesWithDifferentHeading(8, 315));
        assertEquals(IntArrayList.from(6, 5, 4, 3, 2), resolver.setTolerance(50).getEdgesWithDifferentHeading(8, 315));
    }

    @Test
    public void curvyEdge() {
        //    1 -|
        // |- 0 -|
        // |- 2
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).create();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(1, 0.01, 0.00);
        na.setNode(0, 0.00, 0.00);
        na.setNode(2, -0.01, 0.00);
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(10)).
                setWayGeometry(Helper.createPointList(0.00, 0.01, 0.01, 0.01));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 2).setDistance(10)).
                setWayGeometry(Helper.createPointList(0.00, -0.01, -0.01, -0.01));
        HeadingResolver resolver = new HeadingResolver(graph);
        resolver.setTolerance(120);
        // asking for the edges not going east returns 0-2
        assertEquals(IntArrayList.from(1), resolver.getEdgesWithDifferentHeading(0, 90));
        // asking for the edges not going west returns 0-1
        assertEquals(IntArrayList.from(0), resolver.getEdgesWithDifferentHeading(0, 270));
    }

    @Test
    public void withQueryGraph() {
        //    2
        // 0 -x- 1
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).create();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 48.8611, 1.2194);
        na.setNode(1, 48.8538, 2.3950);

        EdgeIteratorState edge = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(10));
        Snap snap = createSnap(edge, 48.859, 2.00, 0);
        QueryGraph queryGraph = QueryGraph.create(graph, snap);
        HeadingResolver resolver = new HeadingResolver(queryGraph);

        // if the heading points East we get the Western edge 0->2
        assertEquals("0->2", queryGraph.getEdgeIteratorState(1, Integer.MIN_VALUE).toString());
        assertEquals(IntArrayList.from(1), resolver.getEdgesWithDifferentHeading(2, 90));

        // if the heading points West we get the Eastern edge 2->1
        assertEquals("2->1", queryGraph.getEdgeIteratorState(2, Integer.MIN_VALUE).toString());
        assertEquals(IntArrayList.from(2), resolver.getEdgesWithDifferentHeading(2, 270));
    }

    private Snap createSnap(EdgeIteratorState closestEdge, double lat, double lon, int wayIndex) {
        Snap snap = new Snap(lat, lon);
        snap.setClosestEdge(closestEdge);
        snap.setSnappedPosition(Snap.Position.EDGE);
        snap.setWayIndex(wayIndex);
        snap.calcSnappedPoint(new DistanceCalcEuclidean());
        return snap;
    }

}