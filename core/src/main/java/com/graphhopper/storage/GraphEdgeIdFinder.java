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
package com.graphhopper.storage;

import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.Polygon;
import com.graphhopper.util.shapes.*;
import org.locationtech.jts.algorithm.RectangleLineIntersector;
import org.locationtech.jts.geom.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.graphhopper.util.shapes.BBox.toEnvelope;

/**
 * This class allows to find edges (or construct shapes) from shape filter.
 *
 * @author Robin Boldt
 */
public class GraphEdgeIdFinder {

    private final static int P_RADIUS = 5;
    private final Graph graph;
    private final LocationIndex locationIndex;

    public GraphEdgeIdFinder(Graph graph, LocationIndex locationIndex) {
        this.graph = graph;
        this.locationIndex = locationIndex;
    }

    /**
     * This method fills the edgeIds hash with edgeIds found inside the specified shape
     */
    public void findEdgesInShape(final GHIntHashSet edgeIds, final Shape shape, EdgeFilter filter) {
        locationIndex.query(shape.getBounds(), new LocationIndex.EdgeVisitor(graph.createEdgeExplorer(filter)) {
            @Override
            public void onEdge(EdgeIteratorState edge, int nodeA, int nodeB) {
                if (shape.intersects(edge.fetchWayGeometry(FetchMode.ALL).makeImmutable()))
                    edgeIds.add(edge.getEdge());
            }
        });
    }

    /**
     * This method fills the edgeIds hash with edgeIds found inside the specified geometry
     */
    public void fillEdgeIDs(final GHIntHashSet edgeIds, final Geometry geometry, EdgeFilter filter) {
        if (geometry instanceof Point) {
            Point p = (Point) geometry;
            findEdgesInShape(edgeIds, new Circle(p.getY(), p.getX(), P_RADIUS), filter);
        } else if (geometry instanceof LineString) {
            locationIndex.query(BBox.fromEnvelope(geometry.getEnvelopeInternal()), new LocationIndex.EdgeVisitor(graph.createEdgeExplorer(filter)) {
                @Override
                public void onEdge(EdgeIteratorState edge, int nodeA, int nodeB) {
                    if (geometry.intersects(edge.fetchWayGeometry(FetchMode.ALL).toLineString(false)))
                        edgeIds.add(edge.getEdge());
                }
            });
        } else if (geometry instanceof MultiPoint) {
            for (Coordinate coordinate : geometry.getCoordinates()) {
                findEdgesInShape(edgeIds, new Circle(coordinate.y, coordinate.x, P_RADIUS), filter);
            }
        }
    }

    public static GraphEdgeIdFinder.BlockArea createBlockArea(Graph graph, LocationIndex locationIndex,
                                                              List<GHPoint> points, PMap hints, EdgeFilter edgeFilter) {
        String blockAreaStr = hints.getString(Parameters.Routing.BLOCK_AREA, "");
        GraphEdgeIdFinder.BlockArea blockArea = new GraphEdgeIdFinder(graph, locationIndex).
                parseBlockArea(blockAreaStr, edgeFilter, hints.getDouble(Parameters.Routing.BLOCK_AREA + ".edge_id_max_area", 1000 * 1000));
        for (GHPoint p : points) {
            if (blockArea.contains(p))
                throw new IllegalArgumentException("Request with " + Parameters.Routing.BLOCK_AREA + " contained query point " + p + ". This is not allowed.");
        }
        return blockArea;
    }

