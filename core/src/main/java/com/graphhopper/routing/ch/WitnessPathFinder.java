package com.graphhopper.routing.ch;

import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.util.CHEdgeExplorer;
import com.graphhopper.util.CHEdgeIterator;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

import java.util.List;
import java.util.PriorityQueue;

public class WitnessPathFinder {
    private IntObjectMap<CHEntry> chEntries;
    private PriorityQueue<CHEntry> priorityQueue;
    private final CHGraph graph;
    private final Weighting weighting;
    private final TraversalMode traversalMode;
    private CHEdgeExplorer outEdgeExplorer;
    private boolean alreadyRun;

    public WitnessPathFinder(CHGraph graph, Weighting weighting, TraversalMode traversalMode, List<CHEntry> initialEntries, int fromNode) {
        if (traversalMode != TraversalMode.EDGE_BASED_2DIR) {
            throw new IllegalArgumentException("Traversal mode " + traversalMode + "not supported");
        }
        this.graph = graph;
        this.weighting = weighting;
        this.traversalMode = traversalMode;
        outEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(weighting.getFlagEncoder(), false, true));
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 2000);
        initCollections(size);
        initEntries(initialEntries);
        priorityQueue.addAll(initialEntries);
    }

    private void initEntries(List<CHEntry> initialEntries) {
        for (CHEntry chEntry : initialEntries) {
            CHEdgeIteratorState edgeIteratorState = graph.getEdgeIteratorState(chEntry.incEdge, chEntry.adjNode);
            //more traversal key mess ...
            int traversalId = GHUtility.createEdgeKey(edgeIteratorState.getBaseNode(), edgeIteratorState.getAdjNode(),
                    edgeIteratorState.getEdge(), false);
            chEntries.put(traversalId, chEntry);
        }
    }

    public CHEntry getFoundEntry(int edge, int adjNode) {
        // todo: this is similar to some code in EdgeBasedNodeContractor and should be cleaned up, see comments there
        CHEdgeIteratorState eis = graph.getEdgeIteratorState(edge, adjNode);
        int edgeKey = GHUtility.createEdgeKey(eis.getBaseNode(), eis.getAdjNode(), eis.getEdge(), false);
        CHEntry entry = chEntries.get(edgeKey);
        return entry != null ? entry : new CHEntry(edge, edge, adjNode, Double.POSITIVE_INFINITY);
    }

    public void findTarget(double maxWeight, int targetEdge, int targetNode) {
        // todo: we should allow rerunning the search for different target edges and max weights and thereby reuse
        // results from previous searches
        if (alreadyRun) {
            throw new IllegalStateException("already run, you need to create a new instance");
        }
        alreadyRun = true;
        while (!priorityQueue.isEmpty()) {
            CHEntry currEdge = priorityQueue.poll();
            if ((currEdge.incEdge == targetEdge && currEdge.adjNode == targetNode) || currEdge.weight > maxWeight)
                break;

            CHEdgeIterator iter = outEdgeExplorer.setBaseNode(currEdge.adjNode);
            while (iter.next()) {
                if ((!traversalMode.hasUTurnSupport() && iter.getLastOrigEdge() == currEdge.incEdge) ||
                        graph.getLevel(iter.getAdjNode()) < graph.getLevel(iter.getBaseNode()))
                    continue;

                int edgeId = iter.getLastOrigEdge();
                EdgeIteratorState iterState = graph.getEdgeIteratorState(edgeId, iter.getAdjNode());
                int traversalId = traversalMode.createTraversalId(iterState, false);
                double weight = weighting.calcWeight(iter, false, currEdge.incEdge) + currEdge.weight;
                if (Double.isInfinite(weight))
                    continue;

                CHEntry entry = chEntries.get(traversalId);
                if (entry == null) {
                    entry = createEntry(iter, currEdge, weight);
                    chEntries.put(traversalId, entry);
                    priorityQueue.add(entry);
                } else if (entry.weight > weight) {
                    priorityQueue.remove(entry);
                    updateEntry(entry, iter, weight, currEdge);
                    priorityQueue.add(entry);
                }
            }
        }
    }

    private CHEntry createEntry(CHEdgeIterator iter, CHEntry parent, double weight) {
        CHEntry entry = new CHEntry(iter.getEdge(), iter.getLastOrigEdge(), iter.getAdjNode(), weight);
        entry.parent = parent;
        return entry;
    }

    private void updateEntry(CHEntry entry, CHEdgeIterator iter, double weight, CHEntry parent) {
        entry.edge = iter.getEdge();
        entry.incEdge = iter.getLastOrigEdge();
        entry.weight = weight;
        entry.parent = parent;
    }

    private void initCollections(int size) {
        priorityQueue = new PriorityQueue<>(size);
        chEntries = new GHIntObjectHashMap<>(size);
    }

}
