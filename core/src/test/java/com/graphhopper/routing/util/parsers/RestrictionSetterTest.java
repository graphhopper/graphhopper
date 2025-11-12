package com.graphhopper.routing.util.parsers;

import com.carrotsearch.hppc.BitSet;
import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntIndexedContainer;
import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.TurnRestriction;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static org.junit.jupiter.api.Assertions.*;

public class RestrictionSetterTest {
    private DecimalEncodedValue speedEnc;
    private BooleanEncodedValue turnRestrictionEnc;
    private BooleanEncodedValue turnRestrictionEnc2;
    private BaseGraph graph;
    private RestrictionSetter r;

    @BeforeEach
    void setup() {
        speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
        turnRestrictionEnc = TurnRestriction.create("car1");
        turnRestrictionEnc2 = TurnRestriction.create("car2");
        EncodingManager encodingManager = EncodingManager.start()
                .add(speedEnc)
                .add(turnRestrictionEnc)
                .add(turnRestrictionEnc2)
                .build();
        graph = new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();
        r = new RestrictionSetter(graph, List.of(turnRestrictionEnc, turnRestrictionEnc2));
    }

    @Test
    void viaNode_no() {
        // 0-1-2
        //   | |
        //   3-4
        int a = edge(0, 1);
        int b = edge(1, 2);
        edge(1, 3);
        edge(2, 4);
        edge(3, 4);
        setRestrictions(RestrictionSetter.createViaNodeRestriction(a, 1, b));
        assertPath(0, 2, nodes(0, 1, 3, 4, 2));
    }

    @Test
    void viaEdge_no() {
        //     4
        //  a b|c
        // 0-1-2-3
        //   | |
        //   5 6
        //   | |
        //   8-9
        int a = edge(0, 1);
        int b = edge(1, 2);
        int c = edge(2, 3);
        edge(2, 4);
        edge(1, 5);
        edge(5, 8);
        edge(2, 6);
        edge(6, 9);
        edge(8, 9);
        setRestrictions(
                createViaEdgeRestriction(a, b, c)
        );
        // turning from a to b and then to c is not allowed
        assertPath(0, 3, nodes(0, 1, 5, 8, 9, 6, 2, 3));
        // turning from a to b, or b to c is still allowed
        assertPath(0, 4, nodes(0, 1, 2, 4));
        assertPath(5, 3, nodes(5, 1, 2, 3));
    }

    @Test
    void viaEdge_withOverlap() {
        //   a   b   c   d
        // 0---1---2---3---4
        //     |s  |t  |u
        //     5   6   7
        int a = edge(0, 1);
        int b = edge(1, 2);
        int c = edge(2, 3);
        int d = edge(3, 4);
        int s = edge(1, 5);
        int t = edge(2, 6);
        int u = edge(3, 7);

        setRestrictions(
                createViaEdgeRestriction(a, b, c),
                createViaEdgeRestriction(b, c, d)
        );

        assertPath(0, 3, null);
        assertPath(0, 6, nodes(0, 1, 2, 6));
        assertPath(5, 3, nodes(5, 1, 2, 3));
        assertPath(5, 6, nodes(5, 1, 2, 6));

        assertPath(1, 4, null);
        assertPath(1, 7, nodes(1, 2, 3, 7));
        assertPath(6, 4, nodes(6, 2, 3, 4));
        assertPath(6, 7, nodes(6, 2, 3, 7));
    }

    @Test
    void viaEdge_no_withOverlap_more_complex() {
        //    0   1
        //    | a |
        // 2--3---4--5
        //   b|   |d
        // 6--7---8--9
        //    | c |
        //   10---11
        int s = edge(0, 3);
        edge(1, 4);
        edge(2, 3);
        int a = edge(3, 4);
        int t = edge(4, 5);
        int b = edge(3, 7);
        int d = edge(4, 8);
        edge(6, 7);
        int c = edge(7, 8);
        edge(8, 9);
        edge(7, 10);
        edge(8, 11);
        edge(10, 11);
        setRestrictions(
                createViaNodeRestriction(t, 4, d),
                createViaNodeRestriction(s, 3, a),
                createViaEdgeRestriction(a, b, c),
                createViaEdgeRestriction(b, c, d),
                createViaEdgeRestriction(c, d, a),
                createViaEdgeRestriction(d, a, b)
        );

        assertPath(0, 9, nodes(0, 3, 7, 8, 9));
        assertPath(5, 9, nodes(5, 4, 3, 7, 10, 11, 8, 9));
        assertPath(5, 2, nodes(5, 4, 3, 2));
        assertPath(0, 10, nodes(0, 3, 7, 10));
        assertPath(6, 9, nodes(6, 7, 8, 9));
    }

