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
package com.graphhopper.routing;

import com.graphhopper.coll.MyBitSet;
import com.graphhopper.coll.MyBitSetImpl;
import com.graphhopper.routing.AStar.AStarEdge;
import com.graphhopper.routing.util.EdgeLevelFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCosProjection;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GraphUtility;
import com.graphhopper.util.shapes.CoordTrig;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.PriorityQueue;

/**
 * This class implements a bidirectional A* algorithm. It is interesting to note that a
 * bidirectional dijkstra is far more efficient than a single direction one. The same does not hold
 * for a bidirectional A* as the finish condition can not be so strict which leads to either
 * suboptimal paths or suboptimal node exploration (too many nodes). Still very good approximations
 * with a rougly twice times faster running time than the normal A* can be reached.
 *
 * Computing the Shortest Path: A∗ Search Meets Graph Theory ->
 * http://research.microsoft.com/apps/pubs/default.aspx?id=64511
 * http://i11www.iti.uni-karlsruhe.de/_media/teaching/sommer2012/routenplanung/vorlesung4.pdf
 * http://research.microsoft.com/pubs/64504/goldberg-sofsem07.pdf
 * http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
 *
 * better stop condition
 *
 * 1. Ikeda, T., Hsu, M.-Y., Imai, H., Nishimura, S., Shimoura, H., Hashimoto, T., Tenmoku, K., and
 * Mitoh, K. (1994). A fast algorithm for finding better routes by ai search techniques. In VNIS,
 * pages 291–296.
 *
 * 2. Whangbo, T. K. (2007). Efficient modified bidirectional a* algorithm for optimal route-
 * finding. In IEA/AIE, volume 4570, pages 344–353. Springer.
 *
 * or could we even use this three phase approach?
 * www.lix.polytechnique.fr/~giacomon/papers/bidirtimedep.pdf
 *
 * @author Peter Karich
 */
public class AStarBidirection extends AbstractRoutingAlgorithm {

