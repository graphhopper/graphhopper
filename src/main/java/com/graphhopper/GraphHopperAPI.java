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
 * GraphHopperAPI gh = new GraphHopper();
 * gh.load("http://your-graphhopper-service.com/api");
 *
 * gh.algorithm("astar");
 * GHResponse ph = gh.route(new GeoPoint(fromLat, fromLon), new GeoPoint(toLat, toLon));
 * print(ph.distance() + " " + ph.time());
 * for(GeoPoint point : ph.points()) {
 *    add(point.lat, point.lon);
 * }
 *
 * // TODO LATER provide this
 * for(PathSegment ps : ph.segments()) {
 *   print(ps.instructions().text() + " " + ps.distance() + " " + ps.time());
 *   for(GeoPoint p : ps.extractPoints()) {
 *      addToRoute(p.lat, p.lon);
 *   }
 * }
 * </pre>
 *
 * @author Peter Karich
 */
public interface GraphHopperAPI {

    /**
     * Load the specified service or graph file (graphhopper or OSM).
     */
    GraphHopperAPI load(String urlOrFile);

    /**
     * Calculates the path from specified request with startPoint to endPoint.
     */
    GHResponse route(GHRequest request);
}
