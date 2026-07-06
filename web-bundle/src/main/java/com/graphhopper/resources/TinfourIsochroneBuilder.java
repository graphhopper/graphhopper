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

package com.graphhopper.resources;

import com.graphhopper.isochrone.algorithm.ShortestPathTree;
import com.graphhopper.isochrone.algorithm.ShortestPathTree.IsoLabel;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.Vertex;
import org.tinfour.contour.ContourBuilderForTin;
import org.tinfour.contour.ContourRegion;
import org.tinfour.semivirtual.SemiVirtualIncrementalTin;
import org.tinfour.standard.IncrementalTin;
import org.tinfour.utils.HilbertSort;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Alternative isochrone geometry builder using the Tinfour library instead of the JTS
 * Delaunay triangulation + {@link com.graphhopper.isochrone.algorithm.ContourBuilder} pipeline.
 * <p>
 * Tinfour builds the TIN and traces closed contour regions directly. In benchmarks it is roughly
 * 2-3.5x faster than the JTS path and uses about a third less heap. The vertices are Hilbert-sorted
 * before insertion because Tinfour's incremental insertion uses a walk-based point locator that
 * degrades badly (~O(n^2)) when the insertion order is not spatially coherent -- which is the case for
 * the shortest-path-tree order the sites are produced in.
 * <p>
 * This is a "good enough to compare" implementation: each bucket returns the reachable regions as a
 * MultiPolygon; interior holes (unreachable pockets) are not punched out. Selected via the
 * {@code algorithm=tinfour} query parameter of {@link IsochroneResource}.
 */
public class TinfourIsochroneBuilder {

    private final GeometryFactory geometryFactory = new GeometryFactory();
    private final boolean semiVirtual;

    // Per-request timing breakdown (ms) and vertex count, populated by computeIsolines() for debugging/comparison.
    public long searchMillis, sortMillis, tinBuildMillis, contourMillis;
    public int vertexCount;

    public TinfourIsochroneBuilder() {
        this(false);
    }

    /**
     * @param semiVirtual if true use {@link SemiVirtualIncrementalTin} (about half the TIN memory and far fewer
     *                    objects -> less GC pressure, at ~30% more insertion CPU) instead of the standard
     *                    object-based {@link IncrementalTin}. Both need the vertices Hilbert-sorted.
     */
    public TinfourIsochroneBuilder(boolean semiVirtual) {
        this.semiVirtual = semiVirtual;
    }

    /**
     * Runs the shortest-path-tree search and returns one MultiPolygon per z-level (same order as {@code zs}).
     */
    public List<Geometry> computeIsolines(Snap snap, QueryGraph queryGraph, ShortestPathTree spt,
                                          ToDoubleFunction<IsoLabel> fz, List<Double> zs) {
        final NodeAccess na = queryGraph.getNodeAccess();
        final List<Vertex> vertices = new ArrayList<>();
        StopWatch sw = new StopWatch().start();
        // Build vertices directly in the search callback so we never hold a second reachable-set-sized list,
        // which keeps peak memory down on large isochrones.
        spt.search(snap.getClosestNode(), label -> collectVertices(vertices, na, queryGraph, label, fz));
        searchMillis = sw.stop().getMillis();
        vertexCount = vertices.size();

        // We have all the vertices now; free the shortest-path-tree's label map + queues before the memory-heavy
        // TIN build so their (potentially millions of) IsoLabels can be collected. getVisitedNodes() still works.
        spt.clear();

        sw = new StopWatch().start();
        new HilbertSort().sort(vertices);
        sortMillis = sw.stop().getMillis();

        // Tinfour derives its coincidence/degeneracy thresholds from a "nominal point spacing" that
        // defaults to 1.0 -- wrong for lon/lat degrees (spacing ~1e-4), which makes it treat all points
        // as coincident and fail to bootstrap. Estimate the spacing from the data extent + vertex count.
        sw = new StopWatch().start();
        double spacing = estimateNominalSpacing(vertices);
        IIncrementalTin tin = semiVirtual ? new SemiVirtualIncrementalTin(spacing) : new IncrementalTin(spacing);
        tin.add(vertices, null);
        tinBuildMillis = sw.stop().getMillis();
        if (!tin.isBootstrapped())
            throw new IllegalArgumentException("Too few points found. "
                    + "Please try a different 'point' or a larger 'time_limit'.");

        sw = new StopWatch().start();
        List<Geometry> isolines = new ArrayList<>(zs.size());
        for (double z : zs) {
            // Trace closed regions for a single contour level. The TIN (the expensive part) is built once
            // and reused for every bucket; only the cheap contour tracing repeats.
            ContourBuilderForTin cbt = new ContourBuilderForTin(tin, null, new double[]{z}, true);
            List<Polygon> polygons = new ArrayList<>();
            for (ContourRegion region : cbt.getRegions()) {
                // regionIndex 0 == below the contour value == reachable within z
                if (region.getRegionIndex() != 0)
                    continue;
                Polygon polygon = toPolygon(region.getXY());
                if (polygon != null)
                    polygons.add(polygon);
            }
            isolines.add(geometryFactory.createMultiPolygon(polygons.toArray(new Polygon[0])));
        }
        contourMillis = sw.stop().getMillis();
        return isolines;
    }

