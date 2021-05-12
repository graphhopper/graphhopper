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

    /** 1km² */
    public static final double SMALL_AREA = 1_000_000d;
    
    private static final int P_RADIUS = 5;
    private final Graph graph;
    private final LocationIndex locationIndex;
    private final EdgeFilter edgeFilter;
    private final double useEdgeIdsUntilAreaSize;

    /**
     * @param graph
     * @param locationIndex
     * @param edgeFilter
     * @param useEdgeIdsUntilAreaSize until the specified area (specified in m²) use the findEdgesInShape method
     */
    public GraphEdgeIdFinder(Graph graph, LocationIndex locationIndex, EdgeFilter edgeFilter, double useEdgeIdsUntilAreaSize) {
        this.graph = graph;
        this.locationIndex = locationIndex;
        this.edgeFilter = edgeFilter;
        this.useEdgeIdsUntilAreaSize = useEdgeIdsUntilAreaSize;
    }

    private static double calculateArea(Shape shape) {
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
    private static double calculateArea(BBox bBox) {
        double meanLat = (bBox.maxLat + bBox.minLat) / 2;
        return DistancePlaneProjection.DIST_PLANE.calcDist(meanLat, bBox.minLon, meanLat, bBox.maxLon)
                // left side should be equal to right side no mean value necessary
                * DistancePlaneProjection.DIST_PLANE.calcDist(bBox.minLat, bBox.minLon, bBox.maxLat, bBox.minLon);
    }

    private static double calculateArea(Polygon polygon) {
        // for estimation use bounding box as reference:
        return calculateArea(polygon.getBounds()) * polygon.envelope.getArea() / polygon.prepPolygon.getGeometry().getArea();
    }

    private static double calculateArea(Circle circle) {
        return Math.PI * circle.radiusInMeter * circle.radiusInMeter;
    }

    /**
     * This method creates an edgeIds hashset with edgeIds found inside the specified shape
     */
    private GHIntHashSet findEdgesInShape(final Shape shape, EdgeFilter filter) {
        GHIntHashSet edgeIds = new GHIntHashSet();
        locationIndex.query(shape.getBounds(), new LocationIndex.Visitor() {
            @Override
            public void onEdge(int edgeId) {
                EdgeIteratorState edge = graph.getEdgeIteratorStateForKey(edgeId * 2);
                if (filter.accept(edge) && shape.intersects(edge.fetchWayGeometry(FetchMode.ALL).makeImmutable()))
                    edgeIds.add(edge.getEdge());
            }
        });
        return edgeIds;
    }

    public static GraphEdgeIdFinder.ShapeFilter createBlockArea(Graph graph, LocationIndex locationIndex,
                                                              List<GHPoint> points, PMap hints, EdgeFilter edgeFilter) {
        String blockAreaStr = hints.getString(Parameters.Routing.BLOCK_AREA, "");
        double edgeIdMaxArea = hints.getDouble(Parameters.Routing.BLOCK_AREA + ".edge_id_max_area", SMALL_AREA);
        GraphEdgeIdFinder.ShapeFilter blockArea = new GraphEdgeIdFinder(graph, locationIndex, edgeFilter, edgeIdMaxArea).parseBlockArea(blockAreaStr);
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
    public ShapeFilter parseBlockArea(String blockAreaString) {
        final String objectSeparator = ";";
        final String innerObjSep = ",";
        
        List<Shape> shapes = new ArrayList<>();
        // Add blocked circular areas or points
        String[] blockedCircularAreasArr = blockAreaString.split(objectSeparator);
        for (int i = 0; i < blockedCircularAreasArr.length; i++) {
            String objectAsString = blockedCircularAreasArr[i];
            String[] splittedObject = objectAsString.split(innerObjSep);

            Shape shape;
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
            } else {
                throw new IllegalArgumentException(objectAsString + " at index " + i + " need to be defined as lat,lon "
                        + "or as a circle lat,lon,radius or rectangular lat1,lon1,lat2,lon2");
            }
            shapes.add(shape);
        }
        return createFilter(shapes);
    }
    
    public ShapeFilter createFilter(List<Shape> shapes) {
        ShapeFilter shapeFilter = new ShapeFilter(graph);
        for (Shape shape : shapes) {
            if (locationIndex != null && calculateArea(shape) <= useEdgeIdsUntilAreaSize) {
                GHIntHashSet blockedEdges = findEdgesInShape(shape, edgeFilter);
                if (!blockedEdges.isEmpty()) {
                    shapeFilter.add(shape, blockedEdges);
                }
            } else {
                shapeFilter.add(shape);
            }
        }
        return shapeFilter;
    }

    /**
     * This class allows to check if an {@link EdgeIteratorState} intersects one or more shapes.
     */
    public static class ShapeFilter {
        private final List<GHIntHashSet> edgesList = new ArrayList<>();
        private final List<Shape> shapes = new ArrayList<>();
        private final int baseEdgeCount;

        public ShapeFilter(Graph g) {
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

        public void add(Shape shape, GHIntHashSet edgeIds) {
            shapes.add(shape);
            edgesList.add(Objects.requireNonNull(edgeIds));
        }

        public final boolean contains(GHPoint point) {
            for (Shape shape : shapes) {
                if (shape.contains(point.lat, point.lon))
                    return true;
            }
            return false;
        }

        /**
         * @return true if the specified edgeState matches this filter
         */
        public final boolean intersects(EdgeIteratorState edgeState) {
            PointList pointList = null;
            BBox bbox = null;
            for (int shapeIdx = 0; shapeIdx < shapes.size(); shapeIdx++) {
                GHIntHashSet edges = edgesList.get(shapeIdx);
                // the hashset acts as cache that is only useful when filled and for non-virtual edges
                if (!edges.isEmpty() && edgeState.getEdge() < baseEdgeCount) {
                    if (edges.contains(edgeState.getEdge()))
                        return true;
                    continue;
                }

                // compromise: avoid expensive fetch of pillar nodes, which isn't yet fast for being used in Weighting.calc
                if (bbox == null)
                    bbox = GHUtility.createBBox(edgeState);

                Shape shape = shapes.get(shapeIdx);
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