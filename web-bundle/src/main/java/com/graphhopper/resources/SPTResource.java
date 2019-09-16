package com.graphhopper.resources;

import com.graphhopper.GraphHopper;
import com.graphhopper.isochrone.algorithm.Isochrone;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
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

/**
 * This resource provides the entire shortest path tree as response. In a simple CSV format discussed at #1577.
 */
@Path("spt")
public class SPTResource {

    private static final Logger logger = LoggerFactory.getLogger(SPTResource.class);

    private final GraphHopper graphHopper;
    private final EncodingManager encodingManager;

    @Inject
    public SPTResource(GraphHopper graphHopper, EncodingManager encodingManager) {
        this.graphHopper = graphHopper;
        this.encodingManager = encodingManager;
    }

    @GET
    @Produces("text/csv")
    public Response doGet(
            @Context HttpServletRequest httpReq,
            @Context UriInfo uriInfo,
            @QueryParam("vehicle") @DefaultValue("car") String vehicle,
            @QueryParam("reverse_flow") @DefaultValue("false") boolean reverseFlow,
            @QueryParam("point") GHPoint point,
            @QueryParam("columns") String columnsParam,
            @QueryParam("time_limit") @DefaultValue("600") long timeLimitInSeconds,
            @QueryParam("distance_limit") @DefaultValue("-1") double distanceInMeter) {

        if (point == null)
            throw new IllegalArgumentException("point parameter cannot be null");

        StopWatch sw = new StopWatch().start();

        if (!encodingManager.hasEncoder(vehicle))
            throw new IllegalArgumentException("vehicle not supported:" + vehicle);

        FlagEncoder encoder = encodingManager.getEncoder(vehicle);
        EdgeFilter edgeFilter = DefaultEdgeFilter.allEdges(encoder);
        LocationIndex locationIndex = graphHopper.getLocationIndex();
        QueryResult qr = locationIndex.findClosest(point.lat, point.lon, edgeFilter);
        if (!qr.isValid())
            throw new IllegalArgumentException("Point not found:" + point);

        Graph graph = graphHopper.getGraphHopperStorage();
        QueryGraph queryGraph = new QueryGraph(graph);
        queryGraph.lookup(Collections.singletonList(qr));

        HintsMap hintsMap = new HintsMap();
        RouteResource.initHints(hintsMap, uriInfo.getQueryParameters());

        Weighting weighting = graphHopper.createWeighting(hintsMap, encoder, graph);
        Isochrone isochrone = new Isochrone(queryGraph, weighting, reverseFlow);

        if (distanceInMeter > 0) {
            isochrone.setDistanceLimit(distanceInMeter);
        } else {
            isochrone.setTimeLimit(timeLimitInSeconds);
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
                isochrone.search(qr.getClosestNode(), label -> {
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

                logger.info("took: " + sw.stop().getSeconds() + ", visited nodes:" + isochrone.getVisitedNodes() + ", " + uriInfo.getQueryParameters());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
        // took header does not make sense as we stream
        return Response.ok(out).build();
    }
}