    private DistanceCalc dist;
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
    private EdgeLevelFilter edgeFilter;
    public PathBidirRef shortest;
    private CoordTrig fromCoord;
    private CoordTrig toCoord;
    private double approxFactor;

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
        setApproximation(false);
    }

    /**
     * @param fast if true it enables approximative distance calculation from lat,lon values
     */
    public AStarBidirection setApproximation(boolean approx) {
        if (approx) {
            dist = new DistanceCosProjection();
            approxFactor = 0.5;
        } else {
            dist = new DistanceCalc();
            approxFactor = 1.15;
        }
        return this;
    }

    /**
     * Specify a low value like 0.5 for worse but faster results. Or over 1.1 for more precise.
     */
    public AStarBidirection setApproximationFactor(double approxFactor) {
        this.approxFactor = approxFactor;
        return this;
    }

    public RoutingAlgorithm setEdgeFilter(EdgeLevelFilter edgeFilter) {
        this.edgeFilter = edgeFilter;
        return this;
    }

    public EdgeLevelFilter getEdgeFilter() {
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
        currFrom = new AStarEdge(-1, from, 0, 0);
        shortestWeightMapFrom.put(from, currFrom);
        visitedFrom.add(from);
        fromCoord = new CoordTrig(graph.getLatitude(from), graph.getLongitude(from));
    }

    public void initTo(int to) {
        this.to = to;
        currTo = new AStarEdge(-1, to, 0, 0);
        shortestWeightMapTo.put(to, currTo);
        visitedTo.add(to);
        toCoord = new CoordTrig(graph.getLatitude(to), graph.getLongitude(to));
    }

    private Path checkIndenticalFromAndTo() {
        if (from == to) {
            Path p = new Path(graph, weightCalc);
            p.addFrom(from);
            return p;
        }
        return null;
    }

    protected PathBidirRef createPath() {
        return new PathBidirRef(graph, weightCalc);
    }

    public void initPath() {
        shortest = createPath();
        shortest.initWeight();
        // pi_r_of_t = dist.calcDist(fromCoord.lat, fromCoord.lon, toCoord.lat, toCoord.lon);
    }

    @Override public Path calcPath(int from, int to) {
        if (alreadyRun)
            throw new IllegalStateException("Call clear before! But this class is not thread safe!");

        alreadyRun = true;
        initFrom(from);
        initTo(to);
        initPath();

        Path p = checkIndenticalFromAndTo();
        if (p != null)
            return p;

        int finish = 0;
        while (finish < 2) {
            finish = 0;
            if (!fillEdgesFrom())
                finish++;

            if (!fillEdgesTo())
                finish++;
        }

        // System.out.println(toString() + " visited nodes:" + (visitedTo.getCardinality() + visitedFrom.getCardinality()));
        // System.out.println(currFrom.weight + " " + currTo.weight + " " + shortest.weight);
        return shortest.extract();
    }

    // Problem is the correct finish condition! if the bounds are too wide too many nodes are visited :/   
    // d_f (v) + (v, w) + d_r (w) < μ + p_r(t)
    // where pi_r_of_t = p_r(t) = 1/2(pi_r(t) - pi_f(t) + pi_f(s)), and pi_f(t)=0
    public boolean checkFinishCondition() {
        double tmp = shortest.weight * approxFactor;
        if (currFrom == null)
            return currTo.weightToCompare >= tmp;
        else if (currTo == null)
            return currFrom.weightToCompare >= tmp;
        return currFrom.weightToCompare + currTo.weightToCompare >= tmp;
    }

    public boolean fillEdgesFrom() {
        if (currFrom != null) {
            shortestWeightMapOther = shortestWeightMapTo;
            fillEdges(currFrom, toCoord, visitedFrom, prioQueueOpenSetFrom, shortestWeightMapFrom, true);
            if (prioQueueOpenSetFrom.isEmpty()) {
                currFrom = null;
                return false;
            }

            currFrom = prioQueueOpenSetFrom.poll();
            if (checkFinishCondition())
                return false;
            visitedFrom.add(currFrom.endNode);
        } else if (currTo == null)
            return false;

        return true;
    }

    public boolean fillEdgesTo() {
        if (currTo != null) {
            shortestWeightMapOther = shortestWeightMapFrom;
            fillEdges(currTo, fromCoord, visitedTo, prioQueueOpenSetTo, shortestWeightMapTo, false);
            if (prioQueueOpenSetTo.isEmpty()) {
                currTo = null;
                return false;
            }

            currTo = prioQueueOpenSetTo.poll();
            if (checkFinishCondition())
                return false;
            visitedTo.add(currTo.endNode);
        } else if (currFrom == null)
            return false;

        return true;
    }

    private void fillEdges(AStarEdge curr, CoordTrig goal, MyBitSet closedSet, PriorityQueue<AStarEdge> prioQueueOpenSet,
            TIntObjectMap<AStarEdge> shortestWeightMap, boolean out) {

        int currNodeFrom = curr.endNode;
        EdgeIterator iter = GraphUtility.getEdges(graph, currNodeFrom, out);
        if (edgeFilter != null)
            iter = edgeFilter.doFilter(iter);

        while (iter.next()) {
            int neighborNode = iter.node();
            if (closedSet.contains(neighborNode))
                continue;

            // TODO performance: check if the node is already existent in the opposite direction
            // then we could avoid the approximation as we already know the exact complete path!
            double alreadyVisitedWeight = weightCalc.getWeight(iter.distance(), iter.flags()) + curr.weightToCompare;
            AStarEdge de = shortestWeightMap.get(neighborNode);
            if (de == null || de.weightToCompare > alreadyVisitedWeight) {
                double tmpLat = graph.getLatitude(neighborNode);
                double tmpLon = graph.getLongitude(neighborNode);
                double currWeightToGoal = dist.calcDist(goal.lat, goal.lon, tmpLat, tmpLon);
                currWeightToGoal = weightCalc.getMinWeight(currWeightToGoal);
                double estimationFullDist = alreadyVisitedWeight + currWeightToGoal;
                if (de == null) {
                    de = new AStarEdge(iter.edge(), neighborNode, estimationFullDist, alreadyVisitedWeight);
                    shortestWeightMap.put(neighborNode, de);
                } else {
                    prioQueueOpenSet.remove(de);
                    de.edge = iter.edge();
                    de.weight = estimationFullDist;
                    de.weightToCompare = alreadyVisitedWeight;
                }

                de.parent = curr;
                prioQueueOpenSet.add(de);
                updateShortest(de, neighborNode);
            }
        }
    }

//    @Override -> TODO use only weight => then a simple EdgeEntry is possible
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
    
    @Override public String name() {
        return "astarbi";
    }
}
