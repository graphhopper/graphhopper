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
package com.graphhopper.storage.index;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Storable;

/**
 * Provides a way to map real world data "lat,lon" to internal ids/indices of a memory efficient graph
 * - often just implemented as an array.
 * <p>
 * The implementations of findID needs to be thread safe!
 * <p>
 *
 * @author Peter Karich
 */
public interface LocationIndex extends Storable<LocationIndex> {
    /**
     * Integer value to specify the resolution of this location index. The higher the better the
     * resolution.
     */
    LocationIndex setResolution(int resolution);

    /**
     * Creates this index - to be called once before findID.
     */
    LocationIndex prepareIndex();

    /**
     * This method returns the closest QueryResult for the specified location (lat, lon) and only if
     * the filter accepts the edge as valid candidate (e.g. filtering away car-only results for bike
     * search)
     * <p>
     *
     * @param edgeFilter if a graph supports multiple vehicles we have to make sure that the entry
     *                   node into the graph is accessible from a selected vehicle. E.g. if you have a FOOT-query do:
     *                   <pre>DefaultEdgeFilter.allEdges(footFlagEncoder);</pre>
     * @return An object containing the closest node and edge for the specified location. The node id
     * has at least one edge which is accepted from the specified edgeFilter. If nothing is found
     * the method QueryResult.isValid will return false.
     */
    QueryResult findClosest(double lat, double lon, EdgeFilter edgeFilter);

    /**
     * @param approxDist false if initialization and querying should be faster but less precise.
     */
    LocationIndex setApproximation(boolean approxDist);

    void setSegmentSize(int bytes);
}
