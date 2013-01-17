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
package com.graphhopper.routing.rideshare;

import com.graphhopper.coll.MyBitSet;
import com.graphhopper.coll.MyBitSetImpl;
import com.graphhopper.routing.AbstractRoutingAlgorithm;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.PathBidirRef;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GraphUtility;
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
 * @author Peter Karich,
 */
public class DijkstraWhichToOne extends AbstractRoutingAlgorithm {

    private PathBidirRef shortest;
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

    public Path calcPath() {
        // identical
        if (pubTransport.contains(destination))
            return new Path(graph, weightCalc);

        MyBitSet visitedFrom = new MyBitSetImpl(graph.nodes());
        PriorityQueue<EdgeEntry> prioQueueFrom = new PriorityQueue<EdgeEntry>();
        shortestDistMapFrom = new TIntObjectHashMap<EdgeEntry>();

        EdgeEntry entryTo = new EdgeEntry(EdgeIterator.NO_EDGE, destination, 0);
        EdgeEntry currTo = entryTo;
        MyBitSet visitedTo = new MyBitSetImpl(graph.nodes());
        PriorityQueue<EdgeEntry> prioQueueTo = new PriorityQueue<EdgeEntry>();
        shortestDistMapTo = new TIntObjectHashMap<EdgeEntry>();
        shortestDistMapTo.put(destination, entryTo);

        shortest = new PathBidirRef(graph, weightCalc);
        shortest.weight(Double.MAX_VALUE);

        // create several starting points
        if (pubTransport.isEmpty())
            throw new IllegalStateException("You'll need at least one starting point. Set it via addPubTransportPoint");

        EdgeEntry currFrom = null;
        for (int i = 0; i < pubTransport.size(); i++) {
            EdgeEntry tmpFrom = new EdgeEntry(EdgeIterator.NO_EDGE, pubTransport.get(i), 0);
            if (i == 0)
                currFrom = tmpFrom;

            shortestDistMapOther = shortestDistMapTo;
            fillEdges(shortest, tmpFrom, visitedFrom, prioQueueFrom, shortestDistMapFrom, true);
        }

        int finish = 0;
        while (finish < 2 && currFrom.weight + currTo.weight < shortest.weight()) {
            // http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
            // a node from overlap may not be on the shortest path!!
            // => when scanning an arc (v, w) in the forward search and w is scanned in the reverse 
            //    search, update shortest = μ if df (v) + (v, w) + dr (w) < μ            

            finish = 0;
            shortestDistMapOther = shortestDistMapTo;
            fillEdges(shortest, currFrom, visitedFrom, prioQueueFrom, shortestDistMapFrom, true);
            if (!prioQueueFrom.isEmpty()) {
                currFrom = prioQueueFrom.poll();
                visitedFrom.add(currFrom.endNode);
            } else
                finish++;

            shortestDistMapOther = shortestDistMapFrom;
            fillEdges(shortest, currTo, visitedTo, prioQueueTo, shortestDistMapTo, false);
            if (!prioQueueTo.isEmpty()) {
                currTo = prioQueueTo.poll();
                visitedTo.add(currTo.endNode);
            } else
                finish++;
        }

        Path p = shortest.extract();
        if (!p.found())
            return p;
        return p;
    }

    void fillEdges(PathBidirRef shortest, EdgeEntry curr, MyBitSet visitedMain,
            PriorityQueue<EdgeEntry> prioQueue,
            TIntObjectMap<EdgeEntry> shortestDistMap, boolean out) {

        int currVertexFrom = curr.endNode;
        EdgeIterator iter = GraphUtility.getEdges(graph, currVertexFrom, out);
        while (iter.next()) {
            int tmpV = iter.node();
            if (visitedMain.contains(tmpV))
                continue;

            double tmp = weightCalc.getWeight(iter.distance(), iter.flags()) + curr.weight;
            EdgeEntry de = shortestDistMap.get(tmpV);
            if (de == null) {
                de = new EdgeEntry(iter.edge(), tmpV, tmp);
                de.parent = curr;
                shortestDistMap.put(tmpV, de);
                prioQueue.add(de);
            } else if (de.weight > tmp) {
                prioQueue.remove(de);
                de.edge = iter.edge();
                de.weight = tmp;
                de.parent = curr;
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
            if (newShortest < shortest.weight()) {
                shortest.switchToFrom(shortestDistMapFrom == shortestDistMapOther);
                shortest.edgeEntry(de);
                shortest.edgeEntryTo(entryOther);
                shortest.weight(newShortest);
            }
        }
    }

    @Override public Path calcPath(int from, int to) {
        addPubTransportPoint(from);
        setDestination(to);
        return calcPath();
    }

    @Override
    public RoutingAlgorithm clear() {
        throw new UnsupportedOperationException("Not supported yet.");
        // shortest = new PathWrapperRef();
    }
}
