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

import com.graphhopper.util.DistancePlaneProjection;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.PriorityQueue;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.routing.util.WeightApproximator;
import com.graphhopper.routing.util.BeelineWeightApproximator;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

/**
 * This class implements the A* algorithm according to
 * http://en.wikipedia.org/wiki/A*_search_algorithm
 * <p/>
 * Different distance calculations can be used via setApproximation.
 * <p/>
 * @author Peter Karich
 */
public class AStar extends AbstractRoutingAlgorithm
{
    private WeightApproximator weightApprox;
    private int visitedCount;
    private TIntObjectMap<AStarEdge> fromMap;
    private PriorityQueue<AStarEdge> prioQueueOpenSet;
    private AStarEdge currEdge;
    private int to1 = -1;

    public AStar( Graph g, FlagEncoder encoder, Weighting weighting, TraversalMode tMode )
    {
        super(g, encoder, weighting, tMode);
        initCollections(1000);
        BeelineWeightApproximator defaultApprox = new BeelineWeightApproximator(nodeAccess, weighting);
        defaultApprox.setDistanceCalc(new DistancePlaneProjection());
        setApproximation(defaultApprox);
    }

    /**
     * @param approx defines how distance to goal Node is approximated
     */
    public AStar setApproximation( WeightApproximator approx )
    {
        weightApprox = approx;
        return this;
    }

    protected void initCollections( int size )
    {
        fromMap = new TIntObjectHashMap<AStarEdge>();
        prioQueueOpenSet = new PriorityQueue<AStarEdge>(size);
    }

    @Override
    public Path calcPath( int from, int to )
    {
        checkAlreadyRun();
        to1 = to;
        weightApprox.setGoalNode(to);
        currEdge = createEdgeEntry(from, 0);
        if (!traversalMode.isEdgeBased())
        {
            fromMap.put(from, currEdge);
        }
        return runAlgo();
    }

    private Path runAlgo()
    {
        double currWeightToGoal, distEstimation;
        EdgeExplorer explorer = outEdgeExplorer;
        while (true)
        {
            int currVertex = currEdge.adjNode;
            visitedCount++;
            if (isWeightLimitExceeded())
                return createEmptyPath();

            if (finished())
                break;

            EdgeIterator iter = explorer.setBaseNode(currVertex);
            while (iter.next())
            {
                if (!accept(iter, currEdge.edge))
                    continue;

                int neighborNode = iter.getAdjNode();
                int traversalId = traversalMode.createTraversalId(iter, false);
                // cast to float to avoid rounding errors in comparison to float entry of AStarEdge weight
                float alreadyVisitedWeight = (float) (weighting.calcWeight(iter, false, currEdge.edge)
                        + currEdge.weightOfVisitedPath);
                if (Double.isInfinite(alreadyVisitedWeight))
                    continue;

                AStarEdge ase = fromMap.get(traversalId);
                if ((ase == null) || ase.weightOfVisitedPath > alreadyVisitedWeight)
                {
                    currWeightToGoal = weightApprox.approximate(neighborNode);
                    distEstimation = alreadyVisitedWeight + currWeightToGoal;
                    if (ase == null)
                    {
                        ase = new AStarEdge(iter.getEdge(), neighborNode, distEstimation, alreadyVisitedWeight);
                        fromMap.put(traversalId, ase);
                    } else
                    {
                        assert (ase.weight > distEstimation) : "Inconsistent distance estimate";
                        prioQueueOpenSet.remove(ase);
                        ase.edge = iter.getEdge();
                        ase.weight = distEstimation;
                        ase.weightOfVisitedPath = alreadyVisitedWeight;
                    }

                    ase.parent = currEdge;
                    prioQueueOpenSet.add(ase);

                    updateBestPath(iter, ase, traversalId);
                }
            }

            if (prioQueueOpenSet.isEmpty())
                return createEmptyPath();

            currEdge = prioQueueOpenSet.poll();
            if (currEdge == null)
                throw new AssertionError("Empty edge cannot happen");
        }

        return extractPath();
    }

    @Override
    protected Path extractPath()
    {
        return new Path(graph, flagEncoder).setWeight(currEdge.weight).setEdgeEntry(currEdge).extract();
    }

    @Override
    protected AStarEdge createEdgeEntry( int node, double dist )
    {
        return new AStarEdge(EdgeIterator.NO_EDGE, node, dist, dist);
    }

    @Override
    protected boolean finished()
    {
        return currEdge.adjNode == to1;
    }

    @Override
    public int getVisitedNodes()
    {
        return visitedCount;
    }

    @Override
    protected boolean isWeightLimitExceeded()
    {
        return currEdge.weight > weightLimit;
    }

    public static class AStarEdge extends EdgeEntry
    {
        // the variable 'weight' is used to let heap select smallest *full* distance.
        // but to compare distance we need it only from start:
        double weightOfVisitedPath;

        public AStarEdge( int edgeId, int adjNode, double weightForHeap, double weightOfVisitedPath )
        {
            super(edgeId, adjNode, weightForHeap);
            this.weightOfVisitedPath = (float) weightOfVisitedPath;
        }
    }

    @Override
    public String getName()
    {
        return AlgorithmOptions.ASTAR;
    }
}
