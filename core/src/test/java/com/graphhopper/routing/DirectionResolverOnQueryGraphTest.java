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
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.graphhopper.routing.DirectionResolverResult.unrestricted;
import static com.graphhopper.util.EdgeIterator.NO_EDGE;
import static com.graphhopper.util.Helper.createPointList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * This test simulates incoming lat/lon coordinates that get snapped to graph edges (using {@link QueryGraph}) and the
 * calculated directions are tested.
 *
 * @see DirectionResolverTest which tests direction resolving on a normal graph only considering real nodes and edges
 */
public class DirectionResolverOnQueryGraphTest {
    private QueryGraph queryGraph;
    private NodeAccess na;
    private FlagEncoder encoder;
    private Graph g;
    private LocationIndex locationIndex;

    @Before
    public void setup() {
        encoder = new CarFlagEncoder();
        g = new GraphBuilder(EncodingManager.create(encoder)).create();
        na = g.getNodeAccess();
    }

    @Test
    public void junction() {
        //      3
        //    a | b
        // 0 -> 1 -> 2
        //    c | d
        //      4
        addNode(0, 2.000, 1.990);
        addNode(1, 2.000, 2.000);
        addNode(2, 2.000, 2.010);
        addNode(3, 2.010, 2.000);
        addNode(4, 1.990, 2.000);
        addEdge(0, 1, false);
        addEdge(1, 2, false);
        addEdge(1, 3, true);
        addEdge(1, 4, true);
        init();

        // close to the junction we snap to the closest edge (a virtual node 5 will be added)
        // a
        checkResult(2.001, 1.998, onlyLeft(edge(0, 5), edge(5, 1)));
        checkResult(2.002, 1.999, restricted(edge(3, 5), edge(5, 1), edge(1, 5), edge(5, 3)));
        // b
        checkResult(2.001, 2.002, onlyLeft(edge(1, 5), edge(5, 2)));
        checkResult(2.002, 2.001, restricted(edge(1, 5), edge(5, 3), edge(3, 5), edge(5, 1)));
        // c
        checkResult(1.999, 1.998, onlyRight(edge(0, 5), edge(5, 1)));
        checkResult(1.998, 1.999, restricted(edge(1, 5), edge(5, 4), edge(4, 5), edge(5, 1)));
        // d
        checkResult(1.999, 2.002, onlyRight(edge(1, 5), edge(5, 2)));
        checkResult(1.998, 2.001, restricted(edge(4, 5), edge(5, 1), edge(1, 5), edge(5, 4)));

        // precisely hit the junction -> no restriction (there is no reasonable way to restrict the directions for
        // a junction)
        assertUnrestricted(2.0, 2.0);
    }

    @Test
    public void multiple_locations_same_road() {
        //      3     5
        //  0 --x--x--x-- 1
        //         4
        addNode(0, 1, 1);
        addNode(1, 1, 2);
        // make sure graph bounds are valid
        addNode(2, 5, 5);

        addEdge(0, 1, true);
        init();

        // virtual nodes 3,4,5 will be added to query graph
        checkResults(
                result(1.01, 1.2, restricted(edge(4, 3), edge(3, 0), edge(0, 3), edge(3, 4))),
                result(0.99, 1.5, restricted(edge(3, 4), edge(4, 5), edge(5, 4), edge(4, 3))),
                result(1.01, 1.7, restricted(edge(1, 5), edge(5, 4), edge(4, 5), edge(5, 1)))
        );
    }

    @Test
    public void multiple_locations_same_road_one_way() {
        //      3     5
        //  0 --x--x--x--> 1
        //         4
        addNode(0, 1, 1);
        addNode(1, 1, 2);
        // make sure graph bounds are valid
        addNode(2, 5, 5);

        addEdge(0, 1, false);
        init();

        // virtual nodes 3,4,5 will be added to query graph
        checkResults(
                result(1.01, 1.2, onlyLeft(edge(0, 3), edge(3, 4))),
                result(0.99, 1.5, onlyRight(edge(3, 4), edge(4, 5))),
                result(1.01, 1.7, onlyLeft(edge(4, 5), edge(5, 1)))
        );
    }

