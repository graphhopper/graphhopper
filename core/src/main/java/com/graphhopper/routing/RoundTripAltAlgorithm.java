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

import com.graphhopper.routing.util.FastestWeighting;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import gnu.trove.set.hash.TIntHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class implements the round trip calculation via calculating the optimal path between the
 * origin and the best point on the "outside Dijkstra border" and uses an alternative path algorithm
 * as backward path.
 * <p>
 * @author Peter Karich
 */
public class RoundTripAltAlgorithm implements RoutingAlgorithm
{
    private final Graph graph;
    private final FlagEncoder flagEncoder;
    private final Weighting weighting;
    private final TraversalMode traversalMode;
    private int visitedNodes;
    private int maxVisitedNodes = Integer.MAX_VALUE;
    private double maxWeightFactor = 2;

    public RoundTripAltAlgorithm( Graph graph, FlagEncoder flagEncoder, Weighting weighting, TraversalMode traversalMode )
    {
        this.graph = graph;
        this.flagEncoder = flagEncoder;
        this.weighting = weighting;

        this.traversalMode = traversalMode;
        if (this.traversalMode != TraversalMode.NODE_BASED)
            throw new IllegalArgumentException("Only node based traversal currently supported for round trip calculation");
    }

    public void setMaxWeightFactor( double maxWeightFactor )
    {
        this.maxWeightFactor = maxWeightFactor;
    }

    /**
     * @param from the node where the round trip should start and end
     * @param maxFullDistance the maximum distance for the whole round trip
     * @return currently no path at all or two paths (one forward and one backward path)
     */
    public List<Path> calcRoundTrips( int from, double maxFullDistance, final double penaltyFactor )
    {
        AltSingleDijkstra altDijkstra = new AltSingleDijkstra(graph, flagEncoder, weighting, traversalMode);
        altDijkstra.setMaxVisitedNodes(maxVisitedNodes);
        altDijkstra.beforeRun(from);
        SPTEntry currFrom = altDijkstra.searchBest(from, maxFullDistance);
        visitedNodes = altDijkstra.getVisitedNodes();
        if (currFrom == null)
            return Collections.emptyList();

        // Assume that the first node breaking through the maxWeight circle is the best connected leading hopefully to good alternatives
        // TODO select more than one 'to'-node?
        int to = currFrom.adjNode;

        // TODO do not extract yet, use the plateau start of the alternative as new 'to', then extract        
        final TIntHashSet forwardEdgeSet = new TIntHashSet();

        // best path FOR FORWARD direction which we need in all cases
        Path bestForwardPath = new Path(graph, flagEncoder)
        {
            @Override
            protected void processEdge( int edgeId, int adjNode )
            {
                super.processEdge(edgeId, adjNode);
                forwardEdgeSet.add(edgeId);
            }
        };

        bestForwardPath.setSPTEntry(currFrom);
        bestForwardPath.setWeight(currFrom.weight);
        bestForwardPath.extract();
        if (forwardEdgeSet.isEmpty())
            return Collections.emptyList();

        List<Path> paths = new ArrayList<Path>();
        // simple penalty method        
        Weighting altWeighting = new FastestWeighting(flagEncoder)
        {
            @Override
            public double calcWeight( EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId )
            {
                double factor = 1;
                if (forwardEdgeSet.contains(edge.getEdge()))
                    factor = penaltyFactor;
                return factor * weighting.calcWeight(edge, reverse, prevOrNextEdgeId);
            }
        };
        AlternativeRoute.AlternativeBidirSearch altBidirDijktra = new AlternativeRoute.AlternativeBidirSearch(graph, flagEncoder,
                altWeighting, traversalMode, 1);
        altBidirDijktra.setMaxVisitedNodes(maxVisitedNodes);
        // find an alternative for backward direction starting from 'to'
        Path bestBackwardPath = altBidirDijktra.searchBest(to, from);

        // path not found -> TODO try with another 'to' point
        if (Double.isInfinite(bestBackwardPath.getWeight()))
            return Collections.emptyList();

        // less weight influence, stronger share avoiding than normal alternative search to increase area between best&alternative
        double weightInfluence = 0.05, maxShareFactor = 0.05, shareInfluence = 2 /*use penaltyFactor?*/,
                minPlateauFactor = 0.1, plateauInfluence = 0.1;
        List<AlternativeRoute.AlternativeInfo> infos = altBidirDijktra.calcAlternatives(2,
                penaltyFactor * maxWeightFactor, weightInfluence,
                maxShareFactor, shareInfluence,
                minPlateauFactor, plateauInfluence);

        visitedNodes += altBidirDijktra.getVisitedNodes();
        if (infos.isEmpty())
            return Collections.emptyList();

        if (infos.size() == 1)
        {
            // fallback to same path for backward direction (or at least VERY similar path as optimal)
            paths.add(bestForwardPath);
            paths.add(infos.get(0).getPath());
        } else
        {
            AlternativeRoute.AlternativeInfo secondBest = null;
            for (AlternativeRoute.AlternativeInfo i : infos)
            {
                if (1 - i.getShareWeight() / i.getPath().getWeight() > 1e-8)
                {
                    secondBest = i;
                    break;
                }
            }
            if (secondBest == null)
                throw new RuntimeException("no second best found. " + infos);

            // correction: remove end standing path
            SPTEntry newTo = secondBest.getShareStart();
            if (newTo.parent != null)
            {
                // in case edge was found in forwardEdgeSet we calculate the first sharing end
                int tKey = traversalMode.createTraversalId(newTo.adjNode, newTo.parent.adjNode, newTo.edge, false);

                // do new extract
                SPTEntry tmpFromSPTEntry = altDijkstra.getFromEntry(tKey);

                // if (tmpFromSPTEntry.parent != null) tmpFromSPTEntry = tmpFromSPTEntry.parent;
                bestForwardPath = new Path(graph, flagEncoder).setSPTEntry(tmpFromSPTEntry).setWeight(tmpFromSPTEntry.weight).extract();

                newTo = newTo.parent;
                // force new 'to'
                newTo.edge = EdgeIterator.NO_EDGE;
                secondBest.getPath().setWeight(secondBest.getPath().getWeight() - newTo.weight).extract();
            }

            paths.add(bestForwardPath);
            paths.add(secondBest.getPath());
        }
        return paths;
    }

