/*
 * Copyright 2015 Peter Karich.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.routing;

import com.graphhopper.routing.util.FastestWeighting;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import gnu.trove.procedure.TIntObjectProcedure;
import gnu.trove.set.hash.TIntHashSet;

/**
 * This class implements the alternative paths search using the "plateau" and partially the
 * "penalty" method discribed in the following papers.
 * <p/>
 * <ul>
 * <li>Choice Routing Explanation - Camvit
 * http://www.camvit.com/camvit-technical-english/Camvit-Choice-Routing-Explanation-english.pdf</li>
 * <li>and refined in: Alternative Routes in Road Networks
 * http://www.cs.princeton.edu/~rwerneck/papers/ADGW10-alternatives-sea.pdf</li>
 * <li>other ideas 'Improved Alternative Route Planning', 2013:
 * https://hal.inria.fr/hal-00871739/document</li>
 * <li>via point 'storage' idea 'Candidate Sets for Alternative Routes in Road Networks', 2013:
 * https://algo2.iti.kit.edu/download/s-csarrn-12.pdf</li>
 * </ul>
 * <p/>
 *
 * @author Peter Karich
 */
public class AlternativeRoute
{
    private final Graph graph;
    private final FlagEncoder flagEncoder;
    private final Weighting weighting;
    private final TraversalMode traversalMode;

    public AlternativeRoute( Graph graph, FlagEncoder flagEncoder, Weighting weighting, TraversalMode traversalMode )
    {
        this.graph = graph;
        this.flagEncoder = flagEncoder;
        this.weighting = weighting;

        // TODO edge based is not yet supported
        // this.traversalMode = traversalMode;
        this.traversalMode = TraversalMode.NODE_BASED;
    }

    /**
     * @param from the node where the round trip should start and end
     * @param maxFullDistance the maximum distance for the whole round trip
     * @param maxWeightFactor the weight until the search is expanded - used for alternative
     * calculation
     * @return currently no path at all or two paths (one forward and one backward path)
     */
    public List<Path> calcRoundTrips( int from, double maxFullDistance, double maxWeightFactor, final double penaltyFactor )
    {
        AltSingleDijkstra altDijkstra = new AltSingleDijkstra(graph, flagEncoder, weighting, traversalMode);
        altDijkstra.beforeRun(from);
        EdgeEntry currFrom = altDijkstra.searchBest(from, maxFullDistance);
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

        bestForwardPath.setEdgeEntry(currFrom);
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
        AltDijkstraBidirectionRef altBidirDijktra = new AltDijkstraBidirectionRef(graph, flagEncoder,
                altWeighting, traversalMode);
        // find an alternative for backward direction starting from 'to'
        Path bestBackwardPath = altBidirDijktra.searchBest(to, from);

        // path not found -> TODO try with another 'to' point
        if (Double.isInfinite(bestBackwardPath.getWeight()))
            return Collections.emptyList();

        List<AlternativeInfo> infos = altBidirDijktra.calcAlternatives(2, penaltyFactor * maxWeightFactor, 0.05, 0.1);
        if (infos.isEmpty())
            return Collections.emptyList();

        if (infos.size() == 1)
        {
            // fallback to same path for backward direction (or at least VERY similar path as optimal)
            paths.add(bestForwardPath);
            paths.add(infos.get(0).getPath().extract());
        } else
        {
            AlternativeInfo secondBest = null;
            for (AlternativeInfo i : infos)
            {
                if (1 - i.getPlateauWeight() / i.getPath().getWeight() > 1e-8)
                {
                    secondBest = i;
                    break;
                }
            }
            if (secondBest == null)
                throw new RuntimeException("no second best found. " + infos);

            EdgeEntry newTo = secondBest.getPlateauStart();
            // find first sharing edge
            while (newTo.parent != null)
            {
                if (forwardEdgeSet.contains(newTo.parent.edge))
                    break;
                newTo = newTo.parent;
            }

            if (newTo.parent != null)
            {
                // in case edge was found in forwardEdgeSet we calculate the first sharing end
                int tKey = traversalMode.createTraversalId(newTo.adjNode, newTo.parent.adjNode, newTo.edge, false);

                // do new extract
                EdgeEntry tmpFromEdgeEntry = altDijkstra.getFromEntry(tKey);

                // if (tmpFromEdgeEntry.parent != null) tmpFromEdgeEntry = tmpFromEdgeEntry.parent;
                bestForwardPath = new Path(graph, flagEncoder).setEdgeEntry(tmpFromEdgeEntry).setWeight(tmpFromEdgeEntry.weight).extract();

                newTo = newTo.parent;
                // force new 'to'
                newTo.edge = EdgeIterator.NO_EDGE;
                secondBest.getPath().setWeight(secondBest.getPath().getWeight() - newTo.weight);
            }

            paths.add(bestForwardPath);
            paths.add(secondBest.getPath().extract());
        }
        return paths;
    }