    @Test
    public void two_locations_same_spot_same_side() {
        //      3
        //      3
        //  0 --x-- 1
        addNode(0, 1, 1);
        addNode(1, 1, 2);
        // make sure graph bounds are valid
        addNode(2, 5, 5);

        addEdge(0, 1, true);
        init();

        // there should only be a single virtual node, used for both queried points
        checkResults(
                result(1.01, 1.5, restricted(edge(1, 3), edge(3, 0), edge(0, 3), edge(3, 1))),
                result(1.01, 1.5, restricted(edge(1, 3), edge(3, 0), edge(0, 3), edge(3, 1)))
        );
    }

    @Test
    public void two_locations_same_spot_different_sides() {
        //      3
        //  0 --x-- 1
        //      3
        addNode(0, 1, 1);
        addNode(1, 1, 2);
        // make sure graph bounds are valid
        addNode(2, 5, 5);

        addEdge(0, 1, true);
        init();

        checkResults(
                result(1.01, 1.5, restricted(edge(1, 3), edge(3, 0), edge(0, 3), edge(3, 1))),
                result(0.99, 1.5, restricted(edge(0, 3), edge(3, 1), edge(1, 3), edge(3, 0)))
        );
    }

    @Test
    public void road_with_geometry() {
        //       3
        //    x --- x
        //   3|  3  |3
        //    0     1
        addNode(0, 1, 2);
        addNode(1, 1, 3);
        // make sure graph has valid bounds
        addNode(2, 5, 5);

        addEdge(0, 1, true).setWayGeometry(createPointList(2, 2, 2, 3));
        init();

        // pillar nodes / geometry are important to decide on which side of the road a location is.
        // note that to determine the edges we are only interested in the virtual edges between the
        // tower nodes and the virtual node
        checkResult(1.50, 1.99, restricted(edge(1, 3), edge(3, 0), edge(0, 3), edge(3, 1)));
        checkResult(2.01, 2.50, restricted(edge(1, 3), edge(3, 0), edge(0, 3), edge(3, 1)));
        checkResult(1.50, 3.01, restricted(edge(1, 3), edge(3, 0), edge(0, 3), edge(3, 1)));
        checkResult(1.99, 2.50, restricted(edge(0, 3), edge(3, 1), edge(1, 3), edge(3, 0)));
    }

    @Test
    public void sharp_curves() {
        // 0 --- x
        //      /
        //     x---- 1
        addNode(0, 2, 1);
        addNode(1, 1, 3);
        addEdge(0, 1, true).setWayGeometry(createPointList(2.0, 1.5, 1.0, 1.3));
        init();
        // these are cases where we snap onto pillar nodes
        // .. at the 'outside' of the turns
        checkResult(2.0, 1.501, restricted(edge(1, 2), edge(2, 0), edge(0, 2), edge(2, 1)));
        checkResult(1.0, 1.299, restricted(edge(0, 2), edge(2, 1), edge(1, 2), edge(2, 0)));
        // .. at the 'inside' of the turns
        checkResult(1.99, 1.49, restricted(edge(0, 2), edge(2, 1), edge(1, 2), edge(2, 0)));
        checkResult(1.01, 1.31, restricted(edge(1, 2), edge(2, 0), edge(0, 2), edge(2, 1)));
    }

    @Test
    public void junction_hitFromTheSide() {
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
        init();

        // we will easily snap onto the (junction) tower node if it is 'exposed' like this (much more likely than
        // hitting the exact coordinates)
        assertUnrestricted(0.99, 2);
    }

    @Test
    public void duplicateCoordinatesAtBaseOrAdjNode() {
        // 0-x   x-1
        //   x-x-x
        addNode(0, 0, 0);
        addNode(1, 1, 1);
        // todo: we add duplicate coordinates to the beginning/end of the geometry, these are currently possible, so we
        // have to handle this separately, see #1694
        addEdge(0, 1, true).setWayGeometry(createPointList(0.1, 0.1, 0.1, 0.1, 0.2, 0.2, 0.9, 0.9, 0.9, 0.9));
        init();
        checkResult(0.1, 0.1, restricted(edge(0, 2), edge(2, 1), edge(1, 2), edge(2, 0)));
        checkResult(0.9, 0.9, restricted(edge(0, 2), edge(2, 1), edge(1, 2), edge(2, 0)));
    }

    private void addNode(int nodeId, double lat, double lon) {
        na.setNode(nodeId, lat, lon);
    }

