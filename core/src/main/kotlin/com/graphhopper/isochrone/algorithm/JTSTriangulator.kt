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
package com.graphhopper.isochrone.algorithm

import com.graphhopper.routing.RouterConfig
import com.graphhopper.routing.querygraph.QueryGraph
import com.graphhopper.storage.index.Snap
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.FetchMode
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder
import org.locationtech.jts.triangulate.quadedge.Vertex
import java.util.function.ToDoubleFunction

class JTSTriangulator(private val routerConfig: RouterConfig) : Triangulator {

    override fun triangulate(
        snap: Snap,
        queryGraph: QueryGraph,
        shortestPathTree: ShortestPathTree,
        fz: ToDoubleFunction<ShortestPathTree.IsoLabel>,
        tolerance: Double
    ): Triangulator.Result {
        val na = queryGraph.nodeAccess
        val sites = ArrayList<Coordinate>()
        shortestPathTree.search(snap.closestNode) { label ->
            val exploreValue = fz.applyAsDouble(label)
            val lat = na.getLat(label.node)
            val lon = na.getLon(label.node)
            val site = Coordinate(lon, lat)
            site.z = exploreValue
            sites.add(site)

            // add a pillar node to increase precision a bit for longer roads
            if (label.parent != null) {
                val edge = queryGraph.getEdgeIteratorState(label.edge, label.node)!!
                val innerPoints = edge.fetchWayGeometry(FetchMode.PILLAR_ONLY)
                if (innerPoints.size() > 0) {
                    var midIndex = innerPoints.size() / 2
                    if (innerPoints.size() % 2 == 0 && edge.get(EdgeIteratorState.REVERSE_STATE))
                        // For edge-based routing we might have explored the same edge in two different directions.
                        // Here we make sure we only include the **same** point twice instead of two different ones.
                        midIndex -= 1
                    val lat2 = innerPoints.getLat(midIndex)
                    val lon2 = innerPoints.getLon(midIndex)
                    val site2 = Coordinate(lon2, lat2)
                    site2.z = exploreValue
                    sites.add(site2)
                }
            }
        }

        if (sites.size > routerConfig.getMaxVisitedNodes() / 3)
            throw IllegalArgumentException("Too many nodes would be included in post processing (" + sites.size + "). Let us know if you need this increased.")

        // Sites may contain repeated coordinates. Especially for edge-based traversal, that's expected -- we visit
        // each node multiple times.
        // But that's okay, the triangulator de-dupes by itself, and it keeps the first z-value it sees, which is
        // what we want.

        val triangulationBuilder = DelaunayTriangulationBuilder()
        triangulationBuilder.setSites(sites)
        triangulationBuilder.setTolerance(tolerance)
        val convexHull = triangulationBuilder.getEdges(GeometryFactory()).convexHull()

        // If there's only one site (and presumably also if the convex hull is otherwise degenerated),
        // the triangulation only contains the frame, and not the site within the frame. Not sure if I agree with that.
        // See ConformingDelaunayTriangulator, it does include a buffer for the frame, but that buffer is zero
        // in these cases.
        // It leads to the following follow-up defect:
        // computeIsoline fails (returns an empty Multipolygon). This is clearly wrong, since
        // the idea is that every real (non-frame) vertex has positive-length-edges around it that can be traversed
        // to get a non-empty polygon.
        // So we exclude this case for now (it is indeed only a corner-case).

        if (convexHull !is Polygon) {
            throw IllegalArgumentException("Too few points found. "
                    + "Please try a different 'point' or a larger 'time_limit'.")
        }

        val tin = triangulationBuilder.subdivision
        @Suppress("UNCHECKED_CAST")
        for (vertex in tin.getVertices(true) as Collection<Vertex>) {
            if (tin.isFrameVertex(vertex)) {
                vertex.z = Double.MAX_VALUE
            }
        }
        val triangulation = ReadableTriangulation.wrap(tin)
        return Triangulator.Result(triangulation, triangulation.getEdges())
    }
}