    /**
     * This method reads the blockAreaString and creates a Collection of Shapes or a set of found edges if area is small enough.
     *
     * @param useEdgeIdsUntilAreaSize until the specified area (specified in m²) use the findEdgesInShape method
     */
    public BlockArea parseBlockArea(String blockAreaString, EdgeFilter filter, double useEdgeIdsUntilAreaSize) {
        final String objectSeparator = ";";
        final String innerObjSep = ",";
        BlockArea blockArea = new BlockArea(graph);

        // Add blocked circular areas or points
        if (!blockAreaString.isEmpty()) {
            String[] blockedCircularAreasArr = blockAreaString.split(objectSeparator);
            for (int i = 0; i < blockedCircularAreasArr.length; i++) {
                String objectAsString = blockedCircularAreasArr[i];
                String[] splittedObject = objectAsString.split(innerObjSep);

                // always add the shape as we'll need this for virtual edges and for debugging.
                if (splittedObject.length > 4) {
                    final Polygon polygon = Polygon.parsePoints(objectAsString);
                    GHIntHashSet blockedEdges = blockArea.add(polygon);
                    if (polygon.calculateArea() <= useEdgeIdsUntilAreaSize)
                        findEdgesInShape(blockedEdges, polygon, filter);
                } else if (splittedObject.length == 4) {
                    final BBox bbox = BBox.parseTwoPoints(objectAsString);
                    final RectangleLineIntersector cachedIntersector = new RectangleLineIntersector(toEnvelope(bbox));
                    BBox preparedBBox = new BBox(bbox.minLon, bbox.maxLon, bbox.minLat, bbox.maxLat) {
                        @Override
                        public boolean intersects(PointList pointList) {
                            return BBox.intersects(cachedIntersector, pointList);
                        }
                    };
                    GHIntHashSet blockedEdges = blockArea.add(preparedBBox);
                    if (bbox.calculateArea() <= useEdgeIdsUntilAreaSize)
                        findEdgesInShape(blockedEdges, preparedBBox, filter);
                } else if (splittedObject.length == 3) {
                    double lat = Double.parseDouble(splittedObject[0]);
                    double lon = Double.parseDouble(splittedObject[1]);
                    int radius = Integer.parseInt(splittedObject[2]);
                    Circle circle = new Circle(lat, lon, radius);
                    GHIntHashSet blockedEdges = blockArea.add(circle);
                    if (circle.calculateArea() <= useEdgeIdsUntilAreaSize)
                        findEdgesInShape(blockedEdges, circle, filter);

                } else if (splittedObject.length == 2) {
                    double lat = Double.parseDouble(splittedObject[0]);
                    double lon = Double.parseDouble(splittedObject[1]);
                    Circle circle = new Circle(lat, lon, P_RADIUS);
                    GHIntHashSet blockedEdges = blockArea.add(circle);
                    findEdgesInShape(blockedEdges, circle, filter);
                } else {
                    throw new IllegalArgumentException(objectAsString + " at index " + i + " need to be defined as lat,lon "
                            + "or as a circle lat,lon,radius or rectangular lat1,lon1,lat2,lon2");
                }
            }
        }
        return blockArea;
    }

    /**
     * This class handles edges and areas where access should be blocked.
     */
    public static class BlockArea {
        private final List<GHIntHashSet> edgesList = new ArrayList<>();
        private final List<Shape> blockedShapes = new ArrayList<>();
        private final int baseEdgeCount;

        public BlockArea(Graph g) {
            baseEdgeCount = g.getAllEdges().length();
        }

        public boolean hasCachedEdgeIds(int shapeIndex) {
            return !edgesList.get(shapeIndex).isEmpty();
        }

        public String toString(int shapeIndex) {
            List<Integer> returnList = new ArrayList<>();
            for (IntCursor intCursor : edgesList.get(shapeIndex)) {
                returnList.add(intCursor.value);
            }
            Collections.sort(returnList);
            return returnList.toString();
        }

        public GHIntHashSet add(Shape shape) {
            blockedShapes.add(shape);
            GHIntHashSet set = new GHIntHashSet();
            edgesList.add(set);
            return set;
        }

        public final boolean contains(GHPoint point) {
            for (Shape shape : blockedShapes) {
                if (shape.contains(point.lat, point.lon))
                    return true;
            }
            return false;
        }

        /**
         * @return true if the specified edgeState is part of this BlockArea
         */
        public final boolean intersects(EdgeIteratorState edgeState) {
            PointList pointList = null;
            BBox bbox = null;
            for (int shapeIdx = 0; shapeIdx < blockedShapes.size(); shapeIdx++) {
                GHIntHashSet blockedEdges = edgesList.get(shapeIdx);
                // blockedEdges acts as cache that is only useful when filled and for non-virtual edges
                if (!blockedEdges.isEmpty() && edgeState.getEdge() < baseEdgeCount) {
                    if (blockedEdges.contains(edgeState.getEdge()))
                        return true;
                    continue;
                }

                // compromise: avoid expensive fetch of pillar nodes, which isn't yet fast for being used in Weighting.calc
                if (bbox == null)
                    bbox = GHUtility.createBBox(edgeState);

                Shape shape = blockedShapes.get(shapeIdx);
                if (shape.getBounds().intersects(bbox)) {
                    if (pointList == null)
                        pointList = edgeState.fetchWayGeometry(FetchMode.ALL).makeImmutable();
                    if (shape.intersects(pointList))
                        return true;
                }
            }
            return false;
        }
    }
}