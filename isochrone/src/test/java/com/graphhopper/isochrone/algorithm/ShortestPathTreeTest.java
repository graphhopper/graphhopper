package com.graphhopper.isochrone.algorithm;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class ShortestPathTreeTest {

    private final EncodingManager encodingManager = EncodingManager.create("car");
    private final FlagEncoder carEncoder = encodingManager.getEncoder("car");
    private GraphHopperStorage graph;

    @Before
    public void setUp() {
        graph = new GraphHopperStorage(new RAMDirectory(), encodingManager, false);
        graph.create(1000);
    }

    @After
    public void tearDown() {
        graph.close();
    }

    // 0-1-2-3
    // |/|/ /|
    // 4-5-- |
    // |/ \--7
    // 6----/
    private void fillTestGraph(Graph graph) {
        GHUtility.setProperties(graph.edge(0, 1).setDistance(70), carEncoder, 10, true, false);
        GHUtility.setProperties(graph.edge(0, 4).setDistance(50), carEncoder, 20, true, false);

        GHUtility.setProperties(graph.edge(1, 4).setDistance(70), carEncoder, 10, true, true);
        GHUtility.setProperties(graph.edge(1, 5).setDistance(70), carEncoder, 10, true, true);
        GHUtility.setProperties(graph.edge(1, 2).setDistance(200), carEncoder, 10, true, true);

        GHUtility.setProperties(graph.edge(5, 2).setDistance(50), carEncoder, 10, true, false);
        GHUtility.setProperties(graph.edge(2, 3).setDistance(50), carEncoder, 10, true, false);

        GHUtility.setProperties(graph.edge(5, 3).setDistance(110), carEncoder, 20, true, false);
        GHUtility.setProperties(graph.edge(3, 7).setDistance(70), carEncoder, 10, true, false);

        GHUtility.setProperties(graph.edge(4, 6).setDistance(50), carEncoder, 20, true, false);
        GHUtility.setProperties(graph.edge(5, 4).setDistance(70), carEncoder, 10, true, false);

        GHUtility.setProperties(graph.edge(5, 6).setDistance(70), carEncoder, 10, true, false);
        GHUtility.setProperties(graph.edge(7, 5).setDistance(50), carEncoder, 20, true, false);

        GHUtility.setProperties(graph.edge(6, 7).setDistance(50), carEncoder, 20, true, true);
    }

    @Test
    public void testSearch60Seconds() {
        fillTestGraph(graph);
        ShortestPathTree instance = new ShortestPathTree(graph, new FastestWeighting(carEncoder, new PMap()), false);
        instance.setTimeLimit(60);
        List<Set<Integer>> res = searchFromNode0Into5Buckets(instance);
        assertEquals("[[0, 4], [6], [1, 7], [5], [2, 3]]", res.toString());
    }

    @Test
    public void testSearch30Seconds() {
        fillTestGraph(graph);
        ShortestPathTree instance = new ShortestPathTree(graph, new FastestWeighting(carEncoder, new PMap()), false);
        instance.setTimeLimit(30);
        List<Set<Integer>> res = searchFromNode0Into5Buckets(instance);
        assertEquals("[[0], [4], [], [6], [1, 7]]", res.toString());
    }

    private List<Set<Integer>> searchFromNode0Into5Buckets(ShortestPathTree instance) {
        final double bucketSize = instance.limit / 5;
        final List<Set<Integer>> list = new ArrayList<>(5);

        for (int i = 0; i < 5; i++) {
            list.add(new HashSet<>());
        }

        instance.search(0, isoLabelWithCoordinates -> {
            int bucketIndex = (int) (isoLabelWithCoordinates.timeMillis / bucketSize);
            if (bucketIndex < 0) {
                throw new IllegalArgumentException("edge cannot have negative explore value " + isoLabelWithCoordinates.nodeId + ", " + isoLabelWithCoordinates);
            } else if (bucketIndex == 5) {
                bucketIndex = 5 - 1;
            } else if (bucketIndex > 5) {
                return;
            }

            list.get(bucketIndex).add(isoLabelWithCoordinates.nodeId);
        });
        return list;
    }
}
