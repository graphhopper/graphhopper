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
package com.graphhopper;

/**
 * Wrapper of the graphhopper online or offline API. Provides read only access.
 * <p>
 *
 * @author Peter Karich
 */
public interface GraphHopperAPI {
    /**
     * Connects to the specified service (graphhopper URL) or loads a graph from the graphhopper
     * folder.
     * <p>
     *
     * @return true if successfully connected or loaded
     */
    boolean load(String urlOrFile);

    /**
     * Calculates the path from specified request visiting the specified locations.
     * <p>
     *
     * @return the response with the route and possible errors
     */
    GHResponse route(GHRequest request);
}
