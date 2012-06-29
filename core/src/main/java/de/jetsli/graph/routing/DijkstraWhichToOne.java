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
import de.jetsli.graph.coll.MyBitSet;
import de.jetsli.graph.coll.MyOpenBitSet;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.Edge;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.PriorityQueue;

/**
 * Public transport represents a collection of Locations. Now it is the aim to find the shortest
 * path of a path ('the public transport') to the destination. In contrast to manyToOne this class
 * only find one shortest path and not all, but it it more memory efficient (ie. the
 * shortest-path-trees do not overlap here)
 *
 * @author Peter Karich, info@jetsli.de
 */
public class DijkstraWhichToOne implements RoutingAlgorithm {

    private Graph graph;
    private TIntArrayList pubTransport = new TIntArrayList();
    private int destination;

    public DijkstraWhichToOne(Graph graph) {
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

    public void setDestination(int index) {
        destination = index;
    }

    public Path calcShortestPath() {
        // identical
        if (pubTransport.contains(destination)) {
            Path p = new Path();
            p.add(new DistEntry(destination, 0));
            return p;
        }
        
        MyBitSet visitedFrom = new MyOpenBitSet(graph.getLocations());
        PriorityQueue<Edge> prioQueueFrom = new PriorityQueue<Edge>();
        TIntObjectMap<Edge> shortestDistMapFrom = new TIntObjectHashMap<Edge>();

        Edge entryTo = new Edge(destination, 0);        
        Edge currTo = entryTo;
        MyBitSet visitedTo = new MyOpenBitSet(graph.getLocations());
        PriorityQueue<Edge> prioQueueTo = new PriorityQueue<Edge>();
        TIntObjectMap<Edge> shortestDistMapTo = new TIntObjectHashMap<Edge>();
        shortestDistMapTo.put(destination, entryTo);

        PathWrapper shortest = new PathWrapper();
        shortest.distance = Double.MAX_VALUE;

        // create several starting points
        if (pubTransport.isEmpty())
            throw new IllegalStateException("You'll need at least one starting point. Set it via addPubTransportPoint");

        Edge currFrom = null;
        for (int i = 0; i < pubTransport.size(); i++) {
            Edge tmpFrom = new Edge(pubTransport.get(i), 0);
            if (i == 0)
                currFrom = tmpFrom;
            fillEdges(shortest, tmpFrom, visitedFrom, prioQueueFrom, shortestDistMapFrom, shortestDistMapTo);
        }                

        int finish = 0;
        while (finish < 2 && currFrom.distance + currTo.distance < shortest.distance) {            
            // http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
            // a node from overlap may not be on the shortest path!!
            // => when scanning an arc (v, w) in the forward search and w is scanned in the reverse 
            //    search, update shortest = μ if df (v) + (v, w) + dr (w) < μ            

            finish = 0;
            fillEdges(shortest, currFrom, visitedFrom, prioQueueFrom, shortestDistMapFrom, shortestDistMapTo);
            if (!prioQueueFrom.isEmpty()) {
                currFrom = prioQueueFrom.poll();
                visitedFrom.add(currFrom.node);
            } else
                finish++;

            fillEdges(shortest, currTo, visitedTo, prioQueueTo, shortestDistMapTo, shortestDistMapFrom);
            if (!prioQueueTo.isEmpty()) {
                currTo = prioQueueTo.poll();
                visitedTo.add(currTo.node);
            } else
                finish++;           
        }

        Path g = shortest.extract();
        if(g == null)
            return null;
        
        if (!pubTransport.contains(g.getFromLoc())) {
            double tmpDist = g.distance();
            g.reverseOrder();
            g.setDistance(tmpDist);
        }

        return g;
    }

    public void fillEdges(PathWrapper shortest, Edge curr, MyBitSet visitedMain,
            PriorityQueue<Edge> prioQueue,
            TIntObjectMap<Edge> shortestDistMap, TIntObjectMap<Edge> shortestDistMapOther) {

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

            Edge entryOther = shortestDistMapOther.get(tmpV);
            if (entryOther == null)
                continue;

            // update μ
            double newShortest = de.distance + entryOther.distance;
            if (newShortest < shortest.distance) {
                shortest.entryFrom = de;
                shortest.entryTo = entryOther;
                shortest.distance = newShortest;
            }
        } // for
    }

    @Override public Path calcShortestPath(int from, int to) {
        addPubTransportPoint(from);
        setDestination(to);
        return calcShortestPath();
    }

    @Override
    public RoutingAlgorithm clear() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
