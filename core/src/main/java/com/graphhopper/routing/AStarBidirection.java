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

import com.graphhopper.routing.util.*;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.PriorityQueue;

import com.graphhopper.routing.AStar.AStarEdge;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;

/**
 * This class implements a bidirectional A* algorithm. It is interesting to note that a
 * bidirectional dijkstra is far more efficient than a single direction one. The same does not hold
 * for a bidirectional A* as the heuristic can not be as tight.
 * <p/>
 * See http://research.microsoft.com/apps/pubs/default.aspx?id=64511
 * http://i11www.iti.uni-karlsruhe.de/_media/teaching/sommer2012/routenplanung/vorlesung4.pdf
 * http://research.microsoft.com/pubs/64504/goldberg-sofsem07.pdf
 * http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
 * <p/>
 * and
 * <p/>
 * 1. Ikeda, T., Hsu, M.-Y., Imai, H., Nishimura, S., Shimoura, H., Hashimoto, T., Tenmoku, K., and
 * Mitoh, K. (1994). A fast algorithm for finding better routes by ai search techniques. In VNIS,
 * pages 291–296.
 * <p/>
 * 2. Whangbo, T. K. (2007). Efficient modified bidirectional a* algorithm for optimal route-
 * finding. In IEA/AIE, volume 4570, pages 344–353. Springer.
 * <p/>
 * or could we even use this three phase approach?
 * www.lix.polytechnique.fr/~giacomon/papers/bidirtimedep.pdf
 * <p/>
 * @author Peter Karich
 * @author jansoe
 */
public class AStarBidirection extends AbstractBidirAlgo
{
    private ConsistentWeightApproximator weightApprox;
    private PriorityQueue<AStarEdge> prioQueueOpenSetFrom;
    private TIntObjectMap<AStarEdge> bestWeightMapFrom;
    private PriorityQueue<AStarEdge> prioQueueOpenSetTo;
    private TIntObjectMap<AStarEdge> bestWeightMapTo;
    private TIntObjectMap<AStarEdge> bestWeightMapOther;
    protected AStarEdge currFrom;
    protected AStarEdge currTo;
    protected PathBidirRef bestPath;

    public AStarBidirection( Graph graph, FlagEncoder encoder, Weighting weighting, TraversalMode tMode )
    {
        super(graph, encoder, weighting, tMode);
        int nodes = Math.max(20, graph.getNodes());
        initCollections(nodes);
        BeelineWeightApproximator defaultApprox = new BeelineWeightApproximator(nodeAccess, weighting);
        defaultApprox.setDistanceCalc(new DistancePlaneProjection());
        setApproximation(defaultApprox);
    }

    protected void initCollections( int size )
    {
        prioQueueOpenSetFrom = new PriorityQueue<AStarEdge>(size / 10);
        bestWeightMapFrom = new TIntObjectHashMap<AStarEdge>(size / 10);

        prioQueueOpenSetTo = new PriorityQueue<AStarEdge>(size / 10);
        bestWeightMapTo = new TIntObjectHashMap<AStarEdge>(size / 10);
    }

    /**
     * @param approx if true it enables approximative distance calculation from lat,lon values
     */
    public AStarBidirection setApproximation( WeightApproximator approx )
    {
        weightApprox = new ConsistentWeightApproximator(approx);
        return this;
    }

    @Override
    protected AStarEdge createEdgeEntry( int node, double dist )
    {
        return new AStarEdge(EdgeIterator.NO_EDGE, node, dist, dist);
    }

    @Override
    public void initFrom( int from, double dist )
    {
        currFrom = createEdgeEntry(from, dist);
        weightApprox.setSourceNode(from);
        prioQueueOpenSetFrom.add(currFrom);
        if (!traversalMode.isEdgeBased())
        {
            bestWeightMapFrom.put(from, currFrom);
            if (currTo != null)
            {
                bestWeightMapOther = bestWeightMapTo;
                updateBestPath(GHUtility.getEdge(graph, from, currTo.adjNode), currTo, from);
            }
        } else
        {
            if (currTo != null && currTo.adjNode == from)
            {
                // special case of identical start and end
                bestPath.edgeEntry = currFrom;
                bestPath.edgeTo = currTo;
                finishedFrom = true;
                finishedTo = true;
            }
        }
    }

    @Override
    public void initTo( int to, double dist )
    {
        currTo = createEdgeEntry(to, dist);
        weightApprox.setGoalNode(to);
        prioQueueOpenSetTo.add(currTo);
        if (!traversalMode.isEdgeBased())
        {
            bestWeightMapTo.put(to, currTo);
            if (currFrom != null)
            {
                bestWeightMapOther = bestWeightMapFrom;
                updateBestPath(GHUtility.getEdge(graph, currFrom.adjNode, to), currFrom, to);
            }
        } else
        {
            if (currFrom != null && currFrom.adjNode == to)
            {
                // special case of identical start and end
                bestPath.edgeEntry = currFrom;
                bestPath.edgeTo = currTo;
                finishedFrom = true;
                finishedTo = true;
            }
        }
    }

