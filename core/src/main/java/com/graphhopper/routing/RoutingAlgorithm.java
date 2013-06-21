/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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

import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.util.NotThreadSafe;

/**
 * Calculates the shortest path from the specified node ids. Can be used only once.
 * <p/>
 * @author Peter Karich
 */
@NotThreadSafe
public interface RoutingAlgorithm
{
    /**
     * Calculates the fastest or shortest path.
     * <p/>
     * @return the path but check the method found() to make sure if the path is valid.
     */
    Path calcPath( int from, int to );

    /**
     * Changes the used weight calculation (e.g. fastest, shortest). Default is shortest.
     */
    RoutingAlgorithm setType( WeightCalculation calc );

    /**
     * @return name of this algorithm
     */
    String getName();

    /**
     * Returns the visited nodes after searching. Useful for debugging.
     */
    int getVisitedNodes();
}
