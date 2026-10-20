package com.graphhopper.routing.util;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.Level;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelEdgeFilterTest {

    @Test
    void testAccept() {
        DecimalEncodedValue levelEnc = Level.create();
        EncodingManager em = new EncodingManager.Builder().add(levelEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(100);

        edge.set(levelEnc, 1.0);

        LevelEdgeFilter filterLevel1 = new LevelEdgeFilter(EdgeFilter.ALL_EDGES, levelEnc, 1.0);
        assertTrue(filterLevel1.accept(edge));

        LevelEdgeFilter filterLevel0 = new LevelEdgeFilter(EdgeFilter.ALL_EDGES, levelEnc, 0.0);
        assertFalse(filterLevel0.accept(edge));

        LevelEdgeFilter filterNaN = new LevelEdgeFilter(EdgeFilter.ALL_EDGES, levelEnc, Double.NaN);
        assertTrue(filterNaN.accept(edge));
    }
}
