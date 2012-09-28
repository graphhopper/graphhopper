/*
 *  Copyright 2012 Peter Karich 
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.routing;

import de.jetsli.graph.coll.MyBitSet;
import de.jetsli.graph.coll.MyBitSetImpl;
import de.jetsli.graph.routing.AStar.AStarEdge;
import de.jetsli.graph.routing.util.EdgePrioFilter;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.util.DistanceCalc;
import de.jetsli.graph.util.DistanceCosProjection;
import de.jetsli.graph.util.EdgeIterator;
import de.jetsli.graph.util.GraphUtility;
import de.jetsli.graph.util.shapes.CoordTrig;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.PriorityQueue;

/**
 * This class implements the A* algorithm according to
 * http://en.wikipedia.org/wiki/A*_search_algorithm
 *
 * Different distance calculations can be used via the setFast method.
 *
 * @author Peter Karich
 */
public class AStarBidirection extends AbstractRoutingAlgorithm {

    private DistanceCalc dist = new DistanceCalc();
    private int from, to;
    private MyBitSet visitedFrom;
    private PriorityQueue<AStarEdge> prioQueueOpenSetFrom;
    private TIntObjectMap<AStarEdge> shortestWeightMapFrom;
    private MyBitSet visitedTo;
    private PriorityQueue<AStarEdge> prioQueueOpenSetTo;
    private TIntObjectMap<AStarEdge> shortestWeightMapTo;
    private boolean alreadyRun;
    private AStarEdge currFrom;
    private AStarEdge currTo;
    private TIntObjectMap<AStarEdge> shortestWeightMapOther;
    private EdgePrioFilter edgeFilter;
    public PathBidirRef shortest;
    private CoordTrig fromCoord;
    private CoordTrig toCoord;

    public AStarBidirection(Graph graph) {
        super(graph);
        int locs = Math.max(20, graph.getNodes());
        visitedFrom = new MyBitSetImpl(locs);
        prioQueueOpenSetFrom = new PriorityQueue<AStarEdge>(locs / 10);
        shortestWeightMapFrom = new TIntObjectHashMap<AStarEdge>(locs / 10);

        visitedTo = new MyBitSetImpl(locs);
        prioQueueOpenSetTo = new PriorityQueue<AStarEdge>(locs / 10);
        shortestWeightMapTo = new TIntObjectHashMap<AStarEdge>(locs / 10);

        clear();
    }

    /**
     * @param fast if true it enables approximative distance calculation from lat,lon values
     */
    public AStarBidirection setApproximation(boolean approx) {
        if (approx)
            dist = new DistanceCosProjection();
        else
            dist = new DistanceCalc();
        return this;
    }

    public RoutingAlgorithm setEdgeFilter(EdgePrioFilter edgeFilter) {
        this.edgeFilter = edgeFilter;
        return this;
    }

    public EdgePrioFilter getEdgeFilter() {
        return edgeFilter;
    }

    @Override
    public RoutingAlgorithm clear() {
        alreadyRun = false;
        visitedFrom.clear();
        prioQueueOpenSetFrom.clear();
        shortestWeightMapFrom.clear();

        visitedTo.clear();
        prioQueueOpenSetTo.clear();
        shortestWeightMapTo.clear();
        return this;
    }

    public void initFrom(int from) {
        this.from = from;
        currFrom = new AStar.AStarEdge(from, 0, 0);
        shortestWeightMapFrom.put(from, currFrom);
        visitedFrom.add(from);
        fromCoord = new CoordTrig(graph.getLatitude(from), graph.getLongitude(from));
    }

    public void initTo(int to) {
        this.to = to;
        currTo = new AStar.AStarEdge(to, 0, 0);
        shortestWeightMapTo.put(to, currTo);
        visitedTo.add(to);
        toCoord = new CoordTrig(graph.getLatitude(to), graph.getLongitude(to));
    }

    @Override public Path calcPath(int from, int to) {
        if (alreadyRun)
            throw new IllegalStateException("Call clear before! But this class is not thread safe!");

        alreadyRun = true;
        initPath();
        initFrom(from);
        initTo(to);

        Path p = checkIndenticalFromAndTo();
        if (p != null)
            return p;

        int counter = 0;
        int finish = 0;
        while (finish < 2) {
            counter++;
            finish = 0;
            if (!fillEdgesFrom())
                finish++;

            if (!fillEdgesTo())
                finish++;
        }

        return shortest.extract();
    }