    @Test
    void common_via_edge_opposite_direction() {
        //    a   b
        //  0---1---2
        //      |c
        //  3---4---5
        //    d   e
        int a = edge(0, 1);
        int b = edge(1, 2);
        int c = edge(1, 4);
        int d = edge(3, 4);
        int e = edge(4, 5);

        setRestrictions(
                // A rather common case where u-turns between the a-b and d-e lanes are forbidden.
                createViaEdgeRestriction(b, c, e),
                createViaEdgeRestriction(d, c, a)
        );

        assertPath(0, 2, nodes(0, 1, 2));
        assertPath(0, 5, nodes(0, 1, 4, 5));
        assertPath(0, 3, nodes(0, 1, 4, 3));
        assertPath(2, 0, nodes(2, 1, 0));
        assertPath(2, 3, nodes(2, 1, 4, 3));
        assertPath(2, 5, null);
        assertPath(3, 0, null);
        assertPath(3, 2, nodes(3, 4, 1, 2));
        assertPath(3, 5, nodes(3, 4, 5));
        assertPath(5, 0, nodes(5, 4, 1, 0));
        assertPath(5, 2, nodes(5, 4, 1, 2));
        assertPath(5, 3, nodes(5, 4, 3));
    }

    @Test
    void viaEdge_common_via_edge_opposite_direction_edge0() {
        //    a   v   b
        //  0---1---2---3
        int v = edge(1, 2);
        int a = edge(0, 1);
        int b = edge(2, 3);

        setRestrictions(
                // This is rather academic, but with our initial implementation for the special case
                // where the via edge is edge 0 we could not use two restrictions even though the
                // edge is used in opposite directions.
                createViaEdgeRestriction(a, v, b),
                createViaEdgeRestriction(b, v, a)
        );
        assertPath(0, 3, null);
        assertPath(1, 3, nodes(1, 2, 3));
        assertPath(3, 0, null);
        assertPath(2, 0, nodes(2, 1, 0));
    }

    @Test
    void common_via_edge_same_direction() {
        //    a   b
        //  0---1---2
        //      |c
        //  3---4---5
        //    d   e
        int a = edge(0, 1);
        int b = edge(1, 2);
        int c = edge(1, 4);
        int d = edge(3, 4);
        int e = edge(4, 5);

        assertPath(0, 3, nodes(0, 1, 4, 3));
        assertPath(2, 5, nodes(2, 1, 4, 5));
        // Here edge c is used by both restrictions in the same direction. This requires a second
        // artificial edge and our first implementation did not allow this, see #2907
        setRestrictions(
                createViaEdgeRestriction(a, c, d),
                createViaEdgeRestriction(b, c, e)
        );
        assertPath(0, 3, null);
        assertPath(3, 0, nodes(3, 4, 1, 0));
        assertPath(2, 5, null);
        assertPath(5, 2, nodes(5, 4, 1, 2));
        assertPath(0, 2, nodes(0, 1, 2));
        assertPath(1, 3, nodes(1, 4, 3));
        assertPath(0, 5, nodes(0, 1, 4, 5));
        assertPath(2, 3, nodes(2, 1, 4, 3));
    }

    @Test
    void viaEdgeAndNode() {
        // 4-0-1-2
        //     |
        //     3
        int e0_1 = edge(0, 1);
        int e0_4 = edge(0, 4);
        int e1_2 = edge(1, 2);
        int e1_3 = edge(1, 3);
        assertPath(0, 3, nodes(0, 1, 3));
        assertPath(4, 2, nodes(4, 0, 1, 2));
        setRestrictions(
                createViaEdgeRestriction(e0_4, e0_1, e1_2),
                createViaNodeRestriction(e0_1, 1, e1_3)
        );
        assertPath(4, 2, null);
        assertPath(0, 3, null);
    }

    @Test
    void pTurn() {
        // 0-1-2
        //   |
        //   3-|
        //   | |
        //   4-|
        int e0_1 = edge(0, 1);
        int e1_2 = edge(1, 2);
        int e1_3 = edge(1, 3);
        int e3_4 = edge(3, 4);
        int e4_3 = edge(4, 3);

        setRestrictions(
                createViaNodeRestriction(e0_1, 1, e1_2),
                // here the edges e3_4 and e4_3 share two nodes, but the restrictions are still well-defined
                createViaEdgeRestriction(e1_3, e4_3, e3_4),
                // attention: this restriction looks like it makes the previous one redundant,
                //            but it doesn't, because it points the other way
                createViaNodeRestriction(e4_3, 3, e3_4),
                createViaNodeRestriction(e3_4, 4, e4_3)
        );
        assertPath(0, 2, null);
    }

    @Test
    void redundantRestriction_simple() {
        // 0-1-2
        int e0_1 = edge(0, 1);
        int e1_2 = edge(1, 2);
        setRestrictions(
                createViaNodeRestriction(e0_1, 1, e1_2),
                createViaNodeRestriction(e0_1, 1, e1_2)
        );
        assertPath(0, 2, null);
    }

