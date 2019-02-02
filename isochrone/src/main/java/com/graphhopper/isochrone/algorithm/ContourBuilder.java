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
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;

import java.util.*;

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
    private QuadEdgeSubdivision triangulation;

    private GeometryFactory geometryFactory = new GeometryFactory();

    public ContourBuilder(QuadEdgeSubdivision triangulation) {
        this.triangulation = triangulation;
    }

    public MultiPolygon computeIsoline(double z0) {
        Set<QuadEdge> processed = new HashSet<>();
        List<LinearRing> rings = new ArrayList<>();

        Queue<QuadEdge> processQ = new ArrayDeque<>(getPrimaryEdges());
        while (!processQ.isEmpty()) {
            QuadEdge e = processQ.remove();
            if (processed.contains(e))
                continue;
            processed.add(e);
            int cut = cut(e.orig().getZ(), e.dest().getZ(), z0);
            if (cut == 0) {
                continue; // While, next edge
            }
            List<Coordinate> polyPoints = new ArrayList<>();
            boolean ccw = cut > 0;
            while (true) {
                // Add a point to polyline
                Coordinate cC;
                if (triangulation.isFrameVertex(e.orig())) {
                    cC = moveEpsilonTowards(e.dest().getCoordinate(), e.orig().getCoordinate());
                } else if (triangulation.isFrameVertex(e.dest())) {
                    cC = moveEpsilonTowards(e.orig().getCoordinate(), e.dest().getCoordinate());
                } else {
                    cC = e.orig().midPoint(e.dest()).getCoordinate();
                }
                polyPoints.add(cC);
                processed.add(e);
                QuadEdge E1 = ccw ? e.oNext().getPrimary() : e.oPrev().getPrimary();
                QuadEdge E2 = ccw ? e.dPrev().getPrimary() : e.dNext().getPrimary();
                int cut1 = E1 == null ? 0 : cut(E1.orig().getZ(), E1.dest().getZ(), z0);
                int cut2 = E2 == null ? 0 : cut(E2.orig().getZ(), E2.dest().getZ(), z0);
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

    @SuppressWarnings("unchecked") // JTS is not generified
    private Collection<QuadEdge> getPrimaryEdges() {
        return (Collection<QuadEdge>) triangulation.getPrimaryEdges(true);
    }

    private Coordinate moveEpsilonTowards(Coordinate coordinate, Coordinate distantFrameCoordinate) {
        return new Coordinate(coordinate.x + EPSILON * (distantFrameCoordinate.x - coordinate.x), coordinate.y + EPSILON * (distantFrameCoordinate.y - coordinate.y));
    }

    private int cut(double za, double zb, double z0) {
        if (za <= z0 && zb > z0) return 1;
        if (za > z0 && zb <= z0) return -1;
        return 0;
    }

    @SuppressWarnings("unchecked")
    private List<Polygon> punchHoles(List<LinearRing> rings) {
        List<Polygon> shells = new ArrayList<>(rings.size());
        List<LinearRing> holes = new ArrayList<>(rings.size() / 2);
        // 1. Split the polygon list in two: shells and holes (CCW and CW)
        for (LinearRing ring : rings) {
            if (CGAlgorithms.signedArea(ring.getCoordinateSequence()) > 0.0)
                holes.add(ring);
            else
                shells.add(geometryFactory.createPolygon(ring));
        }
        // 2. Sort the shells based on number of points to optimize step 3.
        Collections.sort(shells, new Comparator<Polygon>() {
            @Override
            public int compare(Polygon o1, Polygon o2) {
                return o2.getNumPoints() - o1.getNumPoints();
            }
        });
        for (Polygon shell : shells) {
            shell.setUserData(new ArrayList<LinearRing>());
        }
        // 3. For each hole, determine which shell it fits in.
        for (LinearRing hole : holes) {
            outer: {
                // Probably most of the time, the first shell will be the one
                for (Polygon shell : shells) {
                    if (shell.contains(hole)) {
                        ((List<LinearRing>) shell.getUserData()).add(hole);
                        break outer;
                    }
                }
                throw new RuntimeException("Found a hole without a shell.");
            }
        }
        // 4. Build the list of punched polygons
        List<Polygon> punched = new ArrayList<>(shells.size());
        for (Polygon shell : shells) {
            List<LinearRing> shellHoles = ((List<LinearRing>) shell.getUserData());
            punched.add(geometryFactory.createPolygon((LinearRing) (shell.getExteriorRing()),
                    shellHoles.toArray(new LinearRing[shellHoles.size()])));
        }
        return punched;
    }
}