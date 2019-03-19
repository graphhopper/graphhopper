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

import com.graphhopper.isochrone.algorithm.ContourBuilder;
import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.reader.gtfs.*;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.GHPoint;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.triangulate.ConformingDelaunayTriangulator;
import org.locationtech.jts.triangulate.ConstraintVertex;
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

@Path("isochrone")
public class PtIsochroneResource {

    private static final double JTS_TOLERANCE = 0.00001;

    private GtfsStorage gtfsStorage;
    private EncodingManager encodingManager;
    private GraphHopperStorage graphHopperStorage;
    private LocationIndex locationIndex;
//    private final STRtree spatialIndex;

    private final Function<Label, Double> z = label -> (double) label.currentTime;

    public PtIsochroneResource(GtfsStorage gtfsStorage, EncodingManager encodingManager, GraphHopperStorage graphHopperStorage, LocationIndex locationIndex) {
        this.gtfsStorage = gtfsStorage;
        this.encodingManager = encodingManager;
        this.graphHopperStorage = graphHopperStorage;
        this.locationIndex = locationIndex;
//        spatialIndex = new STRtree();
//        PtFlagEncoder ptFlagEncoder = (PtFlagEncoder) encodingManager.getEncoder("pt");
//        AllEdgesIterator allEdges = graphHopperStorage.getAllEdges();
//        while (allEdges.next()) {
//            if (ptFlagEncoder.getEdgeType(allEdges.getFlags()) == GtfsStorage.EdgeType.HIGHWAY) {
//                LineString geom = allEdges.fetchWayGeometry(3).toLineString(false);
//                spatialIndex.insert(geom.getEnvelopeInternal(), allEdges.getEdge());
//            }
//        }
    }

    public static class Response {
        public static class Info {
            public List<String> copyrights = new ArrayList<>();
        }
        public List<JsonFeature> polygons = new ArrayList<>();
        public Info info = new Info();
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public Response doGet(
            @QueryParam("point") GHPoint source,
            @QueryParam("time_limit") @DefaultValue("600") long seconds,
            @QueryParam("reverse_flow") @DefaultValue("false") boolean reverseFlow,
            @QueryParam(Parameters.PT.EARLIEST_DEPARTURE_TIME) String departureTimeString,
            @QueryParam(Parameters.PT.BLOCKED_ROUTE_TYPES) @DefaultValue("0") int blockedRouteTypes,
            @QueryParam("result") @DefaultValue("multipolygon") String format) {
        Instant initialTime;
        try {
            initialTime = Instant.parse(departureTimeString);
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "Illegal value for required parameter %s: [%s]", Parameters.PT.EARLIEST_DEPARTURE_TIME, departureTimeString));
        }

        double targetZ = initialTime.toEpochMilli() + seconds * 1000;

        GeometryFactory geometryFactory = new GeometryFactory();
        QueryGraph queryGraph = new QueryGraph(graphHopperStorage);
        final EdgeFilter filter = DefaultEdgeFilter.allEdges(graphHopperStorage.getEncodingManager().getEncoder("foot"));
        QueryResult queryResult = locationIndex.findClosest(source.lat, source.lon, filter);
        queryGraph.lookup(Collections.singletonList(queryResult));
        if (!queryResult.isValid()) {
            throw new IllegalArgumentException("Cannot find point: " + source);
        }

