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
package com.graphhopper;

/**
 * Wrapper of the graphhopper online or offline API. Provides read only access.
 *
 * Usage:
 * <pre>
 *
 * // offline
 * GraphHopperAPI gh = new GraphHopper().setInMemory(true, true);
 * gh.load("graph-hopper-folder");
 *
 * // online
 * GraphHopperAPI gh = new GraphHopperWeb();
 * gh.load("http://your-graphhopper-service.com/api");
 *
 * gh.algorithm("astar");
 * GHResponse ph = gh.route(new GeoPoint(fromLat, fromLon), new GeoPoint(toLat, toLon));
 * print(ph.distance() + " " + ph.time());
 * PointList points = response.createPoints();
 * for(int i = 0; i &lt; points.size(); i++) {
 *    add(point.latitude(i), point.longitude(i));
 * }
 *
 * </pre>
 *
 * @author Peter Karich
 */
public interface GraphHopperAPI {

    /**
     * Load the specified service or graph file (graphhopper or OSM).
     */
    boolean load(String urlOrFile);

    /**
     * Calculates the path from specified request with startPoint to endPoint.
     *
     * @throws Exceptions if points not found or other problems occur.
     */
    GHResponse route(GHRequest request);
}
