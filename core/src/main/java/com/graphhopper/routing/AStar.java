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

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.PriorityQueue;

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
    private DistanceCalc dist;
    private int visitedCount;
    private TIntObjectMap<AStarEdge> fromMap;
    private PriorityQueue<AStarEdge> prioQueueOpenSet;
    private AStarEdge currEdge;
    private int to1 = -1;
    private double toLat;
    private double toLon;

    public AStar( Graph g, FlagEncoder encoder, Weighting weighting )
    {
        super(g, encoder, weighting);
        initCollections(1000);
        setApproximation(true);
    }

    /**
     * @param approx if true it enables an approximative distance calculation from lat,lon values
     */
    public AStar setApproximation( boolean approx )
    {
        if (approx)
            dist = new DistancePlaneProjection();
        else
            dist = new DistanceCalcEarth();

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
        toLat = graph.getLatitude(to);
        toLon = graph.getLongitude(to);
        to1 = to;
        currEdge = createEdgeEntry(from, 0);
        fromMap.put(from, currEdge);
        return runAlgo();
    }

    private Path runAlgo()
    {
        double currWeightToGoal, distEstimation, tmpLat, tmpLon;
        EdgeExplorer explorer = outEdgeExplorer;
        while (true)
        {
            int currVertex = currEdge.adjNode;
            visitedCount++;
            if (finished())
                break;

            EdgeIterator iter = explorer.setBaseNode(currVertex);
            while (iter.next())
            {
                if (!accept(iter))
                    continue;
                if (currEdge.edge == iter.getEdge())
                    continue;

                int neighborNode = iter.getAdjNode();
                double alreadyVisitedWeight = weighting.calcWeight(iter, false) + currEdge.weightToCompare;
                AStarEdge nEdge = fromMap.get(neighborNode);
                if (nEdge == null || nEdge.weightToCompare > alreadyVisitedWeight)
                {
                    tmpLat = graph.getLatitude(neighborNode);
                    tmpLon = graph.getLongitude(neighborNode);
                    currWeightToGoal = dist.calcDist(toLat, toLon, tmpLat, tmpLon);
                    currWeightToGoal = weighting.getMinWeight(currWeightToGoal);
                    distEstimation = alreadyVisitedWeight + currWeightToGoal;
                    if (nEdge == null)
                    {
                        nEdge = new AStarEdge(iter.getEdge(), neighborNode, distEstimation, alreadyVisitedWeight);
                        fromMap.put(neighborNode, nEdge);
                    } else
                    {
                        prioQueueOpenSet.remove(nEdge);
                        nEdge.edge = iter.getEdge();
                        nEdge.weight = distEstimation;
                        nEdge.weightToCompare = alreadyVisitedWeight;
                    }
                    nEdge.parent = currEdge;
                    prioQueueOpenSet.add(nEdge);
                    updateShortest(nEdge, neighborNode);
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

    public static class AStarEdge extends EdgeEntry
    {
        // the variable 'weight' is used to let heap select smallest *full* distance.
        // but to compare distance we need it only from start:
        double weightToCompare;

        public AStarEdge( int edgeId, int node, double weightForHeap, double weightToCompare )
        {
            super(edgeId, node, weightForHeap);
            // round makes distance smaller => heuristic should underestimate the distance!
            this.weightToCompare = (float) weightToCompare;
        }
    }

    @Override
    public String getName()
    {
        return "astar";
    }
}