    @Override
    protected Path createAndInitPath()
    {
        bestPath = new PathBidirRef(graph, flagEncoder);
        return bestPath;
    }

    @Override
    protected Path extractPath()
    {
        if (finished())
            return bestPath.extract();

        return bestPath;
    }

    @Override
    void checkState( int fromBase, int fromAdj, int toBase, int toAdj )
    {
        if (bestWeightMapFrom.isEmpty() || bestWeightMapTo.isEmpty())
            throw new IllegalStateException("Either 'from'-edge or 'to'-edge is inaccessible. From:" + bestWeightMapFrom + ", to:" + bestWeightMapTo);
    }

    @Override
    protected boolean finished()
    {
        if (finishedFrom || finishedTo)
            return true;

        return currFrom.weight + currTo.weight >= bestPath.getWeight();
    }

    @Override
    protected boolean isWeightLimitExceeded()
    {
        return currFrom.weight + currTo.weight > weightLimit;
    }

    @Override
    boolean fillEdgesFrom()
    {
        if (prioQueueOpenSetFrom.isEmpty())
            return false;

        currFrom = prioQueueOpenSetFrom.poll();
        bestWeightMapOther = bestWeightMapTo;
        fillEdges(currFrom, prioQueueOpenSetFrom, bestWeightMapFrom, outEdgeExplorer, false);
        visitedCountFrom++;
        return true;
    }

    @Override
    boolean fillEdgesTo()
    {
        if (prioQueueOpenSetTo.isEmpty())
            return false;

        currTo = prioQueueOpenSetTo.poll();
        bestWeightMapOther = bestWeightMapFrom;
        fillEdges(currTo, prioQueueOpenSetTo, bestWeightMapTo, inEdgeExplorer, true);
        visitedCountTo++;
        return true;
    }

    private void fillEdges( AStarEdge currEdge, PriorityQueue<AStarEdge> prioQueueOpenSet,
            TIntObjectMap<AStarEdge> shortestWeightMap, EdgeExplorer explorer, boolean reverse )
    {

        int currNode = currEdge.adjNode;
        EdgeIterator iter = explorer.setBaseNode(currNode);
        while (iter.next())
        {
            if (!accept(iter, currEdge.edge))
                continue;

            int neighborNode = iter.getAdjNode();
            int traversalId = traversalMode.createTraversalId(iter, reverse);
            // TODO performance: check if the node is already existent in the opposite direction
            // then we could avoid the approximation as we already know the exact complete path!
            float alreadyVisitedWeight = (float) (weighting.calcWeight(iter, reverse, currEdge.edge)
                    + currEdge.weightOfVisitedPath);
            if (Double.isInfinite(alreadyVisitedWeight))
                continue;

            AStarEdge ase = shortestWeightMap.get(traversalId);
            if (ase == null || ase.weightOfVisitedPath > alreadyVisitedWeight)
            {
                double currWeightToGoal = weightApprox.approximate(neighborNode, reverse);
                double estimationFullDist = alreadyVisitedWeight + currWeightToGoal;
                if (ase == null)
                {
                    ase = new AStarEdge(iter.getEdge(), neighborNode, estimationFullDist, alreadyVisitedWeight);
                    shortestWeightMap.put(traversalId, ase);
                } else
                {
                    assert (ase.weight > estimationFullDist) : "Inconsistent distance estimate";
                    prioQueueOpenSet.remove(ase);
                    ase.edge = iter.getEdge();
                    ase.weight = estimationFullDist;
                    ase.weightOfVisitedPath = alreadyVisitedWeight;
                }

                ase.parent = currEdge;
                prioQueueOpenSet.add(ase);
                updateBestPath(iter, ase, traversalId);
            }
        }
    }

//    @Override -> TODO use only weight => then a simple EdgeEntry is possible
    public void updateBestPath( EdgeIteratorState edgeState, AStarEdge entryCurrent, int currLoc )
    {
        AStarEdge entryOther = bestWeightMapOther.get(currLoc);
        if (entryOther == null)
            return;

        boolean reverse = bestWeightMapFrom == bestWeightMapOther;
        // update μ
        double newWeight = entryCurrent.weightOfVisitedPath + entryOther.weightOfVisitedPath;
        if (traversalMode.isEdgeBased())
        {
            if (entryOther.edge != entryCurrent.edge)
                throw new IllegalStateException("cannot happen for edge based execution of " + getName());

            // see DijkstraBidirectionRef
            if (entryOther.adjNode != entryCurrent.adjNode)
            {
                entryCurrent = (AStar.AStarEdge) entryCurrent.parent;
                newWeight -= weighting.calcWeight(edgeState, reverse, EdgeIterator.NO_EDGE);
            } else
            {
                // we detected a u-turn at meeting point, skip if not supported
                if (!traversalMode.hasUTurnSupport())
                    return;
            }
        }

        if (newWeight < bestPath.getWeight())
        {
            bestPath.setSwitchToFrom(reverse);
            bestPath.edgeEntry = entryCurrent;
            bestPath.edgeTo = entryOther;
            bestPath.setWeight(newWeight);
        }
    }

    @Override
    public String getName()
    {
        return AlgorithmOptions.ASTAR_BI;
    }
}
