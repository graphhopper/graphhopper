package com.graphhopper.routing.util;

import com.graphhopper.routing.ev.AccessControl;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SnapPreventionEdgeFilterTest {

    @Test
    public void accept() {
        EdgeFilter trueFilter = edgeState -> true;
        EncodingManager em = new EncodingManager.Builder().add(RoadClass.create()).add(RoadEnvironment.create())
                .add(AccessControl.create()).build();
        EnumEncodedValue<RoadClass> rcEnc = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        EnumEncodedValue<RoadEnvironment> reEnc = em.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        EnumEncodedValue<AccessControl> acEnc = em.getEnumEncodedValue(AccessControl.KEY, AccessControl.class);
        SnapPreventionEdgeFilter filter = new SnapPreventionEdgeFilter(trueFilter, rcEnc, reEnc, acEnc, Arrays.asList("motorway", "ferry"));
        BaseGraph graph = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(100);

        assertTrue(filter.accept(edge));
        edge.set(reEnc, RoadEnvironment.FERRY);
        assertFalse(filter.accept(edge));
        edge.set(reEnc, RoadEnvironment.FORD);
        assertTrue(filter.accept(edge));

        edge.set(rcEnc, RoadClass.RESIDENTIAL);
        assertTrue(filter.accept(edge));
        edge.set(rcEnc, RoadClass.MOTORWAY);
        assertFalse(filter.accept(edge));
    }

    @Test
    public void acceptAccessControlRestricted() {
        EdgeFilter trueFilter = edgeState -> true;
        EncodingManager em = new EncodingManager.Builder().add(RoadClass.create()).add(RoadEnvironment.create())
                .add(AccessControl.create()).build();
        EnumEncodedValue<RoadClass> rcEnc = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        EnumEncodedValue<RoadEnvironment> reEnc = em.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        EnumEncodedValue<AccessControl> acEnc = em.getEnumEncodedValue(AccessControl.KEY, AccessControl.class);
        SnapPreventionEdgeFilter filter = new SnapPreventionEdgeFilter(trueFilter, rcEnc, reEnc, acEnc,
                Arrays.asList("access_control_restricted"));
        BaseGraph graph = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(100);

        // Should accept by default (no access control)
        assertTrue(filter.accept(edge));

        // Should reject when access_control=FULL
        edge.set(acEnc, AccessControl.FULL);
        assertFalse(filter.accept(edge));

        // Should accept when access_control=OTHER
        edge.set(acEnc, AccessControl.OTHER);
        assertTrue(filter.accept(edge));
    }
}