    @Test
    void multiViaEdge_no() {
        //   a   b
        // 0---1---2
        //    c| e |d
        //     3---4
        //   g |f
        // 5---6---7
        //       h

        int a = edge(0, 1);
        int b = edge(1, 2);
        int c = edge(1, 3);
        int d = edge(2, 4);
        int e = edge(3, 4);
        int f = edge(3, 6);
        int g = edge(5, 6);
        int h = edge(6, 7);
        setRestrictions(
                createViaEdgeRestriction(a, c, f, g)
        );
        assertPath(0, 5, nodes(0, 1, 2, 4, 3, 6, 5));
        assertPath(1, 5, nodes(1, 3, 6, 5));
        assertPath(0, 7, nodes(0, 1, 3, 6, 7));
    }

    @Test
    void multiViaEdge_overlapping() {
        //   a   b   c   d
        // 0---1---2---3---4
        //     |e  |f
        //     5   6

        int a = edge(0, 1);
        int b = edge(1, 2);
        int c = edge(2, 3);
        int d = edge(3, 4);
        int e = edge(5, 1);
        int f = edge(6, 2);
        assertPath(5, 6, nodes(5, 1, 2, 6));
        assertPath(0, 4, nodes(0, 1, 2, 3, 4));
        setRestrictions(
                createViaEdgeRestriction(e, b, f),
                createViaEdgeRestriction(a, b, c, d)
        );
        assertPath(5, 6, null);
        assertPath(0, 4, null);
        assertPath(0, 6, nodes(0, 1, 2, 6));
        assertPath(5, 4, nodes(5, 1, 2, 3, 4));
    }

    @Test
    void singleViaEdgeRestriction() {
        //   5
        //   |
        // 0-1-2-3
        //     |
        //     4
        int e0_1 = edge(0, 1);
        int e1_2 = edge(1, 2);
        int e2_3 = edge(2, 3);
        int e2_4 = edge(2, 4);
        int e5_1 = edge(5, 1);
        assertPath(0, 3, nodes(0, 1, 2, 3));
        assertPath(0, 4, nodes(0, 1, 2, 4));
        assertPath(5, 3, nodes(5, 1, 2, 3));
        assertPath(5, 4, nodes(5, 1, 2, 4));
        setRestrictions(
                createViaEdgeRestriction(e0_1, e1_2, e2_4)
        );
        assertPath(0, 3, nodes(0, 1, 2, 3));
        // turning right at 2 is forbidden, iff we come from 0
        assertPath(0, 4, null);
        assertPath(5, 3, nodes(5, 1, 2, 3));
        assertPath(5, 4, nodes(5, 1, 2, 4));
        assertEquals(6, graph.getTurnCostStorage().getTurnCostsCount());
    }

    @Test
    void multiViaEdgeRestriction() {
        //   5
        //   |
        // 0-1-6-2-3
        //       |
        //       4
        int e0_1 = edge(0, 1);
        int e1_6 = edge(1, 6);
        int e6_2 = edge(6, 2);
        int e2_3 = edge(2, 3);
        int e2_4 = edge(2, 4);
        int e5_1 = edge(5, 1);
        assertPath(0, 3, nodes(0, 1, 6, 2, 3));
        assertPath(0, 4, nodes(0, 1, 6, 2, 4));
        assertPath(5, 3, nodes(5, 1, 6, 2, 3));
        assertPath(5, 4, nodes(5, 1, 6, 2, 4));
        setRestrictions(
                createViaEdgeRestriction(e0_1, e1_6, e6_2, e2_4)
        );
        assertPath(0, 3, nodes(0, 1, 6, 2, 3));
        // turning right at 2 is forbidden, iff we come from 0
        assertPath(0, 4, null);
        assertPath(5, 3, nodes(5, 1, 6, 2, 3));
        assertPath(5, 4, nodes(5, 1, 6, 2, 4));
        assertEquals(11, graph.getTurnCostStorage().getTurnCostsCount());
    }

    @Test
    void overlappingSingleViaEdgeRestriction() {
        //     7
        //     |
        // 0-1-2-3
        //   | |
        //   5 4
        int e0_1 = edge(0, 1);
        int e1_2 = edge(1, 2);
        int e2_3 = edge(2, 3);
        int e2_4 = edge(2, 4);
        int e5_1 = edge(5, 1);
        int e2_7 = edge(2, 7);
        for (int i : new int[]{3, 4, 7}) {
            assertPath(0, i, nodes(0, 1, 2, i));
            assertPath(5, i, nodes(5, 1, 2, i));
        }
        setRestrictions(
                createViaEdgeRestriction(e0_1, e1_2, e2_3),
                createViaEdgeRestriction(e0_1, e1_2, e2_4)
        );
        // coming from 0 we cannot turn onto 3 or 4
        assertPath(0, 3, null);
        assertPath(0, 4, null);
        assertPath(0, 7, nodes(0, 1, 2, 7));
        assertPath(5, 3, nodes(5, 1, 2, 3));
        assertPath(5, 4, nodes(5, 1, 2, 4));
        assertPath(5, 7, nodes(5, 1, 2, 7));
        assertEquals(7, graph.getTurnCostStorage().getTurnCostsCount());
    }

