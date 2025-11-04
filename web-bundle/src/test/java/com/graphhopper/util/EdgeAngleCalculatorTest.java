package com.graphhopper.util;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EdgeAngleCalculator.
 * <p>
 * Key principle: ALL edges (both current and candidates) come INTO the common node.
 * This matches how road networks store bidirectional edges - each edge direction is
 * stored separately. The calculator compares the approach bearing of the current edge
 * with the approach bearings of candidate edges to find the straightest continuation.
 */
public class EdgeAngleCalculatorTest {

    private BaseGraph graph;
    private EdgeAngleCalculator calculator;
    private DecimalEncodedValue speedEnc;

    @BeforeEach
    void setUp() {
        speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        EncodingManager encodingManager = EncodingManager.start().add(speedEnc).build();
        graph = new BaseGraph.Builder(encodingManager).create();
        calculator = new EdgeAngleCalculator(graph);
    }

    /**
     * Test a simple straight-through scenario where one edge continues straight
     * and another makes a 90-degree turn.
     * <p>
     * Layout:
     *     2
     *     |
     * 0---1---3
     */
    @Test
    void testSelectStraightestEdge_SimpleStraight() {
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 50.0, 10.0);   // West
        na.setNode(1, 50.0, 10.1);   // Center (common node)
        na.setNode(2, 50.1, 10.1);   // North
        na.setNode(3, 50.0, 10.2);   // East

        // Current edge: 0 -> 1 (approaching from west)
        EdgeIteratorState currentEdge = graph.edge(1, 0).set(speedEnc, 50).setDistance(100);

        // Candidate edges coming INTO node 1
        EdgeIteratorState straightEdge = graph.edge(1, 3).set(speedEnc, 50).setDistance(100);  // From east
        EdgeIteratorState turnEdge = graph.edge(1, 2).set(speedEnc, 50).setDistance(100);      // From north

        // Get current state oriented toward the common node
        EdgeIteratorState currentState = graph.getEdgeIteratorState(currentEdge.getEdge(), 1);

        List<Integer> candidates = Arrays.asList(straightEdge.getEdge(), turnEdge.getEdge());

        int selected = calculator.selectStraightestEdge(candidates, currentState, 1);

