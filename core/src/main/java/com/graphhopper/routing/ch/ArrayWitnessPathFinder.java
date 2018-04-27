package com.graphhopper.routing.ch;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.apache.commons.collections.IntDoubleBinaryHeap;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Arrays;

import static java.lang.Double.isInfinite;

public class ArrayWitnessPathFinder extends WitnessPathFinder {
    private double[] weights;
    private int[] edges;
    private int[] incEdges;
    private int[] parents;
    private int[] adjNodes;
    private boolean[] onOrigPaths;

    private IntObjectMap<WitnessSearchEntry> rootParents;
    private IntDoubleBinaryHeap heap;
    private IntArrayList changedEdges;

    public ArrayWitnessPathFinder(GraphHopperStorage graph, CHGraph chGraph, TurnWeighting turnWeighting) {
        super(graph, chGraph, turnWeighting);
        final int numOriginalEdges = graph.getBaseGraph().getAllEdges().length();
        final int numEntries = 2 * numOriginalEdges;
        initStorage(numEntries);
        initCollections();
    }

    public WitnessSearchEntry runSearch(int toNode, int targetEdge) {
        // todo: write a test for this case where it becomes clear
        bestWeight = fromNode == toNode
                ? calcTurnWeight(sourceEdge, fromNode, targetEdge)
                : Double.POSITIVE_INFINITY;
        resIncEdge = EdgeIterator.NO_EDGE;
        resViaCenter = false;

        // check if we can already reach the target from the shortest path tree we discovered so far
        EdgeIterator inIter = origInEdgeExplorer.setBaseNode(toNode);
        while (inIter.next()) {
            final int incEdge = inIter.getLastOrigEdge();
            final int edgeKey = getEdgeKey(incEdge, toNode);
            if (edges[edgeKey] == -1) {
                continue;
            }
            updateBestPath(toNode, targetEdge, edgeKey);
        }

        // run dijkstra to find the optimal path
        while (!heap.isEmpty()) {
            if (numOnOrigPath < 1 && (!resViaCenter || isInfinite(bestWeight))) {
                // we have not found a connection to the target edge yet and there are no entries
                // in the priority queue anymore that are part of the direct path via the center node
                // -> we will not need a shortcut
                break;
            }
            final int currKey = heap.peek_element();
            if (weights[currKey] > bestWeight) {
                // just reaching this edge is more expensive than the best path found so far including the turn costs
                // to reach the targetOutEdge -> we can stop
                // important: we only peeked so far, so we keep the entry for future searches
                break;
            }
            heap.poll_element();
            numPolledEdges++;
            pollCount++;

            if (onOrigPaths[currKey]) {
                numOnOrigPath--;
            }

            // after a certain amount of edges has been settled we no longer expand entries
            // that are not on a path via the center node
            if (numSettledEdges > maxSettledEdges && !onOrigPaths[currKey]) {
                continue;
            }

            EdgeIterator iter = outEdgeExplorer.setBaseNode(adjNodes[currKey]);
            while (iter.next()) {
                if (isContracted(iter.getAdjNode())) {
                    continue;
                }
                // do not allow u-turns
                if (iter.getFirstOrigEdge() == incEdges[currKey]) {
                    continue;
                }
                double weight = turnWeighting.calcWeight(iter, false, incEdges[currKey]) + weights[currKey];
                if (isInfinite(weight)) {
                    continue;
                }
                boolean onOrigPath = onOrigPaths[currKey] && iter.getAdjNode() == centerNode;

                // dijkstra expansion: add or update current entries
                int key = getEdgeKey(iter.getLastOrigEdge(), iter.getAdjNode());
                if (edges[key] == -1) {
                    setEntry(key, iter, weight, currKey, onOrigPath);
                    changedEdges.add(key);
                    heap.insert_(weight, key);
                    updateBestPath(toNode, targetEdge, key);
                } else if (weight < weights[key]) {
                    updateEntry(key, iter, weight, currKey, onOrigPath);
                    heap.update_(weight, key);
                    updateBestPath(toNode, targetEdge, key);
                }
            }
            numSettledEdges++;
        }

        if (resViaCenter) {
            // the best path we could find is an original path so we return it
            // (note that this path may contain loops at the center node)
            int edgeKey = getEdgeKey(resIncEdge, toNode);
            WitnessSearchEntry result = getEntryForKey(edgeKey);
            WitnessSearchEntry entry = result;
            while (parents[edgeKey] >= 0) {
                edgeKey = parents[edgeKey];
                WitnessSearchEntry parent = getEntryForKey(edgeKey);
                entry.parent = parent;
                entry = parent;
            }
            entry.parent = rootParents.get(parents[edgeKey]);
            return result;
        } else {
            return null;
        }
    }