    @Test
    void overlappingSingleViaEdgeRestriction_differentStarts() {
        //     7
        //     |
        // 0-1-2-3
        //   | |
        //   5 4
        int e0_1 = edge(0, 1);
        int e1_2 = edge(1, 2);
        int e2_3 = edge(2, 3);
        int e5_1 = edge(5, 1);
        int e2_4 = edge(2, 4);
        int e2_7 = edge(2, 7);
        for (int i : new int[]{3, 4, 7}) {
            assertPath(0, i, nodes(0, 1, 2, i));
            assertPath(5, i, nodes(5, 1, 2, i));
        }
        assertPath(7, 4, nodes(7, 2, 4));
        setRestrictions(
                createViaEdgeRestriction(e0_1, e1_2, e2_3),
                createViaEdgeRestriction(e5_1, e1_2, e2_7),
                createViaEdgeRestriction(e0_1, e1_2, e2_4),
                createViaEdgeRestriction(e5_1, e1_2, e2_4)
        );
        assertPath(0, 3, null);
        assertPath(0, 4, null);
        assertPath(0, 7, nodes(0, 1, 2, 7));
        assertPath(5, 3, nodes(5, 1, 2, 3));
        assertPath(5, 4, null);
        assertPath(5, 7, null);
        assertPath(7, 4, nodes(7, 2, 4));
        assertEquals(20, graph.getTurnCostStorage().getTurnCostsCount());
    }

    @Test
    void overlappingSingleViaEdgeRestriction_oppositeDirections() {
        //     7
        //     |
        // 0-1-2-3
        //   |
        //   5
        int e0_1 = edge(0, 1);
        int e1_2 = edge(1, 2);
        int e2_3 = edge(2, 3);
        int e5_1 = edge(5, 1);
        int e2_7 = edge(2, 7);
        for (int i : new int[]{3, 7}) {
            assertPath(0, i, nodes(0, 1, 2, i));
            assertPath(5, i, nodes(5, 1, 2, i));
            assertPath(i, 0, nodes(i, 2, 1, 0));
            assertPath(i, 5, nodes(i, 2, 1, 5));
        }
        setRestrictions(
                createViaEdgeRestriction(e0_1, e1_2, e2_3),
                createViaEdgeRestriction(e2_7, e1_2, e5_1)
        );
        assertPath(0, 3, null);
        assertPath(0, 7, nodes(0, 1, 2, 7));
        assertPath(5, 3, nodes(5, 1, 2, 3));
        assertPath(5, 7, nodes(5, 1, 2, 7));
        assertPath(3, 0, nodes(3, 2, 1, 0));
        assertPath(3, 5, nodes(3, 2, 1, 5));
        assertPath(7, 0, nodes(7, 2, 1, 0));
        assertPath(7, 5, null);
        assertEquals(18, graph.getTurnCostStorage().getTurnCostsCount());
    }

    @Test
    void viaNode() {
        // 4-0-1-2
        //     |
        //     3
        int e0_1 = edge(0, 1);
        int e0_4 = edge(0, 4);
        int e1_2 = edge(1, 2);
        int e1_3 = edge(1, 3);
        assertPath(0, 3, nodes(0, 1, 3));
        assertPath(4, 2, nodes(4, 0, 1, 2));
        setRestrictions(
                createViaEdgeRestriction(e0_4, e0_1, e1_2),
                createViaNodeRestriction(e0_1, 1, e1_3)
        );
        assertPath(4, 2, null);
        assertPath(0, 3, null);
        assertEquals(8, graph.getTurnCostStorage().getTurnCostsCount());
    }

    @Test
    void circle() {
        //    0
        //   / \
        //  1---2-3
        //  |
        //  4
        int e4_1 = edge(4, 1, false);
        int e1_0 = edge(1, 0, false);
        int e0_2 = edge(0, 2, false);
        int e2_1 = edge(2, 1, false);
        int e2_3 = edge(2, 3, false);
        assertPath(4, 3, nodes(4, 1, 0, 2, 3));
        setRestrictions(
                createViaEdgeRestriction(e4_1, e1_0, e0_2, e2_3)
        );
        // does this route make sense? no. is it forbidden according to the given restrictions? also no.
        assertPath(4, 3, nodes(4, 1, 0, 2, 1, 0, 2, 3));
        assertEquals(11, graph.getTurnCostStorage().getTurnCostsCount());
    }

