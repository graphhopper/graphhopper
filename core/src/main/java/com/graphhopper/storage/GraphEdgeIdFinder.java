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

import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.BreadthFirstSearch;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.Circle;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.Shape;
// import com.graphhopper.util.shapes.Shape;
import com.vividsolutions.jts.geom.*;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;

/**
 * This class allows to find edges (or construct shapes) from shape filter.
 *
 * @author Robin Boldt
 */
public class GraphEdgeIdFinder {

    private final Graph graph;
    private final LocationIndex locationIndex;

    public GraphEdgeIdFinder(Graph graph, LocationIndex locationIndex) {
        this.graph = graph;
        this.locationIndex = locationIndex;
    }

    /**
     * This method fills the edgeIds hash with edgeIds found close (exact match) to the specified point
     */
    public void findClosestEdgeToPoint(GHIntHashSet edgeIds, GHPoint point, EdgeFilter filter) {
        findClosestEdge(edgeIds, point.getLat(), point.getLon(), filter);
    }

    /**
     * This method fills the edgeIds hash with edgeIds found close (exact match) to the specified lat,lon
     */
    public void findClosestEdge(GHIntHashSet edgeIds, double lat, double lon, EdgeFilter filter) {
        QueryResult qr = locationIndex.findClosest(lat, lon, filter);
        if (qr.isValid())
            edgeIds.add(qr.getClosestEdge().getEdge());
    }
    
    
    /**
     * This method fills the edgeIds hash with edgeIds found inside the specified shape
     */
    public void findEdgesInShape(final GHIntHashSet edgeIds, final Shape shape, EdgeFilter filter) {
        GHPoint center = shape.getCenter();
        QueryResult qr = locationIndex.findClosest(center.getLat(), center.getLon(), filter);
        // TODO: if there is no street close to the center it'll fail although there are roads covered. Maybe we should check edge points or some random points in the Shape instead?
        if (!qr.isValid())
            throw new IllegalArgumentException("Shape " + shape + " does not cover graph");

        if (shape.contains(qr.getSnappedPoint().lat, qr.getSnappedPoint().lon))
            edgeIds.add(qr.getClosestEdge().getEdge());

        BreadthFirstSearch bfs = new BreadthFirstSearch() {
            final NodeAccess na = graph.getNodeAccess();
            final Shape localShape = shape;

            @Override
            protected boolean goFurther(int nodeId) {
                return localShape.contains(na.getLatitude(nodeId), na.getLongitude(nodeId));
            }

            @Override
            protected boolean checkAdjacent(EdgeIteratorState edge) {
                if (localShape.contains(na.getLatitude(edge.getAdjNode()), na.getLongitude(edge.getAdjNode()))) {
                    edgeIds.add(edge.getEdge());
                    return true;
                }
                return false;
            }
        };
        bfs.start(graph.createEdgeExplorer(filter), qr.getClosestNode());
    }
    
    /**
     * This method fills the edgeIds hash with edgeIds found inside the specified shape
     */
    public void findEdgesInShape(final GHIntHashSet edgeIds, final Geometry shape, EdgeFilter filter) {
    	//GHPoint center = shape.getCentroid();
        Coordinate p = shape.getCentroid().getCoordinate();
        //Point2D vp = viewport.toView(new Point2D.Double(p.x, p.y));
        QueryResult qr = locationIndex.findClosest(p.x, p.y, filter);
        // TODO: if there is no street close to the center it'll fail although there are roads covered. Maybe we should check edge points or some random points in the Shape instead?
        if (!qr.isValid())
            throw new IllegalArgumentException("Shape " + shape + " does not cover graph");
        final GeometryFactory geomFact = new GeometryFactory();
        Geometry snappedPoint = geomFact.createPoint(new Coordinate(qr.getSnappedPoint().lat, qr.getSnappedPoint().lon));
        
        if (shape.contains(snappedPoint))
            edgeIds.add(qr.getClosestEdge().getEdge());

        BreadthFirstSearch bfs = new BreadthFirstSearch() {
            final NodeAccess na = graph.getNodeAccess();
            final Geometry localShape = shape;

            @Override
            protected boolean goFurther(int nodeId) {
            	Geometry naPoint = geomFact.createPoint(new Coordinate(na.getLatitude(nodeId), na.getLongitude(nodeId)));
            	
                return localShape.contains(naPoint);
            }

            @Override
            protected boolean checkAdjacent(EdgeIteratorState edge) {
            	Geometry adjPoint = geomFact.createPoint(new Coordinate(na.getLatitude(edge.getAdjNode()), na.getLongitude(edge.getAdjNode())));
                if (localShape.contains(adjPoint)) {
                    edgeIds.add(edge.getEdge());
                    return true;
                }
                return false;
            }
        };
        bfs.start(graph.createEdgeExplorer(filter), qr.getClosestNode());
    }

    /**
     * This method fills the edgeIds hash with edgeIds found inside the specified geometry
     */
    public void fillEdgeIDs(GHIntHashSet edgeIds, Geometry geometry, EdgeFilter filter) {
        if (geometry instanceof Point) {
            GHPoint point = GHPoint.create((Point) geometry);
            findClosestEdgeToPoint(edgeIds, point, filter);
        } else if (geometry instanceof LineString) {
            PointList pl = PointList.from((LineString) geometry);
            // TODO do map matching or routing
            int lastIdx = pl.size() - 1;
            if (pl.size() >= 2) {
                double meanLat = (pl.getLatitude(0) + pl.getLatitude(lastIdx)) / 2;
                double meanLon = (pl.getLongitude(0) + pl.getLongitude(lastIdx)) / 2;
                findClosestEdge(edgeIds, meanLat, meanLon, filter);
            }
        } else if (geometry instanceof MultiPoint) {
            for (Coordinate coordinate : geometry.getCoordinates()) {
                findClosestEdge(edgeIds, coordinate.y, coordinate.x, filter);
            }
        }
    }

