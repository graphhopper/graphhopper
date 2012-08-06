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
package de.jetsli.graph.routing.rideshare;

import de.jetsli.graph.routing.DijkstraBidirectionRef;
import de.jetsli.graph.routing.Path;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.EdgeEntry;

/**
 * @author Peter Karich, info@jetsli.de
 */
public class DijkstraTwoDrivers {

    private Graph graph;
    private DijkstraBidirectionRef driverA;
    private DijkstraBidirectionRef driverB;
    private int meetingPoint;
    private int fromA, toA;
    private int fromB, toB;
    private double overallDistance = Double.MAX_VALUE;

    public DijkstraTwoDrivers(Graph graph) {
        this.graph = graph;
    }

    public void setDriverA(int fromA, int toA) {
        this.fromA = fromA;
        this.toA = toA;
    }

    public void setDriverB(int fromB, int toB) {
        this.fromB = fromB;
        this.toB = toB;
    }

    public void calcShortestPath() {
        // There are two bidirectional dijkstras going on: two for driver A and two for B.
        // Now update the overall shortest path only when all 4 shortest-path-trees (spt's) contain the vertex (from the relaxed edges of the current spt).
        // The breaking condition is different to normal bi-dijkstra - see **
        //
        // The meeting point is M and not N where all 4 spt's have minimal values:
        //         B1
        //            
        //          N   B2
        //             M
        //   A1       A2
        //        
        // And so the A1-spt needs to reach not only the A2-spt but even the point M
        //  ** break search only 
        // if max(A1-shortest-path-tree, A2-spt) + max(B1-spt, B2-spt) + DELTA >= last shortest path

        // But problem regarding the DELTA: distanceA + distanceB can be of very different length. e.g. 7+1==4+4
        // Should we add e.g. the difference of both detours or different of percentage to the distance?
        // E.g. normally tourA is 7. detour is 1, tourB is 10, detour is 5 => DELTA == 4
        // But the DELTA is not monotonically increasing! => we cannot easily break. 
        // Hmmh but this is not really working also because the max+max is not really correct

        // And the more I think about it. it is more: max(A1-shortest-path-tree, A2-spt, B1-spt, B2-spt) + DELTA >= last shortest path
        // As the newly discovered point M (from A1-spt) needs old information of the spt's from A2,B1 and B2
        // We can use a heuristical value which assumes the sp of both direct paths are found:
        // break if max(currA1+currA2, currB1+currB2) > 1.1 * max(sp-A,sp-B)        
        // => so found only the detour if its at maximum 20% longer then the actual distance.
        // to further improve processing we can break the calculation of B before A if sp-B is smaller than A

        // HA! or just stop the A search alone  if min(currA1, currA2) > personalFactor * sp-A
        //     and the same for the B search    if min(currB1, currB2) > personalFactor * sp-B
        // default is personalFactor=1.1?
        // -> hmmh should this be lower to make it faster? because it is min(currA1, currA2) and not currA1+currA2

        driverA = new DijkstraBidirectionCombined(graph) {
            @Override public DijkstraBidirectionRef getOtherDriver() {
                return driverB;
            }
        }.initFrom(fromA).initTo(toA);

        driverB = new DijkstraBidirectionCombined(graph) {
            @Override public DijkstraBidirectionRef getOtherDriver() {
                return driverA;
            }
        }.initFrom(fromB).initTo(toB);

        while (true) {
            driverA.fillEdgesFrom();
            driverA.fillEdgesTo();
            driverB.fillEdgesFrom();
            driverB.fillEdgesTo();

            if (driverA.checkFinishCondition() && driverB.checkFinishCondition())
                break;
        }
    }

    public Path getBestForA() {
        return driverA.getShortest();
    }

    public Path getBestForB() {
        return driverB.getShortest();
    }

    public int getMeetingPoint() {
        return meetingPoint;
    }

    private abstract class DijkstraBidirectionCombined extends DijkstraBidirectionRef {

        public DijkstraBidirectionCombined(Graph graph) {
            super(graph);
        }

        public abstract DijkstraBidirectionRef getOtherDriver();

        @Override public boolean checkFinishCondition() {
            if (currFrom == null)
                return currTo.weight >= shortest.weight;
            else if (currTo == null)
                return currFrom.weight >= shortest.weight;

            return Math.min(currFrom.weight, currTo.weight) >= shortest.weight;
        }

        @Override public void updateShortest(EdgeEntry shortestDE, int currLoc) {
            EdgeEntry fromOther = getOtherDriver().getShortestWeightFrom(currLoc);
            EdgeEntry toOther = getOtherDriver().getShortestWeightTo(currLoc);
            EdgeEntry entryOther = shortestWeightMapOther.get(currLoc);
            if (fromOther == null || toOther == null || entryOther == null)
                return;

            // update μ
            double shortestOther = fromOther.weight + toOther.weight;
            double shortestCurrent = shortestDE.weight + entryOther.weight;
            double newShortest = shortestCurrent + shortestOther;
            if (newShortest < overallDistance) {
                // TODO: minimize not only the sum but also the difference => multi modal search!
                overallDistance = newShortest;
                meetingPoint = currLoc;

                getOtherDriver().shortest.edgeFrom = fromOther;
                getOtherDriver().shortest.edgeTo = toOther;
                getOtherDriver().shortest.weight = shortestOther;

                shortest.edgeFrom = shortestDE;
                shortest.edgeTo = entryOther;
                shortest.weight = shortestCurrent;
            }
        }
    }
}