    // http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
    // a node from overlap may not be on the shortest path!!
    // => when scanning an arc (v, w) in the forward search and w is scanned in the reverse 
    //    search, update shortest = μ if df (v) + (v, w) + dr (w) < μ            
    public boolean checkFinishCondition() {
        if (currFrom == null)
            return currTo.weightToCompare >= shortest.weight;
        else if (currTo == null)
            return currFrom.weightToCompare >= shortest.weight;
        return currFrom.weightToCompare + currTo.weightToCompare >= shortest.weight;
    }

    private void fillEdges(AStarEdge curr, CoordTrig goal, MyBitSet closedSet, PriorityQueue<AStarEdge> prioQueueOpenSet,
            TIntObjectMap<AStarEdge> shortestWeightMap, boolean out) {

        int currNodeFrom = curr.node;
        EdgeIterator iter = GraphUtility.getEdges(graph, currNodeFrom, out);
        if (edgeFilter != null)
            iter = edgeFilter.doFilter(iter);

        while (iter.next()) {
            int neighborNode = iter.node();
            if (closedSet.contains(neighborNode))
                continue;

            double alreadyVisitedWeight = weightCalc.getWeight(iter) + curr.weightToCompare;
            AStarEdge de = shortestWeightMap.get(neighborNode);
            if (de == null || de.weightToCompare > alreadyVisitedWeight) {
                double tmpLat = graph.getLatitude(neighborNode);
                double tmpLon = graph.getLongitude(neighborNode);
                double currWeightToGoal = dist.calcDistKm(goal.lat, goal.lon, tmpLat, tmpLon);
                currWeightToGoal = weightCalc.apply(currWeightToGoal);
                double distEstimation = alreadyVisitedWeight + currWeightToGoal;
                if (de == null) {
                    de = new AStarEdge(neighborNode, distEstimation, alreadyVisitedWeight);
                    shortestWeightMap.put(neighborNode, de);
                } else {
                    prioQueueOpenSet.remove(de);
                    de.weight = distEstimation;
                    de.weightToCompare = alreadyVisitedWeight;
                }

                de.prevEntry = curr;
                prioQueueOpenSet.add(de);
                updateShortest(de, neighborNode);
            }
        }
    }

//    @Override -> TODO weightToCompare with weight => then a simple EdgeEntry is possible
    public void updateShortest(AStarEdge shortestDE, int currLoc) {
        AStarEdge entryOther = shortestWeightMapOther.get(currLoc);
        if (entryOther == null)
            return;

        // update μ
        double newShortest = shortestDE.weightToCompare + entryOther.weightToCompare;
        if (newShortest < shortest.weight) {
            shortest.switchWrapper = shortestWeightMapFrom == shortestWeightMapOther;
            shortest.edgeFrom = shortestDE;
            shortest.edgeTo = entryOther;
            shortest.weight = newShortest;
        }
    }

    public boolean fillEdgesFrom() {
        if (currFrom != null) {
            shortestWeightMapOther = shortestWeightMapTo;
            fillEdges(currFrom, toCoord, visitedFrom, prioQueueOpenSetFrom, shortestWeightMapFrom, true);
            if (prioQueueOpenSetFrom.isEmpty())
                return false;

            currFrom = prioQueueOpenSetFrom.poll();
            if (checkFinishCondition())
                return false;
            visitedFrom.add(currFrom.node);
        } else if (currTo == null)
            throw new IllegalStateException("Shortest Path not found? " + from + " " + to);
        return true;
    }

    public boolean fillEdgesTo() {
        if (currTo != null) {
            shortestWeightMapOther = shortestWeightMapFrom;
            fillEdges(currTo, fromCoord, visitedTo, prioQueueOpenSetTo, shortestWeightMapTo, false);
            if (prioQueueOpenSetTo.isEmpty())
                return false;

            currTo = prioQueueOpenSetTo.poll();
            if (checkFinishCondition())
                return false;
            visitedTo.add(currTo.node);
        } else if (currFrom == null)
            throw new IllegalStateException("Shortest Path not found? " + from + " " + to);
        return true;
    }

    private Path checkIndenticalFromAndTo() {
        if (from == to) {
            Path p = new Path(weightCalc);
            p.add(from);
            return p;
        }
        return null;
    }

    protected PathBidirRef createPath() {
        return new PathBidirRef(graph, weightCalc);
    }

    public void initPath() {
        shortest = createPath();
        shortest.weight(Double.MAX_VALUE);
    }
}
