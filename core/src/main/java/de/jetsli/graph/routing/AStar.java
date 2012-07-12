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
import de.jetsli.graph.storage.Edge;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.util.ApproxCalcDistance;
import de.jetsli.graph.util.CalcDistance;
import de.jetsli.graph.util.EdgeIdIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.PriorityQueue;

/**
 * @author Peter Karich
 */
public class AStar implements RoutingAlgorithm {

    private Graph graph;
    private CalcDistance approxDist = new CalcDistance();

    public AStar(Graph g) {
        this.graph = g;
    }

    @Override public Path calcShortestPath(int from, int to) {
        MyOpenBitSet closedSet = new MyOpenBitSet(graph.getNodes());
        TIntObjectMap<AStarEdge> map = new TIntObjectHashMap<AStarEdge>();
        PriorityQueue<AStarEdge> openSet = new PriorityQueue<AStarEdge>();
        double lat = graph.getLatitude(to);
        double lon = graph.getLongitude(to);
        double tmpLat = graph.getLatitude(from);
        double tmpLon = graph.getLongitude(from);
        double distToGoal = approxDist.calcDistKm(lat, lon, tmpLat, tmpLon);
        double fDistComplete = 0 + distToGoal;
        AStarEdge fromEntry = new AStarEdge(from, 0, fDistComplete);
        AStarEdge curr = fromEntry;
        while (true) {
            int currVertex = curr.node;
            EdgeIdIterator iter = graph.getOutgoing(currVertex);
            while (iter.next()) {
                int neighborNode = iter.nodeId();
                if (closedSet.contains(neighborNode))
                    continue;

                // possibilities: 
                // 1. we could use landmarks and less expensive triangular formular
                // which satisfies the h(x) requirement instead of this expensive real calculation
                // 2. use less expensive calc distance 
                // (e.g. normed dist ... hmh but then entry.distance of edges needs to be normed too!)                
                double gDist = iter.distance() + curr.distToCompare;
                AStarEdge de = map.get(neighborNode);
                if (de == null) {
                    // dup code
                    tmpLat = graph.getLatitude(neighborNode);
                    tmpLon = graph.getLongitude(neighborNode);
                    distToGoal = approxDist.calcDistKm(lat, lon, tmpLat, tmpLon);
                    fDistComplete = gDist + distToGoal;
                    // --

                    de = new AStarEdge(neighborNode, gDist, fDistComplete);
                    de.prevEntry = curr;
                    map.put(neighborNode, de);
                    openSet.add(de);
                } else if (de.distToCompare > gDist) {
                    // dup code
                    tmpLat = graph.getLatitude(neighborNode);
                    tmpLon = graph.getLongitude(neighborNode);
                    distToGoal = approxDist.calcDistKm(lat, lon, tmpLat, tmpLon);
                    fDistComplete = gDist + distToGoal;
                    // --

                    openSet.remove(de);
                    de.distToCompare = gDist;
                    de.distance = fDistComplete;
                    de.prevEntry = curr;
                    openSet.add(de);
                }

                updateShortest(de, neighborNode);
            }
            if (to == currVertex)
                break;

            closedSet.add(currVertex);
            curr = openSet.poll();
            if (curr == null)
                return null;
        }

        // extract path from shortest-path-tree
        Path path = new Path();
        double distance = 0;
        while (curr.node != from) {
            int tmpFrom = curr.node;
            path.add(curr);
            curr = (AStarEdge) curr.prevEntry;
            distance += getDistance(tmpFrom, curr.node);
        }
        path.add(fromEntry);
        path.reverseOrder();
        path.setDistance(distance);
        return path;
    }

    private static class AStarEdge extends Edge {

        // the variable 'distance' is used to let heap select smallest *full* distance.
        // but to compare distance we need it only from start:
        double distToCompare;

        public AStarEdge(int loc, double distToCompare, double distForHeap) {
            super(loc, distForHeap);
            this.distToCompare = distToCompare;
        }
    }

    public double getDistance(int from, int to) {
        EdgeIdIterator iter = graph.getIncoming(from);
        while (iter.next()) {
            if (iter.nodeId() == to)
                return iter.distance();
        }
        throw new IllegalStateException("couldn't extract path. distance for " + from + " to " + to + " not found!?");
    }

    public void updateShortest(Edge shortestDE, int currLoc) {
    }

    @Override public RoutingAlgorithm clear() {
        return this;
    }
}
