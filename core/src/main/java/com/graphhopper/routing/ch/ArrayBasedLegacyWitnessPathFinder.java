package com.graphhopper.routing.ch;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.graphhopper.apache.commons.collections.IntDoubleBinaryHeap;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArrayBasedLegacyWitnessPathFinder extends LegacyWitnessPathFinder {
    private double[] weights;
    private int[] edges;
    private int[] incEdges;
    private int[] parents;
    private int[] adjNodes;
    private boolean[] onOrigPaths;

    private List<WitnessSearchEntry> rootParents;
    private IntDoubleBinaryHeap heap;
    private IntArrayList changedEdges;

    public ArrayBasedLegacyWitnessPathFinder(CHGraph graph, Weighting weighting, TraversalMode traversalMode, int maxLevel) {
        super(graph, weighting, traversalMode, maxLevel);
        final int numOriginalEdges = graph.getBaseGraph().getAllEdges().length();
        final int numEntries = 2 * numOriginalEdges;
        initStorage(numEntries);
        initCollections();
    }

    @Override
    protected void initEntries(IntObjectMap<WitnessSearchEntry> initialEntries) {
        int parentId = -1;
        for (IntObjectCursor<WitnessSearchEntry> e : initialEntries) {
            if (e.value.onOrigPath) {
                numOnOrigPath++;
                avoidNode = e.value.adjNode;
            }
            // we use descending negative keys for the parents, so we can retrieve them later
            setInitEntry(e.key, e.value, --parentId);
            changedEdges.add(e.key);
            heap.insert_(e.value.weight, e.key);
        }
    }

    @Override
    public WitnessSearchEntry getFoundEntry(int origEdge, int adjNode) {
        int edgeKey = getEdgeKey(origEdge, adjNode);
        if (parents[edgeKey] == -1) {
            return new WitnessSearchEntry(origEdge, origEdge, adjNode, Double.POSITIVE_INFINITY, false);
        }
        WitnessSearchEntry result = getEntryForKey(edgeKey);
        WitnessSearchEntry entry = result;
        while (parents[edgeKey] >= 0) {
            edgeKey = parents[edgeKey];
            WitnessSearchEntry parent = getEntryForKey(edgeKey);
            entry.parent = parent;
            entry = parent;
        }
        entry.parent = rootParents.get(-parents[edgeKey] - 2);
        return result;
    }

    @Override
    public WitnessSearchEntry getFoundEntryNoParents(int origEdge, int adjNode) {
        int edgeKey = getEdgeKey(origEdge, adjNode);
        if (parents[edgeKey] == -1) {
            return null;
        } else {
            return getEntryForKey(edgeKey);
        }
    }

    @Override
    public void findTarget(int targetEdge, int targetNode) {
        boolean targetDiscoveredByOrigPath = false;
        int targetKey = getEdgeKey(targetEdge, targetNode);
        if (heap.isEmpty() || weights[targetKey] < heap.peek_key()) {
            return;
        }

        while (!heap.isEmpty()) {
            final int currKey = heap.peek_element();
            if (incEdges[currKey] == targetEdge && adjNodes[currKey] == targetNode) {
                // important: we only peeked so far so we keep the entry for future searches
                break;
            }
            heap.poll_element();
            numEntriesPolled++;
            pollCount++;

            if (onOrigPaths[currKey]) {
                numOnOrigPath--;
            }

            if (numSettledEdges > maxSettledEdges && !onOrigPaths[currKey]) {
                continue;
            }

            EdgeIterator iter = outEdgeExplorer.setBaseNode(adjNodes[currKey]);
            while (iter.next()) {
                if ((!traversalMode.hasUTurnSupport() && iter.getFirstOrigEdge() == incEdges[currKey]) ||
                        isContracted(iter.getAdjNode())) {
                    continue;
                }
                double weight = weighting.calcWeight(iter, false, incEdges[currKey]) + weights[currKey];
                if (Double.isInfinite(weight)) {
                    continue;
                }

                boolean onOrigPath = onOrigPaths[currKey] && iter.getBaseNode() == iter.getAdjNode();
                int key = getEdgeKey(iter.getLastOrigEdge(), iter.getAdjNode());
                if (edges[key] == -1) {
                    setEntry(key, iter, weight, currKey, onOrigPath);
                    if (targetDiscoveredByOrigPath(targetEdge, targetNode, currKey, iter)) {
                        targetDiscoveredByOrigPath = true;
                    }
                    changedEdges.add(key);
                    heap.insert_(weight, key);
                } else if (weight < weights[key]) {
                    updateEntry(currKey, iter, weight, key, onOrigPath);
                    if (targetDiscoveredByOrigPath(targetEdge, targetNode, currKey, iter)) {
                        targetDiscoveredByOrigPath = true;
                    }
                    heap.update_(weight, key);
                }
            }
            numSettledEdges++;
            if (numOnOrigPath < 1 && !targetDiscoveredByOrigPath) {
                break;
            }
        }
    }

    private WitnessSearchEntry getEntryForKey(int edgeKey) {
        return new WitnessSearchEntry(edges[edgeKey], incEdges[edgeKey], adjNodes[edgeKey], weights[edgeKey], onOrigPaths[edgeKey]);
    }

    private void setInitEntry(int key, WitnessSearchEntry entry, int parentId) {
        edges[key] = entry.edge;
        incEdges[key] = entry.incEdge;
        adjNodes[key] = entry.adjNode;
        weights[key] = entry.weight;
        parents[key] = parentId;
        rootParents.add(entry.getParent());
        onOrigPaths[key] = entry.onOrigPath;
    }

    private void setEntry(int key, EdgeIteratorState iter, double weight, int parent, boolean onOrigPath) {
        edges[key] = iter.getEdge();
        incEdges[key] = iter.getLastOrigEdge();
        adjNodes[key] = iter.getAdjNode();
        weights[key] = weight;
        parents[key] = parent;
        if (onOrigPath) {
            onOrigPaths[key] = true;
            numOnOrigPath++;
        }
    }

    private void updateEntry(int currKey, EdgeIteratorState iter, double weight, int key, boolean onOrigPath) {
        edges[key] = iter.getEdge();
        weights[key] = weight;
        parents[key] = currKey;
        if (onOrigPath) {
            if (!onOrigPaths[key]) {
                numOnOrigPath++;
            }
        } else {
            if (onOrigPaths[key]) {
                numOnOrigPath--;
            }
        }
        onOrigPaths[key] = onOrigPath;
    }

    @Override
    protected void doReset() {
        for (int i = 0; i < changedEdges.size(); ++i) {
            resetEntry(changedEdges.get(i));
        }
        rootParents.clear();
        changedEdges.elementsCount = 0;
        heap.clear();
    }

    private void initStorage(int numEntries) {
        weights = new double[numEntries];
        Arrays.fill(weights, Double.POSITIVE_INFINITY);

        edges = new int[numEntries];
        Arrays.fill(edges, EdgeIterator.NO_EDGE);

        incEdges = new int[numEntries];
        Arrays.fill(incEdges, EdgeIterator.NO_EDGE);

        parents = new int[numEntries];
        Arrays.fill(parents, -1);

        adjNodes = new int[numEntries];
        Arrays.fill(adjNodes, -1);

        onOrigPaths = new boolean[numEntries];
        Arrays.fill(onOrigPaths, false);
    }

    private void resetEntry(int key) {
        weights[key] = Double.POSITIVE_INFINITY;
        edges[key] = EdgeIterator.NO_EDGE;
        incEdges[key] = EdgeIterator.NO_EDGE;
        parents[key] = -1;
        adjNodes[key] = -1;
        onOrigPaths[key] = false;
    }

    private boolean targetDiscoveredByOrigPath(int targetEdge, int targetNode, int currKey, EdgeIteratorState iter) {
        return onOrigPaths[currKey] && iter.getLastOrigEdge() == targetEdge && iter.getAdjNode() == targetNode;
    }

    private void initCollections() {
        // todo: so far these initial capacities are purely guessed
        rootParents = new ArrayList<>(10);
        changedEdges = new IntArrayList(1000);
        heap = new IntDoubleBinaryHeap(1000);
    }
}
