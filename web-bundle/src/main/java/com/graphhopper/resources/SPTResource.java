package com.graphhopper.resources;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.ProfileConfig;
import com.graphhopper.http.GHPointParam;
import com.graphhopper.isochrone.algorithm.ShortestPathTree;
import com.graphhopper.routing.ProfileResolver;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.BlockAreaWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphEdgeIdFinder;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import io.dropwizard.jersey.params.LongParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.*;

import static com.graphhopper.resources.RouteResource.errorIfLegacyParameters;
import static com.graphhopper.routing.util.TraversalMode.EDGE_BASED;
import static com.graphhopper.routing.util.TraversalMode.NODE_BASED;

/**
 * This resource provides the entire shortest path tree as response. In a simple CSV format discussed at #1577.
 */
@Path("spt")
public class SPTResource {

    private static final Logger logger = LoggerFactory.getLogger(SPTResource.class);

    public static class IsoLabelWithCoordinates {
        public int nodeId = -1;
        public int edgeId, prevEdgeId, prevNodeId = -1;
        public int timeMillis, prevTimeMillis;
        public int distance, prevDistance;
        public GHPoint coordinate, prevCoordinate;
    }

    private final GraphHopper graphHopper;
    private final ProfileResolver profileResolver;
    private final EncodingManager encodingManager;

    @Inject
    public SPTResource(GraphHopper graphHopper, ProfileResolver profileResolver, EncodingManager encodingManager) {
        this.graphHopper = graphHopper;
        this.profileResolver = profileResolver;
        this.encodingManager = encodingManager;
    }

    @GET
    @Produces("text/csv")
    public Response doGet(
            @Context UriInfo uriInfo,
            @QueryParam("profile") String profileName,
            @QueryParam("reverse_flow") @DefaultValue("false") boolean reverseFlow,
            @QueryParam("point") @NotNull GHPointParam point,
            @QueryParam("columns") String columnsParam,
            @QueryParam("time_limit") @DefaultValue("600") LongParam timeLimitInSeconds,
            @QueryParam("distance_limit") @DefaultValue("-1") LongParam distanceInMeter) {
        try {
            return executeGet(uriInfo, profileName, reverseFlow, point, columnsParam, timeLimitInSeconds, distanceInMeter);
        } catch (IllegalArgumentException e) {
            return returnBadRequest(e.getMessage());
        }
    }

