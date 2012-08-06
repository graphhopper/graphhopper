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
package de.jetsli.graph.routing.rideshare;

import de.jetsli.graph.storage.Edge;
import de.jetsli.graph.coll.MyBitSet;
import de.jetsli.graph.coll.MyOpenBitSet;
import de.jetsli.graph.routing.AbstractRoutingAlgorithm;
import de.jetsli.graph.routing.Path;
import de.jetsli.graph.routing.PathWrapperRef;
import de.jetsli.graph.routing.RoutingAlgorithm;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.EdgeEntry;
import de.jetsli.graph.util.EdgeIdIterator;
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
public class DijkstraWhichToOne extends AbstractRoutingAlgorithm {

    private PathWrapperRef shortest;
    private TIntObjectMap<EdgeEntry> shortestDistMapOther;
    private TIntObjectMap<EdgeEntry> shortestDistMapFrom;
    private TIntObjectMap<EdgeEntry> shortestDistMapTo;
    private TIntArrayList pubTransport = new TIntArrayList();
    private int destination;

    public DijkstraWhichToOne(Graph graph) {
        super(graph);
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
            p.add(destination);
            return p;
        }

        MyBitSet visitedFrom = new MyOpenBitSet(graph.getNodes());
        PriorityQueue<EdgeEntry> prioQueueFrom = new PriorityQueue<EdgeEntry>();
        shortestDistMapFrom = new TIntObjectHashMap<EdgeEntry>();

        EdgeEntry entryTo = new EdgeEntry(destination, 0);
        EdgeEntry currTo = entryTo;
        MyBitSet visitedTo = new MyOpenBitSet(graph.getNodes());
        PriorityQueue<EdgeEntry> prioQueueTo = new PriorityQueue<EdgeEntry>();
        shortestDistMapTo = new TIntObjectHashMap<EdgeEntry>();
        shortestDistMapTo.put(destination, entryTo);

        shortest = new PathWrapperRef(graph);
        shortest.weight = Double.MAX_VALUE;

        // create several starting points
        if (pubTransport.isEmpty())
            throw new IllegalStateException("You'll need at least one starting point. Set it via addPubTransportPoint");

        EdgeEntry currFrom = null;
        for (int i = 0; i < pubTransport.size(); i++) {
            EdgeEntry tmpFrom = new EdgeEntry(pubTransport.get(i), 0);
            if (i == 0)
                currFrom = tmpFrom;

            shortestDistMapOther = shortestDistMapTo;
            fillEdges(shortest, tmpFrom, visitedFrom, prioQueueFrom, shortestDistMapFrom, true);
        }

        int finish = 0;
        while (finish < 2 && currFrom.weight + currTo.weight < shortest.weight) {
            // http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
            // a node from overlap may not be on the shortest path!!
            // => when scanning an arc (v, w) in the forward search and w is scanned in the reverse 
            //    search, update shortest = μ if df (v) + (v, w) + dr (w) < μ            

            finish = 0;
            shortestDistMapOther = shortestDistMapTo;
            fillEdges(shortest, currFrom, visitedFrom, prioQueueFrom, shortestDistMapFrom, true);
            if (!prioQueueFrom.isEmpty()) {
                currFrom = prioQueueFrom.poll();
                visitedFrom.add(currFrom.node);
            } else
                finish++;

            shortestDistMapOther = shortestDistMapFrom;
            fillEdges(shortest, currTo, visitedTo, prioQueueTo, shortestDistMapTo, false);
            if (!prioQueueTo.isEmpty()) {
                currTo = prioQueueTo.poll();
                visitedTo.add(currTo.node);
            } else
                finish++;
        }

        Path g = shortest.extract();
        if (g == null)
            return null;

        if (!pubTransport.contains(g.getFromLoc())) {
            double tmpDist = g.distance();
            g.reverseOrder();
            g.setDistance(tmpDist);
        }

        return g;
    }

    public void fillEdges(PathWrapperRef shortest, EdgeEntry curr, MyBitSet visitedMain,
            PriorityQueue<EdgeEntry> prioQueue,
            TIntObjectMap<EdgeEntry> shortestDistMap, boolean out) {

        int currVertexFrom = curr.node;
        EdgeIdIterator iter;
        if (out)
            iter = graph.getOutgoing(currVertexFrom);
        else
            iter = graph.getIncoming(currVertexFrom);
        while (iter.next()) {
            int tmpV = iter.nodeId();
            if (visitedMain.contains(tmpV))
                continue;

            double tmp = getWeight(iter) + curr.weight;
            EdgeEntry de = shortestDistMap.get(tmpV);
            if (de == null) {
                de = new EdgeEntry(tmpV, tmp);
                de.prevEntry = curr;
                shortestDistMap.put(tmpV, de);
                prioQueue.add(de);
            } else if (de.weight > tmp) {
                // use fibonacci? see http://stackoverflow.com/q/6273833/194609
                // in fibonacci heaps there is decreaseKey but it has a lot more overhead per entry
                prioQueue.remove(de);
                de.weight = tmp;
                de.prevEntry = curr;
                prioQueue.add(de);
            }

            updateShortest(de, tmpV);
        }
    }

    @Override
    public void updateShortest(EdgeEntry de, int currLoc) {
        EdgeEntry entryOther = shortestDistMapOther.get(currLoc);
        if (entryOther != null) {
            // update μ
            double newShortest = de.weight + entryOther.weight;
            if (newShortest < shortest.weight) {
                shortest.switchWrapper = shortestDistMapFrom == shortestDistMapOther;
                shortest.edgeFrom = de;
                shortest.edgeTo = entryOther;
                shortest.weight = newShortest;
            }
        }
    }

    @Override public Path calcPath(int from, int to) {
        addPubTransportPoint(from);
        setDestination(to);
        return calcShortestPath();
    }

    @Override
    public RoutingAlgorithm clear() {
        throw new UnsupportedOperationException("Not supported yet.");
        // shortest = new PathWrapperRef();
    }
}
