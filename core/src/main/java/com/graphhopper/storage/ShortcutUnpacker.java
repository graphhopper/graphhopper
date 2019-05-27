package com.graphhopper.storage;

import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Locale;

import static com.graphhopper.util.EdgeIterator.NO_EDGE;

/**
 * Recursively unpack shortcuts.
 * <p>
 *
 * @author Peter Karich
 * @author easbar
 * @see PrepareContractionHierarchies
 */
public class ShortcutUnpacker {
    private final Graph graph;
    private final Visitor visitor;
    private final boolean edgeBased;
    private boolean reverseOrder;

    public ShortcutUnpacker(Graph graph, Visitor visitor, boolean edgeBased) {
        this.graph = graph;
        this.visitor = visitor;
        this.edgeBased = edgeBased;
    }

    /**
     * Finds an edge/shortcut with the given id and adjNode and calls the visitor for each original edge that is
     * packed inside this shortcut (or if an original edge is given simply calls the visitor on it).
     *
     * @param reverseOrder if true the original edges will be traversed in reverse order
     */
    public void visitOriginalEdgesFwd(int edgeId, int adjNode, boolean reverseOrder, int prevOrNextEdgeId) {
        doVisitOriginalEdges(edgeId, adjNode, reverseOrder, false, prevOrNextEdgeId);
    }

    public void visitOriginalEdgesBwd(int edgeId, int adjNode, boolean reverseOrder, int prevOrNextEdgeId) {
        doVisitOriginalEdges(edgeId, adjNode, reverseOrder, true, prevOrNextEdgeId);
    }

    private void doVisitOriginalEdges(int edgeId, int adjNode, boolean reverseOrder, boolean reverse, int prevOrNextEdgeId) {
        this.reverseOrder = reverseOrder;
        CHEdgeIteratorState edge = getEdge(edgeId, adjNode);
        if (edge == null) {
            throw new IllegalArgumentException("Edge with id: " + edgeId + " does not exist or does not touch node " + adjNode);
        }
        expandEdge(edge, reverse, prevOrNextEdgeId);
    }

    private void expandEdge(CHEdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        if (!edge.isShortcut()) {
            visitor.visit(edge, reverse, prevOrNextEdgeId);
            return;
        }
        if (edgeBased) {
            expandSkippedEdgesEdgeBased(edge.getSkippedEdge1(), edge.getSkippedEdge2(), edge.getBaseNode(), edge.getAdjNode(), reverse, prevOrNextEdgeId);
        } else {
            expandSkippedEdgesNodeBased(edge.getSkippedEdge1(), edge.getSkippedEdge2(), edge.getBaseNode(), edge.getAdjNode(), reverse);
        }
    }

    private void expandSkippedEdgesEdgeBased(int skippedEdge1, int skippedEdge2, int base, int adj, boolean reverse, int prevOrNextEdgeId) {
        if (reverse) {
            int tmp = skippedEdge1;
            skippedEdge1 = skippedEdge2;
            skippedEdge2 = tmp;
        }
        CHEdgeIteratorState sk2 = getEdge(skippedEdge2, adj);
        assert sk2 != null : "skipped edge " + skippedEdge2 + " + is not attached to adjNode " + adj + ". this should " +
                "never happen because edge-based CH does not use bidirectional shortcuts at the moment";
        CHEdgeIteratorState sk1 = getEdge(skippedEdge1, sk2.getBaseNode());
        if (base == adj && (sk1.getAdjNode() == sk1.getBaseNode() || sk2.getAdjNode() == sk2.getBaseNode())) {
            throw new IllegalStateException(String.format(Locale.ROOT,
                    "error: detected edge where a skipped edges is a loop. this should never happen. base: %d, adj: %d, " +
                            "skip-edge1: %d, skip-edge2: %d, reverse: %b", base, adj, skippedEdge1, skippedEdge2, reverse));
        }
        int adjEdge = getOppositeEdge(sk1, base);
        if (reverseOrder) {
            expandEdge(sk2, reverse, adjEdge);
            expandEdge(sk1, reverse, prevOrNextEdgeId);
        } else {
            expandEdge(sk1, reverse, prevOrNextEdgeId);
            expandEdge(sk2, reverse, adjEdge);
        }
    }

    private void expandSkippedEdgesNodeBased(int skippedEdge1, int skippedEdge2, int base, int adj, boolean reverse) {
        CHEdgeIteratorState sk2 = getEdge(skippedEdge2, adj);
        CHEdgeIteratorState sk1;
        if (sk2 == null) {
            sk2 = getEdge(skippedEdge1, adj);
            sk1 = getEdge(skippedEdge2, sk2.getBaseNode());
        } else {
            sk1 = getEdge(skippedEdge1, sk2.getBaseNode());
        }
        if (reverseOrder) {
            expandEdge(sk2, reverse, NO_EDGE);
            expandEdge(sk1, reverse, NO_EDGE);
        } else {
            expandEdge(sk1, reverse, NO_EDGE);
            expandEdge(sk2, reverse, NO_EDGE);
        }
    }

    private int getOppositeEdge(CHEdgeIteratorState edgeState, int adjNode) {
        assert edgeState.getBaseNode() == adjNode || edgeState.getAdjNode() == adjNode : "adjNode " + adjNode + " must be one of adj/base of edgeState: " + edgeState;
        // since the first/last orig edge is not stateful (just like skipped1/2) we have to find out which one
        // is attached to adjNode, similar as we do for skipped1/2.
        return graph.isAdjacentToNode(edgeState.getOrigEdgeLast(), adjNode)
                ? edgeState.getOrigEdgeFirst()
                : edgeState.getOrigEdgeLast();
    }

    private CHEdgeIteratorState getEdge(int edgeId, int adjNode) {
        return (CHEdgeIteratorState) graph.getEdgeIteratorState(edgeId, adjNode);
    }

    public interface Visitor {
        void visit(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId);
    }
}