        assertEquals(straightEdge.getEdge(), selected,
                "Should select the edge from node 3 (continues straight east) over the edge from node 2 (90° turn north)");
    }

    /**
     * Test selecting between two turns where one is more gradual.
     * <p>
     * Layout (viewed from above):
     *       2
     *      /
     *     1 -- 3
     *    /
     *   0
     */
    @Test
    void testSelectStraightestEdge_GradualTurn() {
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 49.9, 9.9);    // Southwest
        na.setNode(1, 50.0, 10.0);   // Center (common node)
        na.setNode(2, 50.1, 10.1);   // Northeast (gradual continuation)
        na.setNode(3, 50.0, 10.2);   // East (sharper turn)

        // Current edge: coming from southwest
        EdgeIteratorState currentEdge = graph.edge(1, 0).set(speedEnc, 50).setDistance(100);

        // Candidate edges coming INTO node 1
        EdgeIteratorState gradualTurn = graph.edge(1, 2).set(speedEnc, 50).setDistance(100);   // Northeast continuation
        EdgeIteratorState sharpTurn = graph.edge(1, 3).set(speedEnc, 50).setDistance(100);     // East (sharper)

        EdgeIteratorState currentState = graph.getEdgeIteratorState(currentEdge.getEdge(), 1);

        List<Integer> candidates = Arrays.asList(gradualTurn.getEdge(), sharpTurn.getEdge());

        int selected = calculator.selectStraightestEdge(candidates, currentState, 1);

        assertEquals(gradualTurn.getEdge(), selected,
                "Should select the edge to node 2 (gradual northeast continuation) over the edge to node 3 (sharper east turn)");
    }

    /**
     * Test with a single candidate edge (should return that edge immediately).
     */
    @Test
    void testSelectStraightestEdge_SingleCandidate() {
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 50.0, 10.0);
        na.setNode(1, 50.0, 10.1);
        na.setNode(2, 50.0, 10.2);

        EdgeIteratorState currentEdge = graph.edge(0, 1).set(speedEnc, 50).setDistance(100);
        EdgeIteratorState onlyCandidate = graph.edge(2, 1).set(speedEnc, 50).setDistance(100);

        EdgeIteratorState currentState = graph.getEdgeIteratorState(currentEdge.getEdge(), 1);

        List<Integer> candidates = Collections.singletonList(onlyCandidate.getEdge());

        int selected = calculator.selectStraightestEdge(candidates, currentState, 1);

        assertEquals(onlyCandidate.getEdge(), selected,
            "Should return the only candidate edge");
    }

    /**
     * Test a 4-way intersection where one edge continues straight.
     * <p>
     * Layout:
     *       2
     *       |
     *   3---1---4
     *       |
     *       0
     */
    @Test
    void testSelectStraightestEdge_FourWayIntersection() {
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 49.9, 10.0);   // South
        na.setNode(1, 50.0, 10.0);   // Center (common node)
        na.setNode(2, 50.1, 10.0);   // North (straight continuation)
        na.setNode(3, 50.0, 9.9);    // West
        na.setNode(4, 50.0, 10.1);   // East

        // Current edge: coming from south
        EdgeIteratorState currentEdge = graph.edge(1, 0).set(speedEnc, 50).setDistance(100);

        // Candidate edges coming INTO node 1
        EdgeIteratorState straightEdge = graph.edge(1, 2).set(speedEnc, 50).setDistance(100);   // North (straight)
        EdgeIteratorState westEdge = graph.edge(1, 3).set(speedEnc, 50).setDistance(100);       // West (90° turn)
        EdgeIteratorState eastEdge = graph.edge(1, 4).set(speedEnc, 50).setDistance(100);       // East (90° turn)

        EdgeIteratorState currentState = graph.getEdgeIteratorState(currentEdge.getEdge(), 1);

        List<Integer> candidates = Arrays.asList(straightEdge.getEdge(), westEdge.getEdge(), eastEdge.getEdge());

        int selected = calculator.selectStraightestEdge(candidates, currentState, 1);

        assertEquals(straightEdge.getEdge(), selected,
                "Should select the edge to node 2 (straight north) over the edges to nodes 3 and 4 (90° turns east/west)");
    }

    /**
     * Test with edges that have multiple geometry points (not just straight lines).
     * <p>
     * This simulates curved roads where the edge geometry has intermediate waypoints.
     * The bearing calculation should use the points nearest to the common node to
     * determine the approach/exit angles, not the endpoints of the entire edge.
     * <p>
     * Layout:
     *   0 (curved) -> 1 -> 2 (straight)
     *                 |
     *                 3 (curved)
     */
    @Test
    void testSelectStraightestEdge_WithGeometryPoints() {
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 50.0, 9.8);    // West
        na.setNode(1, 50.0, 10.0);   // Center (common node)
        na.setNode(2, 50.0, 10.2);   // East (straight continuation)
        na.setNode(3, 49.8, 10.0);   // South

        // Current edge: curved approach from west
        EdgeIteratorState currentEdge = graph.edge(1, 0).set(speedEnc, 50).setDistance(150);
        // Add geometry points to simulate a curved road that straightens out as it approaches node 1
        currentEdge.setWayGeometry(Helper.createPointList(50.0, 9.85, 50.0, 9.9, 50.0, 9.95));

        // Candidate 1: straight continuation east (no geometry points needed)
        EdgeIteratorState straightEdge = graph.edge(1, 2).set(speedEnc, 50).setDistance(100);

        // Candidate 2: curved road turning south
        EdgeIteratorState curvedTurnEdge = graph.edge(1, 3).set(speedEnc, 50).setDistance(150);
        // Add geometry points to simulate a road that curves as it leaves node 1
        curvedTurnEdge.setWayGeometry(Helper.createPointList(49.9, 10.0, 49.85, 10.0));

        EdgeIteratorState currentState = graph.getEdgeIteratorState(currentEdge.getEdge(), 1);

        List<Integer> candidates = Arrays.asList(straightEdge.getEdge(), curvedTurnEdge.getEdge());

        int selected = calculator.selectStraightestEdge(candidates, currentState, 1);

        assertEquals(straightEdge.getEdge(), selected,
                "Should select the straight edge (to node 2) over the curved turning edge (to node 3), " +
                "using the geometry points nearest to the common node for bearing calculation");
    }

    /**
     * Test validation: null candidate list should throw exception.
     */
    @Test
    void testSelectStraightestEdge_NullCandidates() {
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 50.0, 10.0);
        na.setNode(1, 50.0, 10.1);

        EdgeIteratorState currentEdge = graph.edge(0, 1).set(speedEnc, 50).setDistance(100);
        EdgeIteratorState currentState = graph.getEdgeIteratorState(currentEdge.getEdge(), 1);

        assertThrows(IllegalArgumentException.class, () -> {
            calculator.selectStraightestEdge(null, currentState, 1);
        }, "Should throw exception for null candidate list");
    }

    /**
     * Test validation: empty candidate list should throw exception.
     */
    @Test
    void testSelectStraightestEdge_EmptyCandidates() {
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 50.0, 10.0);
        na.setNode(1, 50.0, 10.1);

        EdgeIteratorState currentEdge = graph.edge(0, 1).set(speedEnc, 50).setDistance(100);
        EdgeIteratorState currentState = graph.getEdgeIteratorState(currentEdge.getEdge(), 1);

        assertThrows(IllegalArgumentException.class, () -> {
            calculator.selectStraightestEdge(Collections.emptyList(), currentState, 1);
        }, "Should throw exception for empty candidate list");
    }

    /**
     * Test validation: null current state should throw exception.
     */
    @Test
    void testSelectStraightestEdge_NullCurrentState() {
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 50.0, 10.0);
        na.setNode(1, 50.0, 10.1);
        na.setNode(2, 50.0, 10.2);

        EdgeIteratorState edge = graph.edge(2, 1).set(speedEnc, 50).setDistance(100);
        List<Integer> candidates = Collections.singletonList(edge.getEdge());

        assertThrows(NullPointerException.class, () -> {
            calculator.selectStraightestEdge(candidates, null, 1);
        }, "Should throw exception for null current state");
    }

    /**
     * Test a Y-junction where we need to choose between two similar angles.
     * <p>
     * Layout:
     *     2   3
     *      \ /
     *       1
     *       |
     *       0
     */
    @Test
    void testSelectStraightestEdge_YJunction() {
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 49.9, 10.0);   // South
        na.setNode(1, 50.0, 10.0);   // Center (common node)
        na.setNode(2, 50.1, 9.94);   // Northwest branch
        na.setNode(3, 50.1, 10.05);  // Northeast branch (more aligned)

        // Current edge comes INTO node 1, candidate edges ALSO COME INTO node 1
        EdgeIteratorState currentEdge = graph.edge(0, 1).set(speedEnc, 50).setDistance(100);
        EdgeIteratorState leftBranch = graph.edge(2, 1).set(speedEnc, 50).setDistance(100);
        EdgeIteratorState rightBranch = graph.edge(3, 1).set(speedEnc, 50).setDistance(100);

        EdgeIteratorState currentState = graph.getEdgeIteratorState(currentEdge.getEdge(), 1);

        List<Integer> candidates = Arrays.asList(leftBranch.getEdge(), rightBranch.getEdge());

        int selected = calculator.selectStraightestEdge(candidates, currentState, 1);

        // The right branch (node 3) should be more aligned with continuing straight
        assertEquals(rightBranch.getEdge(), selected,
            "Should select the branch more aligned with straight continuation");
    }

    /**
     * Test with nearly parallel edges to ensure precision in angle calculation.
     * <p>
     * This test verifies that when two candidate edges have very similar approach
     * bearings, the calculator can distinguish which one is more aligned with the
     * current trajectory.
     */
    @Test
    void testSelectStraightestEdge_NearlyParallelEdges() {
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 50.0, 10.0);      // West
        na.setNode(1, 50.0, 10.1);      // Center (common node)
        na.setNode(2, 50.0, 10.2);      // East - straight
        na.setNode(3, 50.005, 10.2);    // East-NE - slightly off

        EdgeIteratorState currentEdge = graph.edge(1, 0).set(speedEnc, 50).setDistance(100);
        EdgeIteratorState straightEdge = graph.edge(2, 1).set(speedEnc, 50).setDistance(100);
        EdgeIteratorState slightlyOffEdge = graph.edge(3, 1).set(speedEnc, 50).setDistance(100);

        // Get current state oriented AWAY from common node (as it is in real usage)
        EdgeIteratorState currentState = graph.getEdgeIteratorState(currentEdge.getEdge(), 0);

        List<Integer> candidates = Arrays.asList(straightEdge.getEdge(), slightlyOffEdge.getEdge());

        int selected = calculator.selectStraightestEdge(candidates, currentState, 1);

        assertEquals(straightEdge.getEdge(), selected,
                "Should select the perfectly straight edge (from node 2 at same latitude) over the slightly deviated edge (from node 3 to the northeast)");
    }

    /**
     * REGRESSION TEST: Real-world scenario from integration test.
     * <p>
     * This test uses actual coordinates from a real road network intersection
     * to ensure the calculator correctly selects the straightest continuation.
     * <p>
     * Based on integration test logs:
     * - Common node: 151819 at (39.586740, -119.827434)
     * - Current edge: from 151819 to 151673 (approach bearing ~21.77°)
     * - Candidate 1: from 151821 to 151819 (turn angle ~24.87°, deviation ~155.13°)
     * - Candidate 2: from 151675 to 151819 (turn angle ~176.04°, deviation ~3.96°) ← CORRECT
     * <p>
     * The correct choice is candidate 2 (from node 151675) because it has a turn angle
     * closest to 180°, representing the straightest continuation through the intersection.
     */
    @Test
    void testSelectStraightestEdge_RealWorldRegression() {
        // Real-world coordinates from Nevada road network
        NodeAccess na = graph.getNodeAccess();

        // Common node (intersection point)
        na.setNode(151819, 39.586740, -119.827434);

        // Node we're coming from (current edge endpoint)
        na.setNode(151673, 39.587210, -119.827190);

        // Candidate edge endpoints
        na.setNode(151821, 39.586828, -119.827440);  // Sharp turn (~25° turn angle)
        na.setNode(151675, 39.585116, -119.828524);  // Straight continuation (~176° turn angle)

        // Create edges: all coming INTO the common node 151819
        EdgeIteratorState currentEdge = graph.edge(151819, 151673).set(speedEnc, 50).setDistance(100);
        EdgeIteratorState sharpTurnEdge = graph.edge(151821, 151819).set(speedEnc, 50).setDistance(100);
        EdgeIteratorState straightEdge = graph.edge(151675, 151819).set(speedEnc, 50).setDistance(100);

        // Orient current edge toward the common node
        EdgeIteratorState currentState = graph.getEdgeIteratorState(currentEdge.getEdge(), 151673);

        List<Integer> candidates = Arrays.asList(sharpTurnEdge.getEdge(), straightEdge.getEdge());

        int selected = calculator.selectStraightestEdge(candidates, currentState, 151819);

        assertEquals(straightEdge.getEdge(), selected,
            "Should select the edge from node 151675 as it continues nearly straight (176° turn angle) " +
            "rather than the edge from node 151821 which makes a sharp turn (25° turn angle)");
    }
}