    @Override
    public Path calcPath( int from, int to )
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public List<Path> calcPaths( int from, int to )
    {
        // TODO use to-point to indicate direction too, not only distance
        double fromLat = graph.getNodeAccess().getLat(from), fromLon = graph.getNodeAccess().getLon(from);
        double toLat = graph.getNodeAccess().getLat(to), toLon = graph.getNodeAccess().getLon(to);

        double maxDist = Helper.DIST_EARTH.calcDist(fromLat, fromLon, toLat, toLon) * 2;
        double penaltyFactor = 2;
        return calcRoundTrips(from, maxDist, penaltyFactor);
    }

    @Override
    public void setMaxVisitedNodes( int numberOfNodes )
    {
        this.maxVisitedNodes = numberOfNodes;
    }

    @Override
    public String getName()
    {
        return AlgorithmOptions.ROUND_TRIP_ALT;
    }

    @Override
    public int getVisitedNodes()
    {
        return visitedNodes;
    }

    /**
     * Helper class for one to many dijkstra
     */
    static class AltSingleDijkstra extends DijkstraBidirectionRef
    {
        public AltSingleDijkstra( Graph g, FlagEncoder encoder, Weighting weighting, TraversalMode tMode )
        {
            super(g, encoder, weighting, tMode);
        }

        void beforeRun( int from )
        {
            checkAlreadyRun();
            createAndInitPath();
            initFrom(from, 0);
        }

        public SPTEntry getFromEntry( int key )
        {
            return bestWeightMapFrom.get(key);
        }

        SPTEntry searchBest( int from, double maxFullDistance )
        {
            NodeAccess na = graph.getNodeAccess();
            DistanceCalc distanceCalc = Helper.DIST_PLANE;
            // get the 'to' via exploring the graph and select the node which reaches the maxWeight radius the fastest!
            // '/2' because we need just one direction
            double maxDistance = distanceCalc.calcNormalizedDist(maxFullDistance / 2);
            double lat1 = na.getLatitude(from), lon1 = na.getLongitude(from);
            double lastNormedDistance = -1;
            boolean tmpFinishedFrom = false;
            SPTEntry tmp = null;

            while (!tmpFinishedFrom)
            {
                tmpFinishedFrom = !fillEdgesFrom();

                // DO NOT use currFrom.adjNode and instead use parent as currFrom can contain 
                // a very big weight making it an unreasonable goal
                // (think about "avoid motorway" and see #419)
                tmp = currFrom.parent;
                if (tmp == null)
                    continue;

                double lat2 = na.getLatitude(tmp.adjNode), lon2 = na.getLongitude(tmp.adjNode);
                lastNormedDistance = distanceCalc.calcNormalizedDist(lat1, lon1, lat2, lon2);
                if (lastNormedDistance > maxDistance)
                    break;
            }

            // if no path found close to the maxWeight radius then do not return anything!
            if (tmpFinishedFrom
                    && lastNormedDistance > 0
                    && lastNormedDistance < distanceCalc.calcNormalizedDist(maxFullDistance / 2 / 4))
                return null;

            return tmp;
        }
    }
}
