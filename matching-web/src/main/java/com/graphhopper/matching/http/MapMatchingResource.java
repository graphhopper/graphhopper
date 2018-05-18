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
package com.graphhopper.matching.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.http.WebHelper;
import com.graphhopper.matching.*;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.*;

import static com.graphhopper.util.Parameters.Routing.*;

/**
 * Resource to use map matching of GraphHopper in a remote client application.
 *
 * @author Peter Karich
 */
@javax.ws.rs.Path("match")
public class MapMatchingResource {

    private static final Logger logger = LoggerFactory.getLogger(MapMatchingResource.class);

    private final GraphHopper graphHopper;
    private final EncodingManager encodingManager;
    private final double gpsMaxAccuracy;
    private final TranslationMap trMap;

    @Inject
    public MapMatchingResource(CmdArgs configuration, GraphHopper graphHopper, EncodingManager encodingManager,
                               TranslationMap trMap) {
        this.graphHopper = graphHopper;
        this.encodingManager = encodingManager;
        this.gpsMaxAccuracy = configuration.getDouble("web.gps.max_accuracy", 100);
        this.trMap = trMap;
    }

    @POST
    @Consumes({MediaType.APPLICATION_XML, "application/gpx+xml"})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, "application/gpx+xml"})
    public Response doGet(
            @Context HttpServletRequest httpReq,
            @QueryParam(WAY_POINT_MAX_DISTANCE) @DefaultValue("1") double minPathPrecision,
            @QueryParam("type") @DefaultValue("json") String outType,
            @QueryParam(INSTRUCTIONS) @DefaultValue("true") boolean instructions,
            @QueryParam(CALC_POINTS) @DefaultValue("true") boolean calcPoints,
            @QueryParam("elevation") @DefaultValue("false") boolean enableElevation,
            @QueryParam("points_encoded") @DefaultValue("true") boolean pointsEncoded,
            @QueryParam("vehicle") @DefaultValue("car") String vehicleStr,
            @QueryParam("locale") @DefaultValue("en") String localeStr,
            @QueryParam(Parameters.DETAILS.PATH_DETAILS) List<String> pathDetails,
            @QueryParam("gpx.route") @DefaultValue("true") boolean withRoute /* default to false for the route part in next API version, see #437 */,
            @QueryParam("gpx.track") @DefaultValue("true") boolean withTrack,
            @QueryParam("gpx.waypoints") @DefaultValue("false") boolean withWayPoints,
            @QueryParam("gpx.trackname") @DefaultValue("GraphHopper Track") String trackName,
            @QueryParam("gpx.millis") String timeString,
            @QueryParam("traversal_keys") @DefaultValue("false") boolean enableTraversalKeys,
            @QueryParam(MAX_VISITED_NODES) @DefaultValue("3000") int maxVisitedNodes,
            @QueryParam("gps_accuracy") @DefaultValue("40") double gpsAccuracy) {

        boolean writeGPX = "gpx".equalsIgnoreCase(outType);
        if (!encodingManager.supports(vehicleStr)) {
            throw new WebApplicationException(WebHelper.errorResponse(new IllegalArgumentException("Vehicle not supported: " + vehicleStr), writeGPX));
        }

        PathWrapper matchGHRsp = new PathWrapper();
        GPXFile gpxFile = new GPXFile();
        try {
            gpxFile = parseGPX(httpReq);
        } catch (Exception ex) {
            matchGHRsp.addError(ex);
        }

        instructions = writeGPX || instructions;
        maxVisitedNodes = Math.min(maxVisitedNodes, 5000);
        gpsAccuracy = Math.min(Math.max(gpsAccuracy, 5), gpsMaxAccuracy);

        MatchResult matchRsp = null;
        StopWatch sw = new StopWatch().start();

        if (!matchGHRsp.hasErrors()) {
            try {
                AlgorithmOptions opts = AlgorithmOptions.start()
                        .traversalMode(graphHopper.getTraversalMode())
                        .maxVisitedNodes(maxVisitedNodes)
                        .hints(new HintsMap().put("vehicle", vehicleStr))
                        .build();
                MapMatching matching = new MapMatching(graphHopper, opts);
                matching.setMeasurementErrorSigma(gpsAccuracy);
                matchRsp = matching.doWork(gpxFile.getEntries());

                // fill GHResponse for identical structure
                Path path = matching.calcPath(matchRsp);
                Translation tr = trMap.getWithFallBack(Helper.getLocale(localeStr));
                DouglasPeucker peucker = new DouglasPeucker().setMaxDistance(minPathPrecision);
                PathMerger pathMerger = new PathMerger().
                        setEnableInstructions(instructions).
                        setPathDetailsBuilders(graphHopper.getPathDetailsBuilderFactory(), pathDetails).
                        setDouglasPeucker(peucker).
                        setSimplifyResponse(minPathPrecision > 0);
                pathMerger.doWork(matchGHRsp, Collections.singletonList(path), tr);

            } catch (Exception ex) {
                matchGHRsp.addError(ex);
            }
        }

        // TODO: Request logging and timing should perhaps be done somewhere outside
        float took = sw.stop().getSeconds();
        String infoStr = httpReq.getRemoteAddr() + " " + httpReq.getLocale() + " " + httpReq.getHeader("User-Agent");
        String logStr = httpReq.getQueryString() + ", " + infoStr + ", took:" + took + ", entries:" + gpxFile.getEntries().size() + ", " + matchGHRsp.getDebugInfo();

        if (matchGHRsp.hasErrors()) {
            if (matchGHRsp.getErrors().get(0) instanceof IllegalArgumentException) {
                logger.error(logStr + ", errors:" + matchGHRsp.getErrors());
            } else {
                logger.error(logStr + ", errors:" + matchGHRsp.getErrors(), matchGHRsp.getErrors().get(0));
            }
        } else {
            logger.info(logStr);
        }

        Object object;
        if (matchGHRsp.hasErrors()) {
            logger.error(logStr + ", errors:" + matchGHRsp.getErrors());
            throw new WebApplicationException(WebHelper.errorResponse(matchGHRsp.getErrors(), writeGPX));
        } else if ("extended_json".equals(outType)) {
            object = convertToTree(matchRsp, enableElevation, pointsEncoded);

        } else if (writeGPX) {
            GHResponse rsp = new GHResponse();
            rsp.add(matchGHRsp);
            return WebHelper.gpxSuccessResponseBuilder(rsp, timeString, trackName, enableElevation, withRoute, withTrack, withWayPoints).
                    header("X-GH-Took", "" + Math.round(took * 1000)).
                    build();

        } else {
            GHResponse rsp = new GHResponse();
            rsp.add(matchGHRsp);
            ObjectNode map = WebHelper.jsonObject(rsp, instructions, calcPoints, enableElevation, pointsEncoded, took);

            Map<String, Object> matchResult = new HashMap<>();
            matchResult.put("distance", matchRsp.getMatchLength());
            matchResult.put("time", matchRsp.getMatchMillis());
            matchResult.put("original_distance", matchRsp.getGpxEntriesLength());
            matchResult.put("original_time", matchRsp.getGpxEntriesMillis());
            map.putPOJO("map_matching", matchResult);

            if (enableTraversalKeys) {
                List<Integer> traversalKeylist = new ArrayList<>();
                for (EdgeMatch em : matchRsp.getEdgeMatches()) {
                    EdgeIteratorState edge = em.getEdgeState();
                    // encode edges as traversal keys which includes orientation, decode simply by multiplying with 0.5
                    traversalKeylist.add(GHUtility.createEdgeKey(edge.getBaseNode(), edge.getAdjNode(), edge.getEdge(), false));
                }
                map.putPOJO("traversal_keys", traversalKeylist);
            }

            object = map;
        }

        return Response.ok(object).
                header("X-GH-Took", "" + Math.round(took * 1000)).
                build();
    }

    private GPXFile parseGPX(HttpServletRequest httpReq) throws IOException {
        GPXFile file = new GPXFile();
        return file.doImport(httpReq.getInputStream(), 20);
    }

    static JsonNode convertToTree(MatchResult result, boolean elevation, boolean pointsEncoded) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ObjectNode diary = root.putObject("diary");
        ArrayNode entries = diary.putArray("entries");
        ObjectNode route = entries.addObject();
        ArrayNode links = route.putArray("links");
        for (int emIndex = 0; emIndex < result.getEdgeMatches().size(); emIndex++) {
            ObjectNode link = links.addObject();
            EdgeMatch edgeMatch = result.getEdgeMatches().get(emIndex);
            PointList pointList = edgeMatch.getEdgeState().fetchWayGeometry(emIndex == 0 ? 3 : 2);
            final ObjectNode geometry = link.putObject("geometry");
            if (pointList.size() < 2) {
                geometry.putPOJO("coordinates", pointsEncoded ? WebHelper.encodePolyline(pointList, elevation) : pointList.toLineString(elevation));
                geometry.put("type", "Point");
            } else {
                geometry.putPOJO("coordinates", pointsEncoded ? WebHelper.encodePolyline(pointList, elevation) : pointList.toLineString(elevation));
                geometry.put("type", "LineString");
            }
            link.put("id", edgeMatch.getEdgeState().getEdge());
            ArrayNode wpts = link.putArray("wpts");
            for (GPXExtension extension : edgeMatch.getGpxExtensions()) {
                ObjectNode wpt = wpts.addObject();
                wpt.put("x", extension.getQueryResult().getSnappedPoint().lon);
                wpt.put("y", extension.getQueryResult().getSnappedPoint().lat);
                wpt.put("timestamp", extension.getEntry().getTime());
            }
        }
        return root;
    }
}