    /**
     * This method calculates best paths (alternatives) between 'from' and 'to', where maxPaths-1
     * alternatives are searched and they are only accepted if they are not too similar but close to
     * the best path.
     * <p/>
     *
     * @param maxShare a number between 0 and 1, where 0 means no similarity is accepted and 1 means
     * even different paths with identical weight are accepted
     * @param maxWeightFactor a number of at least 1, which stands for a filter throwing away all
     * alternatives with a weight higher than weight*maxWeightFactor
     */
    public List<AlternativeInfo> calcAlternatives( int from, int to, int maxPaths,
            double maxShare, double maxWeightFactor )
    {
        AltDijkstraBidirectionRef altBidirDijktra = new AltDijkstraBidirectionRef(graph, flagEncoder, weighting, traversalMode);
        altBidirDijktra.searchBest(from, to);

        List<AlternativeInfo> alternatives = altBidirDijktra.calcAlternatives(maxPaths, maxWeightFactor, 2, 0.1);
        for (AlternativeInfo a : alternatives)
        {
            a.getPath().extract();
        }
        return alternatives;
    }

    private static final Comparator<AlternativeInfo> ALT_COMPARATOR = new Comparator<AlternativeInfo>()
    {
        @Override
        public int compare( AlternativeInfo o1, AlternativeInfo o2 )
        {
            return Double.compare(o1.sortBy, o2.sortBy);
        }
    };

    public static class AlternativeInfo
    {
        private final double sortBy;
        private final Path path;
        private final EdgeEntry plateauStart;
        private final EdgeEntry plateauEnd;
        private final double plateauWeight;

        public AlternativeInfo( double sortBy, Path path, EdgeEntry plateauStart, EdgeEntry plateauEnd, double plateauWeight )
        {
            this.sortBy = sortBy;
            this.path = path;
            this.plateauStart = plateauStart;
            this.plateauEnd = plateauEnd;
            this.plateauWeight = plateauWeight;
        }

        public Path getPath()
        {
            return path;
        }

        public EdgeEntry getPlateauEnd()
        {
            return plateauEnd;
        }

        public EdgeEntry getPlateauStart()
        {
            return plateauStart;
        }

        public double getPlateauWeight()
        {
            return plateauWeight;
        }

        public double getSortBy()
        {
            return sortBy;
        }

        @Override
        public String toString()
        {
            return sortBy + "," + plateauWeight + "," + path.toString();
        }
    }

    /**
     * Helper class to find alternatives and alternatives for round trip.
     */
    public static class AltDijkstraBidirectionRef extends DijkstraBidirectionRef
    {
        public AltDijkstraBidirectionRef( Graph graph, FlagEncoder encoder, Weighting weighting, TraversalMode tMode )
        {
            super(graph, encoder, weighting, tMode);
        }

        @Override
        public boolean finished()
        {
            // we need to finish BOTH searches identical to CH
            if (finishedFrom && finishedTo)
                return true;

            if (currFrom.weight + currTo.weight > weightLimit)
                return true;

            // TODO reduce search space via the paper 'Improved Alternative Route Planning'
            return currFrom.weight > bestPath.getWeight() && currTo.weight > bestPath.getWeight();
        }

        public Path searchBest( int to, int from )
        {
            createAndInitPath();
            initFrom(to, 0);
            initTo(from, 0);
            // init collections and bestPath.getWeight properly
            runAlgo();
            return bestPath;
        }

