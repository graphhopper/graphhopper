package com.graphhopper.routing.util.parsers;

import com.carrotsearch.hppc.BitSet;
import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.reader.osm.Pair;
import com.graphhopper.reader.osm.RestrictionTopology;
import com.graphhopper.reader.osm.RestrictionType;
import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.TurnRestriction;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.graphhopper.reader.osm.OSMRestrictionConverter.buildRestrictionsForOSMRestriction;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Here we test turn restrictions on a basic graph, but specifically for OSM (no- and only-restrictions).
 * This is somewhat redundant with {@link RestrictionSetterTest}. Generally, lower-level tests in
 * {@link RestrictionSetterTest} should be preferred.
 */
public class OSMRestrictionSetterTest {
    private static final IntArrayList NO_PATH = IntArrayList.from();
    private DecimalEncodedValue speedEnc;
    private BooleanEncodedValue turnRestrictionEnc;
    private BaseGraph graph;
    private RestrictionSetter r;

    @BeforeEach
    void setup() {
        speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
        turnRestrictionEnc = TurnRestriction.create("car1");
        EncodingManager encodingManager = EncodingManager.start()
                .add(speedEnc)
                .add(turnRestrictionEnc)
                .build();
        graph = new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();
        r = new RestrictionSetter(graph, List.of(turnRestrictionEnc));
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
        RestrictionTopology topology = RestrictionTopology.node(a, 1, b);
        setRestrictions(List.of(new Pair<>(topology, RestrictionType.NO)));
        assertEquals(nodes(0, 1, 3, 4, 2), calcPath(0, 2));
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
        RestrictionTopology topology = RestrictionTopology.node(a, 1, b);
        setRestrictions(List.of(new Pair<>(topology, RestrictionType.ONLY)));
        assertEquals(nodes(0, 1, 2, 4, 3), calcPath(0, 3));
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
        RestrictionTopology topology = RestrictionTopology.way(a, b, c, nodes(1, 2));
        setRestrictions(List.of(
                new Pair<>(topology, RestrictionType.NO)
        ));
        // turning from a to b and then to c is not allowed
        assertEquals(nodes(0, 1, 5, 8, 9, 6, 2, 3), calcPath(0, 3));
        // turning from a to b, or b to c is still allowed
        assertEquals(nodes(0, 1, 2, 4), calcPath(0, 4));
        assertEquals(nodes(5, 1, 2, 3), calcPath(5, 3));
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

        setRestrictions(List.of(
                new Pair<>(RestrictionTopology.way(a, b, c, nodes(1, 2)), RestrictionType.NO),
                new Pair<>(RestrictionTopology.way(b, c, d, nodes(2, 3)), RestrictionType.NO)
        ));

        assertEquals(NO_PATH, calcPath(0, 3)); // a-b-c
        assertEquals(nodes(0, 1, 2, 6), calcPath(0, 6)); // a-b-t
        assertEquals(nodes(5, 1, 2, 3), calcPath(5, 3)); // s-b-c
        assertEquals(nodes(5, 1, 2, 6), calcPath(5, 6)); // s-b-t

        assertEquals(NO_PATH, calcPath(1, 4)); // b-c-d
        assertEquals(nodes(1, 2, 3, 7), calcPath(1, 7)); // b-c-u
        assertEquals(nodes(6, 2, 3, 4), calcPath(6, 4)); // t-c-d
        assertEquals(nodes(6, 2, 3, 7), calcPath(6, 7)); // t-c-u
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
        setRestrictions(List.of(
                new Pair<>(RestrictionTopology.node(t, 4, d), RestrictionType.NO),
                new Pair<>(RestrictionTopology.node(s, 3, a), RestrictionType.NO),
                new Pair<>(RestrictionTopology.way(a, b, c, nodes(3, 7)), RestrictionType.NO),
                new Pair<>(RestrictionTopology.way(b, c, d, nodes(7, 8)), RestrictionType.NO),
                new Pair<>(RestrictionTopology.way(c, d, a, nodes(8, 4)), RestrictionType.NO),
                new Pair<>(RestrictionTopology.way(d, a, b, nodes(4, 3)), RestrictionType.NO)
        ));

        assertEquals(nodes(0, 3, 7, 8, 9), calcPath(0, 9));
        assertEquals(nodes(5, 4, 3, 7, 10, 11, 8, 9), calcPath(5, 9));
        assertEquals(nodes(5, 4, 3, 2), calcPath(5, 2));
        assertEquals(nodes(0, 3, 7, 10), calcPath(0, 10));
        assertEquals(nodes(6, 7, 8, 9), calcPath(6, 9));
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

        setRestrictions(List.of(
                // A rather common case where u-turns between the a-b and d-e lanes are forbidden.
                new Pair<>(RestrictionTopology.way(b, c, e, nodes(1, 4)), RestrictionType.NO),
                new Pair<>(RestrictionTopology.way(d, c, a, nodes(4, 1)), RestrictionType.NO)
        ));

        assertEquals(nodes(0, 1, 2), calcPath(0, 2));
        assertEquals(nodes(0, 1, 4, 5), calcPath(0, 5));
        assertEquals(nodes(0, 1, 4, 3), calcPath(0, 3));
        assertEquals(nodes(2, 1, 0), calcPath(2, 0));
        assertEquals(nodes(2, 1, 4, 3), calcPath(2, 3));
        assertEquals(NO_PATH, calcPath(2, 5));
        assertEquals(NO_PATH, calcPath(3, 0));
        assertEquals(nodes(3, 4, 1, 2), calcPath(3, 2));
        assertEquals(nodes(3, 4, 5), calcPath(3, 5));
        assertEquals(nodes(5, 4, 1, 0), calcPath(5, 0));
        assertEquals(nodes(5, 4, 1, 2), calcPath(5, 2));
        assertEquals(nodes(5, 4, 3), calcPath(5, 3));
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

        assertEquals(nodes(0, 1, 4, 3), calcPath(0, 3));
        assertEquals(nodes(2, 1, 4, 5), calcPath(2, 5));
        // Here edge c is used by both restrictions in the same direction. This requires a second
        // artificial edge and our first implementation did not allow this, see #2907
        setRestrictions(List.of(
                new Pair<>(RestrictionTopology.way(a, c, d, nodes(1, 4)), RestrictionType.NO),
                new Pair<>(RestrictionTopology.way(b, c, e, nodes(1, 4)), RestrictionType.NO)
        ));
        assertEquals(NO_PATH, calcPath(0, 3));
        assertEquals(nodes(3, 4, 1, 0), calcPath(3, 0));
        assertEquals(NO_PATH, calcPath(2, 5));
        assertEquals(nodes(5, 4, 1, 2), calcPath(5, 2));
        assertEquals(nodes(0, 1, 2), calcPath(0, 2));
        assertEquals(nodes(1, 4, 3), calcPath(1, 3));
        assertEquals(nodes(0, 1, 4, 5), calcPath(0, 5));
        assertEquals(nodes(2, 1, 4, 3), calcPath(2, 3));
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
        setRestrictions(List.of(
                new Pair<>(RestrictionTopology.way(a, d, f, nodes(2, 5)), RestrictionType.ONLY),
                // we add a few more restrictions, because that happens a lot in real data
                new Pair<>(RestrictionTopology.node(d, 5, e), RestrictionType.NO),
                new Pair<>(RestrictionTopology.node(e, 5, f), RestrictionType.NO)
        ));
        // following the restriction is allowed of course
        assertEquals(nodes(1, 2, 5, 7), calcPath(1, 7));
        // taking another turn at the beginning is not allowed
        assertEquals(NO_PATH, calcPath(1, 3));
        // taking another turn after the first turn is not allowed either
        assertEquals(NO_PATH, calcPath(1, 4));
        // coming from somewhere else we can go anywhere
        assertEquals(nodes(0, 2, 5, 6), calcPath(0, 6));
        assertEquals(nodes(0, 2, 5, 7), calcPath(0, 7));
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
        assertEquals(nodes(0, 1, 2, 4), calcPath(0, 4));
        assertEquals(nodes(5, 1, 2, 3), calcPath(5, 3));
        setRestrictions(List.of(
                // These are two 'only' via-way restrictions that share the same via way. A real-world example can
                // be found in Rüdesheim am Rhein (49.97645, 7.91309) where vehicles either have to go straight or enter the ferry depending
                // on the from-way, even though they use the same via way before. This is the same
                // problem we saw in #2907.
                new Pair<>(RestrictionTopology.way(a, c, d, nodes(1, 2)), RestrictionType.ONLY),
                new Pair<>(RestrictionTopology.way(b, c, e, nodes(1, 2)), RestrictionType.ONLY)
        ));
        assertEquals(nodes(0, 1, 2, 3), calcPath(0, 3));
        assertEquals(NO_PATH, calcPath(5, 3));
        assertEquals(nodes(5, 1, 2, 4), calcPath(5, 4));
        assertEquals(NO_PATH, calcPath(0, 4));
        assertEquals(nodes(3, 2, 1, 0), calcPath(3, 0));
        assertEquals(nodes(3, 2, 1, 5), calcPath(3, 5));
        assertEquals(nodes(4, 2, 1, 0), calcPath(4, 0));
        assertEquals(nodes(4, 2, 1, 5), calcPath(4, 5));
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
        setRestrictions(List.of(
                // here the via-edge is used in opposite directions (this used to be important with our initial implementation for via-way restrictions)
                new Pair<>(RestrictionTopology.way(a, c, d, nodes(1, 2)), RestrictionType.ONLY),
                new Pair<>(RestrictionTopology.way(e, c, b, nodes(2, 1)), RestrictionType.ONLY)
        ));
        assertEquals(nodes(0, 1, 2, 3), calcPath(0, 3));
        assertEquals(NO_PATH, calcPath(0, 4));
        assertEquals(NO_PATH, calcPath(0, 5));
        assertEquals(nodes(3, 2, 1, 0), calcPath(3, 0));
        assertEquals(nodes(3, 2, 4), calcPath(3, 4));
        assertEquals(nodes(3, 2, 1, 5), calcPath(3, 5));
        assertEquals(NO_PATH, calcPath(4, 0));
        assertEquals(NO_PATH, calcPath(4, 3));
        assertEquals(nodes(4, 2, 1, 5), calcPath(4, 5));
        assertEquals(nodes(5, 1, 0), calcPath(5, 0));
        assertEquals(nodes(5, 1, 2, 3), calcPath(5, 3));
        assertEquals(nodes(5, 1, 2, 4), calcPath(5, 4));
    }

