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

import com.conveyal.gtfs.model.Stop;
import com.graphhopper.gtfs.*;
import com.graphhopper.http.GHLocationParam;
import com.graphhopper.http.OffsetDateTimeParam;
import com.graphhopper.isochrone.algorithm.ContourBuilder;
import com.graphhopper.isochrone.algorithm.ReadableTriangulation;
import com.graphhopper.jackson.ResponsePathSerializer;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.core.util.JsonFeature;
import com.graphhopper.util.shapes.BBox;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.triangulate.ConformingDelaunayTriangulator;
import org.locationtech.jts.triangulate.ConstraintVertex;
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.*;

@Path("isochrone-pt")
public class PtIsochroneResource {

    private static final double JTS_TOLERANCE = 0.00001;

    private final GtfsStorage gtfsStorage;
    private final EncodingManager encodingManager;
    private final BaseGraph baseGraph;
    private final LocationIndex locationIndex;

    @Inject
    public PtIsochroneResource(GtfsStorage gtfsStorage, EncodingManager encodingManager, BaseGraph baseGraph, LocationIndex locationIndex) {
        this.gtfsStorage = gtfsStorage;
        this.encodingManager = encodingManager;
        this.baseGraph = baseGraph;
        this.locationIndex = locationIndex;
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
            @QueryParam("point") GHLocationParam sourceParam,
            @QueryParam("time_limit") @DefaultValue("600") long seconds,
            @QueryParam("reverse_flow") @DefaultValue("false") boolean reverseFlow,
            @QueryParam("pt.earliest_departure_time") @NotNull OffsetDateTimeParam departureTimeParam,
            @QueryParam("pt.blocked_route_types") @DefaultValue("0") int blockedRouteTypes,
            @QueryParam("result") @DefaultValue("multipolygon") String format) {
        Instant initialTime = departureTimeParam.get().toInstant();
        GHLocation location = sourceParam.get();

        double targetZ = seconds * 1000;

        GeometryFactory geometryFactory = new GeometryFactory();
        BooleanEncodedValue accessEnc = encodingManager.getBooleanEncodedValue(VehicleAccess.key("foot"));
        DecimalEncodedValue speedEnc = encodingManager.getDecimalEncodedValue(VehicleSpeed.key("foot"));
        final Weighting weighting = new FastestWeighting(accessEnc, speedEnc);
        DefaultSnapFilter snapFilter = new DefaultSnapFilter(weighting, encodingManager.getBooleanEncodedValue(Subnetwork.key("foot")));

        PtLocationSnapper.Result snapResult = new PtLocationSnapper(baseGraph, locationIndex, gtfsStorage).snapAll(Arrays.asList(location), Arrays.asList(snapFilter));
        GraphExplorer graphExplorer = new GraphExplorer(snapResult.queryGraph, gtfsStorage.getPtGraph(), weighting, gtfsStorage, RealtimeFeed.empty(), reverseFlow, false, false, 5.0, reverseFlow, blockedRouteTypes);
        MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, reverseFlow, false, false, 0, Collections.emptyList());

        Map<Coordinate, Double> z1 = new HashMap<>();
        NodeAccess nodeAccess = snapResult.queryGraph.getNodeAccess();

        for (Label label : router.calcLabels(snapResult.nodes.get(0), initialTime)) {
            if (!((label.currentTime - initialTime.toEpochMilli()) * (reverseFlow ? -1 : 1) <= targetZ)) {
                break;
            }
            if (label.node.streetNode != -1) {
                Coordinate nodeCoordinate = new Coordinate(nodeAccess.getLon(label.node.streetNode), nodeAccess.getLat(label.node.streetNode));
                z1.merge(nodeCoordinate, (double) (label.currentTime - initialTime.toEpochMilli()) * (reverseFlow ? -1 : 1), Math::min);
            } else if (label.edge != null && (label.edge.getType() == GtfsStorage.EdgeType.EXIT_PT || label.edge.getType() == GtfsStorage.EdgeType.ENTER_PT)) {
                GtfsStorage.PlatformDescriptor platformDescriptor = label.edge.getPlatformDescriptor();
                Stop stop = gtfsStorage.getGtfsFeeds().get(platformDescriptor.feed_id).stops.get(platformDescriptor.stop_id);
                Coordinate nodeCoordinate = new Coordinate(stop.stop_lon, stop.stop_lat);
                z1.merge(nodeCoordinate, (double) (label.currentTime - initialTime.toEpochMilli()) * (reverseFlow ? -1 : 1), Math::min);
            }
        }

        if (format.equals("multipoint")) {
            MultiPoint exploredPoints = geometryFactory.createMultiPointFromCoords(z1.keySet().toArray(new Coordinate[0]));
            return wrap(exploredPoints);
        } else {
            MultiPoint exploredPoints = geometryFactory.createMultiPointFromCoords(z1.keySet().toArray(new Coordinate[0]));

            // Get at least all nodes within our bounding box (I think convex hull would be enough.)
            // I think then we should have all possible encroaching points. (Proof needed.)
            locationIndex.query(BBox.fromEnvelope(exploredPoints.getEnvelopeInternal()), edgeId -> {
                EdgeIteratorState edge = snapResult.queryGraph.getEdgeIteratorStateForKey(edgeId * 2);
                z1.merge(new Coordinate(nodeAccess.getLon(edge.getBaseNode()), nodeAccess.getLat(edge.getBaseNode())), Double.MAX_VALUE, Math::min);
                z1.merge(new Coordinate(nodeAccess.getLon(edge.getAdjNode()), nodeAccess.getLat(edge.getAdjNode())), Double.MAX_VALUE, Math::min);
            });
            exploredPoints = geometryFactory.createMultiPointFromCoords(z1.keySet().toArray(new Coordinate[0]));

            CoordinateList siteCoords = DelaunayTriangulationBuilder.extractUniqueCoordinates(exploredPoints);
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

            ReadableTriangulation triangulation = ReadableTriangulation.wrap(tin);
            ContourBuilder contourBuilder = new ContourBuilder(triangulation);
            MultiPolygon isoline = contourBuilder.computeIsoline(targetZ, triangulation.getEdges());

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
                response.info.copyrights.addAll(ResponsePathSerializer.COPYRIGHTS);
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
        response.info.copyrights.addAll(ResponsePathSerializer.COPYRIGHTS);
        return response;
    }

}
