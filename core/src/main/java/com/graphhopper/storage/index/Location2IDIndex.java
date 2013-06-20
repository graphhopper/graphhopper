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
package com.graphhopper.storage.index;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Storable;

/**
 * Provides a way to map realword data "lat,lon" to internal ids/indices of a
 * memory efficient graph - often just implemented as an array.
 *
 * The implementations of findID needs to be thread safe!
 *
 * @author Peter Karich
 */
public interface Location2IDIndex extends Storable<Location2IDIndex> {

    /**
     * Integer value to specify the resolution of this location index. The
     * higher the better the resolution.
     */
    Location2IDIndex resolution(int resolution);

    /**
     * Creates this index - to be called once before findID.
     */
    Location2IDIndex prepareIndex();

    /**
     * @return the node id for the specified geo location (latitude,longitude)
     */
    int findID(double lat, double lon);

    /**
     * @param edgeFilter if a graph supports multiple vehicles we have to make
     * sure that the entry node into the graph is accessible from a selected
     * vehicle. E.g. if you have a FOOT-query do:      <pre>
     *   new DefaultEdgeFilter(new FootFlagEncoder());
     * </pre>
     * @return node id for the specfied location. The node id has at least one
     * edge which is accepted from the specified edgeFilter
     */
    LocationIDResult findClosest(double lat, double lon, EdgeFilter edgeFilter);

    /**
     * @param approxDist If false this makes initialization and querying faster
     * but less precise.
     */
    Location2IDIndex precision(boolean approxDist);
}
