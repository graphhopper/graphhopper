package com.graphhopper.routing.weighting.custom;

import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.Polygon;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CustomWeightingHelperTest {

    @Test
    public void testInRectangle() {
        Polygon square = new Polygon(new double[]{0, 0, 20, 20}, new double[]{0, 20, 20, 0});
        assertTrue(square.isRectangle());

        BaseGraph g = new BaseGraph.Builder(1).create();

        // (1,1) (2,2) (3,3)
        // Polygon fully contains the edge and its BBox
        g.getNodeAccess().setNode(0, 1, 1);
        g.getNodeAccess().setNode(1, 3, 3);
        EdgeIteratorState edge = g.edge(0, 1).setWayGeometry(Helper.createPointList(2, 2));
        assertTrue(CustomWeightingHelper.in(square, edge));

        // (0,0) (20,0) (20,20)
        // Polygon contains the edge; BBoxes overlap
        g.getNodeAccess().setNode(2, 0, 0);
        g.getNodeAccess().setNode(3, 20, 20);
        edge = g.edge(2, 3).setWayGeometry(Helper.createPointList(20, 0));
        assertTrue(CustomWeightingHelper.in(square, edge));

        // (0,30) (10,40) (20,50)
        // Edge is outside the polygon; BBoxes are not intersecting
        g.getNodeAccess().setNode(4, 0, 30);
        g.getNodeAccess().setNode(5, 20, 50);
        edge = g.edge(4, 5).setWayGeometry(Helper.createPointList(10, 40));
        assertFalse(CustomWeightingHelper.in(square, edge));

        // (0,30) (30,30) (30,0)
        // Edge is outside the polygon; BBoxes are intersecting
        g.getNodeAccess().setNode(6, 0, 30);
        g.getNodeAccess().setNode(7, 30, 0);
        edge = g.edge(6, 7).setWayGeometry(Helper.createPointList(30, 30));
        assertFalse(CustomWeightingHelper.in(square, edge));
    }
}
