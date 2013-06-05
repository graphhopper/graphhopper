/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
package com.graphhopper.routing;

import com.graphhopper.coll.IntDoubleBinHeap;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeWrapper;

/**
 * Calculates shortest path in bidirectional way. Compared to
 * DijkstraBidirectionRef this class is more memory efficient as it does not go
 * the normal Java way via references. In first tests this class saves 30%
 * memory, but as you can see it is more complicated.
 *
 * TODO: use only one EdgeWrapper to save memory. This is not easy if we want it
 * to be as fast as the current solution. But we need to try it out if a
 * forwardSearchBitset.contains(ref) is that expensive
 *
 * TODO EdgeWrapper: instead of creating references point to the edges itself =>
 * we only need an edge+node array and from that can retrieve eg. the distance
 *
 * @author Peter Karich
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
    private IntDoubleBinHeap openSetFrom;
    private EdgeWrapper wrapperFrom;
    private int visitedFromCount;
    private IntDoubleBinHeap openSetTo;
    private EdgeWrapper wrapperTo;
    private int visitedToCount;
    private boolean alreadyRun;

    public DijkstraBidirection(Graph graph, FlagEncoder encoder) {
        super(graph, encoder);
        int locs = Math.max(20, graph.nodes());
        openSetFrom = new IntDoubleBinHeap(locs / 10);
        wrapperFrom = new EdgeWrapper(locs / 10);

        openSetTo = new IntDoubleBinHeap(locs / 10);
        wrapperTo = new EdgeWrapper(locs / 10);
    }

    DijkstraBidirection initFrom(int from) {
        this.from = from;
        currFrom = from;
        currFromWeight = 0;
        currFromRef = wrapperFrom.add(from, 0, EdgeIterator.NO_EDGE);
        return this;
    }

    DijkstraBidirection initTo(int to) {
        this.to = to;
        currTo = to;
        currToWeight = 0;
        currToRef = wrapperTo.add(to, 0, EdgeIterator.NO_EDGE);
        return this;
    }

    @Override public Path calcPath(int from, int to) {
        if (alreadyRun)
            throw new IllegalStateException("Create a new instance per call");
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

    void initPath() {
        shortest = new PathBidir(graph, flagEncoder, wrapperFrom, wrapperTo);
    }

    // http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
    // a node from overlap may not be on the shortest path!!
    // => when scanning an arc (v, w) in the forward search and w is scanned in the reverseOrder 
    //    search, update shortest = μ if df (v) + (v, w) + dr (w) < μ            
    boolean checkFinishCondition() {
        return currFromWeight + currToWeight >= shortest.weight();
    }

    void fillEdges(int currNode, double currWeight, int currRef,
            IntDoubleBinHeap prioQueue, EdgeWrapper wrapper, EdgeFilter filter) {

        EdgeIterator iter = graph.getEdges(currNode, filter);
        while (iter.next()) {
            if (!accept(iter))
                continue;
            int neighborNode = iter.adjNode();
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

    void updateShortest(int nodeId, int ref, double weight) {
        int otherRef = wrapperOther.getRef(nodeId);
        if (otherRef < 0)
            return;

        // update μ
        double newWeight = weight + wrapperOther.getWeight(otherRef);
        if (newWeight < shortest.weight()) {
            shortest.switchWrapper = wrapperFrom == wrapperOther;
            shortest.fromRef = ref;
            shortest.toRef = otherRef;
            shortest.weight(newWeight);
        }
    }

    boolean fillEdgesFrom() {
        wrapperOther = wrapperTo;
        fillEdges(currFrom, currFromWeight, currFromRef, openSetFrom, wrapperFrom, outEdgeFilter);
        visitedFromCount++;
        if (openSetFrom.isEmpty())
            return false;

        currFromRef = openSetFrom.poll_element();
        currFrom = wrapperFrom.getNode(currFromRef);
        currFromWeight = wrapperFrom.getWeight(currFromRef);
        if (checkFinishCondition())
            return false;
        return true;
    }

    boolean fillEdgesTo() {
        wrapperOther = wrapperFrom;
        fillEdges(currTo, currToWeight, currToRef, openSetTo, wrapperTo, inEdgeFilter);
        visitedToCount++;
        if (openSetTo.isEmpty())
            return false;

        currToRef = openSetTo.poll_element();
        currTo = wrapperTo.getNode(currToRef);
        currToWeight = wrapperTo.getWeight(currToRef);
        if (checkFinishCondition())
            return false;
        return true;
    }

    @Override
    public int visitedNodes() {
        return visitedFromCount + visitedToCount;
    }

    private Path checkIndenticalFromAndTo() {
        if (from == to)
            return new Path(graph, flagEncoder);
        return null;
    }

    @Override public String name() {
        return "dijkstraNativebi";
    }
}
