package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.Curvature;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.core.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CurvatureCalculatorTest {

    private final EncodingManager em = EncodingManager.start().add(Curvature.create()).build();

    @Test
    public void testCurvature() {
        CurvatureCalculator calculator = new CurvatureCalculator(em.getDecimalEncodedValue(Curvature.KEY));
        IntsRef ints = em.createEdgeFlags();
        calculator.handleWayTags(ints, getStraightWay(), null);
        double valueStraight = em.getDecimalEncodedValue(Curvature.KEY).getDecimal(false, ints);

        ints = em.createEdgeFlags();
        calculator.handleWayTags(ints, getCurvyWay(), null);
        double valueCurvy = em.getDecimalEncodedValue(Curvature.KEY).getDecimal(false, ints);

        assertTrue(valueCurvy < valueStraight, "The bendiness of the straight road is smaller than the one of the curvy road");
    }

    private ReaderWay getStraightWay() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        PointList pointList = new PointList();
        pointList.add(50.9, 13.13);
        pointList.add(50.899, 13.13);
        way.setTag("point_list", pointList);
        way.setTag("edge_distance", 100d);
        return way;
    }

    private ReaderWay getCurvyWay() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        PointList pointList = new PointList();
        pointList.add(50.9, 13.13);
        pointList.add(50.899, 13.129);
        pointList.add(50.899, 13.13);
        way.setTag("point_list", pointList);
        way.setTag("edge_distance", 160d);
        return way;
    }

}