    @Test
    void avoidRedundantRestrictions() {
        //   /- 1 - 2 - 3 - 4 - 5
        //  0               |   |
        //   \- 6 - 7 - 8 - 9 - 10
        edge(0, 1);
        edge(1, 2);
        edge(2, 3);
        edge(3, 4);
        edge(4, 5);
        edge(0, 6);
        edge(6, 7);
        edge(7, 8);
        edge(8, 9);
        edge(9, 10);
        edge(4, 9);
        edge(5, 10);

        setRestrictions(
                createViaEdgeRestriction(9, 8, 7),
                createViaEdgeRestriction(0, 1, 2, 3, 4, 11, 9, 8, 7, 6, 5),
                createViaEdgeRestriction(1, 2, 3, 4, 11, 9, 8, 7, 6, 5, 0),
                createViaEdgeRestriction(2, 3, 4, 11, 9, 8, 7, 6, 5, 0, 1),
                createViaEdgeRestriction(3, 4, 11, 9, 8, 7, 6, 5, 0, 1, 2),
                createViaEdgeRestriction(4, 11, 9, 8, 7, 6, 5, 0, 1, 2, 3)
        );
        // only six restrictions? yes, because except the first restriction they are all ignored, bc they are redundant anyway.
        // without this optimization there would be 415 turn cost entries and many artificial edges!
        assertEquals(6, graph.getTurnCostStorage().getTurnCostsCount());
    }

    @Test
    void duplicateRestrictions() {
        // 0-1-2
        int e0_1 = edge(0, 1);
        int e1_2 = edge(1, 2);
        assertPath(0, 2, nodes(0, 1, 2));
        setRestrictions(
                createViaNodeRestriction(e0_1, 1, e1_2),
                createViaNodeRestriction(e0_1, 1, e1_2)
        );
        assertPath(0, 2, null);
        assertEquals(1, graph.getTurnCostStorage().getTurnCostsCount());
    }

    @Test
    void duplicateViaEdgeRestrictions() {
        // 0-1-2-3
        int e0_1 = edge(0, 1);
        int e1_2 = edge(1, 2);
        int e2_3 = edge(2, 3);
        assertPath(0, 3, nodes(0, 1, 2, 3));
        setRestrictions(
                // they should not cancel each other out, of course
                createViaEdgeRestriction(e0_1, e1_2, e2_3),
                createViaEdgeRestriction(e0_1, e1_2, e2_3)
        );
        assertPath(0, 3, null);
        assertEquals(6, graph.getTurnCostStorage().getTurnCostsCount());
    }

    @Test
    void duplicateEdgesInViaEdgeRestriction() {
        // 0-1-2
        //   |\
        //   3 4
        int e0_1 = edge(0, 1);
        int e1_2 = edge(1, 2);
        int e1_3 = edge(1, 3);
        int e1_4 = edge(1, 4);
        setRestrictions(
                createViaNodeRestriction(e1_3, 1, e0_1),
                createViaEdgeRestriction(e1_2, e1_2, e0_1),
                createViaNodeRestriction(e1_3, 1, e1_4)
        );
        // todo: this test is incomplete: we'd like to check that 1-2-1-0 is forbidden, but it is forbidden anyway
        //       because of the infinite default u-turn costs. even if we used finite u-turn costs we could not test
        //       this atm, bc the turn restriction provider applies the default u-turn costs even when an actual restriction
        //       is present
        assertPath(3, 0, null);
//        assertPath(3, 4, nodes(3, 1, 2, 1, 4));
        assertEquals(8, graph.getTurnCostStorage().getTurnCostsCount());
    }

    @Test
    void circleEdgesInViaEdgeRestriction() {
        // 0=1-2
        //   |\
        //   3 4
        int e0_1 = edge(0, 1);
        int e1_0 = edge(0, 1);
        int e1_2 = edge(1, 2);
        int e1_3 = edge(1, 3);
        int e1_4 = edge(1, 4);
        assertPath(3, 2, nodes(3, 1, 2));
        setRestrictions(
                createViaEdgeRestriction(e0_1, e1_0, e1_2),
                createViaNodeRestriction(e1_3, 1, e1_2),
                createViaNodeRestriction(e1_3, 1, e1_0),
                createViaNodeRestriction(e1_4, 1, e1_2),
                createViaNodeRestriction(e1_4, 1, e0_1)
        );
        // coming from 3 we are forced to go onto e0_1, but from there we can't go to 2 bc of the via-edge restriction
        assertPath(3, 2, null);
        // these work
        assertPath(0, 2, nodes(0, 1, 2));
        assertPath(4, 2, nodes(4, 1, 0, 1, 2));
        assertEquals(11, graph.getTurnCostStorage().getTurnCostsCount());
    }

