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

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.SimpleBooleanEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.WeightApproximator;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AStarBidirectionTest {
    @Test
    void infeasibleApproximator_noException() {
        // An infeasible approximator means that the weight of the entries polled from the priority queue does not
        // increase monotonically. Here we deliberately choose the approximations and edge distances such that the fwd
        // search first explores the 0-1-2-3-4 branch, then polls node 10 which causes an update for node 2, but the
        // search stops before node 2 is polled again such that nodes 3 and 4 cannot be updated, because the bwd search
        // already arrived and the stopping criterion is fulfilled. Node 2 still remains in the queue at this point.
        // This means the resulting path contains the invalid search tree branch 2(old)-3-4 and is not the shortest path,
        // because the SPTEntry for node 3 still points to the outdated/deleted entry for node 2.
        // We do not expect an exception, though, because for an infeasible approximator we cannot expect optimal paths.
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).create();
        // 0-1----2-3-4----5-6-7-8-9
        //    \  /
        //     10
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(0, 1).setDistance(100));
        // the distance 1-2 is longer than 1-10-2
        // we deliberately use 2-1 as storage direction, even though the edge points from 1 to 2, because this way
        // we can reproduce the 'Calculating time should not require to read speed from edge in wrong direction' error
        // from #2600
        graph.edge(2, 1).setDistance(300).set(accessEnc, false, true).set(speedEnc, 60);
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 3).setDistance(100));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(3, 4).setDistance(100));
        // distance 4-5 is very long
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(4, 5).setDistance(10_000));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(5, 6).setDistance(100));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(6, 7).setDistance(100));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(7, 8).setDistance(100));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(8, 9).setDistance(100));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 10).setDistance(100));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(10, 2).setDistance(100));

        Weighting weighting = new ShortestWeighting(accessEnc, speedEnc);
        AStarBidirection algo = new AStarBidirection(graph, weighting, TraversalMode.NODE_BASED);
        algo.setApproximation(new InfeasibleApproximator());
        Path path = algo.calcPath(0, 9);
        // the path is not the shortest path, but the suboptimal one we get for this approximator
        assertEquals(11_000, path.getDistance());
        assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), path.calcNodes());

        // this returns the correct path
        Dijkstra dijkstra = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED);
        Path optimalPath = dijkstra.calcPath(0, 9);
        assertEquals(10_900, optimalPath.getDistance());
        assertEquals(IntArrayList.from(0, 1, 10, 2, 3, 4, 5, 6, 7, 8, 9), optimalPath.calcNodes());
    }

    private static class InfeasibleApproximator implements WeightApproximator {
        int to;

        @Override
        public double approximate(int currentNode) {
            // we only consider the fwd search (going to 9). for the bwd search we simply approximate 0
            if (to != 9)
                return 0;
            // we use a super-simple approximator that just returns 0 for all nodes but one. for node 10 we use
            // a 'better' approximation that is still off. it is certainly not an over-approximation, because of the
            // long edge 4-5, but it makes the approximator infeasible, because
            // d(10, 2) + h(2) = 100 + 0 = 100 and h(10) = 1000, so it does not hold that d(10, 2) + h(2) >= h(10)
            if (currentNode == 10)
                return 1000;
            else
                return 0;
        }

        @Override
        public void setTo(int to) {
            this.to = to;
        }

        @Override
        public WeightApproximator reverse() {
            // the reverse approximator is a different object (different 'to' field), but runs the same code
            return new InfeasibleApproximator();
        }

        @Override
        public double getSlack() {
            return 0;
        }
    }
}