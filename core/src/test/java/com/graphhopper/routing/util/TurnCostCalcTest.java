package com.graphhopper.routing.util;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TurnCostCalcTest {
    static double RIGHT = -90, STRAIGHT = 0, LEFT = 90;

    @Test
    public void testTurns() {
        FlagEncoder encoder = new CarFlagEncoder();
        Graph graph = new GraphHopperStorage(new RAMDirectory(), EncodingManager.create(encoder), false, false, 1024 * 1024);
        BooleanEncodedValue accessEnc = encoder.getAccessEnc();
        DecimalEncodedValue avSpeedEnc = encoder.getAverageSpeedEnc();
        NodeAccess nodeAccess = graph.getNodeAccess();

        //       4   5
        //   0 - 1 - 2
        //       3   6

        nodeAccess.setNode(0, 51.0362, 13.714);
        nodeAccess.setNode(1, 51.0362, 13.720);
        nodeAccess.setNode(2, 51.0362, 13.726);
        nodeAccess.setNode(3, 51.0358, 13.720);
        nodeAccess.setNode(4, 51.0366, 13.720);
        nodeAccess.setNode(5, 51.0366, 13.726);
        nodeAccess.setNode(6, 51.0358, 13.726);

        EdgeIteratorState edge01 = graph.edge(0, 1).setDistance(500).set(avSpeedEnc, 50).set(accessEnc, true, true);
        EdgeIteratorState edge13 = graph.edge(1, 3).setDistance(500).set(avSpeedEnc, 50).set(accessEnc, true, true);
        EdgeIteratorState edge14 = graph.edge(1, 4).setDistance(500).set(avSpeedEnc, 50).set(accessEnc, true, true);
        EdgeIteratorState edge26 = graph.edge(2, 6).setDistance(500).set(avSpeedEnc, 50).set(accessEnc, true, true);
        EdgeIteratorState edge25 = graph.edge(2, 5).setDistance(500).set(avSpeedEnc, 50).set(accessEnc, true, true);
        EdgeIteratorState edge12 = graph.edge(1, 2).setDistance(500).set(avSpeedEnc, 50).set(accessEnc, true, true);

        EdgeExplorer explorer = graph.createEdgeExplorer();
        // from top to left
        assertEquals(RIGHT, TurnCostCalc.calcTurnCost180(explorer, nodeAccess, edge14.getEdge(), 1, edge01.getEdge()));
        // top to down
        assertEquals(STRAIGHT, TurnCostCalc.calcTurnCost180(explorer, nodeAccess, edge14.getEdge(), 1, edge13.getEdge()));
        // top to right
        assertEquals(LEFT, TurnCostCalc.calcTurnCost180(explorer, nodeAccess, edge14.getEdge(), 1, edge12.getEdge()));
        // left to down
        assertEquals(RIGHT, TurnCostCalc.calcTurnCost180(explorer, nodeAccess, edge01.getEdge(), 1, edge13.getEdge()));
        // left to top
        assertEquals(LEFT, TurnCostCalc.calcTurnCost180(explorer, nodeAccess, edge01.getEdge(), 1, edge14.getEdge()));
        // left to top
        assertEquals(LEFT, TurnCostCalc.calcTurnCost180(explorer, nodeAccess, edge12.getEdge(), 2, edge25.getEdge()));
        // down to left
        assertEquals(LEFT, TurnCostCalc.calcTurnCost180(explorer, nodeAccess, edge26.getEdge(), 2, edge12.getEdge()));
        // top to left
        assertEquals(RIGHT, TurnCostCalc.calcTurnCost180(explorer, nodeAccess, edge25.getEdge(), 2, edge12.getEdge()));
    }
}