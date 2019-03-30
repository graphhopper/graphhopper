package com.graphhopper.storage;

import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Locale;

public class ShortcutUnpacker {
    private final Graph graph;
    private final Visitor visitor;
    private boolean reverseOrder;

    public ShortcutUnpacker(Graph graph, Visitor visitor) {
        this.graph = graph;
        this.visitor = visitor;
    }

    /**
     * Finds a shortcut with the given id and adjNode and calls the visitor for each original edge that is
     * packed inside this shortcut (or if an original edge is given simply calls the visitor on it).
     *
     * @param reverseOrder if true the visitor original edges will be traversed in reverse order
     */
    public void visitOriginalEdges(int edgeId, int adjNode, boolean reverseOrder) {
        this.reverseOrder = reverseOrder;
        CHEdgeIteratorState edge = getEdge(edgeId, adjNode);
        if (edge == null) {
            throw new IllegalArgumentException("Edge with id: " + edgeId + " does not exist or does not touch node " + adjNode);
        }
        expandEdge(edge, false);
    }

    private void expandEdge(CHEdgeIteratorState edge, boolean reverse) {
        if (!edge.isShortcut()) {
            // todo: should properly pass previous edge here. for example this is important for turn cost time evaluation
            // with edge-based CH, #1585
            visitor.visit(edge, reverse, EdgeIterator.NO_EDGE);
            return;
        }
        expandSkippedEdges(edge.getSkippedEdge1(), edge.getSkippedEdge2(), edge.getBaseNode(), edge.getAdjNode(), reverse);
    }

    private void expandSkippedEdges(int skippedEdge1, int skippedEdge2, int from, int to, boolean reverse) {
        // for edge-based CH we need to take special care for loop shortcuts
        if (from != to) {
            // get properties like speed of the edge in the correct direction
            if (reverseOrder == reverse) {
                int tmp = from;
                from = to;
                to = tmp;
            }
            CHEdgeIteratorState sk2to = getEdge(skippedEdge2, to);
            if (sk2to != null) {
                expandEdge(sk2to, !reverseOrder);
                expandEdge(getEdge(skippedEdge1, from), reverseOrder);
            } else {
                expandEdge(getEdge(skippedEdge1, to), !reverseOrder);
                expandEdge(getEdge(skippedEdge2, from), reverseOrder);
            }
        } else {
            CHEdgeIteratorState sk1 = getEdge(skippedEdge1, from);
            CHEdgeIteratorState sk2 = getEdge(skippedEdge2, from);
            if (sk1.getAdjNode() == sk1.getBaseNode() || sk2.getAdjNode() == sk2.getBaseNode()) {
                // this is a loop where both skipped edges are loops. but this should never happen.
                throw new IllegalStateException(String.format(Locale.ROOT,
                        "error: detected edge where both skipped edges are loops. from: %d, to: %d, " +
                                "skip-edge1: %d, skip-edge2: %d, reverse: %b", from, to, skippedEdge1, skippedEdge2, reverse));
            }

            if (!reverseOrder) {
                expandEdge(sk1, !reverse);
                expandEdge(sk2, reverse);
            } else {
                expandEdge(sk2, reverse);
                expandEdge(sk1, !reverse);
            }
        }
    }

    private CHEdgeIteratorState getEdge(int edgeId, int adjNode) {
        return (CHEdgeIteratorState) graph.getEdgeIteratorState(edgeId, adjNode);
    }

    public interface Visitor {
        void visit(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId);
    }
}
