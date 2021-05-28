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
import com.graphhopper.util.shapes.*;
import org.locationtech.jts.algorithm.RectangleLineIntersector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.graphhopper.util.shapes.BBox.toEnvelope;

/**
 * This class allows to find edges (or construct shapes) from shape filter.
 *
 * @author Robin Boldt
 */
public class GraphEdgeIdFinder {

    private static final int P_RADIUS = 5;
    private final Graph graph;
    private final LocationIndex locationIndex;

    public GraphEdgeIdFinder(Graph graph, LocationIndex locationIndex) {
        this.graph = graph;
        this.locationIndex = locationIndex;
    }

    static double calculateArea(Shape shape) {
        if (shape instanceof BBox)
            return calculateArea((BBox) shape);
        if (shape instanceof Polygon)
            return calculateArea((Polygon) shape);
        if (shape instanceof Circle)
            return calculateArea((Circle) shape);
        throw new IllegalStateException("Unsupported shape: " + shape);
    }
    
    /**
     * @param bBox
     * @return an estimated area in m^2 using the mean value of latitudes for longitude distance
     */
    static double calculateArea(BBox bBox) {
        double meanLat = (bBox.maxLat + bBox.minLat) / 2;
        return DistancePlaneProjection.DIST_PLANE.calcDist(meanLat, bBox.minLon, meanLat, bBox.maxLon)
                // left side should be equal to right side no mean value necessary
                * DistancePlaneProjection.DIST_PLANE.calcDist(bBox.minLat, bBox.minLon, bBox.maxLat, bBox.minLon);
    }

    static double calculateArea(Polygon polygon) {
        // for estimation use bounding box as reference:
        return calculateArea(polygon.getBounds()) * polygon.envelope.getArea() / polygon.prepPolygon.getGeometry().getArea();
    }

    static double calculateArea(Circle circle) {
        return Math.PI * circle.radiusInMeter * circle.radiusInMeter;
    }

    /**
     * This method creates an edgeIds hashset with edgeIds found inside the specified shape
     */
    private GHIntHashSet findEdgesInShape(final Shape shape, EdgeFilter filter) {
        GHIntHashSet edgeIds = new GHIntHashSet();
        locationIndex.query(shape.getBounds(), edgeId -> {
            EdgeIteratorState edge = graph.getEdgeIteratorStateForKey(edgeId * 2);
            if (filter.accept(edge) && shape.intersects(edge.fetchWayGeometry(FetchMode.ALL).makeImmutable()))
                edgeIds.add(edge.getEdge());
        });
        return edgeIds;
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

                Shape shape;
                boolean point = false;
                // always add the shape as we'll need this for virtual edges and for debugging.
                if (splittedObject.length > 4) {
                    shape = Polygon.parsePoints(objectAsString);
                } else if (splittedObject.length == 4) {
                    final BBox bbox = BBox.parseTwoPoints(objectAsString);
                    final RectangleLineIntersector cachedIntersector = new RectangleLineIntersector(toEnvelope(bbox));
                    shape = new BBox(bbox.minLon, bbox.maxLon, bbox.minLat, bbox.maxLat) {
                        @Override
                        public boolean intersects(PointList pointList) {
                            return BBox.intersects(cachedIntersector, pointList);
                        }
                    };
                } else if (splittedObject.length == 3) {
                    double lat = Double.parseDouble(splittedObject[0]);
                    double lon = Double.parseDouble(splittedObject[1]);
                    int radius = Integer.parseInt(splittedObject[2]);
                    shape = new Circle(lat, lon, radius);
                } else if (splittedObject.length == 2) {
                    double lat = Double.parseDouble(splittedObject[0]);
                    double lon = Double.parseDouble(splittedObject[1]);
                    shape = new Circle(lat, lon, P_RADIUS);
                    point = true;
                } else {
                    throw new IllegalArgumentException(objectAsString + " at index " + i + " need to be defined as lat,lon "
                            + "or as a circle lat,lon,radius or rectangular lat1,lon1,lat2,lon2");
                }
                
                
                if (point || calculateArea(shape) <= useEdgeIdsUntilAreaSize) {
                    GHIntHashSet blockedEdges = findEdgesInShape(shape, filter);
                    if (!blockedEdges.isEmpty()) {
                        blockArea.add(shape, blockedEdges);
                    }
                } else {
                    blockArea.add(shape);
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
        
        public void add(Shape shape) {
            add(shape, new GHIntHashSet());
        }

        public void add(Shape shape, GHIntHashSet blockedEdgeIds) {
            blockedShapes.add(shape);
            edgesList.add(Objects.requireNonNull(blockedEdgeIds));
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
                    if (shape instanceof Polygon && ((Polygon) shape).isRectangle())
                        return true;
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