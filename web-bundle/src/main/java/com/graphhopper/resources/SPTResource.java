package com.graphhopper.resources;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.ProfileConfig;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
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

import static com.graphhopper.util.Parameters.Routing.EDGE_BASED;
import static com.graphhopper.util.Parameters.Routing.TURN_COSTS;

/**
 * This resource provides the entire shortest path tree as response. In a simple CSV format discussed at #1577.
 */
@Path("spt")
public class SPTResource {

    private static final Logger logger = LoggerFactory.getLogger(SPTResource.class);

    public static class IsoLabelWithCoordinates {
        public final int nodeId;
        public int edgeId, prevEdgeId, prevNodeId;
        public int timeMillis, prevTimeMillis;
        public int distance, prevDistance;
        public GHPoint coordinate, prevCoordinate;

        public IsoLabelWithCoordinates(int nodeId) {
            this.nodeId = nodeId;
        }
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
            @QueryParam("reverse_flow") @DefaultValue("false") boolean reverseFlow,
            @QueryParam("point") GHPoint point,
            @QueryParam("columns") String columnsParam,
            @QueryParam("time_limit") @DefaultValue("600") long timeLimitInSeconds,
            @QueryParam("distance_limit") @DefaultValue("-1") double distanceInMeter) {

        if (point == null)
            throw new IllegalArgumentException("point parameter cannot be null");

        StopWatch sw = new StopWatch().start();
        HintsMap hintsMap = new HintsMap();
        RouteResource.initHints(hintsMap, uriInfo.getQueryParameters());
        if (!hintsMap.getBool(Parameters.CH.DISABLE, true))
            throw new IllegalArgumentException("Currently you cannot use speed mode for /spt, Do not use `ch.disable=false`");
        if (!hintsMap.getBool(Parameters.Landmark.DISABLE, true))
            throw new IllegalArgumentException("Currently you cannot use hybrid mode for /spt, Do not use `lm.disable=false`");
        if (hintsMap.getBool(Parameters.Routing.EDGE_BASED, false))
            throw new IllegalArgumentException("Currently you cannot use edge-based for /spt. Do not use `edge_based=true`");
        if (hintsMap.getBool(Parameters.Routing.TURN_COSTS, false))
            throw new IllegalArgumentException("Currently you cannot use turn costs for /spt, Do not use `turn_costs=true`");

        hintsMap.putObject(Parameters.CH.DISABLE, true);
        hintsMap.putObject(Parameters.Landmark.DISABLE, true);
        // ignore these parameters for profile selection, because we fall back to node-based without turn costs so far
        hintsMap.remove(TURN_COSTS);
        hintsMap.remove(EDGE_BASED);
        // todo: #1934, only try to resolve the profile if no profile is given!
        ProfileConfig profile = profileResolver.resolveProfile(hintsMap);
        FlagEncoder encoder = encodingManager.getEncoder(profile.getVehicle());
        EdgeFilter edgeFilter = DefaultEdgeFilter.allEdges(encoder);
        LocationIndex locationIndex = graphHopper.getLocationIndex();
        QueryResult qr = locationIndex.findClosest(point.lat, point.lon, edgeFilter);
        if (!qr.isValid())
            throw new IllegalArgumentException("Point not found:" + point);

        Graph graph = graphHopper.getGraphHopperStorage();
        QueryGraph queryGraph = QueryGraph.lookup(graph, qr);
        NodeAccess nodeAccess = queryGraph.getNodeAccess();

        // have to disable turn costs, as isochrones are running node-based
        Weighting weighting = graphHopper.createWeighting(profile, hintsMap, true);
        if (hintsMap.has(Parameters.Routing.BLOCK_AREA))
            weighting = new BlockAreaWeighting(weighting, GraphEdgeIdFinder.createBlockArea(graph, locationIndex,
                    Collections.singletonList(point), hintsMap, DefaultEdgeFilter.allEdges(encoder)));

        ShortestPathTree shortestPathTree = new ShortestPathTree(queryGraph, weighting, reverseFlow);

        if (distanceInMeter > 0) {
            shortestPathTree.setDistanceLimit(distanceInMeter + Math.max(distanceInMeter * 0.14, 2_000));
        } else {
            double limit = timeLimitInSeconds * 1000;
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

    private IsoLabelWithCoordinates isoLabelWithCoordinates(NodeAccess na, ShortestPathTree.IsoLabel label) {
        double lat = na.getLatitude(label.adjNode);
        double lon = na.getLongitude(label.adjNode);
        IsoLabelWithCoordinates isoLabelWC = new IsoLabelWithCoordinates(label.adjNode);
        isoLabelWC.coordinate = new GHPoint(lat, lon);
        isoLabelWC.timeMillis = Math.round(label.time);
        isoLabelWC.distance = (int) Math.round(label.distance);
        isoLabelWC.edgeId = label.edge;
        if (label.parent != null) {
            ShortestPathTree.IsoLabel prevLabel = (ShortestPathTree.IsoLabel) label.parent;
            int prevNodeId = prevLabel.adjNode;
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
