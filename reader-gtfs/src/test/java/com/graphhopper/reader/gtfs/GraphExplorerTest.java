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

package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.DistanceCalc2D;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

public class GraphExplorerTest {

    private final PtFlagEncoder pt;
    private final FootFlagEncoder foot;
    private final EncodingManager encodingManager;

    public GraphExplorerTest() {
        pt = new PtFlagEncoder();
        foot = new FootFlagEncoder();
        encodingManager = new EncodingManager(Arrays.asList(pt, foot), 8);
    }

    @Test
    public void testEverythingEmpty() {
        GraphHopperStorage graph = new GraphHopperStorage(new RAMDirectory("wurst"), encodingManager, false, new GraphExtension.NoOpExtension());
        GtfsStorage gtfsStorage = mock(GtfsStorage.class);
        RealtimeFeed realtimeFeed = mock(RealtimeFeed.class);
        List<VirtualEdgeIteratorState> extraEdges = new ArrayList<>();
        GraphExplorer testee = new GraphExplorer(graph, new FastestWeighting(foot), pt, gtfsStorage, realtimeFeed, false, extraEdges, false, 5.0);
        assertThat((Iterable<EdgeIteratorState>) () -> testee.exploreEdgesAround(new Label(0, 0, 0, 0, 0, 0.0, 0L, 0, 0, false, null)).iterator(),
                emptyIterable());
    }

    @Test
    public void testNonEmptyGraph() {
        GraphHopperStorage graph = new GraphHopperStorage(new RAMDirectory("wurst"), encodingManager, false, new GraphExtension.NoOpExtension());
        graph.create(0);
        EdgeIteratorState d = graph.edge(4, 5);
        d.setFlags(pt.setAccess(d.getFlags(), true, false));
        d.setFlags(foot.setAccess(d.getFlags(), true, false));

        GtfsStorage gtfsStorage = mock(GtfsStorage.class);
        RealtimeFeed realtimeFeed = mock(RealtimeFeed.class);
        List<VirtualEdgeIteratorState> extraEdges = new ArrayList<>();

        GraphExplorer testee = new GraphExplorer(graph, new FastestWeighting(foot), pt, gtfsStorage, realtimeFeed, false, extraEdges, false, 5.0);

        assertThat(() -> testee.exploreEdgesAround(new Label(0, -1, 4, 0, 0, 0.0, 0L, 0, 0, false, null)).map(Object::toString).iterator(),
                contains(d.toString()));
    }

    @Test
    public void testExtraEdgesWithEmptyGraph() {
        GraphHopperStorage graph = new GraphHopperStorage(new RAMDirectory("wurst"), encodingManager, false, new GraphExtension.NoOpExtension());

        GtfsStorage gtfsStorage = mock(GtfsStorage.class);
        RealtimeFeed realtimeFeed = mock(RealtimeFeed.class);
        List<VirtualEdgeIteratorState> extraEdges = new ArrayList<>();
        VirtualEdgeIteratorState e = new VirtualEdgeIteratorState(0, 0, 0, 1, 0.0, 0L, "", new PointList());
        e.setFlags(foot.setAccess(e.getFlags(), true, false));
        extraEdges.add(e);
        VirtualEdgeIteratorState f = new VirtualEdgeIteratorState(1, 1, 1, 2, 0.0, 0L, "", new PointList());
        f.setFlags(foot.setAccess(f.getFlags(), true, false));
        extraEdges.add(f);
        VirtualEdgeIteratorState g = new VirtualEdgeIteratorState(2, 2, 1, 3, 0.0, 0L, "", new PointList());
        g.setFlags(foot.setAccess(g.getFlags(), true, false));
        extraEdges.add(g);

        GraphExplorer testee = new GraphExplorer(graph, new FastestWeighting(foot), pt, gtfsStorage, realtimeFeed, false, extraEdges, false, 5.0);
        assertThat(() -> testee.exploreEdgesAround(new Label(0, -1, 0, 0, 0, 0.0, 0L, 0, 0, false, null)).map(Object::toString).iterator(),
                contains(e.toString()));
        assertThat(() -> testee.exploreEdgesAround(new Label(0, -1, 1, 0, 0, 0.0, 0L, 0, 0, false, null)).map(Object::toString).iterator(),
                contains(f.toString(), g.toString()));

    }

    @Test
    public void testExtraEdgesWithNonEmptyGraph() {
        GraphHopperStorage graph = new GraphHopperStorage(new RAMDirectory("wurst"), encodingManager, false, new GraphExtension.NoOpExtension());
        graph.create(0);
        EdgeIteratorState d = graph.edge(4, 5);
        d.setFlags(pt.setAccess(d.getFlags(), true, false));
        d.setFlags(foot.setAccess(d.getFlags(), true, false));

        int edgeId = graph.getAllEdges().length();

        GtfsStorage gtfsStorage = mock(GtfsStorage.class);
        RealtimeFeed realtimeFeed = mock(RealtimeFeed.class);
        List<VirtualEdgeIteratorState> extraEdges = new ArrayList<>();
        VirtualEdgeIteratorState e = new VirtualEdgeIteratorState(-1, edgeId++, 0, 1, 0.0, 0L, "", new PointList());
        e.setFlags(foot.setAccess(e.getFlags(), true, false));
        extraEdges.add(e);
        VirtualEdgeIteratorState f = new VirtualEdgeIteratorState(-1, edgeId++, 1, 2, 0.0, 0L, "", new PointList());
        f.setFlags(foot.setAccess(f.getFlags(), true, false));
        extraEdges.add(f);
        VirtualEdgeIteratorState g = new VirtualEdgeIteratorState(-1, edgeId++, 1, 3, 0.0, 0L, "", new PointList());
        g.setFlags(foot.setAccess(g.getFlags(), true, false));
        extraEdges.add(g);
        VirtualEdgeIteratorState h = new VirtualEdgeIteratorState(-1, edgeId++, 4, 7, 0.0, 0L, "", new PointList());
        h.setFlags(foot.setAccess(h.getFlags(), true, false));
        extraEdges.add(h);

        GraphExplorer testee = new GraphExplorer(graph, new FastestWeighting(foot), pt, gtfsStorage, realtimeFeed, false, extraEdges, false, 5.0);
        assertThat(() -> testee.exploreEdgesAround(new Label(0, -1, 0, 0, 0, 0.0, 0L, 0, 0, false, null)).map(Object::toString).iterator(),
                contains(e.toString()));
        assertThat(() -> testee.exploreEdgesAround(new Label(0, -1, 1, 0, 0, 0.0, 0L, 0, 0, false, null)).map(Object::toString).iterator(),
                contains(f.toString(), g.toString()));
        assertThat(() -> testee.exploreEdgesAround(new Label(0, -1, 4, 0, 0, 0.0, 0L, 0, 0, false, null)).map(Object::toString).iterator(),
                contains(d.toString(), h.toString()));
    }