        PtFlagEncoder ptFlagEncoder = (PtFlagEncoder) encodingManager.getEncoder("pt");
        GraphExplorer graphExplorer = new GraphExplorer(queryGraph, new FastestWeighting(encodingManager.getEncoder("foot")), ptFlagEncoder, gtfsStorage, RealtimeFeed.empty(gtfsStorage), reverseFlow, Collections.emptyList(), false, 5.0);
        MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, ptFlagEncoder, reverseFlow, Double.MAX_VALUE, false, false, false, 1000000, Collections.emptyList());

        Map<Coordinate, Double> z1 = new HashMap<>();
        NodeAccess nodeAccess = queryGraph.getNodeAccess();

        MultiCriteriaLabelSetting.SPTVisitor sptVisitor = nodeLabel -> {
            Coordinate nodeCoordinate = new Coordinate(nodeAccess.getLongitude(nodeLabel.adjNode), nodeAccess.getLatitude(nodeLabel.adjNode));
            z1.merge(nodeCoordinate, this.z.apply(nodeLabel), Math::min);
        };

        if (format.equals("multipoint")) {
            router.calcLabels(queryResult.getClosestNode(), -1, initialTime, blockedRouteTypes, sptVisitor, label -> label.currentTime <= targetZ);
            MultiPoint exploredPoints = geometryFactory.createMultiPointFromCoords(z1.keySet().toArray(new Coordinate[0]));
            return wrap(exploredPoints);
        } else {
            router.calcLabelsAndNeighbors(queryResult.getClosestNode(), -1, initialTime, blockedRouteTypes, sptVisitor, label -> label.currentTime <= targetZ);
            MultiPoint exploredPointsAndNeighbors = geometryFactory.createMultiPointFromCoords(z1.keySet().toArray(new Coordinate[0]));

            // This is what we need to do once we can do bounding-box queries on our spatial index.
            // Then it should be impossible for unreachable encroaching points to not be found.

//            spatialIndex.query(exploredPointsAndNeighbors.getEnvelopeInternal(), edgeId -> {
//                EdgeIteratorState e = graphHopperStorage.getEdgeIteratorState((int) edgeId, Integer.MIN_VALUE);
//                Coordinate nodeCoordinate = new Coordinate(nodeAccess.getLongitude(e.getBaseNode()), nodeAccess.getLatitude(e.getBaseNode()));
//                z1.merge(nodeCoordinate, Double.MAX_VALUE, Math::min);
//                nodeCoordinate = new Coordinate(nodeAccess.getLongitude(e.getAdjNode()), nodeAccess.getLatitude(e.getAdjNode()));
//                z1.merge(nodeCoordinate, Double.MAX_VALUE, Math::min);
//            });
//            exploredPointsAndNeighbors = geometryFactory.createMultiPointFromCoords(z1.keySet().toArray(new Coordinate[0]));

            CoordinateList siteCoords = DelaunayTriangulationBuilder.extractUniqueCoordinates(exploredPointsAndNeighbors);
            List<ConstraintVertex> constraintVertices = new ArrayList<>();
            for (Object siteCoord : siteCoords) {
                Coordinate coord = (Coordinate) siteCoord;
                constraintVertices.add(new ConstraintVertex(coord));
            }

            ConformingDelaunayTriangulator cdt = new ConformingDelaunayTriangulator(constraintVertices, JTS_TOLERANCE);
            cdt.setConstraints(new ArrayList(), new ArrayList());
            cdt.formInitialDelaunay();

            QuadEdgeSubdivision tin = cdt.getSubdivision();

            for (Vertex vertex : (Collection<Vertex>) tin.getVertices(true)) {
                if (tin.isFrameVertex(vertex)) {
                    vertex.setZ(Double.MAX_VALUE);
                } else {
                    Double aDouble = z1.get(vertex.getCoordinate());
                    if (aDouble != null) {
                        vertex.setZ(aDouble);
                    } else {
                        vertex.setZ(Double.MAX_VALUE);
                    }
                }
            }

            ContourBuilder contourBuilder = new ContourBuilder(tin);
            MultiPolygon isoline = contourBuilder.computeIsoline(targetZ);

            // debugging tool
            if (format.equals("triangulation")) {
                Response response = new Response();
                for (Vertex vertex : (Collection<Vertex>) tin.getVertices(true)) {
                    JsonFeature feature = new JsonFeature();
                    feature.setGeometry(geometryFactory.createPoint(vertex.getCoordinate()));
                    HashMap<String, Object> properties = new HashMap<>();
                    properties.put("z", vertex.getZ());
                    feature.setProperties(properties);
                    response.polygons.add(feature);
                }
                for (QuadEdge edge : (Collection<QuadEdge>) tin.getPrimaryEdges(false)) {
                    JsonFeature feature = new JsonFeature();
                    feature.setGeometry(edge.toLineSegment().toGeometry(geometryFactory));
                    HashMap<String, Object> properties = new HashMap<>();
                    feature.setProperties(properties);
                    response.polygons.add(feature);
                }
                JsonFeature feature = new JsonFeature();
                feature.setGeometry(isoline);
                HashMap<String, Object> properties = new HashMap<>();
                properties.put("z", targetZ);
                feature.setProperties(properties);
                response.polygons.add(feature);
                response.info.copyrights.add("GraphHopper");
                response.info.copyrights.add("OpenStreetMap contributors");
                return response;
            } else {
                return wrap(isoline);
            }
        }

    }

    private Response wrap(Geometry isoline) {
        JsonFeature feature = new JsonFeature();
        feature.setGeometry(isoline);
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("bucket", 0);
        feature.setProperties(properties);

        Response response = new Response();
        response.polygons.add(feature);
        response.info.copyrights.add("GraphHopper");
        response.info.copyrights.add("OpenStreetMap contributors");
        return response;
    }

}
