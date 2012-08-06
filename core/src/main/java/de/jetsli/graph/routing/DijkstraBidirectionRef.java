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

import de.jetsli.graph.coll.MyBitSet;
import de.jetsli.graph.coll.MyOpenBitSet;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.EdgeEntry;
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
public class DijkstraBidirectionRef extends AbstractRoutingAlgorithm {

    private int from, to;
    private MyBitSet visitedFrom;
    private PriorityQueue<EdgeEntry> openSetFrom;
    private TIntObjectMap<EdgeEntry> shortestWeightMapFrom;
    private MyBitSet visitedTo;
    private PriorityQueue<EdgeEntry> openSetTo;
    private TIntObjectMap<EdgeEntry> shortestWeightMapTo;
    private boolean alreadyRun;
    protected EdgeEntry currFrom;
    protected EdgeEntry currTo;
    protected TIntObjectMap<EdgeEntry> shortestWeightMapOther;
    public PathWrapperRef shortest;

    public DijkstraBidirectionRef(Graph graph) {
        super(graph);
        int locs = Math.max(20, graph.getNodes());
        visitedFrom = new MyOpenBitSet(locs);
        openSetFrom = new PriorityQueue<EdgeEntry>(locs / 10);
        shortestWeightMapFrom = new TIntObjectHashMap<EdgeEntry>(locs / 10);

        visitedTo = new MyOpenBitSet(locs);
        openSetTo = new PriorityQueue<EdgeEntry>(locs / 10);
        shortestWeightMapTo = new TIntObjectHashMap<EdgeEntry>(locs / 10);

        clear();
    }

    @Override
    public RoutingAlgorithm clear() {
        alreadyRun = false;
        visitedFrom.clear();
        openSetFrom.clear();
        shortestWeightMapFrom.clear();

        visitedTo.clear();
        openSetTo.clear();
        shortestWeightMapTo.clear();

        shortest = new PathWrapperRef(graph);
        shortest.weight = Double.MAX_VALUE;
        return this;
    }

    public void addSkipNode(int node) {
        visitedFrom.add(node);
        visitedTo.add(node);
    }

    public DijkstraBidirectionRef initFrom(int from) {
        this.from = from;
        currFrom = new EdgeEntry(from, 0);
        shortestWeightMapFrom.put(from, currFrom);
        return this;
    }

    public DijkstraBidirectionRef initTo(int to) {
        this.to = to;
        currTo = new EdgeEntry(to, 0);
        shortestWeightMapTo.put(to, currTo);
        return this;
    }

    @Override public Path calcPath(int from, int to) {
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
        return shortest.extract();
    }

    // http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
    // a node from overlap may not be on the shortest path!!
    // => when scanning an arc (v, w) in the forward search and w is scanned in the reverse 
    //    search, update shortest = μ if df (v) + (v, w) + dr (w) < μ            
    public boolean checkFinishCondition() {
        if (currFrom == null)
            return currTo.weight >= shortest.weight;
        else if (currTo == null)
            return currFrom.weight >= shortest.weight;
        return currFrom.weight + currTo.weight >= shortest.weight;
    }

    public void fillEdges(EdgeEntry curr, MyBitSet visitedMain, PriorityQueue<EdgeEntry> prioQueue,
            TIntObjectMap<EdgeEntry> shortestWeightMap, boolean out) {

        int currNodeFrom = curr.node;
        EdgeIdIterator iter;
        if (out)
            iter = graph.getOutgoing(currNodeFrom);
        else
            iter = graph.getIncoming(currNodeFrom);

        while (iter.next()) {
            int neighborNode = iter.nodeId();
            if (visitedMain.contains(neighborNode))
                continue;

            double tmpWeight = getWeight(iter) + curr.weight;
            EdgeEntry de = shortestWeightMap.get(neighborNode);
            if (de == null) {
                de = new EdgeEntry(neighborNode, tmpWeight);
                de.prevEntry = curr;
                shortestWeightMap.put(neighborNode, de);
                prioQueue.add(de);
            } else if (de.weight > tmpWeight) {
                // use fibonacci? see http://stackoverflow.com/q/6273833/194609
                // in fibonacci heaps there is decreaseKey but it has a lot more overhead per entry
                prioQueue.remove(de);
                de.weight = tmpWeight;
                de.prevEntry = curr;
                prioQueue.add(de);
            }

            // TODO optimize: call only if necessary
            updateShortest(de, neighborNode);
        }
    }

    @Override
    public void updateShortest(EdgeEntry shortestDE, int currLoc) {
        EdgeEntry entryOther = shortestWeightMapOther.get(currLoc);
        if (entryOther == null)
            return;

        // update μ
        double newShortest = shortestDE.weight + entryOther.weight;
        if (newShortest < shortest.weight) {
            shortest.switchWrapper = shortestWeightMapFrom == shortestWeightMapOther;
            shortest.edgeFrom = shortestDE;
            shortest.edgeTo = entryOther;
            shortest.weight = newShortest;
        }
    }

    public boolean fillEdgesFrom() {
        if (currFrom != null) {
            shortestWeightMapOther = shortestWeightMapTo;
            fillEdges(currFrom, visitedFrom, openSetFrom, shortestWeightMapFrom, true);
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
            shortestWeightMapOther = shortestWeightMapFrom;
            fillEdges(currTo, visitedTo, openSetTo, shortestWeightMapTo, false);
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
            p.add(from);
            return p;
        }
        return null;
    }

    public EdgeEntry getShortestWeightFrom(int nodeId) {
        return shortestWeightMapFrom.get(nodeId);
    }

    public EdgeEntry getShortestWeightTo(int nodeId) {
        return shortestWeightMapTo.get(nodeId);
    }
}
