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

package com.graphhopper.routing.weighting;

import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.storage.BaseGraph;

/**
 * Implementations of this interface define how turn costs and turn times are calculated.
 */
public interface TurnCostProvider {
    /**
     * @return the turn weight of a transitions from the edge with id {@param inEdge} to the edge with id
     * {@param outEdge} at the node with id {@param viaNode}
     */
    double calcTurnWeight(int inEdge, int viaNode, int outEdge);

    /**
     * @return the time it takes to take a turn in milli-seconds
     * @see #calcTurnWeight(int, int, int)
     */
    long calcTurnMillis(int inEdge, int viaNode, int outEdge);

    interface TurnWeightMapping {
        double calcTurnWeight(BaseGraph graph, EdgeIntAccess edgeIntAccess, int inEdge, int viaNode, int outEdge);
    }

    TurnCostProvider NO_TURN_COST_PROVIDER = new TurnCostProvider() {
        @Override
        public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
            return 0;
        }

        @Override
        public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
            return 0;
        }

    };
}
