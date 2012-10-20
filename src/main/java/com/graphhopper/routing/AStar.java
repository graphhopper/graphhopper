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
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCosProjection;
import com.graphhopper.util.EdgeIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.PriorityQueue;

/**
 * This class implements the A* algorithm according to
 * http://en.wikipedia.org/wiki/A*_search_algorithm
 *
 * Different distance calculations can be used via setApproximation.
 *
 * @author Peter Karich
 */
public class AStar extends AbstractRoutingAlgorithm {

    private DistanceCalc dist = new DistanceCosProjection();

    public AStar(Graph g) {
        super(g);
    }

    /**
     * @param fast if true it enables approximative distance calculation from lat,lon values
     */
    public AStar setApproximation(boolean approx) {
        if (approx)
            dist = new DistanceCosProjection();
        else
            dist = new DistanceCalc();
        return this;
    }

    @Override public Path calcPath(int from, int to) {
        MyBitSet closedSet = new MyBitSetImpl(graph.getNodes());
        TIntObjectMap<AStarEdge> map = new TIntObjectHashMap<AStarEdge>();
        PriorityQueue<AStarEdge> prioQueueOpenSet = new PriorityQueue<AStarEdge>();
        double toLat = graph.getLatitude(to);
        double toLon = graph.getLongitude(to);
        double currWeightToGoal, distEstimation, tmpLat, tmpLon;
        AStarEdge fromEntry = new AStarEdge(-1, from, 0, 0);
        AStarEdge currEdge = fromEntry;
        while (true) {
            int currVertex = currEdge.endNode;
            EdgeIterator iter = graph.getOutgoing(currVertex);
            while (iter.next()) {
                int neighborNode = iter.node();
                if (closedSet.contains(neighborNode))
                    continue;

                double alreadyVisitedWeight = weightCalc.getWeight(iter) + currEdge.weightToCompare;
                AStarEdge nEdge = map.get(neighborNode);
                if (nEdge == null || nEdge.weightToCompare > alreadyVisitedWeight) {
                    tmpLat = graph.getLatitude(neighborNode);
                    tmpLon = graph.getLongitude(neighborNode);
                    currWeightToGoal = dist.calcDistKm(toLat, toLon, tmpLat, tmpLon);
                    currWeightToGoal = weightCalc.apply(currWeightToGoal);
                    distEstimation = alreadyVisitedWeight + currWeightToGoal;

                    if (nEdge == null) {
                        nEdge = new AStarEdge(iter.edge(), neighborNode, distEstimation, alreadyVisitedWeight);
                        map.put(neighborNode, nEdge);
                    } else {
                        prioQueueOpenSet.remove(nEdge);
                        nEdge.edge = iter.edge();
                        nEdge.weight = distEstimation;
                        nEdge.weightToCompare = alreadyVisitedWeight;
                    }
                    nEdge.parent = currEdge;
                    prioQueueOpenSet.add(nEdge);
                    updateShortest(nEdge, neighborNode);
                }
            }
            if (to == currVertex)
                break;

            closedSet.add(currVertex);
            currEdge = prioQueueOpenSet.poll();
            if (currEdge == null)
                return null;
        }

        // System.out.println(toString() + " visited nodes:" + closedSet.getCardinality());
        // extract path from shortest-path-tree
        Path path = new Path(graph, weightCalc);
        while (currEdge.parent != null) {
            EdgeEntry tmp = currEdge;
            path.add(tmp.endNode);
            currEdge = (AStarEdge) currEdge.parent;
            path.calcWeight(graph.getEdgeProps(tmp.edge, tmp.endNode));
        }
        path.addFrom(from);
        path.reverseOrder();
        return path;
    }

    public static class AStarEdge extends EdgeEntry {

        // the variable 'weight' is used to let heap select smallest *full* distance.
        // but to compare distance we need it only from start:
        double weightToCompare;

        public AStarEdge(int edgeId, int node, double weightForHeap, double weightToCompare) {
            super(edgeId, node, weightForHeap);
            // round makes distance smaller => heuristic should underestimate the distance!
            this.weightToCompare = (float) weightToCompare;
        }
    }
}
