/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package com.graphhopper.isochrone.algorithm

import org.locationtech.jts.algorithm.Area
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.geom.prep.PreparedPolygon
import org.locationtech.jts.triangulate.quadedge.Vertex
import java.util.function.ToIntBiFunction

/**
 *
 * Adapted from org.opentripplanner.common.geometry.DelaunayIsolineBuilder,
 * which is under LGPL.
 *
 * @author laurent
 * @author michaz
 *
 */
class ContourBuilder(private val triangulation: ReadableTriangulation) {

    companion object {
        private const val EPSILON = 0.000001
    }

    // OpenStreetMap has 1E7 (coordinates with 7 decimal places), and we walk on the edges of that grid,
    // so we use 1E8 so we can, in theory, always wedge a point petween any two OSM points.
    private val geometryFactory = GeometryFactory(PrecisionModel(1E8))

    fun computeIsoline(z0: Double, seedEdges: Collection<ReadableQuadEdge>): MultiPolygon {
        val cut = ToIntBiFunction<Vertex, Vertex> { orig, dest ->
            val za = orig.z
            val zb = dest.z
            if (za <= z0 && zb > z0) 1
            else if (za > z0 && zb <= z0) -1
            else 0
        }
        return computeIsoline(cut, seedEdges)
    }

    fun computeIsoline(cut: ToIntBiFunction<Vertex, Vertex>, seedEdges: Collection<ReadableQuadEdge>): MultiPolygon {
        val processed = HashSet<ReadableQuadEdge>()
        val rings = ArrayList<LinearRing>()

        for (f in seedEdges) {
            var e = f.getPrimary()
            if (processed.contains(e))
                continue
            processed.add(e)
            val cut0 = cut.applyAsInt(e.orig(), e.dest())
            if (cut0 == 0) {
                continue // While, next edge
            }
            val polyPoints = ArrayList<Coordinate>()
            var ccw = cut0 > 0
            while (true) {
                // Add a point to polyline
                val cC: Coordinate = if (isFrameVertex(e.orig())) {
                    moveEpsilonTowards(e.dest().coordinate, e.orig().coordinate)
                } else if (isFrameVertex(e.dest())) {
                    moveEpsilonTowards(e.orig().coordinate, e.dest().coordinate)
                } else {
                    e.orig().midPoint(e.dest()).coordinate
                }
                // Strip z coordinate
                polyPoints.add(Coordinate(cC.x, cC.y))
                processed.add(e)
                val edge1 = if (ccw) e.oNext().getPrimary() else e.oPrev().getPrimary()
                val edge2 = if (ccw) e.dPrev().getPrimary() else e.dNext().getPrimary()
                @Suppress("SENSELESS_COMPARISON")
                val cut1 = if (edge1 == null) 0 else cut.applyAsInt(edge1.orig(), edge1.dest())
                @Suppress("SENSELESS_COMPARISON")
                val cut2 = if (edge2 == null) 0 else cut.applyAsInt(edge2.orig(), edge2.dest())
                val ok1 = cut1 != 0 && !processed.contains(edge1)
                val ok2 = cut2 != 0 && !processed.contains(edge2)
                if (ok1) {
                    e = edge1
                    ccw = cut1 > 0
                } else if (ok2) {
                    e = edge2
                    ccw = cut2 > 0
                } else {
                    // This must be the end of the polyline...
                    break
                }
            }
            // Close the polyline
            polyPoints.add(polyPoints[0])
            if (polyPoints.size >= 4) {
                val ring = geometryFactory.createLinearRing(polyPoints.toTypedArray())
                rings.add(ring)
            }
        }
        val isolinePolygons = punchHoles(rings)
        return geometryFactory.createMultiPolygon(isolinePolygons.toTypedArray())
    }

    private fun isFrameVertex(v: Vertex): Boolean = v.z == Double.MAX_VALUE

    private fun moveEpsilonTowards(coordinate: Coordinate, distantFrameCoordinate: Coordinate): Coordinate {
        return Coordinate(
            coordinate.x + EPSILON * (distantFrameCoordinate.x - coordinate.x),
            coordinate.y + EPSILON * (distantFrameCoordinate.y - coordinate.y)
        )
    }

    private fun punchHoles(rings: List<LinearRing>): List<Polygon> {
        val shells = ArrayList<PreparedPolygon>(rings.size)
        val holes = ArrayList<LinearRing>(rings.size / 2)
        // 1. Split the polygon list in two: shells and holes (CCW and CW)
        for (ring in rings) {
            if (Area.ofRingSigned(ring.coordinateSequence) > 0.0)
                holes.add(ring)
            else
                shells.add(PreparedPolygon(geometryFactory.createPolygon(ring)))
        }
        // 2. Sort the shells based on number of points to optimize step 3.
        shells.sortWith { o1, o2 -> o2.geometry.numPoints - o1.geometry.numPoints }
        for (shell in shells) {
            shell.geometry.userData = ArrayList<LinearRing>()
        }
        // 3. For each hole, determine which shell it fits in.
        for (hole in holes) {
            // Probably most of the time, the first shell will be the one
            val shell = shells.firstOrNull { it.contains(hole) }
                ?: throw RuntimeException("Found a hole without a shell.")
            @Suppress("UNCHECKED_CAST")
            (shell.geometry.userData as MutableList<LinearRing>).add(hole)
        }
        // 4. Build the list of punched polygons
        val punched = ArrayList<Polygon>(shells.size)
        for (shell in shells) {
            @Suppress("UNCHECKED_CAST")
            val shellHoles = shell.geometry.userData as List<LinearRing>
            punched.add(
                geometryFactory.createPolygon(
                    (shell.geometry as Polygon).exteriorRing,
                    shellHoles.toTypedArray()
                )
            )
        }
        return punched
    }
}
