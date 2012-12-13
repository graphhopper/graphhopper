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

import com.graphhopper.util.shapes.GeoPoint;

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
 * PathHelper ph = gh.route(new GeoPoint(fromLat, fromLon), new GeoPoint(toLat, toLon));
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

    GraphHopperAPI load(String urlOrFile);

    /**
     * Reduces the node count of the resulting path. Useful for performance or if you're using the
     * web version for network latency. If a high value in meter is specified the route will be less
     * precise along the real networks.
     */
    GraphHopperAPI minPathPrecision(double precision);

    /**
     * Possible values: astar (A* algorithm), astarbi (bidirectional A*) dijkstra (Dijkstra),
     * dijkstrabi and dijkstraNative (a bit faster bidirectional Dijkstra).
     */
    GraphHopperAPI algorithm(String algo);

    /**
     * Calculates the path from specified startPoint to endPoint.
     */
    PathHelper route(GeoPoint from, GeoPoint to);
}
