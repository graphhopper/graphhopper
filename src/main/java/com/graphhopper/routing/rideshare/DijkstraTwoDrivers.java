/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.rideshare;

import com.graphhopper.routing.DijkstraBidirectionRef;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.VehicleEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.EdgeEntry;

/**
 * @author Peter Karich
 */
public class DijkstraTwoDrivers {

    private Graph graph;
    private DijkstraBidirectionRef driverA;
    private DijkstraBidirectionRef driverB;
    private int meetingPoint;
    private int fromA, toA;
    private int fromB, toB;
    private double overallDistance = Double.MAX_VALUE;
    private VehicleEncoder carEncoder;

    public DijkstraTwoDrivers(Graph graph) {
        this.graph = graph;
        this.carEncoder = new CarFlagEncoder();
    }

    public void setDriverA(int fromA, int toA) {
        this.fromA = fromA;
        this.toA = toA;
    }

    public void setDriverB(int fromB, int toB) {
        this.fromB = fromB;
        this.toB = toB;
    }

    public void calcPath() {
        // There are two bidirectional dijkstras going on: two for driver A and two for B.
        // Now update the overall extractPath path only when all 4 extractPath-path-trees (spt's) contain the vertex (from the relaxed edges of the current spt).
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
        // if max(A1-extractPath-path-tree, A2-spt) + max(B1-spt, B2-spt) + DELTA >= last extractPath path

        // But problem regarding the DELTA: distanceA + distanceB can be of very different length. e.g. 7+1==4+4
        // Should we add e.g. the difference of both detours or different of percentage to the distance?
        // E.g. normally tourA is 7. detour is 1, tourB is 10, detour is 5 => DELTA == 4
        // But the DELTA is not monotonically increasing! => we cannot easily break. 
        // Hmmh but this is not really working also because the max+max is not really correct

        // And the more I think about it. it is more: max(A1-extractPath-path-tree, A2-spt, B1-spt, B2-spt) + DELTA >= last extractPath path
        // As the newly discovered point M (from A1-spt) needs old information of the spt's from A2,B1 and B2
        // We can use a heuristical value which assumes the sp of both direct paths are found:
        // break if max(currA1+currA2, currB1+currB2) > 1.1 * max(sp-A,sp-B)        
        // => so found only the detour if its at maximum 20% longer then the actual distance.
        // to further improve processing we can break the calculation of B before A if sp-B is smaller than A

        // HA! or just stop the A search alone  if min(currA1, currA2) > personalFactor * sp-A
        //     and the same for the B search    if min(currB1, currB2) > personalFactor * sp-B
        // default is personalFactor=1.1?
        // -> hmmh should this be lower to make it faster? because it is min(currA1, currA2) and not currA1+currA2

        driverA = new DijkstraBidirectionCombined(graph, carEncoder) {
            @Override public DijkstraBidirectionRef getOtherDriver() {
                return driverB;
            }
        }.initFrom(fromA).initTo(toA).initPath();

        driverB = new DijkstraBidirectionCombined(graph, carEncoder) {
            @Override public DijkstraBidirectionRef getOtherDriver() {
                return driverA;
            }
        }.initFrom(fromB).initTo(toB).initPath();

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
        return driverA.extractPath();
    }

    public Path getBestForB() {
        return driverB.extractPath();
    }

    public int getMeetingPoint() {
        return meetingPoint;
    }

    private abstract class DijkstraBidirectionCombined extends DijkstraBidirectionRef {

        public DijkstraBidirectionCombined(Graph graph, VehicleEncoder encoder) {
            super(graph, encoder);
        }

        public abstract DijkstraBidirectionRef getOtherDriver();

        @Override public boolean checkFinishCondition() {
            if (currFrom == null)
                return currTo.weight >= shortest.weight();
            else if (currTo == null)
                return currFrom.weight >= shortest.weight();

            return Math.min(currFrom.weight, currTo.weight) >= shortest.weight();
        }

        @Override protected void updateShortest(EdgeEntry shortestDE, int currLoc) {
            EdgeEntry fromOther = getOtherDriver().shortestWeightFrom(currLoc);
            EdgeEntry toOther = getOtherDriver().shortestWeightTo(currLoc);
            EdgeEntry entryOther = shortestWeightMapOther.get(currLoc);
            if (fromOther == null || toOther == null || entryOther == null)
                return;

            // update μ
            double shortestOther = fromOther.weight + toOther.weight;
            double shortestCurrent = shortestDE.weight + entryOther.weight;
            double newShortest = shortestCurrent + shortestOther;
            if (newShortest < overallDistance) {
                // LATER: minimize not only the sum but also the difference => multi modal search!
                overallDistance = newShortest;
                meetingPoint = currLoc;

                getOtherDriver().shortest.edgeEntry(fromOther);
                getOtherDriver().shortest.edgeEntryTo(toOther);
                getOtherDriver().shortest.weight(shortestOther);

                shortest.edgeEntry(shortestDE);
                shortest.edgeEntryTo(entryOther);
                shortest.weight(shortestCurrent);
            }
        }
    }
}
