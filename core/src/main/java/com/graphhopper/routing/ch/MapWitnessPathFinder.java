package com.graphhopper.routing.ch;

import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIterator;

import java.util.PriorityQueue;

import static java.lang.Double.isInfinite;

public class MapWitnessPathFinder extends WitnessPathFinder {
    private IntObjectMap<WitnessSearchEntry> entries;
    private PriorityQueue<WitnessSearchEntry> priorityQueue;

    public MapWitnessPathFinder(GraphHopperStorage graph, CHGraph chGraph, TurnWeighting turnWeighting) {
        super(graph, chGraph, turnWeighting);
        doReset();
    }

    @Override
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
            WitnessSearchEntry entry = entries.get(edgeKey);
            if (entry == null) {
                continue;
            }
            updateBestPath(toNode, targetEdge, entry);
        }

        // run dijkstra to find the optimal path
        while (!priorityQueue.isEmpty()) {
            if (numOnOrigPath < 1 && (!resViaCenter || isInfinite(bestWeight))) {
                // we have not found a connection to the target edge yet and there are no entries
                // in the priority queue anymore that are part of the direct path via the center node
                // -> we will not need a shortcut
                break;
            }
            WitnessSearchEntry entry = priorityQueue.peek();
            if (entry.weight > bestWeight) {
                // just reaching this edge is more expensive than the best path found so far including the turn costs
                // to reach the targetOutEdge -> we can stop
                // important: we only peeked so far, so we keep the entry for future searches
                break;
            }
            priorityQueue.poll();
            numPolledEdges++;
            pollCount++;

            if (entry.onOrigPath) {
                numOnOrigPath--;
            }

            // after a certain amount of edges has been settled we no longer expand entries
            // that are not on a path via the center node
            if (numSettledEdges > maxSettledEdges && !entry.onOrigPath) {
                continue;
            }

            EdgeIterator iter = outEdgeExplorer.setBaseNode(entry.adjNode);
            while (iter.next()) {
                if (isContracted(iter.getAdjNode())) {
                    continue;
                }
                // do not allow u-turns
                if (iter.getFirstOrigEdge() == entry.incEdge) {
                    continue;
                }
                double weight = turnWeighting.calcWeight(iter, false, entry.incEdge) + entry.weight;
                if (isInfinite(weight)) {
                    continue;
                }
                boolean onOrigPath = entry.onOrigPath && iter.getAdjNode() == centerNode;

                // dijkstra expansion: add or update current entries
                int key = getEdgeKey(iter.getLastOrigEdge(), iter.getAdjNode());
                int index = entries.indexOf(key);
                if (index < 0) {
                    WitnessSearchEntry newEntry = new WitnessSearchEntry(
                            iter.getEdge(),
                            iter.getLastOrigEdge(),
                            iter.getAdjNode(),
                            weight,
                            onOrigPath
                    );
                    newEntry.parent = entry;
                    if (onOrigPath) {
                        numOnOrigPath++;
                    }
                    entries.indexInsert(index, key, newEntry);
                    priorityQueue.add(newEntry);
                    updateBestPath(toNode, targetEdge, newEntry);
                } else {
                    WitnessSearchEntry existingEntry = entries.indexGet(index);
                    if (weight < existingEntry.weight) {
                        priorityQueue.remove(existingEntry);
                        existingEntry.edge = iter.getEdge();
                        existingEntry.incEdge = iter.getLastOrigEdge();
                        existingEntry.weight = weight;
                        existingEntry.parent = entry;
                        if (onOrigPath) {
                            if (!existingEntry.onOrigPath) {
                                numOnOrigPath++;
                            }
                        } else {
                            if (existingEntry.onOrigPath) {
                                numOnOrigPath--;
                            }
                        }
                        existingEntry.onOrigPath = onOrigPath;
                        priorityQueue.add(existingEntry);
                        updateBestPath(toNode, targetEdge, existingEntry);
                    }
                }
            }
            numSettledEdges++;
        }

        if (resViaCenter) {
            // the best path we could find is an original path so we return it
            // (note that this path may contain loops at the center node)
            int edgeKey = getEdgeKey(resIncEdge, toNode);
            return entries.get(edgeKey);
        } else {
            return null;
        }
    }

    private void updateBestPath(int toNode, int targetEdge, WitnessSearchEntry entry) {
        // when we hit the target node we update the best path
        if (entry.adjNode == toNode) {
            double totalWeight = entry.weight + calcTurnWeight(entry.incEdge, toNode, targetEdge);
            boolean viaCenter = entry.getParent().onOrigPath;
            // when in doubt prefer a witness path over an original path
            double tolerance = viaCenter ? 0 : 1.e-6;
            if (totalWeight - tolerance < bestWeight) {
                bestWeight = totalWeight;
                resIncEdge = entry.incEdge;
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
            double weight = turnWeighting.calcWeight(outIter, false, EdgeIterator.NO_EDGE);
            boolean onOrigPath = outIter.getAdjNode() == centerNode;
            WitnessSearchEntry entry = new WitnessSearchEntry(
                    outIter.getEdge(),
                    outIter.getLastOrigEdge(),
                    outIter.getAdjNode(), turnWeight + weight, onOrigPath);
            entry.parent = new WitnessSearchEntry(
                    EdgeIterator.NO_EDGE,
                    outIter.getFirstOrigEdge(),
                    fromNode, turnWeight, false);
            addOrUpdateInitialEntry(entry);
        }

        // now that we know which entries are actually needed we add them to the priority queue
        for (IntObjectCursor<WitnessSearchEntry> e : entries) {
            if (e.value.onOrigPath) {
                numOnOrigPath++;
            }
            priorityQueue.add(e.value);
        }
    }

    @Override
    void doReset() {
        // todo: tune initial collection sizes
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 2000);
        priorityQueue = new PriorityQueue<>(size);
        entries = new GHIntObjectHashMap<>(size);
    }

    @Override
    int getNumEntries() {
        return entries.size();
    }

    private void addOrUpdateInitialEntry(WitnessSearchEntry entry) {
        int edgeKey = getEdgeKey(entry.incEdge, entry.adjNode);
        int index = entries.indexOf(edgeKey);
        if (index < 0) {
            entries.indexInsert(index, edgeKey, entry);
        } else {
            // there may be entries with the same adjNode and last original edge, but we only need the one with
            // the lowest weight
            WitnessSearchEntry currEntry = entries.indexGet(index);
            if (entry.weight < currEntry.weight) {
                entries.indexReplace(index, entry);
            }
        }
    }

}
