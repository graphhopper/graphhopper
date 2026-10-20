package com.graphhopper.routing.matrix;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MatrixCopyIdentityTest {
    @Test
    void onlyUpstreamCopiesAlignAndUnrelatedIdenticalParallelEdgesRemainDistinct() {
        DecimalEncodedValue speed = new DecimalEncodedValueImpl("speed", 5, 1, true);
        EncodingManager em = EncodingManager.start().add(speed).build();
        try (BaseGraph graph = new BaseGraph.Builder(em).create()) {
            EdgeIteratorState original = graph.edge(0, 1).setDistance(10).set(speed, 5, 5);
            EdgeIteratorState copy = graph.copyEdge(original.getEdge(), true);
            EdgeIteratorState unrelated = graph.edge(0, 1).setDistance(10).set(speed, 5, 5);
            EdgeIteratorState separateGeometry = graph.copyEdge(original.getEdge(), false);
            assertEquals(MatrixPrecisionDiagnostics.canonicalDirectedEdgeKey(graph, original.getEdgeKey()),
                    MatrixPrecisionDiagnostics.canonicalDirectedEdgeKey(graph, copy.getEdgeKey()));
            assertEquals(MatrixPrecisionDiagnostics.canonicalDirectedEdgeKey(graph, original.getReverseEdgeKey()),
                    MatrixPrecisionDiagnostics.canonicalDirectedEdgeKey(graph, copy.getReverseEdgeKey()));
            assertNotEquals(MatrixPrecisionDiagnostics.canonicalDirectedEdgeKey(graph, original.getEdgeKey()),
                    MatrixPrecisionDiagnostics.canonicalDirectedEdgeKey(graph, original.getReverseEdgeKey()));
            assertNotEquals(MatrixPrecisionDiagnostics.canonicalDirectedEdgeKey(graph, original.getEdgeKey()),
                    MatrixPrecisionDiagnostics.canonicalDirectedEdgeKey(graph, unrelated.getEdgeKey()));
            assertNotEquals(MatrixPrecisionDiagnostics.canonicalDirectedEdgeKey(graph, original.getEdgeKey()),
                    MatrixPrecisionDiagnostics.canonicalDirectedEdgeKey(graph, separateGeometry.getEdgeKey()));
        }
    }
}
