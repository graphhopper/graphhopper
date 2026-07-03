package com.graphhopper.routing.ch

import com.graphhopper.storage.RoutingCHEdgeIteratorState
import com.graphhopper.storage.RoutingCHGraph
import com.graphhopper.util.EdgeIterator.Companion.NO_EDGE
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.GHUtility
import java.util.Locale

/**
 * Recursively unpack shortcuts.
 * <p>
 *
 * @author Peter Karich
 * @author easbar
 * @see PrepareContractionHierarchies
 */
class ShortcutUnpacker(private val graph: RoutingCHGraph, private val visitor: Visitor, private val edgeBased: Boolean) {
    private var reverseOrder = false

    /**
     * Finds an edge/shortcut with the given id and adjNode and calls the visitor for each original edge that is
     * packed inside this shortcut (or if an original edge is given simply calls the visitor on it).
     *
     * @param reverseOrder if true the original edges will be traversed in reverse order
     */
    fun visitOriginalEdgesFwd(edgeId: Int, adjNode: Int, reverseOrder: Boolean, prevOrNextEdgeId: Int) {
        doVisitOriginalEdges(edgeId, adjNode, reverseOrder, false, prevOrNextEdgeId)
    }

    fun visitOriginalEdgesBwd(edgeId: Int, adjNode: Int, reverseOrder: Boolean, prevOrNextEdgeId: Int) {
        doVisitOriginalEdges(edgeId, adjNode, reverseOrder, true, prevOrNextEdgeId)
    }

    private fun doVisitOriginalEdges(edgeId: Int, adjNode: Int, reverseOrder: Boolean, reverse: Boolean, prevOrNextEdgeId: Int) {
        this.reverseOrder = reverseOrder
        val edge = getEdge(edgeId, adjNode)
            ?: throw IllegalArgumentException("Edge with id: $edgeId does not exist or does not touch node $adjNode")
        expandEdge(edge, reverse, prevOrNextEdgeId)
    }

    private fun expandEdge(edge: RoutingCHEdgeIteratorState, reverse: Boolean, prevOrNextEdgeId: Int) {
        if (!edge.isShortcut) {
            visitor.visit(graph.baseGraph.getEdgeIteratorState(edge.origEdge, edge.adjNode), reverse, prevOrNextEdgeId)
            return
        }
        if (edgeBased) {
            expandSkippedEdgesEdgeBased(edge.skippedEdge1, edge.skippedEdge2, edge.baseNode, edge.adjNode, reverse, prevOrNextEdgeId)
        } else {
            expandSkippedEdgesNodeBased(edge.skippedEdge1, edge.skippedEdge2, edge.baseNode, edge.adjNode, reverse)
        }
    }

    private fun expandSkippedEdgesEdgeBased(skippedEdge1: Int, skippedEdge2: Int, base: Int, adj: Int, reverse: Boolean, prevOrNextEdgeId: Int) {
        var skippedEdge1 = skippedEdge1
        var skippedEdge2 = skippedEdge2
        if (reverse) {
            val tmp = skippedEdge1
            skippedEdge1 = skippedEdge2
            skippedEdge2 = tmp
        }
        val sk2 = getEdge(skippedEdge2, adj)
        assert(sk2 != null) {
            "skipped edge $skippedEdge2 is not attached to adjNode $adj. this should " +
                    "never happen because edge-based CH does not use bidirectional shortcuts at the moment"
        }
        val sk1 = getEdge(skippedEdge1, sk2!!.baseNode)!!
        if (base == adj && (sk1.adjNode == sk1.baseNode || sk2.adjNode == sk2.baseNode)) {
            throw IllegalStateException(String.format(Locale.ROOT,
                    "error: detected edge where a skipped edges is a loop. this should never happen. base: %d, adj: %d, " +
                            "skip-edge1: %d, skip-edge2: %d, reverse: %b", base, adj, skippedEdge1, skippedEdge2, reverse))
        }
        val adjEdge = getOppositeEdge(sk1, base)
        if (reverseOrder) {
            expandEdge(sk2, reverse, adjEdge)
            expandEdge(sk1, reverse, prevOrNextEdgeId)
        } else {
            expandEdge(sk1, reverse, prevOrNextEdgeId)
            expandEdge(sk2, reverse, adjEdge)
        }
    }

    private fun expandSkippedEdgesNodeBased(skippedEdge1: Int, skippedEdge2: Int, base: Int, adj: Int, reverse: Boolean) {
        var sk2 = getEdge(skippedEdge2, adj)
        val sk1: RoutingCHEdgeIteratorState?
        if (sk2 == null) {
            sk2 = getEdge(skippedEdge1, adj)
            sk1 = getEdge(skippedEdge2, sk2!!.baseNode)
        } else {
            sk1 = getEdge(skippedEdge1, sk2.baseNode)
        }
        if (reverseOrder) {
            expandEdge(sk2!!, reverse, NO_EDGE)
            expandEdge(sk1!!, reverse, NO_EDGE)
        } else {
            expandEdge(sk1!!, reverse, NO_EDGE)
            expandEdge(sk2!!, reverse, NO_EDGE)
        }
    }

    private fun getOppositeEdge(edgeState: RoutingCHEdgeIteratorState, adjNode: Int): Int {
        assert(edgeState.baseNode == adjNode || edgeState.adjNode == adjNode) {
            "adjNode $adjNode must be one of adj/base of edgeState: $edgeState"
        }
        // since the first/last orig edge key is not stateful (just like skipped1/2) we have to find out which one
        // is attached to adjNode, similar as we do for skipped1/2.
        val adjacentToNode = graph.baseGraph.isAdjacentToNode(GHUtility.getEdgeFromEdgeKey(edgeState.origEdgeKeyLast), adjNode)
        return GHUtility.getEdgeFromEdgeKey(if (adjacentToNode) edgeState.origEdgeKeyFirst else edgeState.origEdgeKeyLast)
    }

    private fun getEdge(edgeId: Int, adjNode: Int): RoutingCHEdgeIteratorState? =
        graph.getEdgeIteratorState(edgeId, adjNode)

    fun interface Visitor {
        fun visit(edge: EdgeIteratorState?, reverse: Boolean, prevOrNextEdgeId: Int)
    }
}
