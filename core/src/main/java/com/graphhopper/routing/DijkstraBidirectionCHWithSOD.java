package com.graphhopper.routing;

import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

/**
 * Uses a very simple version of stall-on-demand (SOD) for CH queries to prevent exploring nodes that can not be part
 * of a shortest path. When a node that is about to be settled is stallable it is not expanded, but no further search
 * for neighboring stallable nodes is performed.
 */
public class DijkstraBidirectionCHWithSOD extends PrepareContractionHierarchies.DijkstraBidirectionCH {
    public DijkstraBidirectionCHWithSOD(Graph graph, Weighting weighting, TraversalMode traversalMode) {
        super(graph, weighting, traversalMode);
    }

    @Override
    public boolean fillEdgesFrom() {
        if (pqOpenSetFrom.isEmpty()) {
            return false;
        }
        currFrom = pqOpenSetFrom.poll();
        visitedCountFrom++;
        if (entryIsStallable(currFrom, bestWeightMapFrom, inEdgeExplorer, false)) {
            return true;
        }
        bestWeightMapOther = bestWeightMapTo;
        fillEdges(currFrom, pqOpenSetFrom, bestWeightMapFrom, outEdgeExplorer, false);
        return true;
    }

    @Override
    public boolean fillEdgesTo() {
        if (pqOpenSetTo.isEmpty()) {
            return false;
        }
        currTo = pqOpenSetTo.poll();
        visitedCountTo++;
        if (entryIsStallable(currTo, bestWeightMapTo, outEdgeExplorer, true)) {
            return true;
        }
        bestWeightMapOther = bestWeightMapFrom;
        fillEdges(currTo, pqOpenSetTo, bestWeightMapTo, inEdgeExplorer, true);
        return true;
    }

    @Override
    public String getName() {
        return "dijkstrabisod|ch";
    }

    @Override
    public String toString() {
        return getName() + "|" + weighting;
    }

    private boolean entryIsStallable(SPTEntry entry, IntObjectMap<SPTEntry> bestWeightMap, EdgeExplorer edgeExplorer,
                                     boolean reverse) {
        // We check for all 'incoming' edges if we can prove that the current node (that is about to be settled) is 
        // reached via a suboptimal path. We do this regardless of the CH level of the adjacent nodes.
        EdgeIterator iter = edgeExplorer.setBaseNode(entry.adjNode);
        while (iter.next()) {
            int traversalId = traversalMode.createTraversalId(iter, reverse);
            SPTEntry adjNode = bestWeightMap.get(traversalId);
            if (adjNode != null &&
                    adjNode.weight + weighting.calcWeight(iter, reverse, iter.getEdge()) < entry.weight) {
                return true;
            }
        }
        return false;
    }
}
