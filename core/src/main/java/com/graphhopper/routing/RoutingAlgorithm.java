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

import com.graphhopper.storage.index.QueryResult;
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
     * Calculates the best path between the specified nodes.
     * <p/>
     * @return the path. Call the method found() to make sure that the path is valid.
     */
    Path calcPath( int from, int to );

    /**
     * Calculates the best path between the specified query results from GPS lookup.
     * <p/>
     * Note: The underlying implementation introduces a state of the algorithm and so it is tightly
     * coupled to the query! Reusing this instance should be done carefully: only from within one
     * thread and only via this calcPath method.
     * <p/>
     * @return the path. Call the method found() to make sure that the path is valid.
     */
    Path calcPath( QueryResult from, QueryResult to );

    /**
     * @return name of this algorithm
     */
    String getName();

    /**
     * Returns the visited nodes after searching. Useful for debugging.
     */
    int getVisitedNodes();
}
