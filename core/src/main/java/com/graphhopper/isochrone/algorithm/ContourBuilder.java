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

import org.locationtech.jts.algorithm.Area;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.prep.PreparedPolygon;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import java.util.*;
import java.util.function.BiPredicate;
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
        return computeIsoline(cut, seedEdges, null);
    }

    /**
     * As {@link #computeIsoline(ToIntBiFunction, Collection)}, but with optional three-cell-junction
     * handling for multi-region ("Voronoi"-style) contours.
     *
     * <p>Every crossing is placed at an edge midpoint and consecutive crossings are joined by a straight
     * chord across their shared mesh face. Where three different regions meet in one face, the three
     * regions' chords are the face's three edge-midpoint connectors, which enclose the medial triangle --
     * a sliver no region claims. When {@code differentCell} is non-null, at each such face this cell's
     * boundary is instead routed through the face centroid, so all three regions meet at one point and
     * tile the face with no gap (see {@link #tripleJunctionCentroid}). The predicate answers "are these
     * two vertices in different regions?", keeping all region/label knowledge with the caller.
     *
     * <p>When {@code differentCell} is null (e.g. ordinary single-threshold isolines, which cannot have a
     * three-region face) the output is identical to the two-argument overload -- no extra points, every
     * topological case traced exactly as before.
     */
    public MultiPolygon computeIsoline(ToIntBiFunction<Vertex, Vertex> cut, Collection<ReadableQuadEdge> seedEdges, BiPredicate<Vertex, Vertex> differentCell) {
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
                ReadableQuadEdge next;
                boolean nextCcw;
                if (ok1) {
                    next = E1;
                    nextCcw = cut1 > 0;
                } else if (ok2) {
                    next = E2;
                    nextCcw = cut2 > 0;
                } else {
                    // This must be the end of the polyline...
                    break;
                }
                // The segment from this crossing to the next lies within the mesh face shared by e and
                // next. If that face is a three-cell junction, splice its centroid in between so this
                // cell -- and, identically, the two others meeting here -- pass through one common point
                // and leave no medial-triangle gap. Non-junction faces get nothing added and are traced
                // exactly as before.
                if (differentCell != null) {
                    Coordinate centroid = tripleJunctionCentroid(e, next, differentCell);
                    if (centroid != null)
                        polyPoints.add(centroid);
                }
                e = next;
                ccw = nextCcw;
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

    /**
     * If the mesh face shared by consecutive contour edges {@code e} and {@code next} is a three-cell
     * junction, returns the face centroid to splice between their two crossings; otherwise {@code null}.
     *
     * <p>{@code e} and {@code next} are two edges of one triangle and share exactly one vertex, the
     * pivot. The face's three vertices are {@code e.orig}, {@code e.dest} and the apex (the endpoint of
     * {@code next} not lying on {@code e}). The two crossings the tracer places sit on the two edges
     * incident to the pivot, so the third edge -- the one whose presence as a boundary reveals a medial
     * gap -- runs between the two NON-pivot vertices. We therefore emit the centroid exactly when those
     * two are in different cells (which, together with the pivot, makes three distinct cells). The
     * {@code differentCell} predicate makes that call, so a frame or beyond-budget corner (not a real
     * cell) yields {@code false} and leaves shell and void faces untouched.
     */
    private static Coordinate tripleJunctionCentroid(ReadableQuadEdge e, ReadableQuadEdge next, BiPredicate<Vertex, Vertex> differentCell) {
        Vertex a = e.orig();
        Vertex b = e.dest();
        Vertex nO = next.orig();
        Vertex nD = next.dest();
        // The pivot is whichever of a/b is also an endpoint of next; the other is e's non-pivot corner.
        boolean aShared = sameVertex(a, nO) || sameVertex(a, nD);
        boolean bShared = sameVertex(b, nO) || sameVertex(b, nD);
        Vertex pivot, nonPivotOnE;
        if (aShared && !bShared) {
            pivot = a;
            nonPivotOnE = b;
        } else if (bShared && !aShared) {
            pivot = b;
            nonPivotOnE = a;
        } else {
            return null; // e and next don't share exactly one vertex as expected -- leave segment straight
        }
        Vertex apex = sameVertex(nO, pivot) ? nD : nO;
        if (sameVertex(apex, a) || sameVertex(apex, b))
            return null; // degenerate face -- play safe
        if (!differentCell.test(nonPivotOnE, apex))
            return null; // not a three-cell junction -> add nothing, identical to previous behavior
        Coordinate ca = a.getCoordinate();
        Coordinate cb = b.getCoordinate();
        Coordinate cc = apex.getCoordinate();
        return new Coordinate((ca.x + cb.x + cc.x) / 3.0, (ca.y + cb.y + cc.y) / 3.0);
    }

    private static boolean sameVertex(Vertex u, Vertex v) {
        return u.getCoordinate().equals2D(v.getCoordinate());
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
            if (Area.ofRingSigned(ring.getCoordinateSequence()) > 0.0)
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
