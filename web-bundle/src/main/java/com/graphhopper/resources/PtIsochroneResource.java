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

import com.conveyal.gtfs.GTFSFeed;
import com.graphhopper.gtfs.*;
import com.graphhopper.http.GHLocationParam;
import com.graphhopper.http.OffsetDateTimeParam;
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
import com.graphhopper.util.exceptions.PointNotFoundException;
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
import java.util.function.Predicate;

@Path("isochrone-pt")
public class PtIsochroneResource {

    private static final double JTS_TOLERANCE = 0.00001;

    private final GtfsStorage gtfsStorage;
    private final EncodingManager encodingManager;
    private final GraphHopperStorage graphHopperStorage;
    private final LocationIndex locationIndex;

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
        final FlagEncoder footEncoder = encodingManager.getEncoder("foot");
        final Weighting weighting = new FastestWeighting(footEncoder);

        Snap snap = findByPointOrStation(location, weighting);
        QueryGraph queryGraph = QueryGraph.create(graphHopperStorage, Collections.singletonList(snap));
        if (!snap.isValid()) {
            throw new PointNotFoundException("Cannot find location: " + location, 0);
        }

        PtEncodedValues ptEncodedValues = PtEncodedValues.fromEncodingManager(encodingManager);
        GraphExplorer graphExplorer = new GraphExplorer(queryGraph, weighting, ptEncodedValues, gtfsStorage, RealtimeFeed.empty(gtfsStorage), reverseFlow, false, false, 5.0, reverseFlow, blockedRouteTypes);
        MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, ptEncodedValues, reverseFlow, false, false, 0, Collections.emptyList());

        Map<Coordinate, Double> z1 = new HashMap<>();
        NodeAccess nodeAccess = queryGraph.getNodeAccess();

        MultiCriteriaLabelSetting.SPTVisitor sptVisitor = nodeLabel -> {
            Coordinate nodeCoordinate = new Coordinate(nodeAccess.getLon(nodeLabel.adjNode), nodeAccess.getLat(nodeLabel.adjNode));
            z1.merge(nodeCoordinate, (double) (nodeLabel.currentTime - initialTime.toEpochMilli()) * (reverseFlow ? -1 : 1), Math::min);
        };

        if (format.equals("multipoint")) {
            calcLabels(router, snap.getClosestNode(), initialTime, sptVisitor, label -> (label.currentTime - initialTime.toEpochMilli()) * (reverseFlow ? -1 : 1) <= targetZ);
            MultiPoint exploredPoints = geometryFactory.createMultiPointFromCoords(z1.keySet().toArray(new Coordinate[0]));
            return wrap(exploredPoints);
        } else {
            calcLabels(router, snap.getClosestNode(), initialTime, sptVisitor, label -> (label.currentTime - initialTime.toEpochMilli()) * (reverseFlow ? -1 : 1) <= targetZ);
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

    private Snap findByPointOrStation(GHLocation location, Weighting weighting) {
        if (location instanceof GHPointLocation) {
            final EdgeFilter filter = new DefaultSnapFilter(weighting, encodingManager.getBooleanEncodedValue(Subnetwork.key("foot")));
            return locationIndex.findClosest(((GHPointLocation) location).ghPoint.lat, ((GHPointLocation) location).ghPoint.lon, filter);
        } else if (location instanceof GHStationLocation) {
            for (Map.Entry<String, GTFSFeed> entry : gtfsStorage.getGtfsFeeds().entrySet()) {
                final Integer node = gtfsStorage.getStationNodes().get(new GtfsStorage.FeedIdWithStopId(entry.getKey(), ((GHStationLocation) location).stop_id));
                if (node != null) {
                    Snap snap = new Snap(graphHopperStorage.getNodeAccess().getLat(node), graphHopperStorage.getNodeAccess().getLon(node));
                    snap.setSnappedPosition(Snap.Position.TOWER);
                    snap.setClosestNode(node);
                    return snap;
                }
            }
            throw new PointNotFoundException("Cannot find station: " + ((GHStationLocation) location).stop_id, 0);
        } else {
            throw new RuntimeException();
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
