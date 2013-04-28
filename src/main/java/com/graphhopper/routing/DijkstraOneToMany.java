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

import com.graphhopper.routing.util.VehicleEncoder;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.PriorityQueue;

/**
 * A simple dijkstra tuned to perform one to many queries more efficient than
 * DijkstraSimple. Old data structures are cache between requests and
 * potentially reused. Useful for CH preparation.
 *
 * @author Peter Karich
 */
public class DijkstraOneToMany extends Dijkstra {

    boolean nextFrom = true;

    public DijkstraOneToMany(Graph graph, VehicleEncoder encoder) {
        super(graph, encoder);
    }

    @Override
    public Path calcPath(int from, int to) {
        EdgeEntry resEdge = calcEdgeEntry(from, to);
        if (resEdge == null)
            return new Path(graph, flagEncoder);
        return super.extractPath(resEdge);
    }

    public DijkstraOneToMany clear() {
        nextFrom = true;
        return this;
    }

    public EdgeEntry calcEdgeEntry(int from, int to) {
        visitedNodes = 0;
        EdgeEntry currEdge;
        if (nextFrom) {
            // start over!
            nextFrom = false;
            map = new TIntObjectHashMap<EdgeEntry>(map.size() > 20 ? map.size() : 10);
            heap = new PriorityQueue<EdgeEntry>(heap.size() > 20 ? heap.size() : 10);
            currEdge = new EdgeEntry(EdgeIterator.NO_EDGE, from, 0d);
            map.put(from, currEdge);
        } else {
            // re-use existing data structures
            EdgeEntry ee = map.get(to);
            if (ee != null || heap.isEmpty())
                return ee;

            currEdge = heap.poll();
        }

        if (finished(currEdge, to))
            return currEdge;
        while (true) {
            visitedNodes++;
            int neighborNode = currEdge.endNode;
            EdgeIterator iter = graph.getEdges(neighborNode, outEdgeFilter);
            while (iter.next()) {
                if (!accept(iter))
                    continue;
                int tmpNode = iter.adjNode();
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
            }

            if (heap.isEmpty())
                return null;
            // calling just peek() is important for cache access of a next query
            currEdge = heap.peek();
            if (finished(currEdge, to))
                return currEdge;
            heap.poll();
        }
    }

    @Override public String name() {
        return "dijkstraOneToMany";
    }
}
