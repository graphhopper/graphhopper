package com.graphhopper.isochrone.algorithm;

import com.graphhopper.json.Statement;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.SimpleBooleanEncodedValue;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.routing.weighting.custom.CustomModelParser;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 * @author Michael Zilske
 */
public class ShortestPathTreeTest {

    private static class TimeBasedUTurnCost implements TurnCostProvider {

        private final int turnMillis;

        public TimeBasedUTurnCost(int turnMillis) {
            this.turnMillis = turnMillis;
        }

        @Override
        public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
            return calcTurnMillis(inEdge, viaNode, outEdge) / 1000.0;
        }

        @Override
        public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
            return inEdge == outEdge ? turnMillis : 0;
        }
    }

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

    private final BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
    private final DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
    private final BooleanEncodedValue ferryEnc = new SimpleBooleanEncodedValue("ferry", false);
    private final EncodingManager encodingManager = EncodingManager.start().add(accessEnc).add(speedEnc).add(ferryEnc).build();
    private BaseGraph graph;

    private Weighting createWeighting() {
        return createWeighting(TurnCostProvider.NO_TURN_COST_PROVIDER);
    }

    private Weighting createWeighting(TurnCostProvider turnCostProvider) {
        CustomModel customModel = CustomModelParser.createBaseCustomModel(accessEnc, speedEnc);
        return CustomModelParser.createWeighting(encodingManager, turnCostProvider, customModel);
    }

    @BeforeEach
    public void setUp() {
        graph = new BaseGraph.Builder(encodingManager).create();
        //         8
        //        /
        // 0-1-2-3
        // |/|/ /|
        // 4-5-- |
        // |/ \--7
        // 6----/
        GHUtility.setSpeed(10, true, false, accessEnc, speedEnc, ((Graph) graph).edge(0, 1).setDistance(70));
        GHUtility.setSpeed(20, true, false, accessEnc, speedEnc, ((Graph) graph).edge(0, 4).setDistance(50));

        GHUtility.setSpeed(10, true, true, accessEnc, speedEnc, ((Graph) graph).edge(1, 4).setDistance(70));
        GHUtility.setSpeed(10, true, true, accessEnc, speedEnc, ((Graph) graph).edge(1, 5).setDistance(70));
        GHUtility.setSpeed(10, true, true, accessEnc, speedEnc, ((Graph) graph).edge(1, 2).setDistance(200));

        GHUtility.setSpeed(10, true, false, accessEnc, speedEnc, ((Graph) graph).edge(5, 2).setDistance(50));
        GHUtility.setSpeed(10, true, false, accessEnc, speedEnc, ((Graph) graph).edge(2, 3).setDistance(50));

        GHUtility.setSpeed(20, true, false, accessEnc, speedEnc, ((Graph) graph).edge(5, 3).setDistance(110));
        GHUtility.setSpeed(10, true, false, accessEnc, speedEnc, ((Graph) graph).edge(3, 7).setDistance(70));

        GHUtility.setSpeed(20, true, false, accessEnc, speedEnc, ((Graph) graph).edge(4, 6).setDistance(50));
        GHUtility.setSpeed(10, true, false, accessEnc, speedEnc, ((Graph) graph).edge(5, 4).setDistance(70));

        GHUtility.setSpeed(10, true, false, accessEnc, speedEnc, ((Graph) graph).edge(5, 6).setDistance(70));
        GHUtility.setSpeed(20, true, false, accessEnc, speedEnc, ((Graph) graph).edge(7, 5).setDistance(50));

        GHUtility.setSpeed(20, true, true, accessEnc, speedEnc, ((Graph) graph).edge(6, 7).setDistance(50));
        GHUtility.setSpeed(20, true, true, accessEnc, speedEnc, ((Graph) graph).edge(3, 8).setDistance(25));
    }

    private int countDirectedEdges(BaseGraph graph) {
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
    public void testSPTAndIsochrone25Seconds() {
        List<ShortestPathTree.IsoLabel> result = new ArrayList<>();
        ShortestPathTree instance = new ShortestPathTree(graph, createWeighting(), false, TraversalMode.NODE_BASED);
        instance.setTimeLimit(25_000);
        instance.search(0, result::add);
        assertEquals(3, result.size());
        assertAll(
                () -> assertEquals(0, result.get(0).time), () -> assertEquals(0, result.get(0).node),
                () -> assertEquals(9000, result.get(1).time), () -> assertEquals(4, result.get(1).node),
                () -> assertEquals(18000, result.get(2).time), () -> assertEquals(6, result.get(2).node)
        );
        Collection<ShortestPathTree.IsoLabel> isochroneEdges = instance.getIsochroneEdges();
        assertArrayEquals(new int[]{1, 7}, isochroneEdges.stream().mapToInt(l -> l.node).sorted().toArray());
    }

    @Test
    public void testSPT26Seconds() {
        List<ShortestPathTree.IsoLabel> result = new ArrayList<>();
        ShortestPathTree instance = new ShortestPathTree(graph, createWeighting(), false, TraversalMode.NODE_BASED);
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
        ShortestPathTree instance = new ShortestPathTree(graph, createWeighting(), false, TraversalMode.NODE_BASED);
        instance.setTimeLimit(Double.MAX_VALUE);
        instance.search(0, result::add);
        assertEquals(9, result.size());
        assertAll(
                () -> assertEquals(0, result.get(0).time), () -> assertEquals(0, result.get(0).node),
                () -> assertEquals(9000, result.get(1).time), () -> assertEquals(4, result.get(1).node),
                () -> assertEquals(18000, result.get(2).time), () -> assertEquals(6, result.get(2).node),
                () -> assertEquals(25200, result.get(3).time), () -> assertEquals(1, result.get(3).node),
                () -> assertEquals(27000, result.get(4).time), () -> assertEquals(7, result.get(4).node),
                () -> assertEquals(36000, result.get(5).time), () -> assertEquals(5, result.get(5).node),
                () -> assertEquals(54000, result.get(6).time), () -> assertEquals(2, result.get(6).node),
                () -> assertEquals(55800, result.get(7).time), () -> assertEquals(3, result.get(7).node),
                () -> assertEquals(60300, result.get(8).time), () -> assertEquals(8, result.get(8).node)
        );
    }

    @Test
    public void testFerry() {
        AllEdgesIterator allEdges = graph.getAllEdges();
        while (allEdges.next()) {
            allEdges.set(ferryEnc, false);
        }
        EdgeIteratorState edge = findEdge(6, 7);
        edge.set(ferryEnc, true);

        List<ShortestPathTree.IsoLabel> result = new ArrayList<>();
        CustomModel customModel = CustomModelParser.createBaseCustomModel(accessEnc, speedEnc);
        customModel.addToPriority(If("ferry", MULTIPLY, "0.005"));
        CustomWeighting weighting = CustomModelParser.createWeighting(encodingManager, TurnCostProvider.NO_TURN_COST_PROVIDER, customModel);
        ShortestPathTree instance = new ShortestPathTree(graph, weighting, false, TraversalMode.NODE_BASED);
        instance.setTimeLimit(30_000);
        instance.search(0, result::add);
        assertEquals(4, result.size());
        assertAll(
                () -> assertEquals(0, result.get(0).time), () -> assertEquals(0, result.get(0).node),
                () -> assertEquals(9000, result.get(1).time), () -> assertEquals(4, result.get(1).node),
                () -> assertEquals(18000, result.get(2).time), () -> assertEquals(6, result.get(2).node),
                () -> assertEquals(25200, result.get(3).time), () -> assertEquals(1, result.get(3).node)
        );
        assertEquals(7, instance.getVisitedNodes(), "If this increases, make sure we are not traversing entire graph irl");
    }


    @Test
    public void testEdgeBasedWithFreeUTurns() {
        List<ShortestPathTree.IsoLabel> result = new ArrayList<>();
        ShortestPathTree instance = new ShortestPathTree(graph, createWeighting(), false, TraversalMode.EDGE_BASED);
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
        Weighting fastestWeighting = createWeighting(FORBIDDEN_UTURNS);
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
    public void testEdgeBasedWithFinitePositiveUTurnCost() {
        TimeBasedUTurnCost turnCost = new TimeBasedUTurnCost(80000);
        Weighting fastestWeighting = createWeighting(turnCost);
        List<ShortestPathTree.IsoLabel> result = new ArrayList<>();
        ShortestPathTree instance = new ShortestPathTree(graph, fastestWeighting, false, TraversalMode.EDGE_BASED);
        instance.setTimeLimit(Double.MAX_VALUE);
        instance.search(0, result::add);
        // Just like with forbidden U-turns, but last thing is I can get out of the dead-end
        assertEquals(countDirectedEdges(graph) + 1, result.size());
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
                () -> assertEquals(126000, result.get(19).time),
                () -> assertEquals(144800, result.get(20).time)
        );
    }

    @Test
    public void testEdgeBasedWithSmallerUTurnCost() {
        TimeBasedUTurnCost turnCost = new TimeBasedUTurnCost(20000);
        List<ShortestPathTree.IsoLabel> result = new ArrayList<>();
        ShortestPathTree instance = new ShortestPathTree(graph, createWeighting(turnCost), false, TraversalMode.EDGE_BASED);
        instance.setTimeLimit(Double.MAX_VALUE);
        instance.search(0, result::add);
        // Something in between
        assertEquals(countDirectedEdges(graph) + 1, result.size());
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
                () -> assertEquals(56000, result.get(11).time),
                () -> assertEquals(60300, result.get(12).time),
                () -> assertEquals(61200, result.get(13).time),
                () -> assertEquals(61200, result.get(14).time),
                () -> assertEquals(61200, result.get(15).time),
                () -> assertEquals(72000, result.get(16).time),
                () -> assertEquals(81000, result.get(17).time),
                () -> assertEquals(84800, result.get(18).time),
                () -> assertEquals(97200, result.get(19).time),
                () -> assertEquals(126000, result.get(20).time)
        );
    }

    @Test
    public void testSearchByDistance() {
        List<ShortestPathTree.IsoLabel> result = new ArrayList<>();
        ShortestPathTree instance = new ShortestPathTree(graph, createWeighting(), false, TraversalMode.NODE_BASED);
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

    EdgeIteratorState findEdge(int a, int b) {
        EdgeIterator edgeIterator = graph.createEdgeExplorer().setBaseNode(a);
        while (edgeIterator.next()) {
            if (edgeIterator.getAdjNode() == b) {
                return edgeIterator;
            }
        }
        throw new RuntimeException("nope");
    }

}