    private Response executeGet(UriInfo uriInfo, String profileName, boolean reverseFlow, GHPointParam point, String columnsParam, LongParam timeLimitInSeconds, LongParam distanceInMeter) {
        StopWatch sw = new StopWatch().start();
        PMap hintsMap = new PMap();
        RouteResource.initHints(hintsMap, uriInfo.getQueryParameters());
        hintsMap.putObject(Parameters.CH.DISABLE, true);
        hintsMap.putObject(Parameters.Landmark.DISABLE, true);
        if (Helper.isEmpty(profileName)) {
            profileName = profileResolver.resolveProfile(hintsMap).getName();
            hintsMap.remove("weighting");
            hintsMap.remove("vehicle");
        }
        errorIfLegacyParameters(hintsMap);
        ProfileConfig profile = graphHopper.getProfile(profileName);
        if (profile == null) {
            throw new IllegalArgumentException("The requested profile '" + profileName + "' does not exist");
        }
        FlagEncoder encoder = encodingManager.getEncoder(profile.getVehicle());
        EdgeFilter edgeFilter = DefaultEdgeFilter.allEdges(encoder);
        LocationIndex locationIndex = graphHopper.getLocationIndex();
        QueryResult qr = locationIndex.findClosest(point.get().lat, point.get().lon, edgeFilter);
        if (!qr.isValid())
            throw new IllegalArgumentException("Point not found:" + point);

        Graph graph = graphHopper.getGraphHopperStorage();
        QueryGraph queryGraph = QueryGraph.lookup(graph, qr);
        NodeAccess nodeAccess = queryGraph.getNodeAccess();

        Weighting weighting = graphHopper.createWeighting(profile, hintsMap);
        if (hintsMap.has(Parameters.Routing.BLOCK_AREA))
            weighting = new BlockAreaWeighting(weighting, GraphEdgeIdFinder.createBlockArea(graph, locationIndex,
                    Collections.singletonList(point.get()), hintsMap, DefaultEdgeFilter.allEdges(encoder)));
        TraversalMode traversalMode = profile.isTurnCosts() ? EDGE_BASED : NODE_BASED;
        ShortestPathTree shortestPathTree = new ShortestPathTree(queryGraph, weighting, reverseFlow, traversalMode);

        if (distanceInMeter.get() > 0) {
            shortestPathTree.setDistanceLimit(distanceInMeter.get() + Math.max(distanceInMeter.get() * 0.14, 2_000));
        } else {
            double limit = timeLimitInSeconds.get() * 1000;
            shortestPathTree.setTimeLimit(limit + Math.max(limit * 0.14, 200_000));
        }

        final String COL_SEP = ",", LINE_SEP = "\n";
        List<String> columns;
        if (!Helper.isEmpty(columnsParam))
            columns = Arrays.asList(columnsParam.split(","));
        else
            columns = Arrays.asList("longitude", "latitude", "time", "distance");

        if (columns.isEmpty())
            throw new IllegalArgumentException("Either omit the columns parameter or specify the columns via comma separated values");

        Map<String, EncodedValue> pathDetails = new HashMap<>();
        for (String col : columns) {
            if (encodingManager.hasEncodedValue(col))
                pathDetails.put(col, encodingManager.getEncodedValue(col, EncodedValue.class));
        }

        StreamingOutput out = output -> {
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(output, Helper.UTF_CS))) {
                StringBuilder sb = new StringBuilder();
                for (String col : columns) {
                    if (sb.length() > 0)
                        sb.append(COL_SEP);
                    sb.append(col);
                }
                sb.append(LINE_SEP);
                writer.write(sb.toString());
                shortestPathTree.search(qr.getClosestNode(), l -> {
                    IsoLabelWithCoordinates label = isoLabelWithCoordinates(nodeAccess, l);
                    sb.setLength(0);
                    for (int colIndex = 0; colIndex < columns.size(); colIndex++) {
                        String col = columns.get(colIndex);
                        if (colIndex > 0)
                            sb.append(COL_SEP);

                        switch (col) {
                            case "node_id":
                                sb.append(label.nodeId);
                                continue;
                            case "prev_node_id":
                                sb.append(label.prevNodeId);
                                continue;
                            case "edge_id":
                                sb.append(label.edgeId);
                                continue;
                            case "prev_edge_id":
                                sb.append(label.prevEdgeId);
                                continue;
                            case "distance":
                                sb.append(label.distance);
                                continue;
                            case "prev_distance":
                                sb.append(label.prevCoordinate == null ? 0 : label.prevDistance);
                                continue;
                            case "time":
                                sb.append(label.timeMillis);
                                continue;
                            case "prev_time":
                                sb.append(label.prevCoordinate == null ? 0 : label.prevTimeMillis);
                                continue;
                            case "longitude":
                                sb.append(label.coordinate.lon);
                                continue;
                            case "prev_longitude":
                                sb.append(label.prevCoordinate == null ? null : label.prevCoordinate.lon);
                                continue;
                            case "latitude":
                                sb.append(label.coordinate.lat);
                                continue;
                            case "prev_latitude":
                                sb.append(label.prevCoordinate == null ? null : label.prevCoordinate.lat);
                                continue;
                        }

                        if (!EdgeIterator.Edge.isValid(label.edgeId))
                            continue;

                        EdgeIteratorState edge = queryGraph.getEdgeIteratorState(label.edgeId, label.nodeId);
                        if (edge == null)
                            continue;

                        if (col.equals(Parameters.Details.STREET_NAME)) {
                            sb.append(edge.getName().replaceAll(",", ""));
                            continue;
                        }

                        EncodedValue ev = pathDetails.get(col);
                        if (ev instanceof DecimalEncodedValue) {
                            DecimalEncodedValue dev = (DecimalEncodedValue) ev;
                            sb.append(reverseFlow ? edge.getReverse(dev) : edge.get(dev));
                        } else if (ev instanceof EnumEncodedValue) {
                            EnumEncodedValue eev = (EnumEncodedValue) ev;
                            sb.append(reverseFlow ? edge.getReverse(eev) : edge.get(eev));
                        } else if (ev instanceof BooleanEncodedValue) {
                            BooleanEncodedValue eev = (BooleanEncodedValue) ev;
                            sb.append(reverseFlow ? edge.getReverse(eev) : edge.get(eev));
                        } else if (ev instanceof IntEncodedValue) {
                            IntEncodedValue eev = (IntEncodedValue) ev;
                            sb.append(reverseFlow ? edge.getReverse(eev) : edge.get(eev));
                        } else {
                            throw new IllegalArgumentException("Unknown property " + col);
                        }
                    }
                    sb.append(LINE_SEP);
                    try {
                        writer.write(sb.toString());
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });

                logger.info("took: " + sw.stop().getSeconds() + ", visited nodes:" + shortestPathTree.getVisitedNodes() + ", " + uriInfo.getQueryParameters());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
        // took header does not make sense as we stream
        return Response.ok(out).build();
    }

    private Response returnBadRequest(String message) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("message", message);
        return Response.status(Response.Status.BAD_REQUEST).entity(json).type(MediaType.APPLICATION_JSON).build();
    }

    private IsoLabelWithCoordinates isoLabelWithCoordinates(NodeAccess na, ShortestPathTree.IsoLabel label) {
        double lat = na.getLatitude(label.node);
        double lon = na.getLongitude(label.node);
        IsoLabelWithCoordinates isoLabelWC = new IsoLabelWithCoordinates();
        isoLabelWC.nodeId = label.node;
        isoLabelWC.coordinate = new GHPoint(lat, lon);
        isoLabelWC.timeMillis = Math.round(label.time);
        isoLabelWC.distance = (int) Math.round(label.distance);
        isoLabelWC.edgeId = label.edge;
        if (label.parent != null) {
            ShortestPathTree.IsoLabel prevLabel = label.parent;
            int prevNodeId = prevLabel.node;
            double prevLat = na.getLatitude(prevNodeId);
            double prevLon = na.getLongitude(prevNodeId);
            isoLabelWC.prevNodeId = prevNodeId;
            isoLabelWC.prevEdgeId = prevLabel.edge;
            isoLabelWC.prevCoordinate = new GHPoint(prevLat, prevLon);
            isoLabelWC.prevDistance = (int) Math.round(prevLabel.distance);
            isoLabelWC.prevTimeMillis = Math.round(prevLabel.time);
        }
        return isoLabelWC;
    }
}