    @Test
    void similarRestrictions() {
        //   4
        //   |
        // 0-1=2-3
        int e0_1 = edge(0, 1);
        int e1_2 = edge(1, 2);
        int e2_1 = edge(2, 1);
        int e2_3 = edge(2, 3);
        int e1_4 = edge(1, 4);
        assertPath(4, 0, nodes(4, 1, 0));
        setRestrictions(
                createViaNodeRestriction(e1_4, 1, e0_1),
                createViaNodeRestriction(e2_1, 2, e1_2),
                createViaNodeRestriction(e1_2, 2, e2_1),
                // This restriction has the same edges (but a different node) than the previous,
                // but it shouldn't affect the others, of course.
                createViaNodeRestriction(e1_2, 1, e2_1)
        );
        assertPath(4, 0, null);
        assertEquals(4, graph.getTurnCostStorage().getTurnCostsCount());
    }

    @Test
    void similarRestrictions_with_artificial_edges() {
        // 0---1---2---3
        //     |   |
        //     5   4
        int e0_1 = edge(0, 1);
        int e5_1 = edge(5, 1);
        int e1_2 = edge(1, 2);
        int e2_3 = edge(2, 3);
        int e2_4 = edge(2, 4);
        setRestrictions(
                // Here we get artificial edges between nodes 1 and 2, and if we did not pay attention the u-turn
                // restrictions 1-2-1 and 2-1-2 would cancel out each other, so the path 0-1-2-1-5 would become
                // possible.
                createViaNodeRestriction(e0_1, 1, e5_1),
                createViaEdgeRestriction(e0_1, e1_2, e2_4),
                createViaEdgeRestriction(e2_4, e1_2, e0_1),
                createViaNodeRestriction(e1_2, 2, e1_2),
                createViaNodeRestriction(e1_2, 1, e1_2)
        );
        assertPath(0, 5, null);
        assertEquals(25, graph.getTurnCostStorage().getTurnCostsCount());
    }

    @Test
    void restrictTurnsBetweenArtificialEdges() {
        // 3->| |<-8
        // 0->1-2->4
        //    |
        //    5
        int e3_1 = edge(3, 1, false);
        int e0_1 = edge(0, 1, false);
        int e1_2 = edge(1, 2);
        int e2_4 = edge(2, 4, false);
        int e1_5 = edge(1, 5);
        int e8_2 = edge(8, 2, false);
        assertPath(3, 4, nodes(3, 1, 2, 4));
        assertPath(0, 4, nodes(0, 1, 2, 4));
        assertPath(5, 4, nodes(5, 1, 2, 4));
        setRestrictions(
                // This yields three artificial edges 1-2.
                createViaEdgeRestriction(e0_1, e1_2, e2_4),
                createViaEdgeRestriction(e3_1, e1_2, e2_4),
                createViaEdgeRestriction(e8_2, e1_2, e1_5)
        );
        // If we did not make sure turning between different artificial edges is forbidden we would get routes like 3-1-2-1-2-4
        assertPath(3, 4, null);
        assertPath(0, 4, null);
        assertPath(5, 4, nodes(5, 1, 2, 4));
    }


    @Test
    void twoProfiles() {
        // Note: There are many more combinations of turn restrictions with multiple profiles that
        //       we could test,
        // 0-1-2
        //   |
        // 3-4-5
        int e0_1 = edge(0, 1);
        int e1_2 = edge(1, 2);
        int e3_4 = edge(3, 4);
        int e4_5 = edge(4, 5);
        int e1_4 = edge(1, 4);
        for (BooleanEncodedValue t : List.of(turnRestrictionEnc, turnRestrictionEnc2)) {
            assertPath(2, 5, t, nodes(2, 1, 4, 5));
            assertPath(3, 0, t, nodes(3, 4, 1, 0));
        }
        List<RestrictionSetter.Restriction> restrictions = List.of(
                createViaEdgeRestriction(e1_2, e1_4, e4_5),
                createViaEdgeRestriction(e3_4, e1_4, e0_1)
        );
        List<BitSet> encBits = List.of(
                encBits(1, 1),
                encBits(1, 1)
        );
        setRestrictions(restrictions, encBits);
        for (BooleanEncodedValue t : List.of(turnRestrictionEnc, turnRestrictionEnc2)) {
            assertPath(2, 5, t, null);
            assertPath(3, 0, t, null);
        }
    }

