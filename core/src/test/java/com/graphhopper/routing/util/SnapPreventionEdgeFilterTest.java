package com.graphhopper.routing.util;

import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.core.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SnapPreventionEdgeFilterTest {

    @Test
    public void accept() {
        EdgeFilter trueFilter = edgeState -> true;
        EncodingManager em = new EncodingManager.Builder().build();
        EnumEncodedValue<RoadClass> rcEnc = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        EnumEncodedValue<RoadEnvironment> reEnc = em.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        SnapPreventionEdgeFilter filter = new SnapPreventionEdgeFilter(trueFilter, rcEnc, reEnc, Arrays.asList("motorway", "ferry"));
        BaseGraph graph = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(1);

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
}