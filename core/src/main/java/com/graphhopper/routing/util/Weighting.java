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
package com.graphhopper.routing.util;

import com.graphhopper.util.EdgeIteratorState;

/**
 * Specifies how the best route is calculated. E.g. the fastest or shortest route.
 * <p>
 * @author Peter Karich
 */
public interface Weighting
{
    /**
     * Used only for the heuristic estimation in A
     * <p>
     * @return minimal weight. E.g. if you calculate the fastest way it is distance/maxVelocity
     */
    double getMinWeight( double distance );

    /**
     * @param edgeState the edge for which the weight should be calculated
     * @param reverse if the specified edge is specified in reverse direction e.g. from the reverse
     * case of a bidirectional search.
     * @param prevOrNextEdgeId if reverse is false this has to be the previous edgeId, if true it
     * has to be the next edgeId in the direction from start to end.
     * @return the calculated weight with the specified velocity has to be in the range of 0 and
     * +Infinity. Make sure your method does not return NaN which can e.g. occur for 0/0.
     */
    double calcWeight( EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId );

    FlagEncoder getFlagEncoder();

    String getName();

    /**
     * Returns true if the specified weighting and encoder matches to this Weighting.
     */
    boolean matches( HintsMap map );
}