    private WitnessSearchEntry getEntryForKey(int edgeKey) {
        return new WitnessSearchEntry(edges[edgeKey], incEdges[edgeKey], adjNodes[edgeKey], weights[edgeKey], onOrigPaths[edgeKey]);
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

    private void updateEntry(int key, EdgeIteratorState iter, double weight, int currKey, boolean onOrigPath) {
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


    private void resetEntry(int key) {
        weights[key] = Double.POSITIVE_INFINITY;
        edges[key] = EdgeIterator.NO_EDGE;
        incEdges[key] = EdgeIterator.NO_EDGE;
        parents[key] = -1;
        adjNodes[key] = -1;
        onOrigPaths[key] = false;
    }

    private void updateBestPath(int toNode, int targetEdge, int edgeKey) {
        // when we hit the target node we update the best path
        if (adjNodes[edgeKey] == toNode) {
            double totalWeight = weights[edgeKey] + calcTurnWeight(incEdges[edgeKey], toNode, targetEdge);
            // we know that there must be some parent so a negative parent key is a real
            // key in the root parents collection --> in this case we did not go via the center
            boolean viaCenter = parents[edgeKey] >= 0 && onOrigPaths[parents[edgeKey]];
            // when in doubt prefer a witness path over an original path
            double tolerance = viaCenter ? 0 : 1.e-6;
            if (totalWeight - tolerance < bestWeight) {
                bestWeight = totalWeight;
                resIncEdge = incEdges[edgeKey];
                resViaCenter = viaCenter;
            }
        }
    }

    @Override
    protected void setInitialEntries(int centerNode, int fromNode, int sourceEdge) {
        EdgeIterator outIter = outEdgeExplorer.setBaseNode(fromNode);
        while (outIter.next()) {
            if (isContracted(outIter.getAdjNode())) {
                continue;
            }
            double turnWeight = calcTurnWeight(sourceEdge, fromNode, outIter.getFirstOrigEdge());
            if (isInfinite(turnWeight)) {
                continue;
            }
            double edgeWeight = turnWeighting.calcWeight(outIter, false, EdgeIterator.NO_EDGE);
            double weight = turnWeight + edgeWeight;
            boolean onOrigPath = outIter.getAdjNode() == centerNode;
            int incEdge = outIter.getLastOrigEdge();
            int adjNode = outIter.getAdjNode();
            int key = getEdgeKey(incEdge, adjNode);
            int parentKey = -key - 1;
            WitnessSearchEntry parent = new WitnessSearchEntry(
                    EdgeIterator.NO_EDGE,
                    outIter.getFirstOrigEdge(),
                    fromNode, turnWeight, false);
            if (edges[key] == -1) {
                edges[key] = outIter.getEdge();
                incEdges[key] = incEdge;
                adjNodes[key] = adjNode;
                weights[key] = weight;
                parents[key] = parentKey;
                onOrigPaths[key] = onOrigPath;
                rootParents.put(parentKey, parent);
                changedEdges.add(key);
            } else if (weight < weights[key]) {
                // there may be entries with the same adjNode and last original edge, but we only need the one with
                // the lowest weight
                edges[key] = outIter.getEdge();
                weights[key] = weight;
                parents[key] = parentKey;
                onOrigPaths[key] = onOrigPath;
                rootParents.put(parentKey, parent);
            }
        }

        // now that we know which entries are actually needed we add them to the heap
        for (int i = 0; i < changedEdges.size(); ++i) {
            int key = changedEdges.get(i);
            if (onOrigPaths[key]) {
                numOnOrigPath++;
            }
            heap.insert_(weights[key], key);
        }
    }

    @Override
    void doReset() {
        for (int i = 0; i < changedEdges.size(); ++i) {
            resetEntry(changedEdges.get(i));
        }
        rootParents.clear();
        changedEdges.elementsCount = 0;
        heap.clear();
    }

    @Override
    int getNumEntries() {
        return heap.getSize();
    }

    private void initCollections() {
        // todo: so far these initial capacities are purely guessed
        rootParents = new IntObjectHashMap<>(10);
        changedEdges = new IntArrayList(1000);
        heap = new IntDoubleBinaryHeap(1000);
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

}
