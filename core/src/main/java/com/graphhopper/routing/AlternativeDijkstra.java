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
 * This class implements the alternative paths search using the plateau method discribed in
 * <p/>
 * <ul>
 * <li>Choice Routing Explanation - Camvit http://www.camvit.com/camvit-technical-english/Camvit-Choice-Routing-Explanation-english.pdf</li>
 * <li>and refined in: Alternative Routes in Road Networks http://www.cs.princeton.edu/~rwerneck/papers/ADGW10-alternatives-sea.pdf</li>
 * <li>other ideas 'Improved Alternative Route Planning', 2013: https://hal.inria.fr/hal-00871739/document</li>
 * <li>via point 'storage' idea 'Candidate Sets for Alternative Routes in Road Networks', 2013: https://algo2.iti.kit.edu/download/s-csarrn-12.pdf</li>
 * </ul>
 *
 * @author Peter Karich
 */
public class AlternativeDijkstra extends DijkstraBidirectionRef
{
    public AlternativeDijkstra(Graph graph, FlagEncoder encoder, Weighting weighting, TraversalMode tMode)
    {
        super(graph, encoder, weighting, tMode);
    }

    public boolean alternativeFinished()
    {
        // we need to finish BOTH searches identical to CH
        if (finishedFrom && finishedTo)
            return true;

        if (currFrom.weight + currTo.weight > weightLimit)
            return true;

        // TODO reduce search space via the paper 'Improved Alternative Route Planning'
        return currFrom.weight > bestPath.getWeight() && currTo.weight > bestPath.getWeight();
    }


