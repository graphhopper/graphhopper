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
package com.graphhopper.geohash;

import com.graphhopper.util.shapes.GHPoint;

/**
 * Defines the mapping between a one dimensional 'number' and a point (lat, lon) which is limited to
 * a defined bounds.
 * <p>
 *
 * @author Peter Karich
 */
public interface KeyAlgo {

    /**
     * Sets the bounds of the underlying key algorithm.
     */
    KeyAlgo setBounds(double minLonInit, double maxLonInit, double minLatInit, double maxLatInit);

    long encode(GHPoint coord);

    long encode(double lat, double lon);

    void decode(long spatialKey, GHPoint latLon);
}