    @Test
    void viaWayAndNode() {
        // 4-0-1-2
        //     |
        //     3
        int e0_1 = edge(0, 1);
        int e0_4 = edge(0, 4);
        int e1_2 = edge(1, 2);
        int e1_3 = edge(1, 3);
        assertEquals(nodes(0, 1, 3), calcPath(0, 3));
        assertEquals(nodes(4, 0, 1, 2), calcPath(4, 2));
        setRestrictions(List.of(
                new Pair<>(RestrictionTopology.way(e0_4, e0_1, e1_2, nodes(0, 1)), RestrictionType.NO),
                new Pair<>(RestrictionTopology.node(e0_1, 1, e1_3), RestrictionType.NO)
        ));
        assertEquals(NO_PATH, calcPath(4, 2));
        assertEquals(NO_PATH, calcPath(0, 3));
    }

    @Test
    void viaWay_overlapping_no_only() {
        //         3
        //   a   b |c
        // 0---1---2---4
        //           d |e
        //             5
        int a = edge(0, 1);
        int b = edge(1, 2);
        int c = edge(2, 3);
        int d = edge(2, 4);
        int e = edge(4, 5);
        setRestrictions(List.of(
                // here the via-way of the first and the from-way of the second restriction overlap
                new Pair<>(RestrictionTopology.way(a, b, c, nodes(1, 2)), RestrictionType.NO),
                new Pair<>(RestrictionTopology.way(b, d, e, nodes(2, 4)), RestrictionType.ONLY)
        ));
        assertEquals(nodes(0, 1, 2, 4, 5), calcPath(0, 5));
    }

