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

public class ArrayBasedWitnessPathFinder extends WitnessPathFinder {
    private double[] weights;
    private int[] edges;
    private int[] incEdges;
    private int[] parents;
    private int[] adjNodes;
    private boolean[] onOripPaths;

    private List<WitnessSearchEntry> rootParents;
    private IntDoubleBinaryHeap heap;
    private IntArrayList changedEdges;

    public ArrayBasedWitnessPathFinder(CHGraph graph, Weighting weighting, TraversalMode traversalMode, int maxLevel) {
        super(graph, weighting, traversalMode, maxLevel);
        final int numOriginalEdges = graph.getBaseGraph().getAllEdges().getMaxId();
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
            setInitEntry(e.key, e.value, --parentId);
            changedEdges.add(e.key);
            heap.insert_(e.value.weight, e.key);
        }
    }

    @Override
    public CHEntry getFoundEntry(int origEdge, int adjNode) {
        int edgeKey = getEdgeKey(origEdge, adjNode);
        if (parents[edgeKey] == -1) {
            return new CHEntry(origEdge, origEdge, adjNode, Double.POSITIVE_INFINITY);
        }
        CHEntry result = new CHEntry(edges[edgeKey], incEdges[edgeKey], adjNodes[edgeKey], weights[edgeKey]);
        CHEntry entry = result;
        while (parents[edgeKey] >= 0) {
            edgeKey = parents[edgeKey];
            CHEntry parent = new CHEntry(edges[edgeKey], incEdges[edgeKey], adjNodes[edgeKey], weights[edgeKey]);
            entry.parent = parent;
            entry = parent;
        }
        entry.parent = rootParents.get(-parents[edgeKey] - 2);
        return result;
    }

    @Override
    public CHEntry getFoundEntryNoParents(int origEdge, int adjNode) {
        int edgeKey = getEdgeKey(origEdge, adjNode);
        if (parents[edgeKey] == -1) {
            return null;
        } else {
            return new CHEntry(edges[edgeKey], incEdges[edgeKey], adjNodes[edgeKey], weights[edgeKey]);
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

            if (onOripPaths[currKey]) {
                numOnOrigPath--;
            }

            if (numOrigEdgesSettled > maxOrigEdgesSettled && !onOripPaths[currKey]) {
                continue;
            }

            EdgeIterator iter = outEdgeExplorer.setBaseNode(adjNodes[currKey]);
            while (iter.next()) {
                if ((!traversalMode.hasUTurnSupport() && iter.getFirstOrigEdge() == incEdges[currKey]) ||
                        graph.getLevel(iter.getAdjNode()) < graph.getLevel(iter.getBaseNode()))
                    continue;
                double weight = weighting.calcWeight(iter, false, incEdges[currKey]) + weights[currKey];
                if (Double.isInfinite(weight)) {
                    continue;
                }

                int adjKey = getEdgeKey(iter.getLastOrigEdge(), iter.getAdjNode());
                if (edges[adjKey] == -1) {
                    setEntry(adjKey, iter, weight, currKey);
                    if (onOripPaths[currKey] && iter.getBaseNode() == iter.getAdjNode()) {
                        onOripPaths[adjKey] = true;
                        numOnOrigPath++;
                    }
                    if (onOripPaths[currKey] && iter.getLastOrigEdge() == targetEdge && iter.getAdjNode() == targetNode) {
                        targetDiscoveredByOrigPath = true;
                    }
                    changedEdges.add(adjKey);
                    heap.insert_(weight, adjKey);
                } else if (weight < weights[adjKey]) {
                    updateEntry(currKey, iter, weight, adjKey);
                    if (onOripPaths[currKey] && iter.getBaseNode() == iter.getAdjNode()) {
                        if (!onOripPaths[adjKey]) {
                            numOnOrigPath++;
                        }
                        onOripPaths[adjKey] = true;
                    }
                    heap.update_(weight, adjKey);
                }
            }
            numOrigEdgesSettled++;
            if (numOnOrigPath < 1 && !targetDiscoveredByOrigPath) {
                break;
            }
        }
    }

    private void setInitEntry(int key, WitnessSearchEntry entry, int parentId) {
        edges[key] = entry.edge;
        incEdges[key] = entry.incEdge;
        adjNodes[key] = entry.adjNode;
        weights[key] = entry.weight;
        parents[key] = parentId;
        rootParents.add(entry.getParent());
        onOripPaths[key] = entry.onOrigPath;
    }

    private void setEntry(int index, EdgeIteratorState iter, double weight, int parent) {
        edges[index] = iter.getEdge();
        incEdges[index] = iter.getLastOrigEdge();
        adjNodes[index] = iter.getAdjNode();
        weights[index] = weight;
        parents[index] = parent;
    }

    private void updateEntry(int index, EdgeIteratorState iter, double weight, int adjKey) {
        edges[adjKey] = iter.getEdge();
        weights[adjKey] = weight;
        parents[adjKey] = index;
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
        Arrays.fill(edges, -1);

        incEdges = new int[numEntries];
        Arrays.fill(incEdges, -1);

        parents = new int[numEntries];
        Arrays.fill(parents, -1);

        adjNodes = new int[numEntries];
        Arrays.fill(adjNodes, -1);

        onOripPaths = new boolean[numEntries];
        Arrays.fill(onOripPaths, false);
    }

    private void resetEntry(int key) {
        weights[key] = Double.POSITIVE_INFINITY;
        edges[key] = -1;
        incEdges[key] = -1;
        parents[key] = -1;
        adjNodes[key] = -1;
        onOripPaths[key] = false;
    }

    private void initCollections() {
        rootParents = new ArrayList<>(10);
        changedEdges = new IntArrayList(1000);
        heap = new IntDoubleBinaryHeap(1000);
    }
}
