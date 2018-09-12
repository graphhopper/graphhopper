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
import com.graphhopper.matching.gpx.Gpx;
import com.graphhopper.http.WebHelper;
import com.graphhopper.matching.EdgeMatch;
import com.graphhopper.matching.GPXExtension;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.routing.AlgorithmOptions;
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
    private final TranslationMap trMap;

    @Inject
    public MapMatchingResource(GraphHopper graphHopper, TranslationMap trMap) {
        this.graphHopper = graphHopper;
        this.trMap = trMap;
    }

    @POST
    @Consumes({MediaType.APPLICATION_XML, "application/gpx+xml"})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, "application/gpx+xml"})
    public Response match(
            Gpx gpx,
            @Context HttpServletRequest request,
            @QueryParam(WAY_POINT_MAX_DISTANCE) @DefaultValue("1") double minPathPrecision,
            @QueryParam("type") @DefaultValue("json") String outType,
            @QueryParam(INSTRUCTIONS) @DefaultValue("true") boolean instructions,
            @QueryParam(CALC_POINTS) @DefaultValue("true") boolean calcPoints,
            @QueryParam("elevation") @DefaultValue("false") boolean enableElevation,
            @QueryParam("points_encoded") @DefaultValue("true") boolean pointsEncoded,
            @QueryParam("vehicle") @DefaultValue("car") String vehicleStr,
            @QueryParam("locale") @DefaultValue("en") String localeStr,
            @QueryParam(Parameters.DETAILS.PATH_DETAILS) List<String> pathDetails,
            @QueryParam("gpx.route") @DefaultValue("true") boolean withRoute,
            @QueryParam("gpx.track") @DefaultValue("true") boolean withTrack,
            @QueryParam("traversal_keys") @DefaultValue("false") boolean enableTraversalKeys,
            @QueryParam(MAX_VISITED_NODES) @DefaultValue("3000") int maxVisitedNodes,
            @QueryParam("gps_accuracy") @DefaultValue("40") double gpsAccuracy) {

        boolean writeGPX = "gpx".equalsIgnoreCase(outType);
        if (gpx.trk == null) {
            throw new IllegalArgumentException("No tracks found in GPX document. Are you using waypoints or routes instead?");
        }
        if (gpx.trk.size() > 1) {
            throw new IllegalArgumentException("GPX documents with multiple tracks not supported yet.");
        }

        instructions = writeGPX || instructions;

        StopWatch sw = new StopWatch().start();

        AlgorithmOptions opts = AlgorithmOptions.start()
                .traversalMode(graphHopper.getTraversalMode())
                .maxVisitedNodes(maxVisitedNodes)
                .hints(new HintsMap().put("vehicle", vehicleStr))
                .build();
        MapMatching matching = new MapMatching(graphHopper, opts);
        matching.setMeasurementErrorSigma(gpsAccuracy);

        List<GPXEntry> measurements = gpx.trk.get(0).getEntries();
        MatchResult matchResult = matching.doWork(measurements);

        // TODO: Request logging and timing should perhaps be done somewhere outside
        float took = sw.stop().getSeconds();
        String infoStr = request.getRemoteAddr() + " " + request.getLocale() + " " + request.getHeader("User-Agent");
        String logStr = request.getQueryString() + ", " + infoStr + ", took:" + took + ", entries:" + measurements.size();
        logger.info(logStr);

        if ("extended_json".equals(outType)) {
            return Response.ok(convertToTree(matchResult, enableElevation, pointsEncoded)).
                    header("X-GH-Took", "" + Math.round(took * 1000)).
                    build();
        } else {
            Translation tr = trMap.getWithFallBack(Helper.getLocale(localeStr));
            DouglasPeucker peucker = new DouglasPeucker().setMaxDistance(minPathPrecision);
            PathMerger pathMerger = new PathMerger().
                    setEnableInstructions(instructions).
                    setPathDetailsBuilders(graphHopper.getPathDetailsBuilderFactory(), pathDetails).
                    setDouglasPeucker(peucker).
                    setSimplifyResponse(minPathPrecision > 0);
            PathWrapper pathWrapper = new PathWrapper();
            pathMerger.doWork(pathWrapper, Collections.singletonList(matchResult.getMergedPath()), tr);

            // GraphHopper thinks an empty path is an invalid path, and further that an invalid path is still a path but
            // marked with a non-empty list of Exception objects. I disagree, so I clear it.
            pathWrapper.getErrors().clear();
            GHResponse rsp = new GHResponse();
            rsp.add(pathWrapper);

            if (writeGPX) {
                long time = System.currentTimeMillis();
                if (!measurements.isEmpty()) {
                    time = measurements.get(0).getTime();
                }
                return Response.ok(rsp.getBest().getInstructions().createGPX(gpx.trk.get(0).name != null ? gpx.trk.get(0).name : "", time, enableElevation, withRoute, withTrack, false, Constants.VERSION), "application/gpx+xml").
                        header("X-GH-Took", "" + Math.round(took * 1000)).
                        build();
            } else {
                ObjectNode map = WebHelper.jsonObject(rsp, instructions, calcPoints, enableElevation, pointsEncoded, took);

                Map<String, Object> matchStatistics = new HashMap<>();
                matchStatistics.put("distance", matchResult.getMatchLength());
                matchStatistics.put("time", matchResult.getMatchMillis());
                matchStatistics.put("original_distance", matchResult.getGpxEntriesLength());
                matchStatistics.put("original_time", matchResult.getGpxEntriesMillis());
                map.putPOJO("map_matching", matchStatistics);

                if (enableTraversalKeys) {
                    List<Integer> traversalKeylist = new ArrayList<>();
                    for (EdgeMatch em : matchResult.getEdgeMatches()) {
                        EdgeIteratorState edge = em.getEdgeState();
                        // encode edges as traversal keys which includes orientation, decode simply by multiplying with 0.5
                        traversalKeylist.add(GHUtility.createEdgeKey(edge.getBaseNode(), edge.getAdjNode(), edge.getEdge(), false));
                    }
                    map.putPOJO("traversal_keys", traversalKeylist);
                }
                return Response.ok(map).
                        header("X-GH-Took", "" + Math.round(took * 1000)).
                        build();
            }
        }
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