    /**
     * This method reads the blockAreaString and creates a Collection of Shapes or a set of found edges if area is small enough.
     * @param useEdgeIdsUntilAreaSize until the specified area (specified in m²) use the findEdgesInShape method
     */
    public BlockArea parseBlockArea(String blockAreaString, EdgeFilter filter, double useEdgeIdsUntilAreaSize) {
        final String objectSeparator = ";";
        final String innerObjSep = ",";
        final String coordObjSep = " ";
        BlockArea blockArea = new BlockArea(graph);

        // Add blocked circular areas or points
        if (!blockAreaString.isEmpty()) {
            String[] blockedCircularAreasArr = blockAreaString.split(objectSeparator);
            for (int i = 0; i < blockedCircularAreasArr.length; i++) {
                String objectAsString = blockedCircularAreasArr[i];
                String[] splittedObject = objectAsString.split(innerObjSep);
                boolean isXYFormat = (splittedObject.length >0 && splittedObject[0].split(coordObjSep).length ==2);
                if(isXYFormat){
                	if (splittedObject.length >= 3 ) {
                		GeometryFactory gf = new GeometryFactory();
                	    Coordinate[] coords = new Coordinate[splittedObject.length];
                	    for  (int j = 0; j < splittedObject.length; j++){
                	    	String[] coordXY = splittedObject[j].split(coordObjSep);
                	    	coords[j] =  new Coordinate(Double.parseDouble(coordXY[0]), Double.parseDouble(coordXY[1]));
                	    }
                	    Polygon geom = gf.createPolygon(gf.createLinearRing(coords), null);
                	    double polygonArea = geom.getArea();
                        if (polygonArea > useEdgeIdsUntilAreaSize)
                            blockArea.add(geom);
                        else
                            findEdgesInShape(blockArea.blockedEdges, geom, filter);
                    }else{
                    	throw new IllegalArgumentException(objectAsString + " at index " + i + " need to be defined as lat,lon "
                                + "or as a circle lat,lon,radius or rectangular lat1,lon1,lat2,lon2");
                    }
                }else{
                	if (splittedObject.length == 4 ) {
                		
                		GeometryFactory gf = new GeometryFactory();
                	    Coordinate[] coords = new Coordinate[]{
                	        new Coordinate(Double.parseDouble(splittedObject[0]), Double.parseDouble(splittedObject[1])),
                	        new Coordinate(Double.parseDouble(splittedObject[2]), Double.parseDouble(splittedObject[1])),
                	        new Coordinate(Double.parseDouble(splittedObject[2]), Double.parseDouble(splittedObject[3])),
                	        new Coordinate(Double.parseDouble(splittedObject[0]), Double.parseDouble(splittedObject[3])),
                	        new Coordinate(Double.parseDouble(splittedObject[0]), Double.parseDouble(splittedObject[1])),
                	    };
                	    Polygon geom = gf.createPolygon(gf.createLinearRing(coords), null);
                	    double rectangleArea = geom.getArea();
                	    if (rectangleArea > useEdgeIdsUntilAreaSize)
                            blockArea.add(geom);
                        else
                            findEdgesInShape(blockArea.blockedEdges, geom, filter);
                    } else if (splittedObject.length == 3) {
                    	double lat = Double.parseDouble(splittedObject[0]);
                        double lon = Double.parseDouble(splittedObject[1]);
                        double radius = Double.parseDouble(splittedObject[2]);
                        GeometryFactory geomFact = new GeometryFactory();
                    	Geometry circle = geomFact.createPoint(new Coordinate(lat, lon)).buffer(radius);
                    	double circleArea = circle.getArea();
                        if (circleArea > useEdgeIdsUntilAreaSize) {
                            blockArea.add(circle);
                        } else {
                            findEdgesInShape(blockArea.blockedEdges, circle, filter);
                        }
                    } else if (splittedObject.length == 2) {
                        double lat = Double.parseDouble(splittedObject[0]);
                        double lon = Double.parseDouble(splittedObject[1]);
                        findClosestEdge(blockArea.blockedEdges, lat, lon, filter);
                    } else {
                        throw new IllegalArgumentException(objectAsString + " at index " + i + " need to be defined as lat,lon "
                                + "or as a circle lat,lon,radius or rectangular lat1,lon1,lat2,lon2");
                    }
                }
            }
        }
        return blockArea;
    }

    /**
     * This class handles edges and areas where access should be blocked.
     */
    public static class BlockArea {
        final GHIntHashSet blockedEdges = new GHIntHashSet();
        final List<Geometry> blockedShapes = new ArrayList<>();
        private final NodeAccess na;

        public BlockArea(Graph g) {
            na = g.getNodeAccess();
        }

        public void add(int edgeId) {
            blockedEdges.addAll(edgeId);
        }

        public void add(Geometry shape) {
            blockedShapes.add(shape);
        }

        /**
         * @return true if the specified edgeState is part of this BlockArea
         */
        public final boolean contains(EdgeIteratorState edgeState) {
            if (!blockedEdges.isEmpty() && blockedEdges.contains(edgeState.getEdge())) {
                return true;
            }

            if (!blockedShapes.isEmpty() && na != null) {
            	GeometryFactory geomFact = new GeometryFactory();
            	Geometry adjPoint = geomFact.createPoint(new Coordinate(na.getLatitude(edgeState.getAdjNode()), na.getLongitude(edgeState.getAdjNode())));
            	
                for (Geometry shape : blockedShapes) {
                    if (shape.contains(adjPoint))
                        return true;
                }
            }
            return false;
        }
    }
}