    private EdgeIteratorState addEdge(int from, int to, boolean bothDirections) {
        return g.edge(from, to, 1, bothDirections);
    }

    private void init() {
        locationIndex = new LocationIndexTree(g, new RAMDirectory());
        locationIndex.prepareIndex();
    }

    private void checkResult(double lat, double lon, ExpectedEdge... edges) {
        checkResults(result(lat, lon, edges));
    }

    private void checkResults(ExpectedResult... expectedResults) {
        List<QueryResult> qrs = new ArrayList<>(expectedResults.length);
        for (ExpectedResult r : expectedResults) {
            qrs.add(getQueryResult(r.lat, r.lon));
        }
        queryGraph = QueryGraph.lookup(g, qrs);
        DirectionResolver resolver = new DirectionResolver(queryGraph, encoder);
        for (int i = 0; i < expectedResults.length; i++) {
            assertEquals("unexpected resolved direction",
                    restrictedDirection(expectedResults[i]),
                    resolver.resolveDirections(qrs.get(i).getClosestNode(), qrs.get(i).getQueryPoint()));
        }
    }

    private ExpectedEdge[] onlyLeft(ExpectedEdge leftIn, ExpectedEdge leftOut) {
        return new ExpectedEdge[]{null, null, leftIn, leftOut};
    }

    private ExpectedEdge[] onlyRight(ExpectedEdge rightIn, ExpectedEdge rightOut) {
        return new ExpectedEdge[]{rightIn, rightOut, null, null};
    }

    private ExpectedEdge[] restricted(ExpectedEdge rightIn, ExpectedEdge rightOut, ExpectedEdge leftIn, ExpectedEdge leftOut) {
        return new ExpectedEdge[]{rightIn, rightOut, leftIn, leftOut};
    }

    private void assertUnrestricted(double lat, double lon) {
        QueryResult qr = getQueryResult(lat, lon);
        queryGraph = QueryGraph.lookup(g, qr);
        DirectionResolver resolver = new DirectionResolver(queryGraph, encoder);
        assertEquals(unrestricted(), resolver.resolveDirections(qr.getClosestNode(), qr.getQueryPoint()));
    }

    private DirectionResolverResult restrictedDirection(ExpectedResult restriction) {
        IntArrayList edgeIds = new IntArrayList(restriction.expectedEdges.length);
        for (ExpectedEdge e : restriction.expectedEdges) {
            edgeIds.add(e == null ? NO_EDGE : findEdge(e.from, e.to));
        }
        return DirectionResolverResult.restricted(edgeIds.get(0), edgeIds.get(1), edgeIds.get(2), edgeIds.get(3));
    }

    private int findEdge(int from, int to) {
        EdgeExplorer explorer = queryGraph.createEdgeExplorer(DefaultEdgeFilter.outEdges(encoder.getAccessEnc()));
        EdgeIterator iter = explorer.setBaseNode(from);
        while (iter.next()) {
            if (iter.getAdjNode() == to) {
                return iter.getEdge();
            }
        }
        throw new IllegalStateException("Could not find edge from: " + from + ", to: " + to);
    }

    private QueryResult getQueryResult(double lat, double lon) {
        return locationIndex.findClosest(lat, lon, EdgeFilter.ALL_EDGES);
    }

    private ExpectedEdge edge(int from, int to) {
        return new ExpectedEdge(from, to);
    }

    private ExpectedResult result(double lat, double lon, ExpectedEdge... edges) {
        return new ExpectedResult(lat, lon, edges);
    }

    // we need to store the from/to nodes instead of obtaining the edges directly, because by the time we specify
    // the expected edges the query graph has not been build yet
    private static class ExpectedEdge {
        int from;
        int to;

        ExpectedEdge(int from, int to) {
            this.from = from;
            this.to = to;
        }
    }

    private static class ExpectedResult {
        double lat;
        double lon;
        ExpectedEdge[] expectedEdges;

        ExpectedResult(double lat, double lon, ExpectedEdge[] expectedEdges) {
            if (expectedEdges.length != 4) {
                fail("there should be four expected edges, but got: " + expectedEdges.length);
            }
            this.lat = lat;
            this.lon = lon;
            this.expectedEdges = expectedEdges;
        }
    }
}