        /**
         * @return the information necessary to handle alternative paths. Note that the paths are
         * not yet extracted.
         */
        public List<AlternativeInfo> calcAlternatives( final int maxPaths, double maxWeightFactor,
                final double weightInfluence, final double minPlateauRatio )
        {
            final double maxWeight = maxWeightFactor * bestPath.getWeight();
            final List<AlternativeInfo> alternatives = new ArrayList<AlternativeInfo>(maxPaths);

            bestWeightMapFrom.forEachEntry(new TIntObjectProcedure<EdgeEntry>()
            {
                double getWorstSortBy()
                {
                    if (alternatives.isEmpty())
                        return (weightInfluence - 1) * bestPath.getWeight();
                    return alternatives.get(alternatives.size() - 1).sortBy;
                }

                @Override
                public boolean execute( final int traversalId, final EdgeEntry fromEdgeEntry )
                {
                    EdgeEntry toEdgeEntry = bestWeightMapTo.get(traversalId);
                    if (toEdgeEntry == null)
                        return true;

                    if (traversalMode.isEdgeBased() && toEdgeEntry.parent != null)
                        toEdgeEntry = toEdgeEntry.parent;

                    // suboptimal path where both EdgeEntries are parallel
                    if (fromEdgeEntry.adjNode != toEdgeEntry.adjNode)
                        return true;

                    final double weight = toEdgeEntry.weight + fromEdgeEntry.weight;
                    if (weight > maxWeight)
                        return true;

                    // accept from-EdgeEntries only if at start of a plateau, i.e. discard if its parent has the same edgeId as the next to-EdgeEntry
                    if (fromEdgeEntry.parent != null)
                    {
                        EdgeEntry tmpFromEntry = traversalMode.isEdgeBased() ? fromEdgeEntry.parent : fromEdgeEntry;
                        if (tmpFromEntry == null || tmpFromEntry.parent == null)
                            return true;

                        int nextToTraversalId = traversalMode.createTraversalId(tmpFromEntry.adjNode, tmpFromEntry.parent.adjNode,
                                tmpFromEntry.edge, true);
                        EdgeEntry tmpNextToEdgeEntry = bestWeightMapTo.get(nextToTraversalId);

                        if (tmpNextToEdgeEntry == null)
                            return true;

                        if (traversalMode.isEdgeBased())
                            tmpNextToEdgeEntry = tmpNextToEdgeEntry.parent;

                        if (fromEdgeEntry.edge == tmpNextToEdgeEntry.edge)
                            return true;
                    }

                    // now we know we are at the beginning of the 'from'-side of the plateau A-B-C and go further to B
                    // where B is the next-'from' of A and B is also the previous-'to' of A.
                    //
                    //      *<-A-B-C->*
                    //        /    \
                    //    start    end
                    //
                    // extend plateau in only one direction necessary (A to B to ...) as we know
                    // that the from-EdgeEntry is the start of the plateau or there is no plateau at all
                    //
                    double plateauWeight = 0;
                    EdgeEntry prevToEdgeEntry = toEdgeEntry;
                    while (prevToEdgeEntry.parent != null)
                    {
                        int nextFromTraversalId = traversalMode.createTraversalId(prevToEdgeEntry.adjNode, prevToEdgeEntry.parent.adjNode,
                                prevToEdgeEntry.edge, false);

                        EdgeEntry nextFromEdgeEntry = bestWeightMapFrom.get(nextFromTraversalId);
                        // end of a plateau
                        if (nextFromEdgeEntry == null)
                            break;

                        // is the next from-EdgeEntry on the plateau?
                        if (prevToEdgeEntry.edge != nextFromEdgeEntry.edge)
                            break;

                        plateauWeight += (prevToEdgeEntry.weight - prevToEdgeEntry.parent.weight);
                        prevToEdgeEntry = prevToEdgeEntry.parent;
                    }

                    if (plateauWeight <= 0)
                        return true;

                    // TODO probably we also need to calculate the share with the optimal path to improve the quality (like in the paper)
                    // weight and plateauWeight should be minimized
                    double sortBy = weightInfluence * weight - plateauWeight;
                    double worstSortBy = getWorstSortBy();

                    if (// avoid short plateaus which would lead to small detours
                            (plateauWeight / weight > minPlateauRatio)
                            && (// better alternative
                            sortBy >= worstSortBy
                            // more alternatives
                            || alternatives.size() < maxPaths))
                    {
                        Path path = new PathBidirRef(graph, flagEncoder).
                                setEdgeEntryTo(toEdgeEntry).setEdgeEntry(fromEdgeEntry).setWeight(weight);
                        alternatives.add(new AlternativeInfo(sortBy, path, fromEdgeEntry, prevToEdgeEntry, plateauWeight));

                        Collections.sort(alternatives, ALT_COMPARATOR);
                        if (alternatives.size() > maxPaths)
                            alternatives.subList(maxPaths, alternatives.size()).clear();
                    }

                    return true;
                }
            });

            return alternatives;
        }
    }

    /**
     * Helper class for one to many dijkstra
     */
    private static class AltSingleDijkstra extends DijkstraBidirectionRef
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

        public EdgeEntry getFromEntry( int key )
        {
            return bestWeightMapFrom.get(key);
        }

        EdgeEntry searchBest( int from, double maxFullDistance )
        {
            NodeAccess na = graph.getNodeAccess();
            DistanceCalc distanceCalc = Helper.DIST_PLANE;
            // get the 'to' via exploring the graph and select the node which reaches the maxWeight radius the fastest!
            // '/2' because we need just one direction
            double maxDistance = distanceCalc.calcNormalizedDist(maxFullDistance / 2);
            double lat1 = na.getLatitude(from), lon1 = na.getLongitude(from);
            double lastNormedDistance = -1;
            boolean tmpFinishedFrom = false;
            while (!tmpFinishedFrom)
            {
                tmpFinishedFrom = !fillEdgesFrom();

                double lat2 = na.getLatitude(currFrom.adjNode), lon2 = na.getLongitude(currFrom.adjNode);
                lastNormedDistance = distanceCalc.calcNormalizedDist(lat1, lon1, lat2, lon2);
                if (lastNormedDistance > maxDistance)
                    break;
            }

            // TODO is this okay? 
            // if no path found close to the weight do not return anything!
            if (tmpFinishedFrom && lastNormedDistance > 0 && lastNormedDistance < distanceCalc.calcNormalizedDist(maxFullDistance / 2 / 4))
                return null;

            return currFrom;
        }
    }
}
