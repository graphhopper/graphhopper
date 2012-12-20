/*
 *  Copyright 2012 Peter Karich 
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing;

import com.graphhopper.coll.MyBitSet;
import com.graphhopper.coll.MyTBitSet;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.PriorityQueue;

/**
 * Implements a single source shortest path algorithm
 * http://en.wikipedia.org/wiki/Dijkstra's_algorithm
 *
 * @author Peter Karich,
 */
public class DijkstraSimple extends AbstractRoutingAlgorithm {

    protected MyBitSet visited = new MyTBitSet();
    private TIntObjectMap<EdgeEntry> map = new TIntObjectHashMap<EdgeEntry>();
    private PriorityQueue<EdgeEntry> heap = new PriorityQueue<EdgeEntry>();
//    private MyDijkstraHeap heapNew = new MyDijkstraHeap();
    private int from;

    public DijkstraSimple(Graph graph) {
        super(graph);
    }

    @Override
    public DijkstraSimple clear() {
        visited.clear();
        map.clear();
//        heapNew.clear();
        heap.clear();
        from = -1;
        return this;
    }

    @Override
    public Path calcPath(int from, int to) {
        EdgeEntry fromEntry = new EdgeEntry(-1, from, 0d);
        this.from = from;
        visited.add(from);
        EdgeEntry currEdge = fromEntry;
        while (true) {
            int neighborNode = currEdge.endNode;
            EdgeIterator iter = getNeighbors(neighborNode);
            while (iter.next()) {
                int tmpNode = iter.node();
                if (visited.contains(tmpNode))
                    continue;

                double tmpWeight = weightCalc.getWeight(iter.distance(), iter.flags()) + currEdge.weight;
                EdgeEntry nEdge = map.get(tmpNode);
                if (nEdge == null) {
                    nEdge = new EdgeEntry(iter.edge(), tmpNode, tmpWeight);
                    nEdge.parent = currEdge;
                    map.put(tmpNode, nEdge);
                    heap.add(nEdge);
                } else if (nEdge.weight > tmpWeight) {
                    heap.remove(nEdge);
                    nEdge.edge = iter.edge();
                    nEdge.weight = tmpWeight;
                    nEdge.parent = currEdge;
                    heap.add(nEdge);
                }

                updateShortest(nEdge, neighborNode);
            }

            visited.add(neighborNode);
            if (finished(currEdge, to))
                break;

            if (heap.isEmpty())
                return new Path();
            currEdge = heap.poll();
            if (currEdge == null)
                throw new IllegalStateException("cannot happen?");
        }

        if (currEdge.endNode != to)
            return new Path();

        return extractPath(currEdge);
    }

// try an update-less dijkstra with new heap
//    public Path calcPathNew(int from, int to) {
//        EdgeEntry fromEntry = new EdgeEntry(-1, from, 0d);
//        this.from = from;
//        visited.add(from);
//        EdgeEntry currEdge = fromEntry;
//        while (true) {
//            int neighborNode = currEdge.endNode;
//            EdgeIterator iter = getNeighbors(neighborNode);
//            while (iter.next()) {
//                int tmpNode = iter.node();
//                if (visited.contains(tmpNode))
//                    continue;
//
//                double tmpWeight = weightCalc.getWeight(iter.distance(), iter.flags()) + currEdge.weight;
//                EdgeEntry nEdge = map.get(tmpNode);
//                if (nEdge == null) {
//                    nEdge = new EdgeEntry(iter.edge(), tmpNode, tmpWeight);
//                    nEdge.parent = currEdge;
//                    map.put(tmpNode, nEdge);
//                } else if (nEdge.weight > tmpWeight) {
//                    nEdge.edge = iter.edge();
//                    nEdge.weight = tmpWeight;
//                    nEdge.parent = currEdge;
//                }
//
//                heapNew.insert_(tmpWeight, tmpNode);
//                updateShortest(nEdge, neighborNode);
//            }
//
//            visited.add(neighborNode);
//            if (finished(currEdge, to))
//                break;
//
//            while (true) {
//                if (heapNew.isEmpty())
//                    return new Path();
//                currEdge = map.get(heapNew.poll_element());
//                if (currEdge == null)
//                    throw new IllegalStateException("cannot happen?");
//                if (!visited.contains(currEdge.endNode))
//                    break;
//            }
//
////            System.out.println(currEdge + " " + heapNew.stats());
//        }
//
//        System.out.println(heapNew.stats());
//        if (currEdge.endNode != to)
//            return new Path();
//        return extractPath(currEdge);
//    }
    public boolean finished(EdgeEntry currEdge, int to) {
        return currEdge.endNode == to;
    }

    public Path extractPath(EdgeEntry goalEdge) {
        // extract path from shortest-path-tree
        Path path = new Path(graph, weightCalc);
        while (goalEdge.edge != -1) {
            path.calcWeight(graph.getEdgeProps(goalEdge.edge, goalEdge.endNode));
            int tmpEnd = goalEdge.endNode;
            path.add(tmpEnd);
            goalEdge = goalEdge.parent;
        }
        path.addFrom(from);
        path.reverseOrder();
        return path.found(true);
    }

    protected EdgeIterator getNeighbors(int neighborNode) {
        return graph.getOutgoing(neighborNode);
    }

    @Override public String name() {
        return "dijkstra";
    }
}