    @Test
    void multiViaWay_only() {
        //   a   b   c
        // 0---1---2---3
        //     |d  |e  |f
        //     4---5---6
        //       g   h

        int a = edge(0, 1);
        int b = edge(1, 2);
        int c = edge(2, 3);
        int d = edge(1, 4);
        int e = edge(2, 5);
        int f = edge(3, 6);
        int g = edge(4, 5);
        int h = edge(5, 6);
        assertEquals(nodes(0, 1, 4), calcPath(0, 4));
        setRestrictions(List.of(
                new Pair<>(RestrictionTopology.way(a, IntArrayList.from(b, c), f, nodes(1, 2, 3)), RestrictionType.ONLY)
        ));
        assertEquals(nodes(0, 1, 2, 3, 6, 5, 4), calcPath(0, 4));
    }

    /**
     * Shorthand version that only sets restriction for the first turn restriction encoded value
     */
    private void setRestrictions(List<Pair<RestrictionTopology, RestrictionType>> osmRestrictions) {
        setRestrictions(osmRestrictions, osmRestrictions.stream().map(r -> encBits(1)).toList());
    }

    private void setRestrictions(List<Pair<RestrictionTopology, RestrictionType>> osmRestrictions, List<BitSet> osmEncBits) {
        List<RestrictionSetter.Restriction> restrictions = new ArrayList<>();
        List<BitSet> encBits = new ArrayList<>();
        for (int i = 0; i < osmRestrictions.size(); i++) {
            Pair<RestrictionTopology, RestrictionType> p = osmRestrictions.get(i);
            List<RestrictionSetter.Restriction> tmpRestrictions = buildRestrictionsForOSMRestriction(graph, p.first, p.second);
            restrictions.addAll(tmpRestrictions);
            final BitSet e = osmEncBits.get(i);
            tmpRestrictions.forEach(__ -> encBits.add(RestrictionSetter.copyEncBits(e)));
        }
        r.setRestrictions(restrictions, encBits);
    }

