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

package com.graphhopper.isochrone.algorithm;

import org.locationtech.jts.algorithm.CGAlgorithms;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.prep.PreparedPolygon;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import java.util.*;
import java.util.function.ToIntBiFunction;

/**
 *
 * Adapted from org.opentripplanner.common.geometry.DelaunayIsolineBuilder,
 * which is under LGPL.
 *
 * @author laurent
 * @author michaz
 *
 */
public class ContourBuilder {

    private static final double EPSILON = 0.000001;

    // OpenStreetMap has 1E7 (coordinates with 7 decimal places), and we walk on the edges of that grid,
    // so we use 1E8 so we can, in theory, always wedge a point petween any two OSM points.
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(1E8));
    private final ReadableTriangulation triangulation;

    public ContourBuilder(ReadableTriangulation triangulation) {
        this.triangulation = triangulation;
    }

    public MultiPolygon computeIsoline(double z0, Collection<ReadableQuadEdge> seedEdges) {
        ToIntBiFunction<Vertex, Vertex> cut = (orig, dest) -> {
            double za = orig.getZ();
            double zb = dest.getZ();
            if (za <= z0 && zb > z0) return 1;
            if (za > z0 && zb <= z0) return -1;
            return 0;
        };
        return computeIsoline(cut, seedEdges);
    }

    public MultiPolygon computeIsoline(ToIntBiFunction<Vertex, Vertex> cut, Collection<ReadableQuadEdge> seedEdges) {
        Set<ReadableQuadEdge> processed = new HashSet<>();
        List<LinearRing> rings = new ArrayList<>();

        for (ReadableQuadEdge f : seedEdges) {
            ReadableQuadEdge e = f.getPrimary();
            if (processed.contains(e))
                continue;
            processed.add(e);
            int cut0 = cut.applyAsInt(e.orig(), e.dest());
            if (cut0 == 0) {
                continue; // While, next edge
            }
            List<Coordinate> polyPoints = new ArrayList<>();
            boolean ccw = cut0 > 0;
            while (true) {
                // Add a point to polyline
                Coordinate cC;
                if (isFrameVertex(e.orig())) {
                    cC = moveEpsilonTowards(e.dest().getCoordinate(), e.orig().getCoordinate());
                } else if (isFrameVertex(e.dest())) {
                    cC = moveEpsilonTowards(e.orig().getCoordinate(), e.dest().getCoordinate());
                } else {
                    cC = e.orig().midPoint(e.dest()).getCoordinate();
                }
                // Strip z coordinate
                polyPoints.add(new Coordinate(cC.x, cC.y));
                processed.add(e);
                ReadableQuadEdge E1 = ccw ? e.oNext().getPrimary() : e.oPrev().getPrimary();
                ReadableQuadEdge E2 = ccw ? e.dPrev().getPrimary() : e.dNext().getPrimary();
                int cut1 = E1 == null ? 0 : cut.applyAsInt(E1.orig(), E1.dest());
                int cut2 = E2 == null ? 0 : cut.applyAsInt(E2.orig(), E2.dest());
                boolean ok1 = cut1 != 0 && !processed.contains(E1);
                boolean ok2 = cut2 != 0 && !processed.contains(E2);
                if (ok1) {
                    e = E1;
                    ccw = cut1 > 0;
                } else if (ok2) {
                    e = E2;
                    ccw = cut2 > 0;
                } else {
                    // This must be the end of the polyline...
                    break;
                }
            }
            // Close the polyline
            polyPoints.add(polyPoints.get(0));
            if (polyPoints.size() >= 4) {
                LinearRing ring = geometryFactory.createLinearRing(polyPoints
                        .toArray(new Coordinate[polyPoints.size()]));
                rings.add(ring);
            }
        }
        List<Polygon> isolinePolygons = punchHoles(rings);
        return geometryFactory.createMultiPolygon(isolinePolygons.toArray(new Polygon[isolinePolygons.size()]));
    }

    private boolean isFrameVertex(Vertex v) {
        return v.getZ() == Double.MAX_VALUE;
    }

    private Coordinate moveEpsilonTowards(Coordinate coordinate, Coordinate distantFrameCoordinate) {
        return new Coordinate(coordinate.x + EPSILON * (distantFrameCoordinate.x - coordinate.x), coordinate.y + EPSILON * (distantFrameCoordinate.y - coordinate.y));
    }

    @SuppressWarnings("unchecked")
    private List<Polygon> punchHoles(List<LinearRing> rings) {
        List<PreparedPolygon> shells = new ArrayList<>(rings.size());
        List<LinearRing> holes = new ArrayList<>(rings.size() / 2);
        // 1. Split the polygon list in two: shells and holes (CCW and CW)
        for (LinearRing ring : rings) {
            if (CGAlgorithms.signedArea(ring.getCoordinateSequence()) > 0.0)
                holes.add(ring);
            else
                shells.add(new PreparedPolygon(geometryFactory.createPolygon(ring)));
        }
        // 2. Sort the shells based on number of points to optimize step 3.
        shells.sort((o1, o2) -> o2.getGeometry().getNumPoints() - o1.getGeometry().getNumPoints());
        for (PreparedPolygon shell : shells) {
            shell.getGeometry().setUserData(new ArrayList<LinearRing>());
        }
        // 3. For each hole, determine which shell it fits in.
        for (LinearRing hole : holes) {
            outer: {
                // Probably most of the time, the first shell will be the one
                for (PreparedPolygon shell : shells) {
                    if (shell.contains(hole)) {
                        ((List<LinearRing>) shell.getGeometry().getUserData()).add(hole);
                        break outer;
                    }
                }
                throw new RuntimeException("Found a hole without a shell.");
            }
        }
        // 4. Build the list of punched polygons
        List<Polygon> punched = new ArrayList<>(shells.size());
        for (PreparedPolygon shell : shells) {
            List<LinearRing> shellHoles = ((List<LinearRing>) shell.getGeometry().getUserData());
            punched.add(geometryFactory.createPolygon((LinearRing) (((Polygon) shell.getGeometry()).getExteriorRing()),
                    shellHoles.toArray(new LinearRing[shellHoles.size()])));
        }
        return punched;
    }
}