    /**
     * This method calculates best paths (alternatives) between 'from' and 'to', where maxPaths-1
     * alternatives are searched and they are only accepted if they are not too similar but close to
     * the best path.
     * <p/>
     *
     * @param maxShare        a number between 0 and 1, where 0 means no similarity is accepted and 1
     *                        means even different paths with identical weight are accepted
     * @param maxWeightFactor a number of at least 1, which stands for a filter throwing away all
     *                        alternatives with a weight higher than weight*maxWeightFactor
     */
    public List<AlternativeInfo> calcPaths(int from, int to, int maxPaths,
                                           double maxShare, double maxWeightFactor)
    {
        checkAlreadyRun();
        createAndInitPath();
        initFrom(from, 0);
        initTo(to, 0);

        final int maxAlt = maxPaths - 1;

        // TODO improve performance and make several destinations possible via:
        // 1. do a normal bidir search
        // 2. expand one tree alone
        // 3. expand the second tree and search for alternatives at the same time        
        while (!alternativeFinished() && !isWeightLimitReached())
        {
            if (!finishedFrom && !finishedTo)
            {
                if (currFrom.weight < currTo.weight)
                    finishedFrom = !fillEdgesFrom();
                else
                    finishedTo = !fillEdgesTo();
            } else if (finishedFrom)
            {
                finishedFrom = !fillEdgesFrom();
            } else
            {
                finishedTo = !fillEdgesTo();
            }
        }

        final double maxWeight = maxWeightFactor * bestPath.getWeight();
        final List<AlternativeInfo> alternatives = new ArrayList<AlternativeInfo>(maxPaths);

        bestPath.extract();

        final double weightInfluence = 2;
        bestWeightMapFrom.forEachEntry(new TIntObjectProcedure<EdgeEntry>()
        {
            double getWorstSortBy()
            {
                if (alternatives.isEmpty())
                    return (weightInfluence - 1) * bestPath.getWeight();
                return alternatives.get(alternatives.size() - 1).sortBy;
            }

            @Override
            public boolean execute(int traversalId, final EdgeEntry fromEdgeEntry)
            {
                final EdgeEntry toEdgeEntry = bestWeightMapTo.get(traversalId);
                if (toEdgeEntry == null)
                    return true;

                // suboptimal path where both EdgeEntries are parallel
                if (fromEdgeEntry.adjNode != toEdgeEntry.adjNode)
                    return true;

                // is this check necessary as the alternativeFinished method stops already early. But early enough?
                final double weight = toEdgeEntry.weight + fromEdgeEntry.weight;
                if (weight > maxWeight)
                    return true;

                EdgeEntry prevToEdgeEntry = toEdgeEntry;

                // accept from-EdgeEntries only if at start of a plateau, i.e. discard if it has the same edgeId as the next-'to' EdgeEntry
                if (fromEdgeEntry.parent != null)
                {
                    int nextTraversalId = traversalMode.createTraversalId(fromEdgeEntry.parent.adjNode, fromEdgeEntry.adjNode,
                            fromEdgeEntry.edge, false);
                    final EdgeEntry tmpNextToEdgeEntry = bestWeightMapTo.get(nextTraversalId);
                    if (tmpNextToEdgeEntry == null /* end of a plateau */
                            || fromEdgeEntry.edge == tmpNextToEdgeEntry.edge)
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
                boolean containsGuttauer = false;
                while (prevToEdgeEntry.parent != null)
                {
                    int nextTraversalId = traversalMode.createTraversalId(prevToEdgeEntry.parent.adjNode, prevToEdgeEntry.adjNode,
                            prevToEdgeEntry.edge, false);

                    EdgeEntry nextFromEdgeEntry = bestWeightMapFrom.get(nextTraversalId);
                    // is next-'from' EdgeEntry on the plateau?
                    if (nextFromEdgeEntry == null /* end of a plateau */
                            || prevToEdgeEntry.edge != nextFromEdgeEntry.edge)
                        break;

                    plateauWeight += (prevToEdgeEntry.weight - prevToEdgeEntry.parent.weight);
                    prevToEdgeEntry = prevToEdgeEntry.parent;
                }

                if (plateauWeight <= 0)
                    return true;

                // TODO do we need to calculate the share (like in the paper?) with the optimal path to improve the quality?

                // weight and plateauWeight should be minimized
                // TODO use weightInfluence=0 for the round trip as this gives better detours (more 'round')
                double sortBy = weightInfluence * weight - plateauWeight;
                double worstSortBy = getWorstSortBy();

                int plateauFromEdge = toEdgeEntry.edge;
                int plateauEndEdge = prevToEdgeEntry.edge;

                if (// avoid short plateaus which would lead to small detours
                        plateauWeight / weight > 0.1
                                // avoid adding optimum twice -> TODO should we better avoid adding it in the first place?
                                && bestPath.getFromEdge() != plateauFromEdge && bestPath.getEndEdge() != plateauEndEdge
                                && (// better alternative
                                sortBy >= worstSortBy
                                        // more alternatives
                                        || alternatives.size() < maxAlt))
                {
                    Path path = new PathBidirRef(graph, flagEncoder).
                            setEdgeEntryTo(toEdgeEntry).setEdgeEntry(fromEdgeEntry).setWeight(weight);
                    alternatives.add(new AlternativeInfo(sortBy, path, plateauFromEdge, plateauEndEdge, plateauWeight));

                    Collections.sort(alternatives, ALT_COMPARATOR);
                    if (alternatives.size() > maxAlt)
                        alternatives.subList(maxAlt, alternatives.size()).clear();
                }

                return true;
            }
        });

        for (AlternativeInfo a : alternatives)
        {
            a.getPath().extract();
            // System.out.println("weight:" + a.getPath().getWeight() + ", plateau weight:" + a.plateauWeight + ", sortby:" + a.getSortBy());
        }

        alternatives.add(0, new AlternativeInfo((weightInfluence - 1) * bestPath.getWeight(), bestPath, bestPath.getFromEdge(), bestPath.getEndEdge(), bestPath.getWeight()));
        // System.out.println("best weight:" + bestPath.getWeight() + ", plateau weight:" + alternatives.get(0).getPlateauWeight() + ", sortby:" + alternatives.get(0).getSortBy());

        return alternatives;
    }

    private static final Comparator<AlternativeInfo> ALT_COMPARATOR = new Comparator<AlternativeInfo>()
    {
        @Override
        public int compare(AlternativeInfo o1, AlternativeInfo o2)
        {
            return Double.compare(o1.sortBy, o2.sortBy);
        }
    };

    public static class AlternativeInfo
    {
        private final double sortBy;
        private final Path path;
        private final int plateauStartEdgeId;
        private final int plateauEndEdgeId;
        private final double plateauWeight;

        public AlternativeInfo(double sortBy, Path path, int plateauStartEdgeId, int plateauEndEdgeId, double plateauWeight)
        {
            this.sortBy = sortBy;
            this.path = path;
            this.plateauStartEdgeId = plateauStartEdgeId;
            this.plateauEndEdgeId = plateauEndEdgeId;
            this.plateauWeight = plateauWeight;
        }

        public Path getPath()
        {
            return path;
        }

        public int getPlateauEndEdgeId()
        {
            return plateauEndEdgeId;
        }

        public int getPlateauStartEdgeId()
        {
            return plateauStartEdgeId;
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
}