    private static double estimateNominalSpacing(List<Vertex> vertices) {
        double xMin = Double.MAX_VALUE, xMax = -Double.MAX_VALUE, yMin = Double.MAX_VALUE, yMax = -Double.MAX_VALUE;
        for (Vertex v : vertices) {
            xMin = Math.min(xMin, v.getX());
            xMax = Math.max(xMax, v.getX());
            yMin = Math.min(yMin, v.getY());
            yMax = Math.max(yMax, v.getY());
        }
        double area = Math.max(1e-12, (xMax - xMin) * (yMax - yMin));
        return Math.sqrt(area / Math.max(1, vertices.size()));
    }

    private Polygon toPolygon(double[] xy) {
        int n = xy.length / 2;
        if (n < 3)
            return null;
        boolean closed = xy[0] == xy[2 * (n - 1)] && xy[1] == xy[2 * (n - 1) + 1];
        Coordinate[] coords = new Coordinate[closed ? n : n + 1];
        for (int i = 0; i < n; i++)
            coords[i] = new Coordinate(xy[2 * i], xy[2 * i + 1]);
        if (!closed)
            coords[n] = new Coordinate(xy[0], xy[1]);
        if (coords.length < 4)
            return null;
        try {
            LinearRing ring = geometryFactory.createLinearRing(coords);
            return geometryFactory.createPolygon(ring);
        } catch (IllegalArgumentException e) {
            // degenerate ring (e.g. zero-length segments) -- skip for this "good enough" comparison path
            return null;
        }
    }

    /**
     * Same site collection as {@link com.graphhopper.isochrone.algorithm.JTSTriangulator}:
     * the node coordinate plus a pillar midpoint per edge (to increase precision on longer roads).
     */
    private static void collectVertices(List<Vertex> vertices, NodeAccess na, QueryGraph queryGraph,
                                        IsoLabel label, ToDoubleFunction<IsoLabel> fz) {
        double exploreValue = fz.applyAsDouble(label);
        vertices.add(new Vertex(na.getLon(label.node), na.getLat(label.node), exploreValue));

        if (label.parent != null) {
            EdgeIteratorState edge = queryGraph.getEdgeIteratorState(label.edge, label.node);
            PointList innerPoints = edge.fetchWayGeometry(FetchMode.PILLAR_ONLY);
            if (innerPoints.size() > 0) {
                int midIndex = innerPoints.size() / 2;
                if (innerPoints.size() % 2 == 0 && edge.get(EdgeIteratorState.REVERSE_STATE))
                    midIndex -= 1;
                vertices.add(new Vertex(innerPoints.getLon(midIndex), innerPoints.getLat(midIndex), exploreValue));
            }
        }
    }
}
