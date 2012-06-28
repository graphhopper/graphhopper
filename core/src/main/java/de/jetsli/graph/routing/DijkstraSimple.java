/*
 *  Copyright 2012 Peter Karich info@jetsli.de
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
package de.jetsli.graph.routing;

import de.jetsli.graph.storage.DistEntry;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.Edge;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.TIntHashSet;
import java.util.PriorityQueue;

/**
 * @author Peter Karich, info@jetsli.de
 */
public class DijkstraSimple implements RoutingAlgorithm {

    private Graph graph;

    public DijkstraSimple(Graph graph) {
        this.graph = graph;
    }

    @Override public Path calcShortestPath(int from, int to) {
        Edge fromEntry = new Edge(from, 0);
        Edge curr = fromEntry;
        TIntHashSet visited = new TIntHashSet();
        TIntObjectMap<Edge> map = new TIntObjectHashMap<Edge>();
        PriorityQueue<Edge> heap = new PriorityQueue<Edge>();

        while (true) {
            int currVertex = curr.node;
            for (DistEntry entry : graph.getOutgoing(currVertex)) {
                int tmpV = entry.node;
                if (visited.contains(tmpV))
                    continue;

                double tmp = entry.distance + curr.distance;
                Edge de = map.get(tmpV);
                if (de == null) {
                    de = new Edge(tmpV, tmp);
                    de.prevEntry = curr;
                    map.put(tmpV, de);
                    heap.add(de);
                } else if (de.distance > tmp) {
                    // use fibonacci? see http://stackoverflow.com/q/6273833/194609
                    // in fibonacci heaps there is decreaseKey                    
                    heap.remove(de);
                    de.distance = tmp;
                    de.prevEntry = curr;
                    heap.add(de);
                }
            }
            if (to == currVertex)
                break;

            visited.add(currVertex);
            curr = heap.poll();
            if (curr == null)
                throw new IllegalStateException("No path found");
        }

        // extract path from shortest-path-tree
        Path path = new Path();
        while (curr.node != from) {
            path.add(curr);
            curr = curr.prevEntry;
        }
        path.add(fromEntry);
        path.reverseOrder();
        return path;
    }
}
