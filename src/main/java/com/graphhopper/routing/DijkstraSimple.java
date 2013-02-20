/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
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

    public DijkstraSimple(Graph graph) {
        super(graph);
    }

    @Override
    public DijkstraSimple clear() {
        visited.clear();
        map.clear();
        heap.clear();
        return this;
    }

    @Override
    public Path calcPath(int from, int to) {
        EdgeEntry fromEntry = new EdgeEntry(EdgeIterator.NO_EDGE, from, 0d);
        visited.add(from);
        EdgeEntry currEdge = fromEntry;
        while (true) {
            int neighborNode = currEdge.endNode;
            EdgeIterator iter = neighbors(neighborNode);
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
                return new Path(graph, weightCalc);
            currEdge = heap.poll();
            if (currEdge == null)
                throw new AssertionError("cannot happen?");
        }

        if (currEdge.endNode != to)
            return new Path(graph, weightCalc);

        return extractPath(currEdge);
    }

    protected boolean finished(EdgeEntry currEdge, int to) {
        return currEdge.endNode == to;
    }

    public Path extractPath(EdgeEntry goalEdge) {
        return new Path(graph, weightCalc).edgeEntry(goalEdge).extract();
    }

    protected EdgeIterator neighbors(int neighborNode) {
        return graph.getEdges(neighborNode, outEdgeFilter);
    }

    @Override public String name() {
        return "dijkstra";
    }

    @Override
    public int calcVisitedNodes() {
        return visited.cardinality();
    }
}
