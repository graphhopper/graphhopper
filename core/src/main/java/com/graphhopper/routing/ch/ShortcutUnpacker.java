package com.graphhopper.routing.ch;

import com.graphhopper.storage.RoutingCHEdgeIteratorState;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.storage.RoutingCHSingleEdgeCursor;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.SingleEdgeCursor;

import java.util.ArrayDeque;
import java.util.Deque;
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
    private final Deque<StackItem> stack = new ArrayDeque<>();
    private final RoutingCHGraph graph;
    private final SingleEdgeCursor singleBaseEdgeCursor;
    private final RoutingCHSingleEdgeCursor singleEdgeCursor;
    private final Visitor visitor;
    private final boolean edgeBased;
    private boolean reverseOrder;

    public ShortcutUnpacker(RoutingCHGraph graph, Visitor visitor, boolean edgeBased) {
        this.graph = graph;
        this.singleBaseEdgeCursor = graph.getBaseGraph().createSingleEdgeCursor();
        this.singleEdgeCursor = graph.createSingleEdgeCursor();
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
        stack.clear();
        stack.push(new StackItem(edgeId, adjNode, reverse, prevOrNextEdgeId));
        while (!stack.isEmpty()) {
            StackItem item = stack.pop();
            expandEdge(item.edgeId, item.adjNode, item.reverse, item.prevOrNextEdgeId);
        }
    }

    private void expandEdge(int edgeId, int adjNode, boolean reverse, int prevOrNextEdgeId) {
        RoutingCHEdgeIteratorState edge = getEdge(edgeId, adjNode);
        if (edge == null) {
            throw new IllegalArgumentException("Edge with id: " + edgeId + " does not exist or does not touch node " + adjNode);
        }
        if (!edge.isShortcut()) {
            visitor.visit(singleBaseEdgeCursor.setEdge(edge.getOrigEdge(), edge.getAdjNode()), reverse, prevOrNextEdgeId);
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

        RoutingCHEdgeIteratorState sk2 = getEdge(skippedEdge2, adj);
        assert sk2 != null : "skipped edge " + skippedEdge2 + " + is not attached to adjNode " + adj + ". this should " +
                "never happen because edge-based CH does not use bidirectional shortcuts at the moment";
        int sk2AdjNode = sk2.getAdjNode();
        int sk2BaseNode = sk2.getBaseNode();
        RoutingCHEdgeIteratorState sk1 = getEdge(skippedEdge1, graph.getOtherNode(skippedEdge2, adj));
        if (base == adj && (sk1.getAdjNode() == sk1.getBaseNode() || sk2AdjNode == sk2BaseNode)) {
            throw new IllegalStateException(String.format(Locale.ROOT,
                    "error: detected edge where a skipped edges is a loop. this should never happen. base: %d, adj: %d, " +
                            "skip-edge1: %d, skip-edge2: %d, reverse: %b", base, adj, skippedEdge1, skippedEdge2, reverse));
        }
        int adjEdge = getOppositeEdge(sk1, base);
        if (reverseOrder) {
            stack.push(new StackItem(skippedEdge1, graph.getOtherNode(skippedEdge2, adj), reverse, prevOrNextEdgeId));
            stack.push(new StackItem(skippedEdge2, adj, reverse, adjEdge));
        } else {
            stack.push(new StackItem(skippedEdge2, adj, reverse, adjEdge));
            stack.push(new StackItem(skippedEdge1, graph.getOtherNode(skippedEdge2, adj), reverse, prevOrNextEdgeId));
        }
    }

    private void expandSkippedEdgesNodeBased(int skippedEdge1, int skippedEdge2, int base, int adj, boolean reverse) {
        RoutingCHEdgeIteratorState sk2 = getEdge(skippedEdge2, adj);
        if (sk2 == null) {
            sk2 = getEdge(skippedEdge1, adj);
            int tmp = skippedEdge2;
            skippedEdge2 = skippedEdge1;
            skippedEdge1 = tmp;
        }
        int sk2BaseNode = sk2.getBaseNode();
        if (reverseOrder) {
            stack.push(new StackItem(skippedEdge1, sk2BaseNode, reverse, NO_EDGE));
            stack.push(new StackItem(skippedEdge2, adj, reverse, NO_EDGE));
        } else {
            stack.push(new StackItem(skippedEdge2, adj, reverse, NO_EDGE));
            stack.push(new StackItem(skippedEdge1, sk2BaseNode, reverse, NO_EDGE));
        }
    }

    private int getOppositeEdge(RoutingCHEdgeIteratorState edgeState, int adjNode) {
        assert edgeState.getBaseNode() == adjNode || edgeState.getAdjNode() == adjNode : "adjNode " + adjNode + " must be one of adj/base of edgeState: " + edgeState;
        // since the first/last orig edge is not stateful (just like skipped1/2) we have to find out which one
        // is attached to adjNode, similar as we do for skipped1/2.
        return graph.getBaseGraph().isAdjacentToNode(edgeState.getOrigEdgeLast(), adjNode)
                ? edgeState.getOrigEdgeFirst()
                : edgeState.getOrigEdgeLast();
    }

    private RoutingCHEdgeIteratorState getEdge(int edgeId, int adjNode) {
        return singleEdgeCursor.setEdge(edgeId, adjNode);
    }

    public interface Visitor {
        void visit(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId);
    }

    private static class StackItem {
        int edgeId;
        int adjNode;
        boolean reverse;
        int prevOrNextEdgeId;

        public StackItem(int edgeId, int adjNode, boolean reverse, int prevOrNextEdgeId) {
            this.edgeId = edgeId;
            this.adjNode = adjNode;
            this.reverse = reverse;
            this.prevOrNextEdgeId = prevOrNextEdgeId;
        }
    }
}
