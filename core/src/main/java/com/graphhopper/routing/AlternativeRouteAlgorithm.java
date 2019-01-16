/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
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

import com.carrotsearch.hppc.IntIndexedContainer;
import com.graphhopper.routing.ch.Path4CH;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Parameters;

import java.util.*;

/**
 * This class is the base for all alternative route algorithms - with and without contraction hierarchies.
 *
 * @author Maximilian Sturm
 */
public class AlternativeRouteAlgorithm extends DijkstraBidirectionRef {
    protected static final Comparator<AlternativeInfo> ALT_COMPARATOR = new Comparator<AlternativeInfo>() {
        @Override
        public int compare(AlternativeInfo o1, AlternativeInfo o2) {
            return Double.compare(o1.getSortBy(), o2.getSortBy());
        }
    };
    protected final boolean CH;

    protected double explorationFactor = 1.0;
    protected double maxWeightFactor = 1.25;
    protected double maxShareFactor = 0.75;
    protected int maxPaths = 3;
    protected int additionalPaths = 3;
    protected double weightInfluence = 2;
    protected double shareInfluence = 1;

    protected AlternativeRouteAlgorithm(Graph graph, Weighting weighting, TraversalMode traversalMode) {
        super(graph, weighting, traversalMode);
        if (graph.getClass().getName().contains("CHGraph") && weighting.getName().contains("prepare"))
            CH = true;
        else
            CH = false;
    }

    /**
     * @return whether the algorithm uses contraction hierarchies. This is true if the graph is a CHGraph and the
     * weighing is a PreparationWeighting
     */
    public boolean isCH() {
        return CH;
    }

    /**
     * @param explorationFactor specifies how much is explored compared to normal bidirectional dijkstra. Higher values
     *                          mean more alternatives may be discovered but this also results in much higher
     *                          computation times
     */
    public void setExplorationFactor(double explorationFactor) {
        this.explorationFactor = explorationFactor;
        if (this.explorationFactor < 1)
            throw new IllegalStateException("The explorationFactor must be bigger or equal to 1");
    }

    /**
     * @param maxWeightFactor defines how much higher the alternative's not-shared weight can be compared to the main
     *                        route's not-shared weight in order to be called good alternative
     */
    public void setMaxWeightFactor(double maxWeightFactor) {
        this.maxWeightFactor = maxWeightFactor;
        if (this.maxWeightFactor <= 1)
            throw new IllegalStateException("The maxWeightFactor must be bigger than 1");
    }

    /**
     * @param maxShareFactor defines the maximum weight an alternative is allowed to share with the main route in order
     *                       to be called good alternative
     */
    public void setMaxShareFactor(double maxShareFactor) {
        this.maxShareFactor = maxShareFactor;
        if (this.maxShareFactor <= 0 || this.maxShareFactor > 1)
            throw new IllegalStateException("The maxShareFactor must be between 0 and 1");
    }

    /**
     * @param maxPaths specifies the maximum amount of paths that will be returned, including the main route
     */
    public void setMaxPaths(int maxPaths) {
        this.maxPaths = maxPaths;
        if (this.maxPaths < 2)
            throw new IllegalStateException("Use normal algorithm with less overhead instead if no alternatives are required");
    }

    /**
     * @param additionalPaths specifies out of how many paths the alternatives will be calculated. Having more
     *                        additional paths will allow the algorithm to choose between more alternatives and
     *                        therefore return better alternatives. It's recommended to set this equal to maxPaths
     */
    public void setAdditionalPaths(int additionalPaths) {
        this.additionalPaths = additionalPaths;
        if (this.additionalPaths < 0)
            throw new IllegalStateException("The amount of additional calculated Paths can't be negative");
    }

    @Override
    protected void initCollections(int size) {
        if (isCH())
            super.initCollections(Math.min(size, 2000));
        else
            super.initCollections(size);
    }

    @Override
    public boolean finished() {
        if (finishedFrom && finishedTo)
            return true;

        if (!bestPath.isFound() && (finishedFrom || finishedTo))
            return true;

        return currFrom.weight > explorationFactor * bestPath.getWeight() && currTo.weight > explorationFactor * bestPath.getWeight();
    }

    @Override
    protected Path createAndInitPath() {
        if (isCH())
            bestPath = new Path4CH(graph, graph.getBaseGraph(), weighting);
        else
            bestPath = new PathBidirRef(graph, weighting);
        return bestPath;
    }