    @Test
    public void testExtraEdgesWithNonEmptyGraphAndQueryGraph() {
        GraphHopperStorage graph = new GraphHopperStorage(new RAMDirectory("wurst"), encodingManager, false, new GraphExtension.NoOpExtension());
        graph.create(0);
        EdgeIteratorState c = graph.edge(4, 3);
        c.setFlags(pt.setAccess(c.getFlags(), true, false));
        c.setFlags(foot.setAccess(c.getFlags(), true, false));
        PointList cp = new PointList();
        c.setWayGeometry(cp);
        EdgeIteratorState d = graph.edge(4, 5);
        d.setFlags(pt.setAccess(d.getFlags(), true, false));
        d.setFlags(foot.setAccess(d.getFlags(), true, false));
        PointList dp = new PointList();
        d.setWayGeometry(dp);
        graph.getNodeAccess().setNode(3, 3.0, 3.0);
        graph.getNodeAccess().setNode(4, 4.0, 4.0);
        graph.getNodeAccess().setNode(5, 5.0, 5.0);


        int edgeId = graph.getAllEdges().length();

        GtfsStorage gtfsStorage = mock(GtfsStorage.class);
        RealtimeFeed realtimeFeed = mock(RealtimeFeed.class);
        List<VirtualEdgeIteratorState> extraEdges = new ArrayList<>();
        VirtualEdgeIteratorState e = new VirtualEdgeIteratorState(-1, edgeId++, 0, 1, 0.0, 0L, "", new PointList());
        e.setFlags(foot.setAccess(e.getFlags(), true, false));
        extraEdges.add(e);
        VirtualEdgeIteratorState f = new VirtualEdgeIteratorState(-1, edgeId++, 1, 2, 0.0, 0L, "", new PointList());
        f.setFlags(foot.setAccess(f.getFlags(), true, false));
        extraEdges.add(f);
        VirtualEdgeIteratorState g = new VirtualEdgeIteratorState(-1, edgeId++, 1, 3, 0.0, 0L, "", new PointList());
        g.setFlags(foot.setAccess(g.getFlags(), true, false));
        extraEdges.add(g);
        VirtualEdgeIteratorState h = new VirtualEdgeIteratorState(-1, edgeId++, 4, 7, 0.0, 0L, "", new PointList());
        h.setFlags(foot.setAccess(h.getFlags(), true, false));
        extraEdges.add(h);

        WrapperGraph wrapperGraph = new WrapperGraph(graph, extraEdges);
        QueryGraph queryGraph = new QueryGraph(wrapperGraph);

        QueryResult point1 = new QueryResult(3.5, 3.5);
        QueryResult point2 = new QueryResult(4.5, 4.5);
        point1.setClosestEdge(c);
        point1.setQueryDistance(0.0);
        point1.setWayIndex(0);
        point1.setSnappedPosition(QueryResult.Position.EDGE);
        point1.calcSnappedPoint(new DistanceCalc2D());
        point2.setClosestEdge(d);
        point2.setQueryDistance(0.0);
        point2.setWayIndex(0);
        point2.setSnappedPosition(QueryResult.Position.EDGE);
        point2.calcSnappedPoint(new DistanceCalc2D());
        queryGraph.lookup(point1, point2);

        GraphExplorer testee = new GraphExplorer(queryGraph, new FastestWeighting(foot), pt, gtfsStorage, realtimeFeed, false, extraEdges, false, 5.0);
        assertThat(() -> testee.exploreEdgesAround(new Label(0, -1, 0, 0, 0, 0.0, 0L, 0, 0, false, null)).map(Object::toString).iterator(),
                contains(e.toString()));
        assertThat(() -> testee.exploreEdgesAround(new Label(0, -1, 1, 0, 0, 0.0, 0L, 0, 0, false, null)).map(Object::toString).iterator(),
                contains(f.toString(), g.toString()));
        assertThat(() -> testee.exploreEdgesAround(new Label(0, -1, 4, 0, 0, 0.0, 0L, 0, 0, false, null)).map(Object::toString).iterator(),
                contains(containsString("4->8"), containsString("4->9"), containsString("4->7")));
        assertThat((Iterable<String>) () -> testee.exploreEdgesAround(new Label(0, -1, 7, 0, 0, 0.0, 0L, 0, 0, false, null)).map(Object::toString).iterator(),
                emptyIterable());

    }


}
