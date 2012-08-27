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

import de.jetsli.graph.coll.IntBinHeap;
import de.jetsli.graph.coll.MyBitSet;
import de.jetsli.graph.coll.MyOpenBitSet;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.util.EdgeIterator;
import de.jetsli.graph.util.EdgeWrapper;

/**
 * Calculates shortest path in bidirectional way. Compared to DijkstraBidirectionRef this class is
 * more memory efficient as it does not go the normal Java way via references. In first tests this
 * class saves 30% memory, but as you can see it is a bit more complicated.
 *
 * TODO: use only one EdgeWrapper to save memory. This is not easy if we want it to be as fast as
 * the current solution. But we need to try it out if a forwardSearchBitset.contains(edgeId) is that
 * expensive
 *
 * @author Peter Karich, info@jetsli.de
 */
public class DijkstraBidirection extends AbstractRoutingAlgorithm {

    private int from, to;
    protected int currFrom;
    protected double currFromWeight;
    protected int currFromEdgeId;
    protected int currTo;
    protected double currToWeight;
    protected int currToEdgeId;
    protected PathWrapper shortest;
    protected EdgeWrapper wrapperOther;
    private MyBitSet visitedFrom;
    private IntBinHeap openSetFrom;
    private EdgeWrapper wrapperFrom;
    private MyBitSet visitedTo;
    private IntBinHeap openSetTo;
    private EdgeWrapper wrapperTo;
    private boolean alreadyRun;

    public DijkstraBidirection(Graph graph) {
        super(graph);
        int locs = Math.max(20, graph.getNodes());
        visitedFrom = new MyOpenBitSet(locs);
        openSetFrom = new IntBinHeap(locs / 10);
        wrapperFrom = new EdgeWrapper(locs / 10);

        visitedTo = new MyOpenBitSet(locs);
        openSetTo = new IntBinHeap(locs / 10);
        wrapperTo = new EdgeWrapper(locs / 10);

        clear();
    }

    @Override
    public RoutingAlgorithm clear() {
        alreadyRun = false;
        visitedFrom.clear();
        openSetFrom.clear();
        wrapperFrom.clear();

        visitedTo.clear();
        openSetTo.clear();
        wrapperTo.clear();

        shortest = new PathWrapper(graph, wrapperFrom, wrapperTo);
        shortest.weight = Double.MAX_VALUE;
        return this;
    }

    public void addSkipNode(int node) {
        visitedFrom.add(node);
        visitedTo.add(node);
    }

    public DijkstraBidirection initFrom(int from) {
        this.from = from;
        currFrom = from;
        currFromWeight = 0;
        currFromEdgeId = wrapperFrom.add(from, 0);
        return this;
    }

    public DijkstraBidirection initTo(int to) {
        this.to = to;
        currTo = to;
        currToWeight = 0;
        currToEdgeId = wrapperTo.add(to, 0);
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
        return currFromWeight + currToWeight >= shortest.weight;
    }

    public void fillEdges(int currNode, double currWeight, int currEdgeId, MyBitSet visitedMain,
            IntBinHeap prioQueue, EdgeWrapper wrapper, boolean out) {

        EdgeIterator iter;
        if (out)
            iter = graph.getOutgoing(currNode);
        else
            iter = graph.getIncoming(currNode);
        while (iter.next()) {
            int neighborNode = iter.node();
            if (visitedMain.contains(neighborNode))
                continue;

            double tmpWeight = getWeight(iter) + currWeight;
            int newEdgeId = wrapper.getEdgeId(neighborNode);
            if (newEdgeId <= 0) {
                newEdgeId = wrapper.add(neighborNode, tmpWeight);
                wrapper.putLink(newEdgeId, currEdgeId);
                prioQueue.insert(newEdgeId, tmpWeight);
            } else {
                double weight = wrapper.getWeight(newEdgeId);
                if (weight > tmpWeight) {
                    // use fibonacci? see http://stackoverflow.com/q/6273833/194609
                    // in fibonacci heaps there is decreaseKey but it has a lot more overhead per entry                    
                    wrapper.putWeight(newEdgeId, tmpWeight);
                    wrapper.putLink(newEdgeId, currEdgeId);
                    prioQueue.rekey(newEdgeId, tmpWeight);
                }
            }

            // TODO optimize: call only if necessary
            updateShortest(neighborNode, newEdgeId, tmpWeight);
        }
    }

    public void updateShortest(int nodeId, int edgeId, double weight) {
        int otherEdgeId = wrapperOther.getEdgeId(nodeId);
        if (otherEdgeId <= 0)
            return;

        // update μ
        double newWeight = weight + wrapperOther.getWeight(otherEdgeId);
        if (newWeight < shortest.weight) {
            shortest.switchWrapper = wrapperFrom == wrapperOther;
            shortest.fromEdgeId = edgeId;
            shortest.toEdgeId = otherEdgeId;
            shortest.weight = newWeight;
        }
    }

    public boolean fillEdgesFrom() {
        wrapperOther = wrapperTo;
        fillEdges(currFrom, currFromWeight, currFromEdgeId, visitedFrom, openSetFrom, wrapperFrom, true);
        if (openSetFrom.isEmpty())
            return false;

        currFromEdgeId = openSetFrom.extractMin();
        currFrom = wrapperFrom.getNode(currFromEdgeId);
        currFromWeight = wrapperFrom.getWeight(currFromEdgeId);
        if (checkFinishCondition())
            return false;
        visitedFrom.add(currFrom);
        return true;
    }

    public boolean fillEdgesTo() {
        wrapperOther = wrapperFrom;
        fillEdges(currTo, currToWeight, currToEdgeId, visitedTo, openSetTo, wrapperTo, false);
        if (openSetTo.isEmpty())
            return false;

        currToEdgeId = openSetTo.extractMin();
        currTo = wrapperTo.getNode(currToEdgeId);
        currToWeight = wrapperTo.getWeight(currToEdgeId);
        if (checkFinishCondition())
            return false;
        visitedTo.add(currTo);
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
}
