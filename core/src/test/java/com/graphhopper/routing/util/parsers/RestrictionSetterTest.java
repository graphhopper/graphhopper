package com.graphhopper.routing.util.parsers;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.reader.osm.GraphRestriction;
import com.graphhopper.reader.osm.Pair;
import com.graphhopper.reader.osm.RestrictionType;
import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.storage.BaseGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class RestrictionSetterTest {
    private static final IntArrayList NO_PATH = IntArrayList.from();
    private DecimalEncodedValue speedEnc;
    private BaseGraph graph;
    private RestrictionSetter r;

    @BeforeEach
    void setup() {
        speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
        EncodingManager encodingManager = EncodingManager.start().add(speedEnc).build();
        graph = new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();
        r = new RestrictionSetter(graph);
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
        GraphRestriction graphRestriction = GraphRestriction.node(a, 1, b);
        BooleanEncodedValue turnRestrictionEnc = createTurnRestrictionEnc("car");
        r.setRestrictions(Arrays.asList(new Pair<>(graphRestriction, RestrictionType.NO)), turnRestrictionEnc);
        assertEquals(nodes(0, 1, 3, 4, 2), calcPath(0, 2, turnRestrictionEnc));
    }

    @Test
    void viaNode_only() {
        // 0-1-2
        //   | |
        //   3-4
        int a = edge(0, 1);
        int b = edge(1, 2);
        edge(1, 3);
        edge(2, 4);
        edge(3, 4);
        GraphRestriction graphRestriction = GraphRestriction.node(a, 1, b);
        BooleanEncodedValue turnRestrictionEnc = createTurnRestrictionEnc("car");
        r.setRestrictions(Arrays.asList(new Pair<>(graphRestriction, RestrictionType.ONLY)), turnRestrictionEnc);
        assertEquals(nodes(0, 1, 2, 4, 3), calcPath(0, 3, turnRestrictionEnc));
    }

    @Test
    void viaWay_no() {
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
        GraphRestriction graphRestriction = GraphRestriction.way(a, b, c, nodes(1, 2));
        BooleanEncodedValue turnRestrictionEnc = createTurnRestrictionEnc("car");
        r.setRestrictions(Arrays.asList(
                new Pair<>(graphRestriction, RestrictionType.NO)
        ), turnRestrictionEnc);
        // turning from a to b and then to c is not allowed
        assertEquals(nodes(0, 1, 5, 8, 9, 6, 2, 3), calcPath(0, 3, turnRestrictionEnc));
        // turning from a to b, or b to c is still allowed
        assertEquals(nodes(0, 1, 2, 4), calcPath(0, 4, turnRestrictionEnc));
        assertEquals(nodes(5, 1, 2, 3), calcPath(5, 3, turnRestrictionEnc));
    }

    @Test
    void viaWay_no_withOverlap() {
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

        BooleanEncodedValue turnRestrictionEnc = createTurnRestrictionEnc("car");
        r.setRestrictions(Arrays.asList(
                new Pair<>(GraphRestriction.way(a, b, c, nodes(1, 2)), RestrictionType.NO),
                new Pair<>(GraphRestriction.way(b, c, d, nodes(2, 3)), RestrictionType.NO)
        ), turnRestrictionEnc);

        assertEquals(NO_PATH, calcPath(0, 3, turnRestrictionEnc)); // a-b-c
        assertEquals(nodes(0, 1, 2, 6), calcPath(0, 6, turnRestrictionEnc)); // a-b-t
        assertEquals(nodes(5, 1, 2, 3), calcPath(5, 3, turnRestrictionEnc)); // s-b-c
        assertEquals(nodes(5, 1, 2, 6), calcPath(5, 6, turnRestrictionEnc)); // s-b-t

        assertEquals(NO_PATH, calcPath(1, 4, turnRestrictionEnc)); // b-c-d
        assertEquals(nodes(1, 2, 3, 7), calcPath(1, 7, turnRestrictionEnc)); // b-c-u
        assertEquals(nodes(6, 2, 3, 4), calcPath(6, 4, turnRestrictionEnc)); // t-c-d
        assertEquals(nodes(6, 2, 3, 7), calcPath(6, 7, turnRestrictionEnc)); // t-c-u
    }

    @Test
    void viaWay_no_withOverlap_more_complex() {
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
        BooleanEncodedValue turnRestrictionEnc = createTurnRestrictionEnc("car");
        r.setRestrictions(Arrays.asList(
                new Pair<>(GraphRestriction.node(t, 4, d), RestrictionType.NO),
                new Pair<>(GraphRestriction.node(s, 3, a), RestrictionType.NO),
                new Pair<>(GraphRestriction.way(a, b, c, nodes(3, 7)), RestrictionType.NO),
                new Pair<>(GraphRestriction.way(b, c, d, nodes(7, 8)), RestrictionType.NO),
                new Pair<>(GraphRestriction.way(c, d, a, nodes(8, 4)), RestrictionType.NO),
                new Pair<>(GraphRestriction.way(d, a, b, nodes(4, 3)), RestrictionType.NO)
        ), turnRestrictionEnc);

        assertEquals(nodes(0, 3, 7, 8, 9), calcPath(0, 9, turnRestrictionEnc));
        assertEquals(nodes(5, 4, 3, 7, 10, 11, 8, 9), calcPath(5, 9, turnRestrictionEnc));
        assertEquals(nodes(5, 4, 3, 2), calcPath(5, 2, turnRestrictionEnc));
        assertEquals(nodes(0, 3, 7, 10), calcPath(0, 10, turnRestrictionEnc));
        assertEquals(nodes(6, 7, 8, 9), calcPath(6, 9, turnRestrictionEnc));
    }

    @Test
    void viaWay_common_via_edge_opposite_direction() {
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

        BooleanEncodedValue turnRestrictionEnc = createTurnRestrictionEnc("car");
        r.setRestrictions(Arrays.asList(
                // A rather common case where u-turns between the a-b and d-e lanes are forbidden.
                // Importantly, the via-edge c is used only once per direction so a single artificial edge is sufficient.
                new Pair<>(GraphRestriction.way(b, c, e, nodes(1, 4)), RestrictionType.NO),
                new Pair<>(GraphRestriction.way(d, c, a, nodes(4, 1)), RestrictionType.NO)
        ), turnRestrictionEnc);

        assertEquals(nodes(0, 1, 2), calcPath(0, 2, turnRestrictionEnc));
        assertEquals(nodes(0, 1, 4, 5), calcPath(0, 5, turnRestrictionEnc));
        assertEquals(nodes(0, 1, 4, 3), calcPath(0, 3, turnRestrictionEnc));
        assertEquals(nodes(2, 1, 0), calcPath(2, 0, turnRestrictionEnc));
        assertEquals(nodes(2, 1, 4, 3), calcPath(2, 3, turnRestrictionEnc));
        assertEquals(NO_PATH, calcPath(2, 5, turnRestrictionEnc));
        assertEquals(NO_PATH, calcPath(3, 0, turnRestrictionEnc));
        assertEquals(nodes(3, 4, 1, 2), calcPath(3, 2, turnRestrictionEnc));
        assertEquals(nodes(3, 4, 5), calcPath(3, 5, turnRestrictionEnc));
        assertEquals(nodes(5, 4, 1, 0), calcPath(5, 0, turnRestrictionEnc));
        assertEquals(nodes(5, 4, 1, 2), calcPath(5, 2, turnRestrictionEnc));
        assertEquals(nodes(5, 4, 3), calcPath(5, 3, turnRestrictionEnc));
    }

    @Test
    void viaWay_common_via_edge_opposite_direction_edge0() {
        //    a   v   b
        //  0---1---2---3
        int v = edge(1, 2);
        int a = edge(0, 1);
        int b = edge(2, 3);

        BooleanEncodedValue turnRestrictionEnc = createTurnRestrictionEnc("car");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> r.setRestrictions(Arrays.asList(
                // This is rather academic, but for the special case where the via edge is edge 0
                // we cannot use two restrictions even though the edge is used in opposite directions.
                new Pair<>(GraphRestriction.way(a, v, b, nodes(1, 2)), RestrictionType.NO),
                new Pair<>(GraphRestriction.way(b, v, a, nodes(2, 1)), RestrictionType.NO)
        ), turnRestrictionEnc));
        assertTrue(ex.getMessage().contains("We cannot deal with multiple via-way restrictions if the via-edge is edge 0"));
    }

    @Test
    void viaWay_common_via_edge_same_direction() {
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

        BooleanEncodedValue turnRestrictionEnc = createTurnRestrictionEnc("car");
        // Here edge c is used by both restrictions in the same direction. Supporting this
        // with our current approach would require a second artificial edge. See #2907
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                r.setRestrictions(Arrays.asList(
                        new Pair<>(GraphRestriction.way(a, c, d, nodes(1, 4)), RestrictionType.NO),
                        new Pair<>(GraphRestriction.way(b, c, e, nodes(1, 4)), RestrictionType.NO)
                ), turnRestrictionEnc));
        assertTrue(ex.getMessage().contains("We cannot deal with multiple via-way restrictions that use the same via edge in the same direction"), ex.getMessage());
    }

    @Test
    void viaWay_only() {
        //      0
        //  a   |b  c
        // 1----2----3
        //      |d
        // 4----5----6
        //  e   |f  g
        //      7
        int a = edge(1, 2);
        int b = edge(0, 2);
        int c = edge(2, 3);
        int d = edge(2, 5);
        int e = edge(4, 5);
        int f = edge(5, 7);
        int g = edge(5, 6);
        BooleanEncodedValue turnRestrictionEnc = createTurnRestrictionEnc("car");
        r.setRestrictions(Arrays.asList(
                new Pair<>(GraphRestriction.way(a, d, f, nodes(2, 5)), RestrictionType.ONLY),
                // we add a few more restrictions, because that happens a lot in real data
                new Pair<>(GraphRestriction.node(d, 5, e), RestrictionType.NO),
                new Pair<>(GraphRestriction.node(e, 5, f), RestrictionType.NO)
        ), turnRestrictionEnc);
        // following the restriction is allowed of course
        assertEquals(nodes(1, 2, 5, 7), calcPath(1, 7, turnRestrictionEnc));
        // taking another turn at the beginning is not allowed
        assertEquals(nodes(), calcPath(1, 3, turnRestrictionEnc));
        // taking another turn after the first turn is not allowed either
        assertEquals(nodes(), calcPath(1, 4, turnRestrictionEnc));
        // coming from somewhere else we can go anywhere
        assertEquals(nodes(0, 2, 5, 6), calcPath(0, 6, turnRestrictionEnc));
        assertEquals(nodes(0, 2, 5, 7), calcPath(0, 7, turnRestrictionEnc));
    }

    @Test
    void viaWay_only_twoRestrictionsSharingSameVia() {
        //   a   c   d
        // 0---1---2---3
        //     |b  |e
        // 5--/     \--4
        int a = edge(0, 1);
        int b = edge(5, 1);
        int c = edge(1, 2);
        int d = edge(2, 3);
        int e = edge(2, 4);
        BooleanEncodedValue turnRestrictionEnc = createTurnRestrictionEnc("car");
        assertThrows(IllegalStateException.class, () -> r.setRestrictions(Arrays.asList(
                        // These are two 'only' via-way restrictions that share the same via way. A real-world example can
                // be found in Rüdesheim am Rhein (49.97645, 7.91309) where vehicles either have to go straight or enter the ferry depending
                // on the from-way, even though they use the same via way before. This is the same
                // problem we saw in #2907.
                        // We have to make sure such cases are ignored already when we parse the OSM data.
                        new Pair<>(GraphRestriction.way(a, c, d, nodes(1, 2)), RestrictionType.ONLY),
                        new Pair<>(GraphRestriction.way(b, c, e, nodes(1, 2)), RestrictionType.ONLY)
                ), turnRestrictionEnc)
        );
    }

    @Test
    void viaWay_only_twoRestrictionsSharingSameVia_different_directions() {
        //   a   c   d
        // 0---1---2---3
        //     |b  |e
        // 5--/     \--4
        int a = edge(0, 1);
        int b = edge(5, 1);
        int c = edge(1, 2);
        int d = edge(2, 3);
        int e = edge(2, 4);
        BooleanEncodedValue turnRestrictionEnc = createTurnRestrictionEnc("car");
        r.setRestrictions(Arrays.asList(
                // since the via-edge is used in opposite directions we can deal with these restrictions
                new Pair<>(GraphRestriction.way(a, c, d, nodes(1, 2)), RestrictionType.ONLY),
                new Pair<>(GraphRestriction.way(e, c, b, nodes(2, 1)), RestrictionType.ONLY)
        ), turnRestrictionEnc);
        assertEquals(nodes(0, 1, 2, 3), calcPath(0, 3, turnRestrictionEnc));
        assertEquals(NO_PATH, calcPath(0, 4, turnRestrictionEnc));
        assertEquals(NO_PATH, calcPath(0, 5, turnRestrictionEnc));
        assertEquals(nodes(3, 2, 1, 0), calcPath(3, 0, turnRestrictionEnc));
        assertEquals(nodes(3, 2, 4), calcPath(3, 4, turnRestrictionEnc));
        assertEquals(nodes(3, 2, 1, 5), calcPath(3, 5, turnRestrictionEnc));
        assertEquals(NO_PATH, calcPath(4, 0, turnRestrictionEnc));
        assertEquals(NO_PATH, calcPath(4, 3, turnRestrictionEnc));
        assertEquals(nodes(4, 2, 1, 5), calcPath(4, 5, turnRestrictionEnc));
        assertEquals(nodes(5, 1, 0), calcPath(5, 0, turnRestrictionEnc));
        assertEquals(nodes(5, 1, 2, 3), calcPath(5, 3, turnRestrictionEnc));
        assertEquals(nodes(5, 1, 2, 4), calcPath(5, 4, turnRestrictionEnc));
    }

    private static BooleanEncodedValue createTurnRestrictionEnc(String name) {
        BooleanEncodedValue turnRestrictionEnc = TurnRestriction.create(name);
        turnRestrictionEnc.init(new EncodedValue.InitializerConfig());
        return turnRestrictionEnc;
    }

    private IntArrayList calcPath(int from, int to, BooleanEncodedValue turnRestrictionEnc) {
        return new IntArrayList(new Dijkstra(graph, new SpeedWeighting(speedEnc, new TurnCostProvider() {
            @Override
            public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
                if (inEdge == outEdge) return Double.POSITIVE_INFINITY;
                return graph.getTurnCostStorage().get(turnRestrictionEnc, inEdge, viaNode, outEdge) ? Double.POSITIVE_INFINITY : 0;
            }

            @Override
            public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
                return Double.isInfinite(calcTurnWeight(inEdge, viaNode, outEdge)) ? Long.MAX_VALUE : 0L;
            }
        }), TraversalMode.EDGE_BASED).calcPath(from, to).calcNodes());
    }

    private IntArrayList nodes(int... nodes) {
        return IntArrayList.from(nodes);
    }

    private int edge(int from, int to) {
        return graph.edge(from, to).setDistance(100).set(speedEnc, 10, 10).getEdge();
    }
}
