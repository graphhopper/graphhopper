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

package com.graphhopper.gtfs;

import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.DistanceCalcEuclidean;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PointList;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.util.Lists.emptyList;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

public class GraphExplorerTest {

    private final PtEncodedValues pt;
    private final FootFlagEncoder foot;
    private final EncodingManager encodingManager;

    public GraphExplorerTest() {
        foot = new FootFlagEncoder();
        encodingManager = PtEncodedValues.createAndAddEncodedValues(EncodingManager.start()).add(foot).build();
        pt = PtEncodedValues.fromEncodingManager(encodingManager);
    }

    @Test
    public void testNonEmptyGraph() {
        GraphHopperStorage graph = new GraphBuilder(encodingManager).create();
        EdgeIteratorState d = graph.edge(4, 5);
        d.set(pt.getAccessEnc(), true);
        d.set(foot.getAccessEnc(), true);

        GtfsStorage gtfsStorage = mock(GtfsStorage.class);
        RealtimeFeed realtimeFeed = mock(RealtimeFeed.class);
        List<VirtualEdgeIteratorState> extraEdges = new ArrayList<>();

        QueryGraph queryGraph = QueryGraph.create(graph, Collections.emptyList());
        GraphExplorer testee = new GraphExplorer(queryGraph, new FastestWeighting(foot), pt, gtfsStorage, realtimeFeed, false, false, false, 5.0, false, 0);

        assertThat(() -> testee.exploreEdgesAround(new Label(0, -1, 4, 0, 0L, 0, 0, false, null)).map(Object::toString).iterator(),
                contains(d.toString()));
    }

    @Test
    public void testExtraEdgesWithEmptyGraph() {
        GraphHopperStorage graph = new GraphBuilder(encodingManager).create();
        GtfsStorage gtfsStorage = mock(GtfsStorage.class);
        RealtimeFeed realtimeFeed = mock(RealtimeFeed.class);
        List<VirtualEdgeIteratorState> extraEdges = new ArrayList<>();
        VirtualEdgeIteratorState e = new VirtualEdgeIteratorState(0, GHUtility.createEdgeKey(0, false), 0, 1, 0.0, encodingManager.createEdgeFlags(), "", new PointList(), false);
        e.set(foot.getAccessEnc(), true);
        extraEdges.add(e);
        VirtualEdgeIteratorState f = new VirtualEdgeIteratorState(1, GHUtility.createEdgeKey(1, false), 1, 2, 0.0, encodingManager.createEdgeFlags(), "", new PointList(), false);
        f.set(foot.getAccessEnc(), true);
        extraEdges.add(f);
        VirtualEdgeIteratorState g = new VirtualEdgeIteratorState(2, GHUtility.createEdgeKey(2, false), 1, 3, 0.0, encodingManager.createEdgeFlags(), "", new PointList(), false);
        g.set(foot.getAccessEnc(), true);
        extraEdges.add(g);

        WrapperGraph wrapperGraph = new WrapperGraph(graph, extraEdges);
        QueryGraph queryGraph = QueryGraph.create(wrapperGraph, Collections.emptyList());
        GraphExplorer testee = new GraphExplorer(queryGraph, new FastestWeighting(foot), pt, gtfsStorage, realtimeFeed, false, false, false, 5.0, false, 0);
        assertThat(() -> testee.exploreEdgesAround(new Label(0, -1, 0, 0, 0L, 0, 0, false, null)).map(Object::toString).iterator(),
                contains(e.toString()));
        assertThat(() -> testee.exploreEdgesAround(new Label(0, -1, 1, 0, 0L, 0, 0, false, null)).map(Object::toString).iterator(),
                contains(f.toString(), g.toString()));
    }

    @Test
    public void testExtraEdgesWithNonEmptyGraph() {
        GraphHopperStorage graph = new GraphHopperStorage(new RAMDirectory("wurst"), encodingManager, false);
        graph.create(0);
        EdgeIteratorState d = graph.edge(4, 5);
        d.set(pt.getAccessEnc(), true);
        d.set(foot.getAccessEnc(), true);

        int edgeId = graph.getEdges();

        GtfsStorage gtfsStorage = mock(GtfsStorage.class);
        RealtimeFeed realtimeFeed = mock(RealtimeFeed.class);
        List<VirtualEdgeIteratorState> extraEdges = new ArrayList<>();
        VirtualEdgeIteratorState e = new VirtualEdgeIteratorState(-1, GHUtility.createEdgeKey(edgeId++, false), 0, 1, 0.0, encodingManager.createEdgeFlags(), "", new PointList(), false);
        e.set(foot.getAccessEnc(), true);
        extraEdges.add(e);
        VirtualEdgeIteratorState f = new VirtualEdgeIteratorState(-1, GHUtility.createEdgeKey(edgeId++, false), 1, 2, 0.0, encodingManager.createEdgeFlags(), "", new PointList(), false);
        f.set(foot.getAccessEnc(), true);
        extraEdges.add(f);
        VirtualEdgeIteratorState g = new VirtualEdgeIteratorState(-1, GHUtility.createEdgeKey(edgeId++, false), 1, 3, 0.0, encodingManager.createEdgeFlags(), "", new PointList(), false);
        g.set(foot.getAccessEnc(), true);
        extraEdges.add(g);
        VirtualEdgeIteratorState h = new VirtualEdgeIteratorState(-1, GHUtility.createEdgeKey(edgeId++, false), 4, 7, 0.0, encodingManager.createEdgeFlags(), "", new PointList(), false);
        h.set(foot.getAccessEnc(), true);
        extraEdges.add(h);

        WrapperGraph wrapperGraph = new WrapperGraph(graph, extraEdges);
        QueryGraph queryGraph = QueryGraph.create(wrapperGraph, emptyList());
        GraphExplorer forward = new GraphExplorer(queryGraph, new FastestWeighting(foot), pt, gtfsStorage, realtimeFeed, false, false, false, 5.0, false, 0);
        assertThat(() -> forward.exploreEdgesAround(new Label(0, -1, 0, 0, 0L, 0, 0, false, null)).map(Object::toString).iterator(),
                contains("0->1"));
        assertThat(() -> forward.exploreEdgesAround(new Label(0, -1, 1, 0, 0L, 0, 0, false, null)).map(Object::toString).iterator(),
                contains("1->2", "1->3"));
        assertThat(() -> forward.exploreEdgesAround(new Label(0, -1, 4, 0, 0L, 0, 0, false, null)).map(Object::toString).iterator(),
                contains("0 4-5", "4->7"));
        GraphExplorer backward = new GraphExplorer(queryGraph, new FastestWeighting(foot), pt, gtfsStorage, realtimeFeed, true, false, false, 5.0, false, 0);
        assertThat(() -> backward.exploreEdgesAround(new Label(0, -1, 1, 0, 0L, 0, 0, false, null)).map(Object::toString).iterator(),
                contains("1->0"));
    }

