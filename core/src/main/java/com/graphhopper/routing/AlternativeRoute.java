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
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import gnu.trove.procedure.TIntObjectProcedure;
import gnu.trove.procedure.TObjectProcedure;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class implements the alternative paths search using the "plateau" and partially the
 * "penalty" method discribed in the following papers.
 * <p/>
 * <ul>
 * <li>Choice Routing Explanation - Camvit 2009:
 * http://www.camvit.com/camvit-technical-english/Camvit-Choice-Routing-Explanation-english.pdf</li>
 * <li>and refined in: Alternative Routes in Road Networks 2010:
 * http://www.cs.princeton.edu/~rwerneck/papers/ADGW10-alternatives-sea.pdf</li>
 * <li>other ideas 'Improved Alternative Route Planning', 2013:
 * https://hal.inria.fr/hal-00871739/document</li>
 * <li>via point 'storage' idea 'Candidate Sets for Alternative Routes in Road Networks', 2013:
 * https://algo2.iti.kit.edu/download/s-csarrn-12.pdf</li>
 * <li>Alternative route graph construction 2011:
 * http://algo2.iti.kit.edu/download/altgraph_tapas_extended.pdf
 * </li>
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
    private double maxWeightFactor = 1.4;
    // the higher the maxWeightFactor the higher the explorationFactor needs to be
    private double maxExplorationFactor = 1;

    private double maxShareFactor = 0.6;
    private double minPlateauFactor = 0.2;
    private int maxPaths = 2;

    public AlternativeRoute( Graph graph, FlagEncoder flagEncoder, Weighting weighting, TraversalMode traversalMode )
    {
        this.graph = graph;
        this.flagEncoder = flagEncoder;
        this.weighting = weighting;
        this.traversalMode = traversalMode;
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
     * This parameter is used to avoid alternatives too similar to the best path. Specify 0.5 to
     * force a same paths of maximum 50%. The unit is the 'weight' returned in the Weighting.
     */
    public void setMaxShareFactor( double maxShareFactor )
    {
        this.maxShareFactor = maxShareFactor;
    }

    /**
     * This method sets the minimum plateau portion of every alternative path that is required.
     */
    public void setMinPlateauFactor( double minPlateauFactor )
    {
        this.minPlateauFactor = minPlateauFactor;
    }

    /**
     * This method sets the graph exploration percentage for alternative paths. Default is 1 (100%).
     * Specify a higher value to get more alternatives (especially if maxWeightFactor is higher than
     * 1.5) and a lower value to improve query time but reduces the possibility to find
     * alternatives. Similar to weightLimit but instead an absolute value a relative percentage to
     * the best found path is used.
     */
    public void setMaxExplorationFactor( double explorationFactor )
    {
        this.maxExplorationFactor = explorationFactor;
    }

    /**
     * Specifies how many paths (including the optimal) are returned. (default is 2)
     */
    public void setMaxPaths( int maxPaths )
    {
        this.maxPaths = maxPaths;
        if (this.maxPaths < 2)
            throw new IllegalStateException("Use normal algorithm with less overhead instead if no alternatives are required");
    }

    /**
     * This method calculates best paths (alternatives) between 'from' and 'to', where maxPaths-1
     * alternatives are searched and they are only accepted if they are not too similar but close to
     * the best path.
     */
    public List<AlternativeInfo> calcAlternatives( int from, int to )
    {
        AltDijkstraBidirectionRef altBidirDijktra = new AltDijkstraBidirectionRef(
                graph, flagEncoder, weighting, traversalMode, maxExplorationFactor * 2);
        altBidirDijktra.searchBest(from, to);
        altBidirDijktra.setWeightLimit(weightLimit);
        visitedNodes = altBidirDijktra.getVisitedNodes();

        List<AlternativeInfo> alternatives = altBidirDijktra.
                calcAlternatives(maxPaths, maxWeightFactor, 10, maxShareFactor, 0.5, minPlateauFactor, -0.2);
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
        private final EdgeEntry shareStart;
        private final EdgeEntry shareEnd;
        private final double shareWeight;
        private final List<String> names;

        public AlternativeInfo( double sortBy, Path path, EdgeEntry shareStart, EdgeEntry shareEnd,
                                double shareWeight, List<String> altNames )
        {
            this.names = altNames;
            this.sortBy = sortBy;
            this.path = path;
            this.path.setDescription(names);
            this.shareStart = shareStart;
            this.shareEnd = shareEnd;
            this.shareWeight = shareWeight;
        }

        public Path getPath()
        {
            return path;
        }

        public EdgeEntry getShareStart()
        {
            return shareStart;
        }

        public EdgeEntry getShareEnd()
        {
            return shareEnd;
        }

        public double getShareWeight()
        {
            return shareWeight;
        }

        public double getSortBy()
        {
            return sortBy;
        }

        @Override
        public String toString()
        {
            return names + ", sortBy:" + sortBy + ", shareWeight:" + shareWeight + ", " + path;
        }
    }

    /**
     * Helper class to find alternatives and alternatives for round trip.
     */
    public static class AltDijkstraBidirectionRef extends DijkstraBidirectionRef
    {
        private final double explorationFactor;

        public AltDijkstraBidirectionRef( Graph graph, FlagEncoder encoder, Weighting weighting, TraversalMode tMode,
                                          double explorationFactor )
        {
            super(graph, encoder, weighting, tMode);
            this.explorationFactor = explorationFactor;
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

            // increase overlap of both searches:
            return currFrom.weight + currTo.weight > explorationFactor * bestPath.getWeight();
            // this is more precise but takes roughly 20% longer:
            // return currFrom.weight > bestPath.getWeight() && currTo.weight > bestPath.getWeight();
        }

        public Path searchBest( int to, int from )
        {
            createAndInitPath();
            initFrom(to, 0);
            initTo(from, 0);
            // init collections and bestPath.getWeight properly
            runAlgo();
            return extractPath();
        }

        /**
         * @return the information necessary to handle alternative paths. Note that the paths are
         * not yet extracted.
         */
        public List<AlternativeInfo> calcAlternatives( final int maxPaths,
                                                       double maxWeightFactor, final double weightInfluence,
                                                       final double maxShareFactor, final double shareInfluence,
                                                       final double minPlateauFactor, final double plateauInfluence )
        {
            final double maxWeight = maxWeightFactor * bestPath.getWeight();
            final TIntObjectHashMap<TIntSet> traversalIDMap = new TIntObjectHashMap<TIntSet>();
            final AtomicInteger startTID = addToMap(traversalIDMap, bestPath);

            // find all 'good' alternatives from forward-SPT matching the backward-SPT and optimize by
            // small total weight (1), small share and big plateau (3a+b) and do these expensive calculations
            // only for plateau start candidates (2)
            final List<AlternativeInfo> alternatives = new ArrayList<AlternativeInfo>(maxPaths);

            double bestPlateau = bestPath.getWeight();
            double bestShare = 0;
            double sortBy = calcSortBy(weightInfluence, bestPath.getWeight(),
                    shareInfluence, bestShare,
                    plateauInfluence, bestPlateau);

            final AlternativeInfo bestAlt = new AlternativeInfo(sortBy, bestPath,
                    bestPath.edgeEntry, bestPath.edgeTo, bestShare, getAltNames(graph, bestPath.edgeEntry));
            alternatives.add(bestAlt);
            final List<EdgeEntry> bestPathEntries = new ArrayList<EdgeEntry>(2);

            bestWeightMapFrom.forEachEntry(new TIntObjectProcedure<EdgeEntry>()
            {
                @Override
                public boolean execute( final int traversalId, final EdgeEntry fromEdgeEntry )
                {
                    EdgeEntry toEdgeEntry = bestWeightMapTo.get(traversalId);
                    if (toEdgeEntry == null)
                        return true;

                    if (traversalMode.isEdgeBased())
                    {
                        if (toEdgeEntry.parent != null)
                            // move to parent for two reasons: 
                            // 1. make only turn costs missing in 'weight' and not duplicating current edge.weight
                            // 2. to avoid duplicate edge in Path
                            toEdgeEntry = toEdgeEntry.parent;
                        // TODO else if fromEdgeEntry.parent != null fromEdgeEntry = fromEdgeEntry.parent;

                    } else
                    {
                        // The alternative path is suboptimal when both entries are parallel
                        if (fromEdgeEntry.edge == toEdgeEntry.edge)
                            return true;
                    }

                    // (1) skip too long paths
                    final double weight = fromEdgeEntry.weight + toEdgeEntry.weight;
                    if (weight > maxWeight)
                        return true;

                    // (2) Use the start traversal ID of a plateau as ID for the alternative path.
                    // Accept from-EdgeEntries only if such a start of a plateau 
                    // i.e. discard if its parent has the same edgeId as the next to-EdgeEntry.                                        
                    // Ignore already added best path
                    if (isBestPath(fromEdgeEntry, bestPath))
                        return true;

                    // For edge based traversal we need the next entry to find out the plateau start
                    EdgeEntry tmpFromEntry = traversalMode.isEdgeBased() ? fromEdgeEntry.parent : fromEdgeEntry;
                    if (tmpFromEntry == null || tmpFromEntry.parent == null)
                    {
                        // we can be here only if edge based and only if entry is not part of the best path 
                        // e.g. when starting point has two edges and one is part of the best path the other edge is path of an alternative
                        assert traversalMode.isEdgeBased();
                    } else
                    {
                        int nextToTraversalId = traversalMode.createTraversalId(tmpFromEntry.adjNode,
                                tmpFromEntry.parent.adjNode, tmpFromEntry.edge, true);
                        EdgeEntry tmpNextToEdgeEntry = bestWeightMapTo.get(nextToTraversalId);
                        if (tmpNextToEdgeEntry == null)
                            return true;

                        if (traversalMode.isEdgeBased())
                            tmpNextToEdgeEntry = tmpNextToEdgeEntry.parent;
                        // skip if on plateau
                        if (fromEdgeEntry.edge == tmpNextToEdgeEntry.edge)
                            return true;
                    }

                    // (3a) calculate plateau, we know we are at the beginning of the 'from'-side of 
                    // the plateau A-B-C and go further to B
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
                    // List<Integer> plateauEdges = new ArrayList<Integer>();
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

                        // plateauEdges.add(prevToEdgeEntry.edge);
                        plateauWeight += (prevToEdgeEntry.weight - prevToEdgeEntry.parent.weight);
                        prevToEdgeEntry = prevToEdgeEntry.parent;
                    }

                    if (plateauWeight <= 0 || plateauWeight / weight < minPlateauFactor)
                        return true;

                    if (fromEdgeEntry.parent == null)
                        throw new IllegalStateException("not implemented yet. in case of an edge based traversal the parent of fromEdgeEntry could be null");

                    // (3b) calculate share                    
                    EdgeEntry fromEE = getFirstShareEE(fromEdgeEntry.parent, true);
                    EdgeEntry toEE = getFirstShareEE(toEdgeEntry.parent, false);
                    double shareWeight = fromEE.weight + toEE.weight;
                    boolean smallShare = shareWeight / bestPath.getWeight() < maxShareFactor;
                    if (smallShare)
                    {
                        List<String> altNames = getAltNames(graph, fromEdgeEntry);
                        double sortBy = calcSortBy(weightInfluence, weight, shareInfluence, shareWeight, plateauInfluence, plateauWeight);
                        double worstSortBy = getWorstSortBy();

                        // plateaus.add(new PlateauInfo(altName, plateauEdges));
                        if (sortBy < worstSortBy || alternatives.size() < maxPaths)
                        {
                            Path path = new PathBidirRef(graph, flagEncoder).
                                    setEdgeEntryTo(toEdgeEntry).setEdgeEntry(fromEdgeEntry).
                                    setWeight(weight);
                            path.extract();

                            // for now do not add alternatives to set, if we do we need to remove then on alternatives.clear too (see below)
                            // AtomicInteger tid = addToMap(traversalIDMap, path);
                            // int tid = traversalMode.createTraversalId(path.calcEdges().get(0), false);
                            alternatives.add(new AlternativeInfo(sortBy, path, fromEE, toEE, shareWeight, altNames));

                            Collections.sort(alternatives, ALT_COMPARATOR);
                            if (alternatives.get(0) != bestAlt)
                                throw new IllegalStateException("best path should be always first entry");

                            if (alternatives.size() > maxPaths)
                                alternatives.subList(maxPaths, alternatives.size()).clear();
                        }
                    }

                    return true;
                }

                /**
                 * Extract path until we stumble over an existing traversal id
                 */
                EdgeEntry getFirstShareEE( EdgeEntry startEE, boolean reverse )
                {
                    while (startEE.parent != null)
                    {
                        // TODO we could make use of traversal ID directly if stored in EdgeEntry
                        int tid = traversalMode.createTraversalId(startEE.adjNode, startEE.parent.adjNode, startEE.edge, reverse);
                        if (isAlreadyExisting(tid))
                            return startEE;

                        startEE = startEE.parent;
                    }

                    return startEE;
                }

                /**
                 * This method returns true if the specified tid is already existent in the
                 * traversalIDMap
                 */
                boolean isAlreadyExisting( final int tid )
                {
                    return !traversalIDMap.forEachValue(new TObjectProcedure<TIntSet>()
                    {
                        @Override
                        public boolean execute( TIntSet set )
                        {
                            return !set.contains(tid);
                        }
                    });
                }

                /**
                 * Return the current worst weight for all alternatives
                 */
                double getWorstSortBy()
                {
                    if (alternatives.isEmpty())
                        throw new IllegalStateException("Empty alternative list cannot happen");
                    return alternatives.get(alternatives.size() - 1).sortBy;
                }

                // returns true if fromEdgeEntry is identical to the specified best path
                boolean isBestPath( EdgeEntry fromEdgeEntry, Path bestPath )
                {
                    if (traversalMode.isEdgeBased())
                    {
                        if (GHUtility.getEdgeFromEdgeKey(startTID.get()) == fromEdgeEntry.edge)
                        {
                            if (fromEdgeEntry.parent == null)
                                throw new IllegalStateException("best path must have no parent but was non-null: " + fromEdgeEntry);

                            return true;
                        }

                    } else
                    {
                        if (fromEdgeEntry.parent == null)
                        {
                            bestPathEntries.add(fromEdgeEntry);
                            if (bestPathEntries.size() > 1)
                                throw new IllegalStateException("There is only one best path but was: " + bestPathEntries);

                            if (startTID.get() != fromEdgeEntry.adjNode)
                                throw new IllegalStateException("Start traversal ID has to be identical to root edge entry "
                                        + "which is the plateau start of the best path but was: " + startTID + " vs. adjNode: " + fromEdgeEntry.adjNode);

                            return true;
                        }
                    }

                    return false;
                }
            });

            return alternatives;
        }

        /**
         * This method adds the traversal IDs of the specified path as set to the specified map.
         */
        AtomicInteger addToMap( TIntObjectHashMap<TIntSet> map, Path path )
        {
            TIntSet set = new TIntHashSet();
            final AtomicInteger startTID = new AtomicInteger(-1);
            for (EdgeIteratorState iterState : path.calcEdges())
            {
                int tid = traversalMode.createTraversalId(iterState, false);
                set.add(tid);
                if (startTID.get() < 0)
                {
                    // for node based traversal we need to explicitely add base node as starting node and to list
                    if (!traversalMode.isEdgeBased())
                    {
                        tid = iterState.getBaseNode();
                        set.add(tid);
                    }

                    startTID.set(tid);
                }
            }
            map.put(startTID.get(), set);
            return startTID;
        }
    }

    static List<String> getAltNames( Graph graph, EdgeEntry ee )
    {
        if (ee == null)
            return Collections.emptyList();

        EdgeIteratorState iter = graph.getEdgeIteratorState(ee.edge, Integer.MIN_VALUE);
        if (iter == null)
            return Collections.emptyList();

        String str = iter.getName();
        if (str.isEmpty())
            return Collections.emptyList();

        return Collections.singletonList(str);
    }

    static double calcSortBy( double weightInfluence, double weight,
                              double shareInfluence, double shareWeight,
                              double plateauInfluence, double plateauWeight )
    {
        return weightInfluence * weight + shareInfluence * shareWeight + plateauInfluence * plateauWeight;
    }

    public static class PlateauInfo
    {
        String name;
        List<Integer> edges;

        public PlateauInfo( String name, List<Integer> edges )
        {
            this.name = name;
            this.edges = edges;
        }

        @Override
        public String toString()
        {
            return name;
        }

        public List<Integer> getEdges()
        {
            return edges;
        }

        public String getName()
        {
            return name;
        }
    }
}