    @Test
    void artificialEdgeSnapping() {
        //   6   0
        //   |   |
        // 1-2-x-3-4
        //   |   |
        //   5   7
        int e1_2 = edge(1, 2);
        int e2_3 = edge(2, 3);
        int e3_4 = edge(3, 4);
        int e0_3 = edge(0, 3);
        int e2_5 = edge(2, 5);
        int e2_6 = edge(2, 6);
        int e3_7 = edge(3, 7);
        updateDistancesFor(graph, 0, 40.03, 5.03);
        updateDistancesFor(graph, 1, 40.02, 5.01);
        updateDistancesFor(graph, 2, 40.02, 5.02);
        updateDistancesFor(graph, 3, 40.02, 5.03);
        updateDistancesFor(graph, 4, 40.02, 5.04);
        updateDistancesFor(graph, 5, 40.01, 5.02);
        updateDistancesFor(graph, 6, 40.03, 5.02);
        updateDistancesFor(graph, 7, 40.01, 5.03);
        assertPath(1, 0, nodes(1, 2, 3, 0));
        assertPath(1, 4, nodes(1, 2, 3, 4));
        assertPath(5, 0, nodes(5, 2, 3, 0));
        assertPath(6, 3, nodes(6, 2, 3));
        assertPath(2, 7, nodes(2, 3, 7));
        setRestrictions(
                createViaEdgeRestriction(e1_2, e2_3, e0_3),
                createViaNodeRestriction(e2_6, 2, e2_3),
                createViaNodeRestriction(e2_3, 3, e3_7)
        );
        assertPath(1, 0, null);
        assertPath(1, 4, nodes(1, 2, 3, 4));
        assertPath(5, 0, nodes(5, 2, 3, 0));
        assertPath(6, 3, null);
        assertPath(2, 7, null);

        // Now we try to route to and from a virtual node x. The problem here is that the 1-2-3-0
        // restriction forces paths coming from 1 onto an artificial edge (2-3)' (denying turns onto
        // 2-3 coming from 1), so if we just snapped to the original edge 2-3 we wouldn't find a path!
        // But if we snapped to the artificial edge we wouldn't find a path if we came from node 5.
        // If x was our starting point we wouldn't be able to go to 0 either.
        LocationIndex locationIndex = new LocationIndexTree(graph, graph.getDirectory()).prepareIndex();
        Snap snap = locationIndex.findClosest(40.02, 5.025, EdgeFilter.ALL_EDGES);
        QueryGraph queryGraph = QueryGraph.create(graph, snap);
        final int x = 8;
        // due to the virtual node the 1-2-3-0 path is now possible
        assertPath(queryGraph, 1, 0, nodes(1, 2, x, 3, 0));
        assertPath(queryGraph, 1, 4, nodes(1, 2, 3, 4));
        assertPath(queryGraph, 1, x, nodes(1, 2, x));
        assertPath(queryGraph, 5, x, nodes(5, 2, x));
        assertPath(queryGraph, x, 0, nodes(x, 3, 0));
        assertPath(queryGraph, x, 4, nodes(x, 3, 4));
        // the 6-2-3 and 2-3-7 restrictions are still enforced, despite the virtual node
        assertPath(queryGraph, 6, 3, null);
        assertPath(queryGraph, 2, 7, null);

        assertEquals(10, graph.getTurnCostStorage().getTurnCostsCount());
    }

    @Test
    void artificialEdgeSnapping_twoVirtualNodes() {
        // 1-2-x-3-y-4-z-5-6
        int e1_2 = edge(1, 2);
        int e2_3 = edge(2, 3);
        int e3_4 = edge(3, 4);
        int e4_5 = edge(4, 5);
        int e5_6 = edge(5, 6);
        updateDistancesFor(graph, 1, 40.02, 5.01);
        updateDistancesFor(graph, 2, 40.02, 5.02);
        updateDistancesFor(graph, 3, 40.02, 5.03);
        updateDistancesFor(graph, 4, 40.02, 5.04);
        updateDistancesFor(graph, 5, 40.02, 5.05);
        updateDistancesFor(graph, 6, 40.02, 5.06);
        assertPath(1, 4, nodes(1, 2, 3, 4));
        assertPath(2, 4, nodes(2, 3, 4));
        assertPath(2, 5, nodes(2, 3, 4, 5));
        assertPath(3, 5, nodes(3, 4, 5));
        assertPath(3, 6, nodes(3, 4, 5, 6));
        assertPath(4, 6, nodes(4, 5, 6));
        setRestrictions(
                createViaEdgeRestriction(e1_2, e2_3, e3_4),
                createViaEdgeRestriction(e2_3, e3_4, e4_5),
                createViaEdgeRestriction(e3_4, e4_5, e5_6)
        );
        assertPath(1, 4, null);
        assertPath(2, 4, nodes(2, 3, 4));
        assertPath(2, 5, null);
        assertPath(3, 5, nodes(3, 4, 5));
        assertPath(3, 6, null);
        assertPath(4, 6, nodes(4, 5, 6));

        // three virtual notes
        LocationIndex locationIndex = new LocationIndexTree(graph, graph.getDirectory()).prepareIndex();
        Snap snapX = locationIndex.findClosest(40.02, 5.025, EdgeFilter.ALL_EDGES);
        Snap snapY = locationIndex.findClosest(40.02, 5.035, EdgeFilter.ALL_EDGES);
        Snap snapZ = locationIndex.findClosest(40.02, 5.045, EdgeFilter.ALL_EDGES);
        QueryGraph queryGraph = QueryGraph.create(graph, List.of(snapX, snapY, snapZ));
        final int x = 8;
        final int y = 7;
        final int z = 9;
        assertEquals(x, snapX.getClosestNode());
        assertEquals(y, snapY.getClosestNode());
        assertEquals(z, snapZ.getClosestNode());
        assertPath(queryGraph, 1, 4, nodes(1, 2, x, 3, 4));
        assertPath(queryGraph, 2, 4, nodes(2, x, 3, 4));
        assertPath(queryGraph, 2, 5, nodes(2, x, 3, y, 4, 5));
        assertPath(queryGraph, 3, 5, nodes(3, y, 4, z, 5));
        assertPath(queryGraph, 3, 6, nodes(3, y, 4, z, 5, 6));
        assertPath(queryGraph, 4, 6, nodes(4, z, 5, 6));
        // turning between the virtual nodes is still possible
        assertPath(queryGraph, x, y, nodes(x, 3, y));
        assertPath(queryGraph, y, x, nodes(y, 3, x));
        assertPath(queryGraph, y, z, nodes(y, 4, z));
        assertPath(queryGraph, z, y, nodes(z, 4, y));

        assertEquals(20, graph.getTurnCostStorage().getTurnCostsCount());
    }

