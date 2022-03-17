package com.graphhopper.routing.util;

import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeadingEdgeFilterTest {

    @Test
    public void getHeading() {
        GHPoint point = new GHPoint(55.67093, 12.577294);
        CarFlagEncoder carEncoder = new CarFlagEncoder();
        EncodingManager encodingManager = new EncodingManager.Builder().add(carEncoder).build();
        BaseGraph g = new BaseGraph.Builder(encodingManager).create();
        EdgeIteratorState edge = g.edge(0, 1);
        g.getNodeAccess().setNode(0, 55.671044, 12.5771583);
        g.getNodeAccess().setNode(1, 55.6704136, 12.5784324);
        // GHUtility.setSpeed(50, 0, carEncoder, edge.getFlags());

        assertEquals(131.2, HeadingEdgeFilter.getHeadingOfGeometryNearPoint(edge, point, 20), .1);
    }
}