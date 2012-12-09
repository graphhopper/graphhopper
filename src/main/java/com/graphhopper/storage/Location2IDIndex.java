/*
 *  Copyright 2012 Peter Karich 
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.storage;

/**
 * Provides a way to map realword data "lat,lon" to internal ids/indices of a memory efficient graph
 * - often just implemented as an array.
 *
 * The implementations of findID needs to be thread safe!
 *
 * @author Peter Karich
 */
public interface Location2IDIndex {

    /**
     * Creates this index - to be called once before findID.
     *
     * @param capacity specifies how many entries will be reserved. More entries means faster and
     * more precise queries.
     */
    Location2IDIndex prepareIndex(int capacity);

    /**
     * @return graph id for specified point (lat,lon)
     */
    int findID(double lat, double lon);

    /**
     * @param approxDist If false this makes initialization and querying faster but less precise.
     */
    Location2IDIndex setPrecision(boolean approxDist);

    float calcMemInMB();
}
