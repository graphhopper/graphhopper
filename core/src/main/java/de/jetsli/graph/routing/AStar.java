/*
 *  Copyright 2012 Peter Karich info@jetsli.de
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

import de.jetsli.graph.coll.MyOpenBitSet;
import de.jetsli.graph.routing.util.EdgeFlags;
import de.jetsli.graph.storage.EdgeEntry;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.util.ApproxCalcDistance;
import de.jetsli.graph.util.CalcDistance;
import de.jetsli.graph.util.EdgeIterator;
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
public class AStar extends AbstractRoutingAlgorithm {

    private CalcDistance dist = new ApproxCalcDistance();

    public AStar(Graph g) {
        super(g);
    }

    /**
     * @param fast if true it enables approximative distance calculation from lat,lon values
     */
    public AStar setFast(boolean fast) {
        if (fast)
            dist = new ApproxCalcDistance();
        else
            dist = new CalcDistance();
        return this;
    }

    @Override public Path calcPath(int from, int to) {
        MyOpenBitSet closedSet = new MyOpenBitSet(graph.getNodes());
        TIntObjectMap<AStarEdge> map = new TIntObjectHashMap<AStarEdge>();
        PriorityQueue<AStarEdge> prioQueueOpenSet = new PriorityQueue<AStarEdge>();
        double toLat = graph.getLatitude(to);
        double toLon = graph.getLongitude(to);
        double tmpLat = graph.getLatitude(from);
        double tmpLon = graph.getLongitude(from);
        double currWeightToGoal = dist.calcDistKm(toLat, toLon, tmpLat, tmpLon);
        currWeightToGoal = weightCalc.apply(currWeightToGoal);
        double distEstimation = 0 + currWeightToGoal;
        AStarEdge fromEntry = new AStarEdge(from, distEstimation, 0);
        AStarEdge currEdge = fromEntry;
        while (true) {
            int currVertex = currEdge.node;
            EdgeIterator iter = graph.getOutgoing(currVertex);
            while (iter.next()) {
                int neighborNode = iter.node();
                if (closedSet.contains(neighborNode))
                    continue;

                float alreadyVisitedWeight = (float) weightCalc.getWeight(iter) + currEdge.weightToCompare;
                AStarEdge nEdge = map.get(neighborNode);
                if (nEdge == null || nEdge.weightToCompare > alreadyVisitedWeight) {
                    tmpLat = graph.getLatitude(neighborNode);
                    tmpLon = graph.getLongitude(neighborNode);
                    currWeightToGoal = dist.calcDistKm(toLat, toLon, tmpLat, tmpLon);
                    currWeightToGoal = weightCalc.apply(currWeightToGoal);
                    distEstimation = alreadyVisitedWeight + currWeightToGoal;

                    if (nEdge == null) {
                        nEdge = new AStarEdge(neighborNode, distEstimation, alreadyVisitedWeight);
                        map.put(neighborNode, nEdge);
                    } else {
                        prioQueueOpenSet.remove(nEdge);
                        nEdge.weightToCompare = alreadyVisitedWeight;
                        nEdge.weight = distEstimation;
                    }
                    nEdge.prevEntry = currEdge;
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

        // extract path from shortest-path-tree
        Path path = new Path(weightCalc);
        while (currEdge.prevEntry != null) {
            int tmpFrom = currEdge.node;
            path.add(tmpFrom);
            currEdge = (AStarEdge) currEdge.prevEntry;
            path.updateProperties(graph.getIncoming(tmpFrom), currEdge.node);
        }
        path.add(fromEntry.node);
        path.reverseOrder();
        return path;
    }    

    private static class AStarEdge extends EdgeEntry {

        // the variable 'weight' is used to let heap select smallest *full* distance.
        // but to compare distance we need it only from start:
        float weightToCompare;

        public AStarEdge(int loc, double weightForHeap, float weightToCompare) {
            super(loc, weightForHeap);
            // round makes distance smaller => heuristic should underestimate the distance!
            this.weightToCompare = (float) weightToCompare;
        }
    }
}
