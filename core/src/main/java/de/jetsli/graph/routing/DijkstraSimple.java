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

import de.jetsli.graph.reader.CarFlags;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.EdgeEntry;
import de.jetsli.graph.util.EdgeIdIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.TIntHashSet;
import java.util.PriorityQueue;

/**
 * @author Peter Karich, info@jetsli.de
 */
public class DijkstraSimple extends AbstractRoutingAlgorithm {

    public DijkstraSimple(Graph graph) {
        super(graph);
    }

    @Override public Path calcPath(int from, int to) {
        EdgeEntry fromEntry = new EdgeEntry(from, 0);
        EdgeEntry currEdge = fromEntry;
        TIntHashSet visited = new TIntHashSet();
        TIntObjectMap<EdgeEntry> map = new TIntObjectHashMap<EdgeEntry>();
        PriorityQueue<EdgeEntry> heap = new PriorityQueue<EdgeEntry>();

        while (true) {
            int neighborNode = currEdge.node;
            EdgeIdIterator iter = graph.getOutgoing(neighborNode);
            while (iter.next()) {
                int tmpV = iter.nodeId();
                if (visited.contains(tmpV))
                    continue;

                double tmpWeight = getWeight(iter) + currEdge.weight;
                EdgeEntry nEdge = map.get(tmpV);
                if (nEdge == null) {
                    nEdge = new EdgeEntry(tmpV, tmpWeight);
                    nEdge.prevEntry = currEdge;
                    map.put(tmpV, nEdge);
                    heap.add(nEdge);
                } else if (nEdge.weight > tmpWeight) {
                    // use fibonacci? see http://stackoverflow.com/q/6273833/194609
                    // in fibonacci heaps there is decreaseKey                    
                    heap.remove(nEdge);
                    nEdge.weight = tmpWeight;
                    nEdge.prevEntry = currEdge;
                    heap.add(nEdge);
                }
            }
            if (to == neighborNode)
                break;

            visited.add(neighborNode);
            currEdge = heap.poll();
            if (currEdge == null)
                return null;

            updateShortest(currEdge, neighborNode);
        }

        // extract path from shortest-path-tree
        Path path = new Path();
        while (currEdge.node != from) {
            int tmpFrom = currEdge.node;            
            path.add(tmpFrom);
            currEdge = currEdge.prevEntry;
            path.updateProperties(graph.getIncoming(tmpFrom), currEdge.node);
        }
        path.add(fromEntry.node);
        path.reverseOrder();
        return path;
    }
}
