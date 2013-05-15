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
package com.graphhopper.storage.index;

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
     * @return graph id for specified point (lat,lon)
     */
    LocationIDResult findID(double lat, double lon);

    /**
     * @param approxDist If false this makes initialization and querying faster
     * but less precise.
     */
    Location2IDIndex precision(boolean approxDist);
}
