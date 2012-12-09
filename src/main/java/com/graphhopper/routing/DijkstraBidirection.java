/*
 *  Copyright 2012 Peter Karich 
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
package com.graphhopper.routing;

import com.graphhopper.coll.IntDoubleBinHeap;
import com.graphhopper.coll.MyBitSet;
import com.graphhopper.coll.MyBitSetImpl;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeWrapper;
import com.graphhopper.util.GraphUtility;

/**
 * Calculates shortest path in bidirectional way. Compared to DijkstraBidirectionRef this class is
 * more memory efficient as it does not go the normal Java way via references. In first tests this
 * class saves 30% memory, but as you can see it is more complicated.
 *
 * TODO: use only one EdgeWrapper to save memory. This is not easy if we want it to be as fast as
 * the current solution. But we need to try it out if a forwardSearchBitset.contains(ref) is that
 * expensive
 *
 * TODO EdgeWrapper: instead of creating references point to the edges itself => we only need an
 * edge+node array and from that can retrieve eg. the distance
 *
 * @author Peter Karich,
 */
public class DijkstraBidirection extends AbstractRoutingAlgorithm {

    private int from, to;
    protected int currFrom;
    protected double currFromWeight;
    protected int currFromRef;
    protected int currTo;
    protected double currToWeight;
    protected int currToRef;
    protected PathBidir shortest;
    protected EdgeWrapper wrapperOther;
    private MyBitSet visitedFrom;
    private IntDoubleBinHeap openSetFrom;
    private EdgeWrapper wrapperFrom;
    private MyBitSet visitedTo;
    private IntDoubleBinHeap openSetTo;
    private EdgeWrapper wrapperTo;
    private boolean alreadyRun;

    public DijkstraBidirection(Graph graph) {
        super(graph);
        int locs = Math.max(20, graph.getNodes());
        visitedFrom = new MyBitSetImpl(locs);
        openSetFrom = new IntDoubleBinHeap(locs / 10);
        wrapperFrom = new EdgeWrapper(locs / 10);

        visitedTo = new MyBitSetImpl(locs);
        openSetTo = new IntDoubleBinHeap(locs / 10);
        wrapperTo = new EdgeWrapper(locs / 10);
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
        currFromRef = wrapperFrom.add(from, 0, -1);
        return this;
    }

    public DijkstraBidirection initTo(int to) {
        this.to = to;
        currTo = to;
        currToWeight = 0;
        currToRef = wrapperTo.add(to, 0, -1);
        return this;
    }

    @Override public Path calcPath(int from, int to) {
        if (alreadyRun)
            throw new IllegalStateException("Call clear before! But this class is not thread safe!");

        alreadyRun = true;
        initPath();
        initFrom(from);
        initTo(to);

        Path p = checkIndenticalFromAndTo();
        if (p != null)
            return p;

        int finish = 0;
        while (finish < 2) {
            finish = 0;
            if (!fillEdgesFrom())
                finish++;

            if (!fillEdgesTo())
                finish++;
        }

        return shortest.extract();
    }

    public void initPath() {
        shortest = new PathBidir(graph, wrapperFrom, wrapperTo, weightCalc);
        shortest.initWeight();
    }

    // http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
    // a node from overlap may not be on the shortest path!!
    // => when scanning an arc (v, w) in the forward search and w is scanned in the reverse 
    //    search, update shortest = μ if df (v) + (v, w) + dr (w) < μ            
    public boolean checkFinishCondition() {
        return currFromWeight + currToWeight >= shortest.weight;
    }

    public void fillEdges(int currNode, double currWeight, int currRef, MyBitSet visitedMain,
            IntDoubleBinHeap prioQueue, EdgeWrapper wrapper, boolean out) {

        EdgeIterator iter = GraphUtility.getEdges(graph, currNode, out);
        while (iter.next()) {
            int neighborNode = iter.node();
            if (visitedMain.contains(neighborNode))
                continue;

            double tmpWeight = weightCalc.getWeight(iter.distance(), iter.flags()) + currWeight;
            int newRef = wrapper.getRef(neighborNode);
            if (newRef < 0) {
                newRef = wrapper.add(neighborNode, tmpWeight, iter.edge());
                wrapper.putParent(newRef, currRef);
                prioQueue.insert_(tmpWeight, newRef);
            } else {
                double weight = wrapper.getWeight(newRef);
                if (weight > tmpWeight) {
                    wrapper.putEdgeId(newRef, iter.edge());
                    wrapper.putWeight(newRef, tmpWeight);
                    wrapper.putParent(newRef, currRef);
                    prioQueue.update_(tmpWeight, newRef);
                }
            }

            updateShortest(neighborNode, newRef, tmpWeight);
        }
    }

    public void updateShortest(int nodeId, int ref, double weight) {
        int otherRef = wrapperOther.getRef(nodeId);
        if (otherRef < 0)
            return;

        // update μ
        double newWeight = weight + wrapperOther.getWeight(otherRef);
        if (newWeight < shortest.weight) {
            shortest.switchWrapper = wrapperFrom == wrapperOther;
            shortest.fromRef = ref;
            shortest.toRef = otherRef;
            shortest.weight = newWeight;
        }
    }

    public boolean fillEdgesFrom() {
        wrapperOther = wrapperTo;
        fillEdges(currFrom, currFromWeight, currFromRef, visitedFrom, openSetFrom, wrapperFrom, true);
        if (openSetFrom.isEmpty())
            return false;

        currFromRef = openSetFrom.poll_element();
        currFrom = wrapperFrom.getNode(currFromRef);
        currFromWeight = wrapperFrom.getWeight(currFromRef);
        if (checkFinishCondition())
            return false;
        visitedFrom.add(currFrom);
        return true;
    }

    public boolean fillEdgesTo() {
        wrapperOther = wrapperFrom;
        fillEdges(currTo, currToWeight, currToRef, visitedTo, openSetTo, wrapperTo, false);
        if (openSetTo.isEmpty())
            return false;

        currToRef = openSetTo.poll_element();
        currTo = wrapperTo.getNode(currToRef);
        currToWeight = wrapperTo.getWeight(currToRef);
        if (checkFinishCondition())
            return false;
        visitedTo.add(currTo);
        return true;
    }

    public int getVisited() {
        return visitedFrom.getCardinality() + visitedTo.getCardinality();
    }

    private Path checkIndenticalFromAndTo() {
        if (from == to) {
            Path p = new Path(graph, weightCalc);
            p.addFrom(from);
            return p;
        }
        return null;
    }
}