    private RestrictionSetter.Restriction createViaNodeRestriction(int fromEdge, int viaNode, int toEdge) {
        return RestrictionSetter.createViaNodeRestriction(fromEdge, viaNode, toEdge);
    }

    private RestrictionSetter.Restriction createViaEdgeRestriction(int... edges) {
        return RestrictionSetter.createViaEdgeRestriction(IntArrayList.from(edges));
    }

    /**
     * Shorthand version that only sets restriction for the first turn restriction encoded value
     */
    private void setRestrictions(RestrictionSetter.Restriction... restrictions) {
        setRestrictions(List.of(restrictions), Stream.of(restrictions).map(r -> encBits(1, 0)).toList());
    }

    private void setRestrictions(List<RestrictionSetter.Restriction> restrictions, List<BitSet> encBits) {
        r.setRestrictions(restrictions, encBits);
    }

    /**
     * Shorthand version that asserts the path for the first turn restriction encoded value
     */
    private void assertPath(int from, int to, IntArrayList expectedNodes) {
        assertPath(graph, from, to, turnRestrictionEnc, expectedNodes);
    }

    private void assertPath(int from, int to, BooleanEncodedValue turnRestrictionEnc, IntArrayList expectedNodes) {
        assertPath(graph, from, to, turnRestrictionEnc, expectedNodes);
    }

    /**
     * Shorthand version that asserts the path for the first turn restriction encoded value
     */
    private void assertPath(Graph graph, int from, int to, IntArrayList expectedNodes) {
        assertPath(graph, from, to, turnRestrictionEnc, expectedNodes);
    }

    private void assertPath(Graph graph, int from, int to, BooleanEncodedValue turnRestrictionEnc, IntArrayList expectedNodes) {
        Path path = calcPath(graph, from, to, turnRestrictionEnc);
        if (expectedNodes == null)
            assertFalse(path.isFound(), "Did not expect to find a path, but found: " + path.calcNodes() + ", edges: " + path.calcEdges());
        else {
            assertTrue(path.isFound(), "Expected path: " + expectedNodes + ", but did not find it");
            IntIndexedContainer nodes = path.calcNodes();
            assertEquals(expectedNodes, nodes);
        }
    }

    private Path calcPath(Graph graph, int from, int to, BooleanEncodedValue turnRestrictionEnc) {
        Dijkstra dijkstra = new Dijkstra(graph, graph.wrapWeighting(new SpeedWeighting(speedEnc, new TurnCostProvider() {
            @Override
            public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
                if (inEdge == outEdge) return Double.POSITIVE_INFINITY;
                return graph.getTurnCostStorage().get(turnRestrictionEnc, inEdge, viaNode, outEdge) ? Double.POSITIVE_INFINITY : 0;
            }

            @Override
            public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
                return Double.isInfinite(calcTurnWeight(inEdge, viaNode, outEdge)) ? Long.MAX_VALUE : 0L;
            }
        })), TraversalMode.EDGE_BASED);
        return dijkstra.calcPath(from, to);
    }

    private IntArrayList nodes(int... nodes) {
        return IntArrayList.from(nodes);
    }

    private BitSet encBits(int... bits) {
        BitSet b = new BitSet(bits.length);
        for (int i = 0; i < bits.length; i++) {
            if (bits[i] != 0 && bits[i] != 1)
                throw new IllegalArgumentException("bits must be 0 or 1");
            if (bits[i] > 0) b.set(i);
        }
        return b;
    }

    private int edge(int from, int to) {
        return edge(from, to, true);
    }

    private int edge(int from, int to, boolean bothDir) {
        return graph.edge(from, to).setDistance(100).set(speedEnc, 10, bothDir ? 10 : 0).getEdge();
    }
}
