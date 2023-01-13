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

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.SimpleBooleanEncodedValue;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.AccessFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.core.util.EdgeExplorer;
import com.graphhopper.core.util.EdgeIterator;
import com.graphhopper.core.util.EdgeIteratorState;
import com.graphhopper.core.util.GHUtility;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.graphhopper.routing.DirectionResolverResult.*;
import static com.graphhopper.util.Helper.createPointList;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link DirectionResolver} on a simple graph (no {@link QueryGraph}.
 *
 * @see DirectionResolverOnQueryGraphTest for tests that include direction resolving for virtual nodes and edges
 */
public class DirectionResolverTest {
    private BooleanEncodedValue accessEnc;
    private DecimalEncodedValue speedEnc;
    private BaseGraph graph;
    private NodeAccess na;

    @BeforeEach
    public void setup() {
        accessEnc = new SimpleBooleanEncodedValue("access", true);
        speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        graph = new BaseGraph.Builder(em).create();
        na = graph.getNodeAccess();
    }

    @Test
    public void isolated_nodes() {
        // 0   1
        addNode(0, 0, 0);
        addNode(1, 0.1, 0.1);

        checkResult(0, impossible());
        checkResult(1, impossible());
    }

    @Test
    public void isolated_nodes_blocked_edge() {
        // 0 |-| 1
        addNode(0, 0, 0);
        addNode(1, 0.1, 0.1);
        // with edges without access flags (blocked edges)
        graph.edge(0, 1).set(accessEnc, false, false);

        checkResult(0, impossible());
        checkResult(1, impossible());
    }

    @Test
    public void isolated_nodes_with_loops() {
        // 1      2__     3    4     5-6
        // |\     |  \    |\   |\      |\
        // x-x    x->x    --   ->      --
        addNode(0, 2, 0);
        addNode(1, 2, 1);
        addNode(2, 2, 2);
        addNode(3, 2, 3);
        addNode(4, 2, 4);
        addNode(5, 2, 5);
        addNode(6, 2, 5.1);
        // make sure graph bounds are valid
        addNode(7, 5, 5);

        addEdge(1, 1, true).setWayGeometry(createPointList(1.9, 1, 1.9, 1.1));
        addEdge(2, 2, false).setWayGeometry(createPointList(1.9, 2, 1.9, 2.1));
        addEdge(3, 3, true);
        addEdge(4, 4, false);
        addEdge(5, 6, true);
        addEdge(6, 6, true);

        checkResult(1, impossible());
        checkResult(2, impossible());
        checkResult(3, impossible());
        checkResult(4, impossible());

        // for node 5 we cannot know (without further loop traversing) that 5 is only connected to a single
        // other node (and its only a loop), so we restrict it as usual
        checkResult(5, restricted(edge(5, 6), edge(6, 5), edge(5, 6), edge(6, 5)));
    }

    @Test
    public void nodes_at_end_of_dead_end_street() {
        //       4
        //       |
        // 0 --> 1 --> 2
        //       |
        //       3
        addNode(0, 2, 1.9);
        addNode(1, 2, 2.0);
        addNode(2, 2, 2.1);
        addNode(3, 1.9, 2.0);
        addNode(4, 2.1, 2.0);
        addEdge(0, 1, false);
        addEdge(1, 2, false);
        addEdge(1, 3, true);
        addEdge(1, 4, true);

        checkResult(0, impossible());
        checkResult(2, impossible());
        // at the end of a dead end street the (only) in/out edges are used as restriction for both right and left
        // side approach
        checkResult(3, restricted(edge(1, 3), edge(3, 1), edge(1, 3), edge(3, 1)));
        checkResult(4, restricted(edge(1, 4), edge(4, 1), edge(1, 4), edge(4, 1)));
    }

    @Test
    public void unreachable_nodes() {
        //   1   3
        //  / \ /
        // 0   2
        addNode(0, 1, 1);
        addNode(1, 2, 1.5);
        addNode(2, 1, 2);
        addNode(3, 2, 2.5);
        addEdge(0, 1, false);
        addEdge(2, 1, false);
        addEdge(2, 3, false);

        // we can go to node 1, but never leave it
        checkResult(1, impossible());
        // we can leave point 2, but never arrive at it
        checkResult(2, impossible());
    }

    @Test
    public void nodes_with_loops() {
        // in case there is a loop edge we simply do not apply any restrictions. loops are rather rare and excluded
        // by the OSM import. Snapping a point onto a tower node that has a loop is even less likely.
        // If there is a loop there is often no reasonable way to restrict the direction similar to junctions.
        // 0------1-------2----3
        // |\     |  \    |\   |\
        // x-x    x->x    --   ->
        addNode(0, 2, 0);
        addNode(1, 2, 1);
        addNode(2, 2, 2);
        addNode(3, 2, 3);
        // make sure graph bounds are valid
        addNode(4, 5, 5);
        addEdge(0, 0, true).setWayGeometry(createPointList(1.9, 0, 1.9, 0.1));
        addEdge(0, 1, true);
        addEdge(1, 1, false).setWayGeometry(createPointList(1.9, 1, 1.9, 1.1));
        addEdge(1, 2, true);
        addEdge(2, 2, true);
        addEdge(2, 3, true);
        addEdge(3, 3, false);

        checkResult(0, unrestricted());
        checkResult(1, unrestricted());
        checkResult(2, unrestricted());
        checkResult(3, unrestricted());
    }

    @Test
    public void junction() {
        //      3___
        //      |   \
        // 0 -> 1 -> 2 - 5
        //      |
        //      4
        addNode(0, 2.000, 1.990);
        addNode(1, 2.000, 2.000);
        addNode(2, 2.000, 2.010);
        addNode(3, 2.010, 2.000);
        addNode(4, 1.990, 2.000);
        addEdge(0, 1, false);
        addEdge(1, 2, false);
        addEdge(1, 3, true);
        addEdge(2, 3, true);
        addEdge(1, 4, true);
        addEdge(2, 5, true);

        // at junctions there is no reasonable way to restrict the directions!
        checkResult(1, unrestricted());
        checkResult(2, unrestricted());
    }

    @Test
    public void junction_exposed() {
        // 0  1  2
        //  \ | /
        //   \|/
        //    3
        addNode(0, 2, 1);
        addNode(1, 2, 2);
        addNode(2, 2, 3);
        addNode(3, 1, 2);
        addEdge(0, 3, true);
        addEdge(1, 3, true);
        addEdge(2, 3, true);
        checkResult(3, unrestricted());
    }

    @Test
    public void duplicateEdges() {
        // 0 = 1 - 2
        addNode(0, 0, 0);
        addNode(1, 1, 1);
        addNode(2, 0, 2);
        addEdge(0, 1, true);
        addEdge(0, 1, true);
        addEdge(1, 2, true);
        // if there are multiple incoming/outgoing edges due to duplicate edges its the same as for a junction,
        // -> we leave the directions unrestricted
        checkResult(1, unrestricted());

        // for duplicate edges at the end of a dead-end road we also leave the direction unrestricted
        checkResult(0, unrestricted());
    }

    @Test
    public void duplicateEdges_in() {
        // 0 => 1 - 2
        addNode(0, 1, 1);
        addNode(1, 2, 2);
        addNode(2, 1, 3);
        // duplicate in edges between 0 and 1 -> we do not apply any restrictions
        addEdge(0, 1, false);
        addEdge(0, 1, false);
        addEdge(1, 2, false);

        checkResult(1, unrestricted());
    }

    @Test
    public void duplicateEdges_out() {
        // 0 - 1 => 2
        addNode(0, 1, 1);
        addNode(1, 2, 2);
        addNode(2, 1, 3);
        // duplicate out edges between 1 and 2 -> we do not apply any restrictions
        addEdge(0, 1, false);
        addEdge(1, 2, false);
        addEdge(1, 2, false);

        checkResult(1, unrestricted());
    }

    @Test
    public void simple_road() {
        //    x   x
        //  0-1-2-3-4
        //    x   x
        addNode(0, 1, 0);
        addNode(1, 1, 1);
        addNode(2, 1, 2);
        addNode(3, 1, 3);
        addNode(4, 1, 4);
        // make sure graph bounds are valid
        addNode(5, 2, 5);

        addEdge(0, 1, true);
        addEdge(1, 2, true);
        addEdge(2, 3, true);
        addEdge(3, 4, true);

        checkResult(1, 1.01, 1, restricted(edge(2, 1), edge(1, 0), edge(0, 1), edge(1, 2)));
        checkResult(1, 0.99, 1, restricted(edge(0, 1), edge(1, 2), edge(2, 1), edge(1, 0)));
        checkResult(3, 1.01, 3, restricted(edge(4, 3), edge(3, 2), edge(2, 3), edge(3, 4)));
        checkResult(3, 0.99, 3, restricted(edge(2, 3), edge(3, 4), edge(4, 3), edge(3, 2)));
    }

    @Test
    public void simple_road_one_way() {
        //     x     x
        //  0->1->2->3->4
        //     x     x
        addNode(0, 1, 0);
        addNode(1, 1, 1);
        addNode(2, 1, 2);
        addNode(3, 1, 3);
        addNode(4, 1, 4);
        // make sure graph bounds are valid
        addNode(5, 2, 5);

        addEdge(0, 1, false);
        addEdge(1, 2, false);
        addEdge(2, 3, false);
        addEdge(3, 4, false);

        // if a location is on the 'wrong'side on a one-way street
        checkResult(1, 1.01, 1, onlyLeft(edge(0, 1), edge(1, 2)));
        checkResult(1, 0.99, 1, onlyRight(edge(0, 1), edge(1, 2)));
        checkResult(3, 1.01, 3, onlyLeft(edge(2, 3), edge(3, 4)));
        checkResult(3, 0.99, 3, onlyRight(edge(2, 3), edge(3, 4)));
    }


    @Test
    public void twoOutOneIn_oneWayRight() {
        //     x
        // 0 - 1 -> 2
        //     x
        addNode(0, 0, 0);
        addNode(1, 1, 1);
        addNode(2, 2, 2);
        addEdge(0, 1, true);
        addEdge(1, 2, false);

        // we cannot approach the southern target so it is on our left
        checkResult(1, 0.99, 1, onlyRight(0, 1));
        // we cannot approach the northern target so it is on our left
        checkResult(1, 1.01, 1, onlyLeft(0, 1));
    }

    @Test
    public void twoOutOneIn_oneWayLeft() {
        //      x
        // 0 <- 1 - 2
        //      x
        addNode(0, 0, 0);
        addNode(1, 1, 1);
        addNode(2, 2, 2);
        addEdge(1, 0, false);
        addEdge(1, 2, true);

        checkResult(1, 0.99, 1, onlyLeft(1, 0));
        checkResult(1, 1.01, 1, onlyRight(1, 0));
    }

    @Test
    public void twoInOneOut_oneWayRight() {
        //     x
        // 0 - 1 <- 2
        //     x
        addNode(0, 0, 0);
        addNode(1, 1, 1);
        addNode(2, 2, 2);
        addEdge(0, 1, true);
        addEdge(2, 1, false);

        checkResult(1, 0.99, 1, onlyLeft(1, 0));
        checkResult(1, 1.01, 1, onlyRight(1, 0));
    }

    @Test
    public void twoInOneOut_oneWayLeft() {
        //      x
        // 0 -> 1 - 2
        //      x
        addNode(0, 0, 0);
        addNode(1, 1, 1);
        addNode(2, 2, 2);
        addEdge(0, 1, false);
        addEdge(2, 1, true);

        checkResult(1, 0.99, 1, onlyRight(0, 1));
        checkResult(1, 1.01, 1, onlyLeft(0, 1));
    }

    private void addNode(int nodeId, double lat, double lon) {
        na.setNode(nodeId, lat, lon);
    }

    private EdgeIteratorState addEdge(int from, int to, boolean bothDirections) {
        return GHUtility.setSpeed(60, true, bothDirections, accessEnc, speedEnc, graph.edge(from, to).setDistance(1));
    }

    private boolean isAccessible(EdgeIteratorState edge, boolean reverse) {
        return reverse ? edge.getReverse(accessEnc) : edge.get(accessEnc);
    }

    private void checkResult(int node, DirectionResolverResult expectedResult) {
        checkResult(node, graph.getNodeAccess().getLat(node), graph.getNodeAccess().getLon(node), expectedResult);
    }

    private void checkResult(int node, double lat, double lon, DirectionResolverResult expectedResult) {
        DirectionResolver resolver = new DirectionResolver(graph, this::isAccessible);
        assertEquals(expectedResult, resolver.resolveDirections(node, new GHPoint(lat, lon)));
    }

    private int edge(int from, int to) {
        EdgeExplorer explorer = graph.createEdgeExplorer(AccessFilter.outEdges(accessEnc));
        EdgeIterator iter = explorer.setBaseNode(from);
        while (iter.next()) {
            if (iter.getAdjNode() == to) {
                return iter.getEdge();
            }
        }
        throw new IllegalStateException("Could not find edge from: " + from + ", to: " + to);
    }

}
