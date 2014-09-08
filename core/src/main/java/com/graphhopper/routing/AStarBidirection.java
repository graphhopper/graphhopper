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

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.PriorityQueue;

import com.graphhopper.routing.AStar.AStarEdge;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;

/**
 * This class implements a bidirectional A* algorithm. It is interesting to note that a
 * bidirectional dijkstra is far more efficient than a single direction one. The same does not hold
 * for a bidirectional A* as the finish condition can not be so strict which leads to either
 * suboptimal paths or suboptimal node exploration (too many nodes). Still very good approximations
 * with a rougly twice times faster running time than the normal A* can be reached.
 * <p/>
 * Computing the Shortest Path: A∗ Search Meets Graph Theory ->
 * http://research.microsoft.com/apps/pubs/default.aspx?id=64511
 * http://i11www.iti.uni-karlsruhe.de/_media/teaching/sommer2012/routenplanung/vorlesung4.pdf
 * http://research.microsoft.com/pubs/64504/goldberg-sofsem07.pdf
 * http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
 * <p/>
 * better stop condition
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
 */
public class AStarBidirection extends AbstractBidirAlgo
{
    private DistanceCalc dist;
    private PriorityQueue<AStarEdge> prioQueueOpenSetFrom;
    private TIntObjectMap<AStarEdge> bestWeightMapFrom;
    private PriorityQueue<AStarEdge> prioQueueOpenSetTo;
    private TIntObjectMap<AStarEdge> bestWeightMapTo;
    private TIntObjectMap<AStarEdge> bestWeightMapOther;
    protected AStarEdge currFrom;
    protected AStarEdge currTo;
    protected double approximationFactor;
    private GHPoint fromCoord;
    private GHPoint toCoord;
    protected PathBidirRef bestPath;

    public AStarBidirection( Graph graph, FlagEncoder encoder, Weighting weighting, TraversalMode tMode )
    {
        super(graph, encoder, weighting, tMode);
        int nodes = Math.max(20, graph.getNodes());
        initCollections(nodes);

        // different default value for approximation than AStar
        setApproximation(false);
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
    public AStarBidirection setApproximation( boolean approx )
    {
        if (approx)
        {
            dist = new DistancePlaneProjection();
            approximationFactor = 0.5;
        } else
        {
            dist = new DistanceCalcEarth();
            approximationFactor = 1.2;
        }
        return this;
    }

    /**
     * Specify a low value like 0.5 for worse but faster results. Or over 1.1 for more precise.
     */
    public AStarBidirection setApproximationFactor( double approxFactor )
    {
        this.approximationFactor = approxFactor;
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
        fromCoord = new GHPoint(nodeAccess.getLatitude(from), nodeAccess.getLongitude(from));
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
                finishedFrom = true;
                finishedTo = true;
            }
        }
    }

    @Override
    public void initTo( int to, double dist )
    {
        currTo = createEdgeEntry(to, dist);
        toCoord = new GHPoint(nodeAccess.getLatitude(to), nodeAccess.getLongitude(to));
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
        return bestPath.extract();
    }

    @Override
    void checkState( int fromBase, int fromAdj, int toBase, int toAdj )
    {
        if (bestWeightMapFrom.isEmpty() || bestWeightMapTo.isEmpty())
            throw new IllegalStateException("Either 'from'-edge or 'to'-edge is inaccessible. From:" + bestWeightMapFrom + ", to:" + bestWeightMapTo);
    }

    // Problem is the correct finish condition! if the bounds are too wide too many nodes are visited :/   
    // d_f (v) + (v, w) + d_r (w) < μ + p_r(t)
    // where pi_r_of_t = p_r(t) = 1/2(pi_r(t) - pi_f(t) + pi_f(s)), and pi_f(t)=0
    @Override
    protected boolean finished()
    {
        if (finishedFrom || finishedTo)
            return true;

        double tmp = bestPath.getWeight() * approximationFactor;
        return currFrom.weightToCompare + currTo.weightToCompare >= tmp;
    }

    @Override
    boolean fillEdgesFrom()
    {
        if (prioQueueOpenSetFrom.isEmpty())
            return false;

        currFrom = prioQueueOpenSetFrom.poll();
        bestWeightMapOther = bestWeightMapTo;
        fillEdges(currFrom, toCoord, prioQueueOpenSetFrom, bestWeightMapFrom, outEdgeExplorer, false);
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
        fillEdges(currTo, fromCoord, prioQueueOpenSetTo, bestWeightMapTo, inEdgeExplorer, true);
        visitedCountTo++;
        return true;
    }

    private void fillEdges( AStarEdge currEdge, GHPoint goal,
            PriorityQueue<AStarEdge> prioQueueOpenSet,
            TIntObjectMap<AStarEdge> shortestWeightMap, EdgeExplorer explorer, boolean reverse )
    {

        int currNode = currEdge.adjNode;
        EdgeIterator iter = explorer.setBaseNode(currNode);
        while (iter.next())
        {
            if (!accept(iter, currEdge.edge))
                continue;

            int neighborNode = iter.getAdjNode();
            int iterationKey = traversalMode.createTraversalId(iter, reverse);
            // TODO performance: check if the node is already existent in the opposite direction
            // then we could avoid the approximation as we already know the exact complete path!
            double alreadyVisitedWeight = weighting.calcWeight(iter, reverse, currEdge.edge) + currEdge.weightToCompare;
            if (Double.isInfinite(alreadyVisitedWeight))
                    continue;
            
            AStarEdge aee = shortestWeightMap.get(iterationKey);
            if (aee == null || aee.weightToCompare > alreadyVisitedWeight)
            {
                double tmpLat = nodeAccess.getLatitude(neighborNode);
                double tmpLon = nodeAccess.getLongitude(neighborNode);
                double currWeightToGoal = dist.calcDist(goal.lat, goal.lon, tmpLat, tmpLon);
                currWeightToGoal = weighting.getMinWeight(currWeightToGoal);
                double estimationFullDist = alreadyVisitedWeight + currWeightToGoal;
                if (aee == null)
                {
                    aee = new AStarEdge(iter.getEdge(), neighborNode, estimationFullDist, alreadyVisitedWeight);
                    shortestWeightMap.put(iterationKey, aee);
                } else if (aee.weight > estimationFullDist)
                {
                    prioQueueOpenSet.remove(aee);
                    aee.edge = iter.getEdge();
                    aee.weight = estimationFullDist;
                    aee.weightToCompare = alreadyVisitedWeight;
                } else
                    continue;

                aee.parent = currEdge;
                prioQueueOpenSet.add(aee);
                updateBestPath(iter, aee, iterationKey);
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
        double newWeight = entryCurrent.weightToCompare + entryOther.weightToCompare;
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
        return "astarbi";
    }
}
