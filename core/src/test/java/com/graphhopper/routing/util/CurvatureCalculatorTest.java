package com.graphhopper.routing.util;

import com.graphhopper.routing.ev.Curvature;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurvatureCalculatorTest {

    private final EncodingManager em = EncodingManager.start().add(Curvature.create()).build();
    private final DecimalEncodedValue curvatureEnc = em.getDecimalEncodedValue(Curvature.KEY);

    @Test
    public void testCurvature() {
        BaseGraph graph = new BaseGraph.Builder(em).create();
        NodeAccess na = graph.getNodeAccess();

        // straight way: 2 tower nodes, ~100m
        na.setNode(0, 50.9, 13.13);
        na.setNode(1, 50.899, 13.13);
        EdgeIteratorState straightEdge = graph.edge(0, 1).setDistance(100);

        // curvy way: 2 tower nodes + 1 pillar, ~160m
        na.setNode(2, 50.9, 13.13);
        na.setNode(3, 50.899, 13.13);
        PointList pillar = new PointList();
        pillar.add(50.899, 13.129);
        EdgeIteratorState curvyEdge = graph.edge(2, 3).setWayGeometry(pillar).setDistance(160);

        new CurvatureCalculator(curvatureEnc).execute(graph);

        double valueStraight = straightEdge.get(curvatureEnc);
        double valueCurvy = curvyEdge.get(curvatureEnc);
        assertTrue(valueCurvy < valueStraight, "The bendiness of the straight road is smaller than the one of the curvy road");
    }

    @Test
    public void testCurvatureWithElevation() {
        BaseGraph graph = new BaseGraph.Builder(em).set3D(true).create();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 50.9, 13.13, 0);
        na.setNode(1, 50.899, 13.13, 100);
        PointList pointList = new PointList(2, true);
        pointList.add(50.9, 13.13, 0);
        pointList.add(50.899, 13.13, 100);
        double distance = DistanceCalcEarth.DIST_EARTH.calcDistance(pointList);
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(distance);

        new CurvatureCalculator(curvatureEnc).execute(graph);

        double curvature = edge.get(curvatureEnc);
        assertEquals(1, curvature, 0.01);
    }
}
