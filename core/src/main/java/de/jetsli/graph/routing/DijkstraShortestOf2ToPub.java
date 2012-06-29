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

import de.jetsli.graph.storage.PathWrapper;
import de.jetsli.graph.storage.DistEntry;
import de.jetsli.graph.coll.MyBitSet;
import de.jetsli.graph.coll.MyOpenBitSet;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.Edge;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.PriorityQueue;

/**
 * Public transport represents a collection of Locations. Then there are two points P1 and P1 and it
 * is the aim to find the shortest path from P1 to one of the public transport points (M) and to P2.
 *
 * <br/> Usage: A driver can carry the passenger from P1 to a public transport point (M) and going
 * back to his own destination P2 and comparing this with the detour of taking the passenger
 * directly to his destination (and then going back to P2).
 *
 * @author Peter Karich, info@jetsli.de
 */
public class DijkstraShortestOf2ToPub implements RoutingAlgorithm {

    private Graph graph;
    private TIntArrayList pubTransport = new TIntArrayList();
    private int fromP1;
    private int toP2;
    private Edge currTo;
    private Edge currFrom;
    private PathWrapper shortest;
    private TIntObjectMap<Edge> shortestDistMapOther;

    public DijkstraShortestOf2ToPub(Graph graph) {
        this.graph = graph;
    }

    public void addPubTransportPoints(int... indices) {
        if (indices.length == 0)
            throw new IllegalStateException("You need to add something");

        for (int i = 0; i < indices.length; i++) {
            addPubTransportPoint(indices[i]);
        }
    }

    public void addPubTransportPoint(int index) {
        if (!pubTransport.contains(index))
            pubTransport.add(index);
    }

    public void setFrom(int from) {
        fromP1 = from;
    }

    public void setTo(int to) {
        toP2 = to;
    }

    public Path calcShortestPath() {
        // identical
        if (pubTransport.contains(fromP1) || pubTransport.contains(toP2))
            return new DijkstraBidirection(graph).calcShortestPath(fromP1, toP2);

        MyBitSet visitedFrom = new MyOpenBitSet(graph.getLocations());
        PriorityQueue<Edge> prioQueueFrom = new PriorityQueue<Edge>();
        TIntObjectMap<Edge> shortestDistMapFrom = new TIntObjectHashMap<Edge>();

        Edge entryTo = new Edge(toP2, 0);
        currTo = entryTo;
        MyBitSet visitedTo = new MyOpenBitSet(graph.getLocations());
        PriorityQueue<Edge> prioQueueTo = new PriorityQueue<Edge>();
        TIntObjectMap<Edge> shortestDistMapTo = new TIntObjectHashMap<Edge>();

        shortest = new PathWrapper();
        shortest.distance = Double.MAX_VALUE;

        // create several starting points
        if (pubTransport.isEmpty())
            throw new IllegalStateException("You'll need at least one starting point. Set it via addPubTransportPoint");

        currFrom = new Edge(fromP1, 0);
        // in the birectional case we maintain the shortest path via:
        // currFrom.distance + currTo.distance >= shortest.distance
        // Now we simply need to check bevor updating if the newly discovered point is from pub tranport
        while (true) {
            if (currFrom != null) {
                shortestDistMapOther = shortestDistMapTo;
                fillEdges(currFrom, visitedFrom, prioQueueFrom, shortestDistMapFrom);
                currFrom = prioQueueFrom.poll();
                if (currFrom != null) {
                    if (checkFinishCondition())
                        break;
                    visitedFrom.add(currFrom.node);
                }
            } else if (currTo == null)
                throw new IllegalStateException("Shortest Path not found? " + fromP1 + " " + toP2);

            if (currTo != null) {
                shortestDistMapOther = shortestDistMapFrom;
                fillEdges(currTo, visitedTo, prioQueueTo, shortestDistMapTo);
                currTo = prioQueueTo.poll();
                if (currTo != null) {
                    if (checkFinishCondition())
                        break;
                    visitedTo.add(currTo.node);
                }
            } else if (currFrom == null)
                throw new IllegalStateException("Shortest Path not found? " + fromP1 + " " + toP2);
        }

        Path g = shortest.extract();
        if (!pubTransport.contains(g.getFromLoc())) {
            double tmpDist = g.distance();
            g.reverseOrder();
            g.setDistance(tmpDist);
        }

        return g;
    }

    // this won't work anymore as the dijkstra heaps are independent and can take a completely 
    // different returning point into account (not necessarily the shortest).
    // example: P1 to M is relativ long, also P2 to M - in sum they can be longer than the shortest.
    // But even now it could be that there is an undiscovered M' from P1 which results in a very short 
    // (and already discovered) back path M'-P2. See test testCalculateShortestPathWithSpecialFinishCondition
//    public boolean checkFinishCondition() {
//        return currFrom.distance + currTo.distance >= shortest.distance;
//    }
    public boolean checkFinishCondition() {
        if (currFrom == null) {
            if (currTo == null)
                throw new IllegalStateException("no shortest path!?");

            return currTo.distance >= shortest.distance;
        } else if (currTo == null)
            return currFrom.distance >= shortest.distance;
        else
            return Math.min(currFrom.distance, currTo.distance) >= shortest.distance;
    }

    public void fillEdges(Edge curr, MyBitSet visitedMain,
            PriorityQueue<Edge> prioQueue, TIntObjectMap<Edge> shortestDistMap) {

        int currVertexFrom = curr.node;
        for (DistEntry entry : graph.getOutgoing(currVertexFrom)) {
            int tmpV = entry.node;
            if (visitedMain.contains(tmpV))
                continue;

            double tmp = entry.distance + curr.distance;
            Edge de = shortestDistMap.get(tmpV);
            if (de == null) {
                de = new Edge(tmpV, tmp);
                de.prevEntry = curr;
                shortestDistMap.put(tmpV, de);
                prioQueue.add(de);
            } else if (de.distance > tmp) {
                // use fibonacci? see http://stackoverflow.com/q/6273833/194609
                // in fibonacci heaps there is decreaseKey but it has a lot more overhead per entry
                prioQueue.remove(de);
                de.distance = tmp;
                de.prevEntry = curr;
                prioQueue.add(de);
            }

            updateShortest(de, tmpV);
        } // for
    }

    public void updateShortest(Edge shortestDE, int currLoc) {
        if (pubTransport.contains(currLoc)) {
            Edge entryOther = shortestDistMapOther.get(currLoc);
            if (entryOther != null) {
                // update μ
                double newShortest = shortestDE.distance + entryOther.distance;
                if (newShortest < shortest.distance) {
                    shortest.entryFrom = shortestDE;
                    shortest.entryTo = entryOther;
                    shortest.distance = newShortest;
                }
            }
        }
    }

    @Override public Path calcShortestPath(int from, int to) {
        addPubTransportPoint(from);
        setFrom(from);
        setTo(to);
        return calcShortestPath();
    }

    @Override
    public RoutingAlgorithm clear() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
