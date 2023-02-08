package com.graphhopper.resources;

import com.graphhopper.core.util.StopWatch;
import com.graphhopper.core.util.EdgeIteratorState;
import com.graphhopper.core.util.EdgeIterator;
import com.graphhopper.core.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.http.GHPointParam;
import com.graphhopper.http.ProfileResolver;
import com.graphhopper.isochrone.algorithm.ShortestPathTree;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.*;

import static com.graphhopper.resources.RouteResource.removeLegacyParameters;
import static com.graphhopper.routing.util.TraversalMode.EDGE_BASED;
import static com.graphhopper.routing.util.TraversalMode.NODE_BASED;
import static com.graphhopper.util.Parameters.Details.STREET_NAME;

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

    // Annotating this as application/json because errors come out as json, and
    // IllegalArgumentExceptions are not mapped to a fixed mediatype, because in RouteResource, it could be GPX.
    @GET
    @Produces({"text/csv", "application/json"})
    public Response doGet(
            @Context UriInfo uriInfo,
            @QueryParam("profile") String profileName,
            @QueryParam("reverse_flow") @DefaultValue("false") boolean reverseFlow,
            @QueryParam("point") @NotNull GHPointParam point,
            @QueryParam("columns") String columnsParam,
            @QueryParam("time_limit") @DefaultValue("600") OptionalLong timeLimitInSeconds,
            @QueryParam("distance_limit") @DefaultValue("-1") OptionalLong distanceInMeter) {
        StopWatch sw = new StopWatch().start();
        PMap hintsMap = new PMap();
        RouteResource.initHints(hintsMap, uriInfo.getQueryParameters());
        hintsMap.putObject(Parameters.CH.DISABLE, true);
        hintsMap.putObject(Parameters.Landmark.DISABLE, true);

        PMap profileResolverHints = new PMap(hintsMap);
        profileResolverHints.putObject("profile", profileName);
        profileName = profileResolver.resolveProfile(profileResolverHints);
        removeLegacyParameters(hintsMap);

        Profile profile = graphHopper.getProfile(profileName);
        if (profile == null)
            throw new IllegalArgumentException("The requested profile '" + profileName + "' does not exist");
        LocationIndex locationIndex = graphHopper.getLocationIndex();
        BaseGraph graph = graphHopper.getBaseGraph();
        Weighting weighting = graphHopper.createWeighting(profile, hintsMap);
        BooleanEncodedValue inSubnetworkEnc = graphHopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(profileName));
        Snap snap = locationIndex.findClosest(point.get().lat, point.get().lon, new DefaultSnapFilter(weighting, inSubnetworkEnc));
        if (!snap.isValid())
            throw new IllegalArgumentException("Point not found:" + point);
        QueryGraph queryGraph = QueryGraph.create(graph, snap);
        NodeAccess nodeAccess = queryGraph.getNodeAccess();
        TraversalMode traversalMode = profile.isTurnCosts() ? EDGE_BASED : NODE_BASED;
        ShortestPathTree shortestPathTree = new ShortestPathTree(queryGraph, queryGraph.wrapWeighting(weighting), reverseFlow, traversalMode);

        if (distanceInMeter.orElseThrow(() -> new IllegalArgumentException("query param distance_limit is not a number.")) > 0) {
            shortestPathTree.setDistanceLimit(distanceInMeter.getAsLong());
        } else {
            double limit = timeLimitInSeconds.orElseThrow(() -> new IllegalArgumentException("query param time_limit is not a number.")) * 1000d;
            shortestPathTree.setTimeLimit(limit);
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
                shortestPathTree.search(snap.getClosestNode(), l -> {
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
                                sb.append(Helper.round6(label.coordinate.lon));
                                continue;
                            case "prev_longitude":
                                sb.append(label.prevCoordinate == null ? null : Helper.round6(label.prevCoordinate.lon));
                                continue;
                            case "latitude":
                                sb.append(Helper.round6(label.coordinate.lat));
                                continue;
                            case "prev_latitude":
                                sb.append(label.prevCoordinate == null ? null : Helper.round6(label.prevCoordinate.lat));
                                continue;
                        }

                        if (!EdgeIterator.Edge.isValid(label.edgeId))
                            continue;

                        EdgeIteratorState edge = queryGraph.getEdgeIteratorState(label.edgeId, label.nodeId);
                        if (edge == null)
                            continue;

                        if (col.equals(STREET_NAME)) {
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
        // Give media type explicitly since we are annotating CSV and JSON, because error messages are JSON.
        return Response.ok(out).type("text/csv").build();
    }

    private IsoLabelWithCoordinates isoLabelWithCoordinates(NodeAccess na, ShortestPathTree.IsoLabel label) {
        double lat = na.getLat(label.node);
        double lon = na.getLon(label.node);
        IsoLabelWithCoordinates isoLabelWC = new IsoLabelWithCoordinates();
        isoLabelWC.nodeId = label.node;
        isoLabelWC.coordinate = new GHPoint(lat, lon);
        isoLabelWC.timeMillis = Math.round(label.time);
        isoLabelWC.distance = (int) Math.round(label.distance);
        isoLabelWC.edgeId = label.edge;
        if (label.parent != null) {
            ShortestPathTree.IsoLabel prevLabel = label.parent;
            int prevNodeId = prevLabel.node;
            double prevLat = na.getLat(prevNodeId);
            double prevLon = na.getLon(prevNodeId);
            isoLabelWC.prevNodeId = prevNodeId;
            isoLabelWC.prevEdgeId = prevLabel.edge;
            isoLabelWC.prevCoordinate = new GHPoint(prevLat, prevLon);
            isoLabelWC.prevDistance = (int) Math.round(prevLabel.distance);
            isoLabelWC.prevTimeMillis = Math.round(prevLabel.time);
        }
        return isoLabelWC;
    }
}
