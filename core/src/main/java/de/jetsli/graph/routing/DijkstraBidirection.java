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
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.PriorityQueue;

/**
 * Calculates shortest path in bidirectional way.
 *
 * Warning: class saves state and cannot be reused.
 *
 * @author Peter Karich, info@jetsli.de
 */
public class DijkstraBidirection implements RoutingAlgorithm {

    private int from, to;
    private Graph graph;
    protected Edge currFrom;
    protected Edge currTo;
    protected PathWrapper shortest;
    protected TIntObjectMap<Edge> shortestDistMapOther;
    private MyBitSet visitedFrom;
    private PriorityQueue<Edge> prioQueueFrom;
    private TIntObjectMap<Edge> shortestDistMapFrom;
    private MyBitSet visitedTo;
    private PriorityQueue<Edge> prioQueueTo;
    private TIntObjectMap<Edge> shortestDistMapTo;
    private boolean alreadyRun;

    public DijkstraBidirection(Graph graph) {
        this.graph = graph;
        int locs = Math.max(20, graph.getLocations());
        visitedFrom = new MyOpenBitSet(locs);
        prioQueueFrom = new PriorityQueue<Edge>(locs / 10);
        shortestDistMapFrom = new TIntObjectHashMap<Edge>(locs / 10);

        visitedTo = new MyOpenBitSet(locs);
        prioQueueTo = new PriorityQueue<Edge>(locs / 10);
        shortestDistMapTo = new TIntObjectHashMap<Edge>(locs / 10);

        clear();
    }

    @Override
    public RoutingAlgorithm clear() {
        alreadyRun = false;
        visitedFrom.clear();
        prioQueueFrom.clear();
        shortestDistMapFrom.clear();

        visitedTo.clear();
        prioQueueTo.clear();
        shortestDistMapTo.clear();

        shortest = new PathWrapper();
        shortest.distance = Double.MAX_VALUE;
        return this;
    }

    public void addSkipNode(int node) {
        visitedFrom.add(node);
        visitedTo.add(node);
    }

    public DijkstraBidirection initFrom(int from) {
        this.from = from;
        currFrom = new Edge(from, 0);
        shortestDistMapFrom.put(from, currFrom);
        return this;
    }

    public DijkstraBidirection initTo(int to) {
        this.to = to;
        currTo = new Edge(to, 0);
        shortestDistMapTo.put(to, currTo);
        return this;
    }

    @Override public Path calcShortestPath(int from, int to) {
        if (alreadyRun)
            throw new IllegalStateException("Do not reuse DijkstraBidirection");

        alreadyRun = true;
        initFrom(from);
        initTo(to);

        Path p = checkIndenticalFromAndTo();
        if (p != null)
            return p;

        int counter = 0;
        int finish = 0;
        while (finish < 2) {
            counter ++;
            finish = 0;
            if (!fillEdgesFrom())
                finish++;

            if (!fillEdgesTo())
                finish++;
        }
        
        return getShortest();
    }

    public Path getShortest() {
        Path p = shortest.extract();
        if (p == null)
            return null;

        if (p.getFromLoc() != from) {
            // move distance adjustment to reverseOrder?
            double tmpDist = p.distance();
            p.reverseOrder();
            p.setDistance(tmpDist);
        }

        return p;
    }

    // http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
    // a node from overlap may not be on the shortest path!!
    // => when scanning an arc (v, w) in the forward search and w is scanned in the reverse 
    //    search, update shortest = μ if df (v) + (v, w) + dr (w) < μ            
    public boolean checkFinishCondition() {
        if (currFrom == null)
            return currTo.distance >= shortest.distance;
        else if (currTo == null)
            return currFrom.distance >= shortest.distance;
        return currFrom.distance + currTo.distance >= shortest.distance;
    }

    public void fillEdges(Edge curr, MyBitSet visitedMain, PriorityQueue<Edge> prioQueue,
            TIntObjectMap<Edge> shortestDistMap) {

        int currVertexFrom = curr.node;
        for (DistEntry entry : graph.getOutgoing(currVertexFrom)) {
            int currentLinkedNode = entry.node;
            if (visitedMain.contains(currentLinkedNode))
                continue;

            double tmp = entry.distance + curr.distance;
            Edge de = shortestDistMap.get(currentLinkedNode);
            if (de == null) {
                de = new Edge(currentLinkedNode, tmp);
                de.prevEntry = curr;
                shortestDistMap.put(currentLinkedNode, de);
                prioQueue.add(de);
            } else if (de.distance > tmp) {
                // use fibonacci? see http://stackoverflow.com/q/6273833/194609
                // in fibonacci heaps there is decreaseKey but it has a lot more overhead per entry
                prioQueue.remove(de);
                de.distance = tmp;
                de.prevEntry = curr;
                prioQueue.add(de);
            }

            updateShortest(de, currentLinkedNode);
        } // for
    }

    public void updateShortest(Edge shortestDE, int currLoc) {
        Edge entryOther = shortestDistMapOther.get(currLoc);
        if (entryOther == null)
            return;

        // update μ
        double newShortest = shortestDE.distance + entryOther.distance;
        if (newShortest < shortest.distance) {
            shortest.entryFrom = shortestDE;
            shortest.entryTo = entryOther;
            shortest.distance = newShortest;
        }
    }

    public boolean fillEdgesFrom() {
        if (currFrom != null) {
            shortestDistMapOther = shortestDistMapTo;
            fillEdges(currFrom, visitedFrom, prioQueueFrom, shortestDistMapFrom);
            if (prioQueueFrom.isEmpty())
                return false;

            currFrom = prioQueueFrom.poll();
            if (checkFinishCondition())
                return false;
            visitedFrom.add(currFrom.node);

        } else if (currTo == null)
            throw new IllegalStateException("Shortest Path not found? " + from + " " + to);

        return true;
    }

    public boolean fillEdgesTo() {
        if (currTo != null) {
            shortestDistMapOther = shortestDistMapFrom;
            fillEdges(currTo, visitedTo, prioQueueTo, shortestDistMapTo);
            if (prioQueueTo.isEmpty())
                return false;

            currTo = prioQueueTo.poll();
            if (checkFinishCondition())
                return false;
            visitedTo.add(currTo.node);
        } else if (currFrom == null)
            throw new IllegalStateException("Shortest Path not found? " + from + " " + to);
        return true;
    }

    private Path checkIndenticalFromAndTo() {
        if (from == to) {
            Path p = new Path();
            p.add(new DistEntry(from, 0));
            return p;
        }
        return null;
    }

    public Edge getShortestDistFrom(int index) {
        return shortestDistMapFrom.get(index);
    }

    public Edge getShortestDistTo(int index) {
        return shortestDistMapTo.get(index);
    }
}