    /**
     * Shorthand version that calculates the path for the first turn restriction encoded value
     */
    private IntArrayList calcPath(int from, int to) {
        return calcPath(from, to, turnRestrictionEnc);
    }

    private IntArrayList calcPath(int from, int to, BooleanEncodedValue turnRestrictionEnc) {
        return calcPath(this.graph, from, to, turnRestrictionEnc);
    }

    /**
     * Shorthand version that calculates the path for the first turn restriction encoded value
     */
    private IntArrayList calcPath(Graph graph, int from, int to) {
        return calcPath(graph, from, to, turnRestrictionEnc);
    }

    private IntArrayList calcPath(Graph graph, int from, int to, BooleanEncodedValue turnRestrictionEnc) {
        return new IntArrayList(new Dijkstra(graph, graph.wrapWeighting(new SpeedWeighting(speedEnc, new TurnCostProvider() {
            @Override
            public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
                if (inEdge == outEdge) return Double.POSITIVE_INFINITY;
                return graph.getTurnCostStorage().get(turnRestrictionEnc, inEdge, viaNode, outEdge) ? Double.POSITIVE_INFINITY : 0;
            }

            @Override
            public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
                return Double.isInfinite(calcTurnWeight(inEdge, viaNode, outEdge)) ? Long.MAX_VALUE : 0L;
            }
        })), TraversalMode.EDGE_BASED).calcPath(from, to).calcNodes());
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
