package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.Curvature;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CurvatureCalculatorTest {

    private final EncodingManager em = EncodingManager.start().add(Curvature.create()).build();
    @Test
    public void testCurvature() {
        BaseGraph graph = new BaseGraph.Builder(em).set3D(true).create();
        NodeAccess na = graph.getNodeAccess();
        // 50--(0.0001)-->49--(0.0004)-->55--(0.0005)-->60
        na.setNode(0, 51.1, 12.001, 50);
        na.setNode(1, 51.1, 12.002, 60);
        EdgeIteratorState edge = graph.edge(0, 1).
                setWayGeometry(Helper.createPointList3D(51.1, 12.0011, 49, 51.1, 12.0015, 55));
        edge.setDistance(100);

//        edge.set(motorcycleAccessEnc, true, true).set(motorcycleSpeedEnc, 10.0, 15.0);

        double bendinessOfStraightWay = getBendiness(edge, 100.0);
        double bendinessOfCurvyWay = getBendiness(edge, 10.0);

        assertTrue(bendinessOfCurvyWay < bendinessOfStraightWay, "The bendiness of the straight road is smaller than the one of the curvy road");
    }

    private double getBendiness(EdgeIteratorState edge, double beelineDistance) {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");

        GHPoint point = new GHPoint(11.3, 45.2);
        GHPoint toPoint = DistanceCalcEarth.DIST_EARTH.projectCoordinate(point.lat, point.lon, beelineDistance, 90);
        PointList pointList = new PointList();
        pointList.add(point);
        pointList.add(toPoint);
        way.setTag("point_list", pointList);
        way.setTag("edge_distance", edge.getDistance());

        CurvatureCalculator calculator = new CurvatureCalculator(em.getDecimalEncodedValue(Curvature.KEY));
        IntsRef flags = calculator.handleWayTags(em.createEdgeFlags(), way, null);
        edge.setFlags(flags);
        return edge.get(em.getDecimalEncodedValue(Curvature.KEY));
    }

}