    @Test
    public void testExtraEdgesWithNonEmptyGraphAndQueryGraph() {
        GraphHopperStorage graph = new GraphHopperStorage(new RAMDirectory("wurst"), encodingManager, false);
        graph.create(0);
        EdgeIteratorState c = graph.edge(4, 3);
        c.set(pt.getAccessEnc(), true);
        c.set(foot.getAccessEnc(), true);
        PointList cp = new PointList();
        c.setWayGeometry(cp);
        EdgeIteratorState d = graph.edge(4, 5);
        d.set(pt.getAccessEnc(), true);
        d.set(foot.getAccessEnc(), true);
        PointList dp = new PointList();
        d.setWayGeometry(dp);
        graph.getNodeAccess().setNode(3, 3.0, 3.0);
        graph.getNodeAccess().setNode(4, 4.0, 4.0);
        graph.getNodeAccess().setNode(5, 5.0, 5.0);


        int edgeId = graph.getEdges();

        GtfsStorage gtfsStorage = mock(GtfsStorage.class);
        RealtimeFeed realtimeFeed = mock(RealtimeFeed.class);
        List<VirtualEdgeIteratorState> extraEdges = new ArrayList<>();
        VirtualEdgeIteratorState e = new VirtualEdgeIteratorState(-1, GHUtility.createEdgeKey(edgeId++, false), 0, 1, 0.0, encodingManager.createEdgeFlags(), "", new PointList(), false);
        e.set(foot.getAccessEnc(), true);
        extraEdges.add(e);
        VirtualEdgeIteratorState f = new VirtualEdgeIteratorState(-1, GHUtility.createEdgeKey(edgeId++, false), 1, 2, 0.0, encodingManager.createEdgeFlags(), "", new PointList(), false);
        f.set(foot.getAccessEnc(), true);
        extraEdges.add(f);
        VirtualEdgeIteratorState g = new VirtualEdgeIteratorState(-1, GHUtility.createEdgeKey(edgeId++, false), 1, 3, 0.0, encodingManager.createEdgeFlags(), "", new PointList(), false);
        g.set(foot.getAccessEnc(), true);
        extraEdges.add(g);
        VirtualEdgeIteratorState h = new VirtualEdgeIteratorState(-1, GHUtility.createEdgeKey(edgeId++, false), 4, 7, 0.0, encodingManager.createEdgeFlags(), "", new PointList(), false);
        h.set(foot.getAccessEnc(), true);
        extraEdges.add(h);

        WrapperGraph wrapperGraph = new WrapperGraph(graph, extraEdges);

        Snap point1 = new Snap(3.5, 3.5);
        Snap point2 = new Snap(4.5, 4.5);
        point1.setClosestEdge(c);
        point1.setQueryDistance(0.0);
        point1.setWayIndex(0);
        point1.setSnappedPosition(Snap.Position.EDGE);
        point1.calcSnappedPoint(new DistanceCalcEuclidean());
        point2.setClosestEdge(d);
        point2.setQueryDistance(0.0);
        point2.setWayIndex(0);
        point2.setSnappedPosition(Snap.Position.EDGE);
        point2.calcSnappedPoint(new DistanceCalcEuclidean());
        QueryGraph queryGraph = QueryGraph.create(wrapperGraph, point1, point2);

        GraphExplorer testee = new GraphExplorer(queryGraph, new FastestWeighting(foot), pt, gtfsStorage, realtimeFeed, false, false, false, 5.0, false, 0);
        assertThat(() -> testee.exploreEdgesAround(new Label(0, -1, 0, 0, 0L, 0, 0, false, null)).map(Object::toString).iterator(),
                contains(e.toString()));
        assertThat(() -> testee.exploreEdgesAround(new Label(0, -1, 1, 0, 0L, 0, 0, false, null)).map(Object::toString).iterator(),
                contains(f.toString(), g.toString()));
        assertThat(() -> testee.exploreEdgesAround(new Label(0, -1, 4, 0, 0L, 0, 0, false, null)).map(Object::toString).iterator(),
                contains(containsString("4->8"), containsString("4->9"), containsString("4->7")));
        assertThat((Iterable<String>) () -> testee.exploreEdgesAround(new Label(0, -1, 7, 0, 0L, 0, 0, false, null)).map(Object::toString).iterator(),
                emptyIterable());

    }


}
