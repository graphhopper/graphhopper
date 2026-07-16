package com.graphhopper.routing.util;

import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlopeCalculatorTest {

    @Test
    void simpleElevation() {
        DecimalEncodedValue averageEnc = AverageSlope.create();
        DecimalEncodedValue maxEnc = MaxSlope.create();
        EncodingManager em = new EncodingManager.Builder().add(averageEnc).add(maxEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).set3D(true).create();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 51.0, 12.001, 0);
        na.setNode(1, 51.0, 12.004, 2);
        PointList pillarNodes = new PointList(3, true);
        pillarNodes.add(51.0, 12.002, 3.5);
        pillarNodes.add(51.0, 12.003, 4);
        EdgeIteratorState edge = graph.edge(0, 1).setWayGeometry(pillarNodes);
        edge.setDistance(DistanceCalcEarth.calcDistance(edge.fetchWayGeometry(FetchMode.ALL), false));

        new SlopeCalculator(maxEnc, averageEnc).execute(graph);

        assertEquals(Math.round(2.0 / 210 * 100), edge.get(averageEnc), 1e-3);
        assertEquals(-Math.round(2.0 / 210 * 100), edge.getReverse(averageEnc), 1e-3);

        assertEquals(Math.round(1.75 / 105 * 100), edge.get(maxEnc), 1e-3);
        assertEquals(-Math.round(1.75 / 105 * 100), edge.getReverse(maxEnc), 1e-3);
    }

    @Test
    public void testAveragingOfMaxSlope() {
        DecimalEncodedValue averageEnc = AverageSlope.create();
        DecimalEncodedValue maxEnc = MaxSlope.create();
        EncodingManager em = new EncodingManager.Builder().add(averageEnc).add(maxEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).set3D(true).create();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 51.0, 12.0010, 10);
        na.setNode(1, 51.0, 12.0070, 7);
        PointList pillarNodes = new PointList(3, true);
        pillarNodes.add(51.0, 12.0014, 8);
        pillarNodes.add(51.0, 12.0034, 8);
        pillarNodes.add(51.0, 12.0054, 0);
        EdgeIteratorState edge = graph.edge(0, 1).setWayGeometry(pillarNodes);
        edge.setDistance(DistanceCalcEarth.calcDistance(edge.fetchWayGeometry(FetchMode.ALL), false));

        new SlopeCalculator(maxEnc, averageEnc).execute(graph);

        assertEquals(-Math.round(8.0 / 210 * 100), edge.get(maxEnc), 1e-3);
        assertEquals(Math.round(8.0 / 210 * 100), edge.getReverse(maxEnc), 1e-3);
    }

    @Test
    public void testMaxSlopeLargerThanMaxStorableDecimal() {
        DecimalEncodedValue averageEnc = AverageSlope.create();
        DecimalEncodedValue maxEnc = MaxSlope.create();
        EncodingManager em = new EncodingManager.Builder().add(averageEnc).add(maxEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).set3D(true).create();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 47.7281561, 11.9993135, 1163.0);
        na.setNode(1, 47.7283135, 11.9991135, 1178.0);
        PointList pillarNodes = new PointList(1, true);
        pillarNodes.add(47.7282782, 11.9991944, 1163.0);
        EdgeIteratorState edge = graph.edge(0, 1).setWayGeometry(pillarNodes);
        edge.setDistance(DistanceCalcEarth.calcDistance(edge.fetchWayGeometry(FetchMode.ALL), false));

        new SlopeCalculator(maxEnc, averageEnc).execute(graph);

        assertEquals(31, edge.get(maxEnc), 1e-3);
        assertEquals(-31, edge.getReverse(averageEnc), 1e-3);
    }

    @Test
    public void testMaxSlopeSmallerThanMinStorableDecimal() {
        DecimalEncodedValue averageEnc = AverageSlope.create();
        DecimalEncodedValue maxEnc = MaxSlope.create();
        EncodingManager em = new EncodingManager.Builder().add(averageEnc).add(maxEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).set3D(true).create();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 47.7283135, 11.9991135, 1178.0);
        na.setNode(1, 47.7281561, 11.9993135, 1163.0);
        PointList pillarNodes = new PointList(1, true);
        pillarNodes.add(47.7282782, 11.9991944, 1163.0);
        EdgeIteratorState edge = graph.edge(0, 1).setWayGeometry(pillarNodes);
        edge.setDistance(DistanceCalcEarth.calcDistance(edge.fetchWayGeometry(FetchMode.ALL), false));

        new SlopeCalculator(maxEnc, averageEnc).execute(graph);

        assertEquals(-31, edge.get(maxEnc), 1e-3);
        assertEquals(31, edge.getReverse(averageEnc), 1e-3);
    }

    @Test
    public void testThrowErrorFor2D() {
        DecimalEncodedValue averageEnc = AverageSlope.create();
        DecimalEncodedValue maxEnc = MaxSlope.create();
        EncodingManager em = new EncodingManager.Builder().add(averageEnc).add(maxEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).create();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 47.7283135, 11.9991135);
        na.setNode(1, 47.7281561, 11.9993135);
        graph.edge(0, 1).setDistance(100);

        assertThrows(IllegalArgumentException.class, () -> new SlopeCalculator(maxEnc, averageEnc).execute(graph));
    }
}
