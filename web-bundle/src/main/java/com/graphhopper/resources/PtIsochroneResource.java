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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateList;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.triangulate.ConformingDelaunayTriangulator;
import org.locationtech.jts.triangulate.ConstraintVertex;
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import com.graphhopper.gtfs.GraphExplorer;
import com.graphhopper.gtfs.GtfsStorage;
import com.graphhopper.gtfs.Label;
import com.graphhopper.gtfs.MultiCriteriaLabelSetting;
import com.graphhopper.gtfs.PtEncodedValues;
import com.graphhopper.gtfs.RealtimeFeed;
import com.graphhopper.isochrone.algorithm.ContourBuilder;
import com.graphhopper.isochrone.algorithm.ReadableTriangulation;
import com.graphhopper.jackson.ResponsePathSerializer;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.JsonFeature;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;

@Path("isochrone-pt")
public class PtIsochroneResource {

    private static final double JTS_TOLERANCE = 0.00001;
    private static final String FOOT_PROFILE_NAME = "foot";

    private final GtfsStorage gtfsStorage;
    private final EncodingManager encodingManager;
    private final GraphHopperStorage graphHopperStorage;
    private final LocationIndex locationIndex;

    private final Function<Label, Double> z = label -> (double) label.currentTime;

    @Inject
    public PtIsochroneResource(GtfsStorage gtfsStorage, EncodingManager encodingManager, GraphHopperStorage graphHopperStorage, LocationIndex locationIndex) {
        this.gtfsStorage = gtfsStorage;
        this.encodingManager = encodingManager;
        this.graphHopperStorage = graphHopperStorage;
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
            @QueryParam("point") GHPoint source,
            @QueryParam("time_limit") @DefaultValue("600") long seconds,
            @QueryParam("reverse_flow") @DefaultValue("false") boolean reverseFlow,
            @QueryParam("pt.earliest_departure_time") String departureTimeString,
            @QueryParam("pt.blocked_route_types") @DefaultValue("0") int blockedRouteTypes,
            @QueryParam("result") @DefaultValue("multipolygon") String format) {
        Instant initialTime;
        try {
            initialTime = Instant.parse(departureTimeString);
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "Illegal value for required parameter %s: [%s]", "pt.earliest_departure_time", departureTimeString));
        }

        double targetZ = initialTime.toEpochMilli() + seconds * 1000;

        GeometryFactory geometryFactory = new GeometryFactory();
        PMap hintsMap = new PMap();
        RouteResource.initHints(hintsMap, new MultivaluedHashMap<>());
        hintsMap.putObject(Parameters.CH.DISABLE, true);
        hintsMap.putObject(Parameters.Landmark.DISABLE, true);
        final FlagEncoder footEncoder = encodingManager.getEncoder(FOOT_PROFILE_NAME);
        final Weighting weighting = new FastestWeighting(footEncoder);
        final EdgeFilter filter = new DefaultSnapFilter(weighting, encodingManager.getBooleanEncodedValue(Subnetwork.key(FOOT_PROFILE_NAME)));
        final Snap snap = locationIndex.findClosest(source.lat, source.lon, filter);
        QueryGraph queryGraph = QueryGraph.create(graphHopperStorage, Collections.singletonList(snap));
        if (!snap.isValid()) {
            throw new IllegalArgumentException("Cannot find point: " + source);
        }

        PtEncodedValues ptEncodedValues = PtEncodedValues.fromEncodingManager(encodingManager);
        GraphExplorer graphExplorer = new GraphExplorer(queryGraph, weighting, ptEncodedValues, gtfsStorage, RealtimeFeed.empty(gtfsStorage), reverseFlow, false, false, 5.0, reverseFlow, blockedRouteTypes);
        MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, ptEncodedValues, reverseFlow, false, false, 1000000, Collections.emptyList());

        Map<Coordinate, Double> z1 = new HashMap<>();
        NodeAccess nodeAccess = queryGraph.getNodeAccess();

        MultiCriteriaLabelSetting.SPTVisitor sptVisitor = nodeLabel -> {
            Coordinate nodeCoordinate = new Coordinate(nodeAccess.getLon(nodeLabel.adjNode), nodeAccess.getLat(nodeLabel.adjNode));
            z1.merge(nodeCoordinate, this.z.apply(nodeLabel), Math::min);
        };

        if (format.equals("multipoint")) {
            calcLabels(router, snap.getClosestNode(), initialTime, sptVisitor, label -> label.currentTime <= targetZ);
            MultiPoint exploredPoints = geometryFactory.createMultiPointFromCoords(z1.keySet().toArray(new Coordinate[0]));
            return wrap(exploredPoints);
        } else {
            calcLabels(router, snap.getClosestNode(), initialTime, sptVisitor, label -> label.currentTime <= targetZ);
            MultiPoint exploredPoints = geometryFactory.createMultiPointFromCoords(z1.keySet().toArray(new Coordinate[0]));

            // Get at least all nodes within our bounding box (I think convex hull would be enough.)
            // I think then we should have all possible encroaching points. (Proof needed.)
            locationIndex.query(BBox.fromEnvelope(exploredPoints.getEnvelopeInternal()), edgeId -> {
                EdgeIteratorState edge = queryGraph.getEdgeIteratorStateForKey(edgeId * 2);
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

    private static void calcLabels(MultiCriteriaLabelSetting router, int from, Instant startTime, MultiCriteriaLabelSetting.SPTVisitor visitor, Predicate<Label> predicate) {
        Iterator<Label> iterator = router.calcLabels(from, startTime).iterator();
        while(iterator.hasNext()) {
            Label label = iterator.next();
            if (!predicate.test(label)) {
                break;
            }
            visitor.visit(label);
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
