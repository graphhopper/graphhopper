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

package com.graphhopper.routing.util;

import com.carrotsearch.hppc.IntArrayDeque;
import com.carrotsearch.hppc.IntScatterSet;
import com.carrotsearch.hppc.IntSet;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

import java.util.function.BiConsumer;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.graphhopper.util.DistancePlaneProjection.DIST_PLANE;

public class RoadDensityCalculator {
    private final Graph graph;
    private final EdgeExplorer edgeExplorer;
    private final IntSet visited;
    private final IntArrayDeque deque;

    public RoadDensityCalculator(Graph graph) {
        this.graph = graph;
        this.edgeExplorer = graph.createEdgeExplorer();
        visited = new IntScatterSet();
        deque = new IntArrayDeque(100);
    }

    /**
     * Loops over all edges of the graph and calls the given edgeHandler for each edge. This is done in parallel using
     * the given number of threads. For every call we can calculate the road density using the provided thread local
     * road density calculator.
     */
    public static void calcRoadDensities(Graph graph, BiConsumer<RoadDensityCalculator, EdgeIteratorState> edgeHandler, int threads) {
        ThreadLocal<RoadDensityCalculator> calculator = ThreadLocal.withInitial(() -> new RoadDensityCalculator(graph));
        Stream<Runnable> roadDensityWorkers = IntStream.range(0, graph.getEdges())
                .mapToObj(i -> () -> {
                    EdgeIteratorState edge = graph.getEdgeIteratorState(i, Integer.MIN_VALUE);
                    edgeHandler.accept(calculator.get(), edge);
                });
        GHUtility.runConcurrently(roadDensityWorkers, threads);
    }

    /**
     * @param radius         in meters
     * @param calcRoadFactor weighting function. use this to define how different kinds of roads shall contribute to the calculated road density
     * @return the road density in the vicinity of the given edge, i.e. the weighted road length divided by the squared radius
     */
    public double calcRoadDensity(EdgeIteratorState edge, double radius, ToDoubleFunction<EdgeIteratorState> calcRoadFactor) {
        visited.clear();
        deque.head = deque.tail = 0;
        double totalRoadWeight = 0;
        NodeAccess na = graph.getNodeAccess();
        int baseNode = edge.getBaseNode();
        int adjNode = edge.getAdjNode();
        GHPoint center = new GHPoint(getLat(na, baseNode, adjNode), getLon(na, baseNode, adjNode));
//        double dlat = radius / 111100;
//        double dlon = radius / (111100 * Math.cos(Math.toRadians(center.lat)));
//        double latlonradius = Math.sqrt(dlat * dlat + dlon * dlon);
//        Geometry circle = new GeometryFactory().createPoint(new Coordinate(center.lon, center.lat)).buffer(latlonradius);
//        Geometry circle = new GeometryFactory().createPolygon(generateApproximatePolygon(center.lon, center.lat, latlonradius, 4));
//        PreparedGeometry preparedCircle = new PreparedGeometryFactory().create(circle);
        deque.addLast(baseNode);
        deque.addLast(adjNode);
        visited.add(baseNode);
        visited.add(adjNode);
        // we just do a BFS search and sum up all the road lengths
        final double radiusNormalized = DIST_PLANE.calcNormalizedDist(radius);
        while (!deque.isEmpty()) {
            int node = deque.removeFirst();
            double distance = DIST_PLANE.calcNormalizedDist(center.lat, center.lon, na.getLat(node), na.getLon(node));
            if (distance > radiusNormalized)
                continue;
            EdgeIterator iter = edgeExplorer.setBaseNode(node);
            while (iter.next()) {
                if (visited.contains(iter.getAdjNode()))
                    continue;
//                LineString lineString = iter.fetchWayGeometry(FetchMode.ALL).toLineString(false);
//                if (!preparedCircle.intersects(lineString))
//                    continue;
//                if (!preparedCircle.contains(lineString))
//                    continue;
//                double length = iter.getDistance();
                double length = 1;
//                double length = circle.intersection(lineString).getLength();
                visited.add(iter.getAdjNode());
                totalRoadWeight += length * calcRoadFactor.applyAsDouble(edge);
                deque.addLast(iter.getAdjNode());
            }
        }
        return totalRoadWeight / radius / radius;
//        return totalRoadWeight / latlonradius / latlonradius;
    }

    private static double getLat(NodeAccess na, int baseNode, int adjNode) {
        return (na.getLat(baseNode) + na.getLat(adjNode)) / 2;
    }

    private static double getLon(NodeAccess na, int baseNode, int adjNode) {
        return (na.getLon(baseNode) + na.getLon(adjNode)) / 2;
    }

    private static Coordinate[] generateApproximatePolygon(double centerX, double centerY, double radius, int numPoints) {
        Coordinate[] coords = new Coordinate[numPoints + 1];
        double angleIncrement = 2 * Math.PI / numPoints;

        for (int i = 0; i < numPoints; i++) {
            double angle = i * angleIncrement;
            double x = centerX + radius * Math.cos(angle);
            double y = centerY + radius * Math.sin(angle);
            coords[i] = new Coordinate(x, y);
        }

        // Close the polygon by adding the first point as the last point
        coords[numPoints] = coords[0];

        return coords;
    }

}
