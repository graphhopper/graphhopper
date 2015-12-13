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

import static com.graphhopper.routing.AlgorithmOptions.ALT_ROUTE;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import gnu.trove.procedure.TIntObjectProcedure;

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
public class AlternativeRoute implements RoutingAlgorithm
{
    private final Graph graph;
    private final FlagEncoder flagEncoder;
    private final Weighting weighting;
    private final TraversalMode traversalMode;
    private double weightLimit = Double.MAX_VALUE;
    private int visitedNodes;
    private double maxWeightFactor = 1;
    private int maxPaths = 1;

    public AlternativeRoute( Graph graph, FlagEncoder flagEncoder, Weighting weighting, TraversalMode traversalMode )
    {
        this.graph = graph;
        this.flagEncoder = flagEncoder;
        this.weighting = weighting;

        this.traversalMode = traversalMode;
        if (this.traversalMode != TraversalMode.NODE_BASED)
            throw new IllegalArgumentException("Only node based traversal currently supported for alternative route calculation");
    }

    @Override
    public void setWeightLimit( double weightLimit )
    {
        this.weightLimit = weightLimit;
    }

    /**
     * Increasing this factor results in returning more alternatives. E.g. if the factor is 2 than
     * all alternatives with a weight 2 times longer than the optimal weight are return. (default is
     * 1)
     */
    public void setMaxWeightFactor( double maxWeightFactor )
    {
        this.maxWeightFactor = maxWeightFactor;
    }

    /**
     * Specifies how many paths (including the optimal) are returned. (default is 1)
     */
    public void setMaxPaths( int maxPaths )
    {
        this.maxPaths = maxPaths;
    }

    /**
     * This method calculates best paths (alternatives) between 'from' and 'to', where maxPaths-1
     * alternatives are searched and they are only accepted if they are not too similar but close to
     * the best path.
     */
    public List<AlternativeInfo> calcAlternatives( int from, int to )
    {
        // TODO 
        // maxShare a number between 0 and 1, where 0 means no similarity is accepted and 1 means
        // even different paths with identical weight are accepted        
        AltDijkstraBidirectionRef altBidirDijktra = new AltDijkstraBidirectionRef(graph, flagEncoder, weighting, traversalMode);
        altBidirDijktra.searchBest(from, to);
        visitedNodes = altBidirDijktra.getVisitedNodes();

        List<AlternativeInfo> alternatives = altBidirDijktra.calcAlternatives(maxPaths, maxWeightFactor, 2, 0.1);
        for (AlternativeInfo a : alternatives)
        {
            a.getPath().extract();
        }
        return alternatives;
    }

    @Override
    public Path calcPath( int from, int to )
    {
        return calcPaths(from, to).get(0);
    }

    @Override
    public List<Path> calcPaths( int from, int to )
    {
        List<AlternativeInfo> alts = calcAlternatives(from, to);
        List<Path> paths = new ArrayList<Path>(alts.size());
        for (AlternativeInfo a : alts)
        {
            paths.add(a.getPath());
        }
        return paths;
    }

    private static final Comparator<AlternativeInfo> ALT_COMPARATOR = new Comparator<AlternativeInfo>()
    {
        @Override
        public int compare( AlternativeInfo o1, AlternativeInfo o2 )
        {
            return Double.compare(o1.sortBy, o2.sortBy);
        }
    };

    @Override
    public String getName()
    {
        return ALT_ROUTE;
    }

    @Override
    public int getVisitedNodes()
    {
        return visitedNodes;
    }

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

            // The following condition is necessary to avoid traversing the full graph if areas are disconnected
            // but it is only valid for none-CH e.g. for CH it can happen that finishedTo is true but the from-SPT could still reach 'to'
            if (!bestPath.isFound() && (finishedFrom || finishedTo))
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
}
