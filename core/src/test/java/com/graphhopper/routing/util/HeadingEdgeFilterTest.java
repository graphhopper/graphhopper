package com.graphhopper.routing.util;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.SimpleBooleanEncodedValue;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.core.util.EdgeIteratorState;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeadingEdgeFilterTest {

    @Test
    public void getHeading() {
        GHPoint point = new GHPoint(55.67093, 12.577294);
        BooleanEncodedValue carAccessEnc = new SimpleBooleanEncodedValue("car_access", true);
        DecimalEncodedValue carSpeedEnc = new DecimalEncodedValueImpl("car_speed", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(carAccessEnc).add(carSpeedEnc).build();
        BaseGraph g = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge = g.edge(0, 1);
        g.getNodeAccess().setNode(0, 55.671044, 12.5771583);
        g.getNodeAccess().setNode(1, 55.6704136, 12.5784324);
        // GHUtility.setSpeed(50, 0, carAccessEnc, carSpeedEnc, edge.getFlags());
        assertEquals(131.2, HeadingEdgeFilter.getHeadingOfGeometryNearPoint(edge, point, 20), .1);
    }
}