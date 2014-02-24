/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
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
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeWrapper;

/**
 * Calculates shortest path in bidirectional way. Compared to DijkstraBidirectionRef this class is
 * more memory efficient as it does not go the normal Java way via references. In first tests this
 * class saves 30% memory, but as you can see it is more complicated.
 * <p/>
 * Possible improvements
 * <p>
 * 1. use only one EdgeWrapper to save memory. This is not easy if we want it to be as fast as the
 * current solution. But we need to try it out if a forwardSearchBitset.contains(ref) is that
 * expensive
 * <p/>
 * 2. instead of creating references point to the edges itself => we only need an edge+node array
 * and from that can retrieve eg. the distance
 * <p/>
 * @author Peter Karich
 */
public class DijkstraBidirection extends AbstractBidirAlgo
{
    private int currFrom;
    private double currFromWeight;
    private int currFromRef;
    private int currTo;
    private double currToWeight;
    private int currToRef;
    private EdgeWrapper parentRefOther;
    private IntDoubleBinHeap openSetFrom;
    private EdgeWrapper parentRefFrom;
    private IntDoubleBinHeap openSetTo;
    private EdgeWrapper parentRefTo;
    private PathBidir nativeBestPath;

    public DijkstraBidirection( Graph graph, FlagEncoder encoder, Weighting weighting )
    {
        super(graph, encoder, weighting);
        initCollections(1000);
    }

    protected void initCollections( int locs )
    {
        openSetFrom = new IntDoubleBinHeap(locs);
        parentRefFrom = new EdgeWrapper(locs);

        openSetTo = new IntDoubleBinHeap(locs);
        parentRefTo = new EdgeWrapper(locs);
    }

    @Override
    public void initFrom( int from, double dist )
    {
        currFrom = from;
        currFromWeight = dist;
        currFromRef = parentRefFrom.add(from, dist, EdgeIterator.NO_EDGE);
        openSetFrom.insert_(currFromWeight, currFromRef);
        if (currTo >= 0)
        {
            parentRefOther = parentRefTo;
            updateShortest(currFrom, currFromRef, currToWeight);
        }
    }

    @Override
    public void initTo( int to, double dist )
    {
        currTo = to;
        currToWeight = dist;
        currToRef = parentRefTo.add(to, dist, EdgeIterator.NO_EDGE);
        openSetTo.insert_(currToWeight, currToRef);
        if (currFrom >= 0)
        {
            parentRefOther = parentRefFrom;
            updateShortest(currTo, currToRef, currFromWeight);
        }
    }

    @Override
    protected void initPath()
    {
        nativeBestPath = new PathBidir(graph, flagEncoder, parentRefFrom, parentRefTo);
    }

    @Override
    public Path extractPath()
    {
        return nativeBestPath.extract();
    }

    @Override
    void checkState( int fromBase, int fromAdj, int toBase, int toAdj )
    {
        if (parentRefFrom.isEmpty() || parentRefTo.isEmpty())
            throw new IllegalStateException("Either 'from'-edge or 'to'-edge is inaccessible. From:" + fromBase + ", to:" + toBase);
    }

    // http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
    // a node from overlap may not be on the shortest path!!
    // => when scanning an arc (v, w) in the forward search and w is scanned in the reverseOrder 
    //    search, update shortest = μ if df (v) + (v, w) + dr (w) < μ            
    @Override
    protected boolean finished()
    {
        if (finishedFrom || finishedTo)
            return true;

        return currFromWeight + currToWeight >= nativeBestPath.getWeight();
    }

    void fillEdges( int currNode, double currWeight, int currRef,
            IntDoubleBinHeap openSet, EdgeWrapper wrapper, EdgeExplorer explorer, boolean reverse)
    {
        EdgeIterator iter = explorer.setBaseNode(currNode);
        while (iter.next())
        {
            if (!accept(iter))
                continue;

            int neighborNode = iter.getAdjNode();
            // minor speed up
            int newRef = wrapper.getRef(neighborNode);
            if (newRef >= 0 && wrapper.getEdgeId(newRef) == iter.getEdge())
                continue;

            double tmpWeight = weighting.calcWeight(iter, reverse) + currWeight;
            if (newRef < 0)
            {
                newRef = wrapper.add(neighborNode, tmpWeight, iter.getEdge());
                wrapper.putParent(newRef, currRef);
                openSet.insert_(tmpWeight, newRef);
            } else
            {
                double weight = wrapper.getWeight(newRef);
                if (weight > tmpWeight)
                {
                    wrapper.putEdgeId(newRef, iter.getEdge());
                    wrapper.putWeight(newRef, tmpWeight);
                    wrapper.putParent(newRef, currRef);
                    openSet.update_(tmpWeight, newRef);
                }
            }

            updateShortest(neighborNode, newRef, tmpWeight);
        }
    }

    void updateShortest( int nodeId, int ref, double weight )
    {
        int otherRef = parentRefOther.getRef(nodeId);
        if (otherRef < 0)
            return;

        // update μ
        double newWeight = weight + parentRefOther.getWeight(otherRef);
        if (newWeight < nativeBestPath.getWeight())
        {
            nativeBestPath.switchWrapper = parentRefFrom == parentRefOther;
            nativeBestPath.fromRef = ref;
            nativeBestPath.toRef = otherRef;
            nativeBestPath.setWeight(newWeight);
        }
    }

    @Override
    boolean fillEdgesFrom()
    {
        if (openSetFrom.isEmpty())
            return false;

        currFromRef = openSetFrom.poll_element();
        currFrom = parentRefFrom.getNode(currFromRef);
        currFromWeight = parentRefFrom.getWeight(currFromRef);

        parentRefOther = parentRefTo;
        fillEdges(currFrom, currFromWeight, currFromRef, openSetFrom, parentRefFrom, outEdgeExplorer, false);
        visitedFromCount++;
        return true;
    }

    @Override
    boolean fillEdgesTo()
    {
        if (openSetTo.isEmpty())
            return false;
        currToRef = openSetTo.poll_element();
        currTo = parentRefTo.getNode(currToRef);
        currToWeight = parentRefTo.getWeight(currToRef);

        parentRefOther = parentRefFrom;
        fillEdges(currTo, currToWeight, currToRef, openSetTo, parentRefTo, inEdgeExplorer, true);
        visitedToCount++;
        return true;
    }

    @Override
    public String getName()
    {
        return "dijkstraNativebi";
    }
}
