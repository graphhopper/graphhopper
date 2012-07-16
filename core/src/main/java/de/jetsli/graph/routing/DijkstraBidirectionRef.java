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
import de.jetsli.graph.util.EdgeIdIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.PriorityQueue;

/**
 * Calculates shortest path in bidirectional way.
 *
 * 'Ref' stands for reference implementation and is using the normal Java-'reference'-way
 *
 * @author Peter Karich, info@jetsli.de
 */
public class DijkstraBidirectionRef implements RoutingAlgorithm {
    
    private int from, to;
    private Graph graph;    
    private MyBitSet visitedFrom;
    private PriorityQueue<Edge> openSetFrom;
    private TIntObjectMap<Edge> shortestDistMapFrom;
    private MyBitSet visitedTo;
    private PriorityQueue<Edge> openSetTo;
    private TIntObjectMap<Edge> shortestDistMapTo;
    private boolean alreadyRun;
    protected Edge currFrom;
    protected Edge currTo;    
    protected TIntObjectMap<Edge> shortestDistMapOther;
    public PathWrapperRef shortest;

    public DijkstraBidirectionRef(Graph graph) {
        this.graph = graph;
        int locs = Math.max(20, graph.getNodes());
        visitedFrom = new MyOpenBitSet(locs);
        openSetFrom = new PriorityQueue<Edge>(locs / 10);
        shortestDistMapFrom = new TIntObjectHashMap<Edge>(locs / 10);

        visitedTo = new MyOpenBitSet(locs);
        openSetTo = new PriorityQueue<Edge>(locs / 10);
        shortestDistMapTo = new TIntObjectHashMap<Edge>(locs / 10);

        clear();
    }

    @Override
    public RoutingAlgorithm clear() {
        alreadyRun = false;
        visitedFrom.clear();
        openSetFrom.clear();
        shortestDistMapFrom.clear();

        visitedTo.clear();
        openSetTo.clear();
        shortestDistMapTo.clear();

        shortest = new PathWrapperRef();
        shortest.distance = Double.MAX_VALUE;
        return this;
    }

    public void addSkipNode(int node) {
        visitedFrom.add(node);
        visitedTo.add(node);
    }

    public DijkstraBidirectionRef initFrom(int from) {
        this.from = from;
        currFrom = new Edge(from, 0);
        shortestDistMapFrom.put(from, currFrom);
        return this;
    }

    public DijkstraBidirectionRef initTo(int to) {
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
            counter++;
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
        EdgeIdIterator iter = graph.getOutgoing(currVertexFrom);
        while (iter.next()) {
            int neighborNode = iter.nodeId();
            if (visitedMain.contains(neighborNode))
                continue;

            double tmpDist = iter.distance() + curr.distance;
            Edge de = shortestDistMap.get(neighborNode);
            if (de == null) {
                de = new Edge(neighborNode, tmpDist);
                de.prevEntry = curr;
                shortestDistMap.put(neighborNode, de);
                prioQueue.add(de);
            } else if (de.distance > tmpDist) {
                // use fibonacci? see http://stackoverflow.com/q/6273833/194609
                // in fibonacci heaps there is decreaseKey but it has a lot more overhead per entry
                prioQueue.remove(de);
                de.distance = tmpDist;
                de.prevEntry = curr;
                prioQueue.add(de);
            }            
            
            // TODO optimize: call only if necessary
            updateShortest(de, neighborNode);
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
            fillEdges(currFrom, visitedFrom, openSetFrom, shortestDistMapFrom);
            if (openSetFrom.isEmpty())
                return false;

            currFrom = openSetFrom.poll();
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
            fillEdges(currTo, visitedTo, openSetTo, shortestDistMapTo);
            if (openSetTo.isEmpty())
                return false;

            currTo = openSetTo.poll();
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

    public Edge getShortestDistFrom(int nodeId) {
        return shortestDistMapFrom.get(nodeId);
    }

    public Edge getShortestDistTo(int nodeId) {
        return shortestDistMapTo.get(nodeId);
    }
}
