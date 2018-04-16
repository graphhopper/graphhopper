package com.graphhopper.routing.util;

import com.graphhopper.routing.VirtualEdgeIteratorState;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Objects;

public class EdgeData {
    private final int edgeId;
    private final int baseNode;
    private final int adjNode;

    public EdgeData(int edgeId, int baseNode, int adjNode) {
        this.edgeId = edgeId;
        this.baseNode = baseNode;
        this.adjNode = adjNode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EdgeData edgeData = (EdgeData) o;
        return edgeId == edgeData.edgeId &&
                baseNode == edgeData.baseNode &&
                adjNode == edgeData.adjNode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(edgeId, baseNode, adjNode);
    }

    public static int getEdgeId(EdgeIteratorState edgeState) {
        if (edgeState instanceof VirtualEdgeIteratorState) {
            VirtualEdgeIteratorState vEdge = (VirtualEdgeIteratorState) edgeState;
            return vEdge.getOriginalTraversalKey() / 2;
        }
        return edgeState.getEdge();
    }
}
