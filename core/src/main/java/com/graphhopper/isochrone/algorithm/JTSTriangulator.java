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

package com.graphhopper.isochrone.algorithm;

import com.graphhopper.routing.RouterConfig;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.core.util.EdgeIteratorState;
import com.graphhopper.core.util.FetchMode;
import com.graphhopper.util.PointList;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.triangulate.ConformingDelaunayTriangulator;
import org.locationtech.jts.triangulate.ConstraintVertex;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

public class JTSTriangulator implements Triangulator {

    private final RouterConfig routerConfig;

    public JTSTriangulator(RouterConfig routerConfig) {
        this.routerConfig = routerConfig;
    }

    public Result triangulate(Snap snap, QueryGraph queryGraph, ShortestPathTree shortestPathTree, ToDoubleFunction<ShortestPathTree.IsoLabel> fz, double tolerance) {
        final NodeAccess na = queryGraph.getNodeAccess();
        Collection<Coordinate> sites = new ArrayList<>();
        shortestPathTree.search(snap.getClosestNode(), label -> {
            double exploreValue = fz.applyAsDouble(label);
            double lat = na.getLat(label.node);
            double lon = na.getLon(label.node);
            Coordinate site = new Coordinate(lon, lat);
            site.z = exploreValue;
            sites.add(site);

            // add a pillar node to increase precision a bit for longer roads
            if (label.parent != null) {
                EdgeIteratorState edge = queryGraph.getEdgeIteratorState(label.edge, label.node);
                PointList innerPoints = edge.fetchWayGeometry(FetchMode.PILLAR_ONLY);
                if (innerPoints.size() > 0) {
                    int midIndex = innerPoints.size() / 2;
                    double lat2 = innerPoints.getLat(midIndex);
                    double lon2 = innerPoints.getLon(midIndex);
                    Coordinate site2 = new Coordinate(lon2, lat2);
                    site2.z = exploreValue;
                    sites.add(site2);
                }
            }
        });

        if (sites.size() > routerConfig.getMaxVisitedNodes() / 3)
            throw new IllegalArgumentException("Too many nodes would be included in post processing (" + sites.size() + "). Let us know if you need this increased.");

        // Sites may contain repeated coordinates. Especially for edge-based traversal, that's expected -- we visit
        // each node multiple times.
        // But that's okay, the triangulator de-dupes by itself, and it keeps the first z-value it sees, which is
        // what we want.

        Collection<ConstraintVertex> constraintVertices = sites.stream().map(ConstraintVertex::new).collect(Collectors.toList());
        ConformingDelaunayTriangulator conformingDelaunayTriangulator = new ConformingDelaunayTriangulator(constraintVertices, tolerance);
        conformingDelaunayTriangulator.setConstraints(new ArrayList<>(), new ArrayList<>());
        conformingDelaunayTriangulator.formInitialDelaunay();
        conformingDelaunayTriangulator.enforceConstraints();
        Geometry convexHull = conformingDelaunayTriangulator.getConvexHull();

        // If there's only one site (and presumably also if the convex hull is otherwise degenerated),
        // the triangulation only contains the frame, and not the site within the frame. Not sure if I agree with that.
        // See ConformingDelaunayTriangulator, it does include a buffer for the frame, but that buffer is zero
        // in these cases.
        // It leads to the following follow-up defect:
        // computeIsoline fails (returns an empty Multipolygon). This is clearly wrong, since
        // the idea is that every real (non-frame) vertex has positive-length-edges around it that can be traversed
        // to get a non-empty polygon.
        // So we exclude this case for now (it is indeed only a corner-case).

        if (!(convexHull instanceof Polygon)) {
            throw new IllegalArgumentException("Too few points found. "
                    + "Please try a different 'point' or a larger 'time_limit'.");
        }

        QuadEdgeSubdivision tin = conformingDelaunayTriangulator.getSubdivision();
        for (Vertex vertex : (Collection<Vertex>) tin.getVertices(true)) {
            if (tin.isFrameVertex(vertex)) {
                vertex.setZ(Double.MAX_VALUE);
            }
        }
        ReadableTriangulation triangulation = ReadableTriangulation.wrap(tin);
        return new Result(triangulation, triangulation.getEdges());
    }
}
