package com.graphhopper.isochrone.algorithm;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Peter Karich
 * @author Michael Zilske
 */
public class ShortestPathTreeTest {

    public static final TurnCostProvider FORBIDDEN_UTURNS = new TurnCostProvider() {
        @Override
        public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
            return inEdge == outEdge ? Double.POSITIVE_INFINITY : 0;
        }

        @Override
        public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
            return 0;
        }

    };

    private final EncodingManager encodingManager = EncodingManager.create("car");
    private final FlagEncoder carEncoder = encodingManager.getEncoder("car");
    private GraphHopperStorage graph;

    @BeforeEach
    public void setUp() {
        graph = new GraphHopperStorage(new RAMDirectory(), encodingManager, false);
        graph.create(1000);

        //         8
        //        /
        // 0-1-2-3
        // |/|/ /|
        // 4-5-- |
        // |/ \--7
        // 6----/
        GHUtility.setProperties(((Graph) graph).edge(0, 1).setDistance(70), carEncoder, 10, true, false);
        GHUtility.setProperties(((Graph) graph).edge(0, 4).setDistance(50), carEncoder, 20, true, false);

        GHUtility.setProperties(((Graph) graph).edge(1, 4).setDistance(70), carEncoder, 10, true, true);
        GHUtility.setProperties(((Graph) graph).edge(1, 5).setDistance(70), carEncoder, 10, true, true);
        GHUtility.setProperties(((Graph) graph).edge(1, 2).setDistance(200), carEncoder, 10, true, true);

        GHUtility.setProperties(((Graph) graph).edge(5, 2).setDistance(50), carEncoder, 10, true, false);
        GHUtility.setProperties(((Graph) graph).edge(2, 3).setDistance(50), carEncoder, 10, true, false);

        GHUtility.setProperties(((Graph) graph).edge(5, 3).setDistance(110), carEncoder, 20, true, false);
        GHUtility.setProperties(((Graph) graph).edge(3, 7).setDistance(70), carEncoder, 10, true, false);

        GHUtility.setProperties(((Graph) graph).edge(4, 6).setDistance(50), carEncoder, 20, true, false);
        GHUtility.setProperties(((Graph) graph).edge(5, 4).setDistance(70), carEncoder, 10, true, false);

        GHUtility.setProperties(((Graph) graph).edge(5, 6).setDistance(70), carEncoder, 10, true, false);
        GHUtility.setProperties(((Graph) graph).edge(7, 5).setDistance(50), carEncoder, 20, true, false);

        GHUtility.setProperties(((Graph) graph).edge(6, 7).setDistance(50), carEncoder, 20, true, true);
        GHUtility.setProperties(((Graph) graph).edge(3, 8).setDistance(25), carEncoder, 20, true, true);
    }

    private int countDirectedEdges(GraphHopperStorage graph) {
        BooleanEncodedValue accessEnc = carEncoder.getAccessEnc();
        int result = 0;
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            if (iter.get(accessEnc))
                result++;
            if (iter.getReverse(accessEnc))
                result++;
        }
        return result;
    }

    @AfterEach
    public void tearDown() {
        graph.close();
    }

    @Test
    public void testSearch25Seconds() {
        List<ShortestPathTree.IsoLabel> result = new ArrayList<>();
        ShortestPathTree instance = new ShortestPathTree(graph, new FastestWeighting(carEncoder, new PMap()), false, TraversalMode.NODE_BASED);
        instance.setTimeLimit(26_000);
        instance.search(0, result::add);
        assertEquals(4, result.size());
        assertAll(
                () -> assertEquals(0, result.get(0).time),
                () -> assertEquals(9000, result.get(1).time),
                () -> assertEquals(18000, result.get(2).time),
                () -> assertEquals(25200, result.get(3).time)
        );
    }

    @Test
    public void testNoTimeLimit() {
        List<ShortestPathTree.IsoLabel> result = new ArrayList<>();
        ShortestPathTree instance = new ShortestPathTree(graph, new FastestWeighting(carEncoder, new PMap()), false, TraversalMode.NODE_BASED);
        instance.setTimeLimit(Double.MAX_VALUE);
        instance.search(0, result::add);
        assertEquals(9, result.size());
        assertAll(
                () -> assertEquals(0, result.get(0).time),
                () -> assertEquals(9000, result.get(1).time),
                () -> assertEquals(18000, result.get(2).time),
                () -> assertEquals(25200, result.get(3).time),
                () -> assertEquals(27000, result.get(4).time),
                () -> assertEquals(36000, result.get(5).time),
                () -> assertEquals(54000, result.get(6).time),
                () -> assertEquals(55800, result.get(7).time)
        );
    }

    @Test
    public void testEdgeBasedWithFreeUTurns() {
        List<ShortestPathTree.IsoLabel> result = new ArrayList<>();
        ShortestPathTree instance = new ShortestPathTree(graph, new FastestWeighting(carEncoder, new PMap()), false, TraversalMode.EDGE_BASED);
        instance.setTimeLimit(Double.MAX_VALUE);
        instance.search(0, result::add);
        // The origin, and every end of every directed edge, are traversed.
        assertEquals(countDirectedEdges(graph) + 1, result.size());
        assertAll(
                () -> assertEquals(0, result.get(0).time),
                () -> assertEquals(9000, result.get(1).time),
                () -> assertEquals(18000, result.get(2).time),
                () -> assertEquals(25200, result.get(3).time),
                () -> assertEquals(27000, result.get(4).time),
                () -> assertEquals(34200, result.get(5).time),
                () -> assertEquals(36000, result.get(6).time),
                () -> assertEquals(36000, result.get(7).time),
                () -> assertEquals(50400, result.get(8).time),
                () -> assertEquals(50400, result.get(9).time),
                () -> assertEquals(54000, result.get(10).time),
                () -> assertEquals(55800, result.get(11).time),
                () -> assertEquals(60300, result.get(12).time),
                () -> assertEquals(61200, result.get(13).time),
                () -> assertEquals(61200, result.get(14).time),
                () -> assertEquals(61200, result.get(15).time),
                () -> assertEquals(64800, result.get(16).time),
                () -> assertEquals(72000, result.get(17).time),
                () -> assertEquals(81000, result.get(18).time),
                () -> assertEquals(97200, result.get(19).time),
                () -> assertEquals(126000, result.get(20).time)
        );
    }

    @Test
    public void testEdgeBasedWithForbiddenUTurns() {
        FastestWeighting fastestWeighting = new FastestWeighting(carEncoder, new PMap(), FORBIDDEN_UTURNS);
        List<ShortestPathTree.IsoLabel> result = new ArrayList<>();
        ShortestPathTree instance = new ShortestPathTree(graph, fastestWeighting, false, TraversalMode.EDGE_BASED);
        instance.setTimeLimit(Double.MAX_VALUE);
        instance.search(0, result::add);
        // Every directed edge of the graph, plus the origin, minus one edge for the dead end, are traversed.
        assertEquals(countDirectedEdges(graph) + 1 - 1, result.size());
        assertAll(
                () -> assertEquals(0, result.get(0).time),
                () -> assertEquals(9000, result.get(1).time),
                () -> assertEquals(18000, result.get(2).time),
                () -> assertEquals(25200, result.get(3).time),
                () -> assertEquals(27000, result.get(4).time),
                () -> assertEquals(34200, result.get(5).time),
                () -> assertEquals(36000, result.get(6).time),
                () -> assertEquals(50400, result.get(7).time),
                () -> assertEquals(50400, result.get(8).time),
                () -> assertEquals(54000, result.get(9).time),
                () -> assertEquals(55800, result.get(10).time),
                () -> assertEquals(60300, result.get(11).time),
                () -> assertEquals(61200, result.get(12).time),
                () -> assertEquals(61200, result.get(13).time),
                () -> assertEquals(61200, result.get(14).time),
                () -> assertEquals(72000, result.get(15).time),
                () -> assertEquals(81000, result.get(16).time),
                () -> assertEquals(90000, result.get(17).time),
                () -> assertEquals(97200, result.get(18).time),
                () -> assertEquals(126000, result.get(19).time)
        );
    }

    @Test
    public void testSearchByDistance() {
        List<ShortestPathTree.IsoLabel> result = new ArrayList<>();
        ShortestPathTree instance = new ShortestPathTree(graph, new FastestWeighting(carEncoder, new PMap()), false, TraversalMode.NODE_BASED);
        instance.setDistanceLimit(110.0);
        instance.search(5, result::add);
        assertEquals(6, result.size());
        // We are searching by time, but terminating by distance.
        // Expected distance values are out of search order,
        // and we cannot terminate at 110 because we still need the 70's.
        // And we do not want the node at 120, even though it is within the space of the search.
        assertAll(
                () -> assertEquals(0.0, result.get(0).distance),
                () -> assertEquals(50.0, result.get(1).distance),
                () -> assertEquals(110.0, result.get(2).distance),
                () -> assertEquals(70.0, result.get(3).distance),
                () -> assertEquals(70.0, result.get(4).distance),
                () -> assertEquals(70.0, result.get(5).distance)
        );
    }

}