    @Override
    public String getName() {
        return Parameters.Algorithms.ALT_ROUTE + (isCH() ? "|CH" : "");
    }

    /**
     * @param mainRoute
     * @param altRoute
     * @return how good an alternative is, with lower values being better. If an alternative is considered bad,
     * Double.MAX_VALUE will be returned
     */
    protected double calcSortBy(Path mainRoute, Path altRoute) {
        if (mainRoute.calcNodes().size() == 0 || altRoute.calcNodes().size() == 0) return Double.MAX_VALUE;
        double share = calcShare(mainRoute, altRoute);
        if (share > maxShareFactor) return Double.MAX_VALUE;
        double weight = calcWeight(mainRoute, altRoute, share);
        if (weight > maxWeightFactor) return Double.MAX_VALUE;
        if (reverse(altRoute)) return Double.MAX_VALUE;
        return weightInfluence * (weight - 1) / (maxWeightFactor - 1) + shareInfluence * share / maxShareFactor;
    }

    /**
     * @param mainRoute
     * @param altRoute
     * @return the shared weight between both routes based on the main route. This will return 1 if both paths are
     * identical and 0 if they are completely different
     */
    protected double calcShare(Path mainRoute, Path altRoute) {
        double sharedWeight = 0;
        boolean sharing = true;
        boolean alreadyShared = false;
        List<EdgeIteratorState> edgeList1 = mainRoute.calcEdges();
        List<EdgeIteratorState> edgeList2 = altRoute.calcEdges();
        Iterator<EdgeIteratorState> iterator1 = edgeList1.iterator();
        while (iterator1.hasNext()) {
            boolean currentlySharing = false;
            EdgeIteratorState edge1 = iterator1.next();
            Iterator<EdgeIteratorState> iterator2 = edgeList2.iterator();
            while (iterator2.hasNext()) {
                EdgeIteratorState edge2 = iterator2.next();
                if (edge1.getEdge() == edge2.getEdge()) {
                    if (isCH()) {

                        Weighting weighting;
                        switch (this.weighting.getName()) {
                            case "prepare|fastest":
                                weighting = new FastestWeighting(this.weighting.getFlagEncoder());
                                break;
                            case "prepare|shortest":
                                weighting = new ShortestWeighting(this.weighting.getFlagEncoder());
                                break;
                            default:
                                weighting = this.weighting;
                        }

                        sharedWeight += weighting.calcWeight(edge1, false, -1);
                    } else {
                        sharedWeight += weighting.calcWeight(edge1, false, -1);
                    }
                    currentlySharing = true;
                    break;
                }
            }
            if (sharing && !currentlySharing) {
                sharing = false;
                if (alreadyShared)
                    return Double.MAX_VALUE;
            } else if (!sharing && currentlySharing) {
                sharing = true;
                alreadyShared = true;
            }
        }
        return sharedWeight / mainRoute.getWeight();
    }

    /**
     * @param mainRoute
     * @param altRoute
     * @param share
     * @return
     */
    protected double calcWeight(Path mainRoute, Path altRoute, double share) {
        double mainWeight = mainRoute.getWeight() * (1 - share);
        double altWeight = altRoute.getWeight() - mainRoute.getWeight() * share;
        return altWeight / mainWeight;
    }

    protected boolean reverse(Path path) {
        IntIndexedContainer nodes = path.calcNodes();
        for (int i = 0; i < nodes.size(); i++) {
            int thisNode = nodes.get(i);
            if (nodes.indexOf(thisNode) != nodes.lastIndexOf(thisNode)) return true;
        }
        return false;
    }

    protected Path createPath(SPTEntry entryFrom, SPTEntry entryTo) {
        if (isCH()) {
            Path4CH path = new Path4CH(graph, graph.getBaseGraph(), weighting);
            path.setSPTEntry(entryFrom);
            path.setSPTEntryTo(entryTo);
            path.setWeight(entryFrom.weight + entryTo.weight);
            return path.extract();
        } else {
            PathBidirRef path = new PathBidirRef(graph, weighting);
            path.setSPTEntry(entryFrom);
            path.setSPTEntryTo(entryTo);
            path.setWeight(entryFrom.weight + entryTo.weight);
            return path.extract();
        }
    }
}
