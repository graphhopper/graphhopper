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

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.BBox;
import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedPolygon;
import org.locationtech.jts.triangulate.ConformingDelaunayTriangulator;
import org.locationtech.jts.triangulate.ConstraintVertex;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

public class JTSTriangulator implements Triangulator {

    private final GraphHopper graphHopper;

    public JTSTriangulator(GraphHopper graphHopper) {
        this.graphHopper = graphHopper;
    }

    public Result triangulate(Snap snap, QueryGraph queryGraph, ShortestPathTree shortestPathTree, ToDoubleFunction<ShortestPathTree.IsoLabel> fz, double tolerance) {
        final NodeAccess na = queryGraph.getNodeAccess();
        List<ShortestPathTree.IsoLabel> labels = new ArrayList<>();
        shortestPathTree.search(snap.getClosestNode(), labels::add);
        labels.addAll(shortestPathTree.getIsochroneEdges());
        Map<Coordinate, Double> zs = new HashMap<>();
        labels.forEach(label -> {
            double exploreValue = fz.applyAsDouble(label);
            double lat = na.getLatitude(label.node);
            double lon = na.getLongitude(label.node);
            Coordinate site = new Coordinate(lon, lat);
            zs.merge(site, exploreValue, Math::min);

            // add a pillar node to increase precision a bit for longer roads
            if (label.parent != null) {
                EdgeIteratorState edge = queryGraph.getEdgeIteratorState(label.edge, label.node);
                PointList innerPoints = edge.fetchWayGeometry(FetchMode.PILLAR_ONLY);
                if (innerPoints.getSize() > 0) {
                    int midIndex = innerPoints.getSize() / 2;
                    double lat2 = innerPoints.getLat(midIndex);
                    double lon2 = innerPoints.getLon(midIndex);
                    Coordinate site2 = new Coordinate(lon2, lat2);
                    zs.merge(site2, exploreValue, Math::min);
                }
            }
        });

        GeometryFactory fact = new GeometryFactory();
        Geometry convexHull = new ConvexHull(zs.keySet().toArray(new Coordinate[0]), fact).getConvexHull();

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

        // Get at least all nodes within our convex hull.
        // I think then we should have all possible encroaching points. (Proof needed.)
        PreparedPolygon preparedConvexHull = new PreparedPolygon((Polygon) convexHull);
        graphHopper.getLocationIndex().query(BBox.fromEnvelope(convexHull.getEnvelopeInternal()), new LocationIndex.Visitor() {
            @Override
            public void onNode(int nodeId) {
                Coordinate nodeCoordinate = new Coordinate(na.getLongitude(nodeId), na.getLatitude(nodeId));
                if (preparedConvexHull.contains(fact.createPoint(nodeCoordinate)))
                    zs.merge(nodeCoordinate, Double.MAX_VALUE, Math::min);
            }
        });
        zs.forEach((coordinate, z) -> coordinate.z = z);

        Set<Coordinate> sites = zs.keySet();
        if (sites.size() > graphHopper.getRouterConfig().getMaxVisitedNodes() / 3)
            throw new IllegalArgumentException("Too many nodes would be included in post processing (" + sites.size() + "). Let us know if you need this increased.");

        Collection<ConstraintVertex> constraintVertices = sites.stream().map(ConstraintVertex::new).collect(Collectors.toList());
        ConformingDelaunayTriangulator conformingDelaunayTriangulator = new ConformingDelaunayTriangulator(constraintVertices, tolerance);
        conformingDelaunayTriangulator.setConstraints(new ArrayList<>(), new ArrayList<>());
        conformingDelaunayTriangulator.formInitialDelaunay();
        conformingDelaunayTriangulator.enforceConstraints();

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
