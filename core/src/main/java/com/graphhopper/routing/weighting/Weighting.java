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

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;

/**
 * Specifies how the best route is calculated. E.g. the fastest or shortest route.
 * <p>
 *
 * @author Peter Karich
 */
public interface Weighting {
    /**
     * Used only for the heuristic estimation in A*
     *
     * @return minimal weight for the specified distance in meter. E.g. if you calculate the fastest
     * way the return value is 'distance/max_velocity'
     */
    double getMinWeight(double distance);

    /**
     * This method calculates the weighting a certain edgeState should be associated. E.g. a high
     * value indicates that the edge should be avoided. Make sure that this method is very fast and
     * optimized as this is called potentially millions of times for one route or a lot more for
     * nearly any preprocessing phase.
     *
     * @param edgeState        the edge for which the weight should be calculated
     * @param reverse          if the specified edge is specified in reverse direction e.g. from the reverse
     *                         case of a bidirectional search.
     * @param prevOrNextEdgeId if reverse is false this has to be the previous edgeId, if true it
     *                         has to be the next edgeId in the direction from start to end.
     * @return the calculated weight with the specified velocity has to be in the range of 0 and
     * +Infinity. Make sure your method does not return NaN which can e.g. occur for 0/0.
     */
    double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId);

    /**
     * This method calculates the time taken (in milli seconds) for the specified edgeState and
     * optionally include the turn costs (in seconds) of the previous (or next) edgeId via
     * prevOrNextEdgeId. Typically used for post-processing and on only a few thousand edges.
     */
    long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId);

    FlagEncoder getFlagEncoder();

    String getName();

    /**
     * Returns true if the specified weighting and encoder matches to this Weighting.
     */
    boolean matches(HintsMap map);
}
