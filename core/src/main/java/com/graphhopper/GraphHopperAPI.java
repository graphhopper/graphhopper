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
 * <p/>
 * Usage:
 * <pre>
 *
 * // init offline graph
 * GraphHopperAPI gh = new GraphHopper().setInMemory(true, true);
 * gh.load("graph-hopper-folder");
 *
 * // init online service
 * GraphHopperAPI gh = new GraphHopperWeb();
 * gh.load("http://your-graphhopper-service.com/api");
 *
 * gh.algorithm("astar");
 * GHResponse ph = gh.route(new GHRequest(new GHPoint(fromLat, fromLon), new GHPoint(toLat, toLon)));
 * print(ph.distance() + " " + ph.time());
 * PointList points = response.getPoints();
 * for(int i = 0; i &lt; points.size(); i++) {
 *    add(point.latitude(i), point.longitude(i));
 * }
 *
 * </pre>
 * <p/>
 * @author Peter Karich
 */
public interface GraphHopperAPI
{
    /**
     * Connects to the specified service (graphhopper URL) or loads a graph from the graphhopper
     * folder.
     * <p>
     * @return true if successfully connected or loaded
     */
    boolean load( String urlOrFile );

    /**
     * Calculates the path from specified request with startPoint to endPoint.
     * <p/>
     * @return the response with the route and possible errors
     */
    GHResponse route( GHRequest request );
}
