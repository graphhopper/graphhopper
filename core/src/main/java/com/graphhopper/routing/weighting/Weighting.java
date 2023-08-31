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

import com.graphhopper.util.EdgeIteratorState;

/**
 * Specifies how the best route is calculated.
 *
 * @author Peter Karich
 */
public interface Weighting {
    int INFINITE_U_TURN_COSTS = -1;

    /**
     * Used only for the heuristic estimation in A*
     *
     * @return minimal weight for the specified distance in meter. E.g. if you calculate the fastest
     * way the return value is 'distance/max_velocity'
     */
    double getMinWeight(double distance);

    /**
     * This method calculates the weight of a given {@link EdgeIteratorState}. E.g. a high value indicates that the edge
     * should be avoided during shortest path search. Make sure that this method is very fast and optimized as this is
     * called potentially millions of times for one route or a lot more for nearly any preprocessing phase.
     *
     * @param edgeState the edge for which the weight should be calculated
     * @param reverse   if the specified edge is specified in reverse direction e.g. from the reverse
     *                  case of a bidirectional search.
     * @return the calculated weight with the specified velocity has to be in the range of 0 and
     * +Infinity. Make sure your method does not return NaN which can e.g. occur for 0/0.
     */
    double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse);

    /**
     * This method calculates the time taken (in milliseconds) to travel along the specified edgeState.
     * It is typically used for post-processing and on only a few thousand edges.
     */
    long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse);

    double calcTurnWeight(int inEdge, int viaNode, int outEdge);

    long calcTurnMillis(int inEdge, int viaNode, int outEdge);

    /**
     * This method can be used to check whether or not this weighting returns turn costs (or if they are all zero).
     * This is sometimes needed to do safety checks as not all graph algorithms can be run edge-based and might yield
     * wrong results when turn costs are applied while running node-based.
     */
    boolean hasTurnCosts();

    String getName();

}
