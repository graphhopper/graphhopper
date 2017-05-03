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
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.http.GraphHopperServlet;
import com.graphhopper.http.RouteSerializer;
import com.graphhopper.matching.EdgeMatch;
import com.graphhopper.matching.GPXFile;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.*;
import com.graphhopper.util.Parameters.Routing;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

/**
 *
 * @author Peter Karich
 */
public class MatchServlet extends GraphHopperServlet {

    private static final String GPX_FORMAT = "gpx";
    private static final String EXTENDED_JSON_FORMAT = "extended_json";

    @Inject
    private GraphHopper hopper;
    @Inject
    private RouteSerializer routeSerializer;
    @Inject
    private TranslationMap trMap;
    @Inject
    @Named("gps.max_accuracy")
    private double gpsMaxAccuracy;

    @Override
    public void doPost(HttpServletRequest httpReq, HttpServletResponse httpRes)
            throws ServletException, IOException {

        String infoStr = httpReq.getRemoteAddr() + " " + httpReq.getLocale();
        String inType = "gpx";
        String contentType = httpReq.getContentType();
        if (contentType.contains("application/xml") || contentType.contains("application/gpx+xml")) {
            inType = "gpx";
        } else if (contentType.contains("application/json")) {
            inType = "json";
        }

        PathWrapper matchGHRsp = new PathWrapper();
        final String outType = getParam(httpReq, "type", "json");
        GPXFile gpxFile = new GPXFile();
        if (inType.equals("gpx")) {
            try {
                gpxFile = parseGPX(httpReq);
            } catch (Exception ex) {
                matchGHRsp.addError(ex);
            }
        } else {
            matchGHRsp.addError(new IllegalArgumentException("Input type not supported " + inType + ", Content-Type:" + contentType));
        }

        boolean writeGPX = GPX_FORMAT.equals(outType);
        boolean pointsEncoded = getBooleanParam(httpReq, "points_encoded", true);
        boolean enableInstructions = writeGPX || getBooleanParam(httpReq, "instructions", true);
        boolean enableElevation = getBooleanParam(httpReq, "elevation", false);

        // TODO export OSM IDs instead, use https://github.com/karussell/graphhopper-osm-id-mapping
        boolean enableTraversalKeys = getBooleanParam(httpReq, "traversal_keys", false);

        String vehicle = getParam(httpReq, "vehicle", "car");
        int maxVisitedNodes = Math.min(getIntParam(httpReq, Routing.MAX_VISITED_NODES, 3000), 5000);
        double wayPointMaxDistance = getDoubleParam(httpReq, Routing.WAY_POINT_MAX_DISTANCE, 1d);
        double defaultAccuracy = 40;
        double gpsAccuracy = Math.min(Math.max(getDoubleParam(httpReq, "gps_accuracy", defaultAccuracy), 5), gpsMaxAccuracy);
        Locale locale = Helper.getLocale(getParam(httpReq, "locale", "en"));
        MatchResult matchRsp = null;
        StopWatch sw = new StopWatch().start();

        if (!matchGHRsp.hasErrors()) {
            try {
                AlgorithmOptions opts = AlgorithmOptions.start()
                        .traversalMode(hopper.getTraversalMode())
                        .maxVisitedNodes(maxVisitedNodes)
                        .hints(new HintsMap().put("vehicle", vehicle))
                        .build();
                MapMatching matching = new MapMatching(hopper, opts);
                matching.setMeasurementErrorSigma(gpsAccuracy);
                matchRsp = matching.doWork(gpxFile.getEntries());

                // fill GHResponse for identical structure            
                Path path = matching.calcPath(matchRsp);
                Translation tr = trMap.getWithFallBack(locale);
                DouglasPeucker peucker = new DouglasPeucker().setMaxDistance(wayPointMaxDistance);
                PathMerger pathMerger = new PathMerger().
                        setDouglasPeucker(peucker).
                        setSimplifyResponse(wayPointMaxDistance > 0);
                pathMerger.doWork(matchGHRsp, Collections.singletonList(path), tr);

            } catch (Exception ex) {
                matchGHRsp.addError(ex);
            }
        }

        float took = sw.stop().getSeconds();

        httpRes.setHeader("X-GH-Took", "" + Math.round(took * 1000));
        if (EXTENDED_JSON_FORMAT.equals(outType)) {
            if (matchGHRsp.hasErrors()) {
                httpRes.setStatus(SC_BAD_REQUEST);
                objectMapper.writeValue(httpRes.getWriter(), matchGHRsp.getErrors());
            } else {
                httpRes.getWriter().write(objectMapper.writeValueAsString(MatchResultToJson.convertToTree(matchRsp, objectMapper)));
            }

        } else if (GPX_FORMAT.equals(outType)) {
            if (matchGHRsp.hasErrors()) {
                httpRes.setStatus(SC_BAD_REQUEST);
                httpRes.getWriter().append(errorsToXML(matchGHRsp.getErrors()));
            } else {
                String xml = createGPXString(httpReq, httpRes, matchGHRsp);
                writeResponse(httpRes, xml);
            }
        } else {
            GHResponse rsp = new GHResponse();
            rsp.add(matchGHRsp);
            Map<String, Object> map = routeSerializer.toJSON(rsp, true, pointsEncoded,
                    enableElevation, enableInstructions);

            if (rsp.hasErrors()) {
                writeJsonError(httpRes, SC_BAD_REQUEST, objectMapper.convertValue(map, JsonNode.class));
            } else {
                if (matchRsp == null) {
                    throw new IllegalStateException("match response has to be none-null if no error happened");
                }

                Map<String, Object> matchResult = new HashMap<String, Object>();
                matchResult.put("distance", matchRsp.getMatchLength());
                matchResult.put("time", matchRsp.getMatchMillis());
                matchResult.put("original_distance", matchRsp.getGpxEntriesLength());
                matchResult.put("original_time", matchRsp.getGpxEntriesMillis());
                map.put("map_matching", matchResult);

                if (enableTraversalKeys) {
                    // encode edges as traversal keys which includes orientation
                    // decode simply by multiplying with 0.5
                    List<Integer> traversalKeylist = new ArrayList<Integer>();
                    for (EdgeMatch em : matchRsp.getEdgeMatches()) {
                        EdgeIteratorState edge = em.getEdgeState();
                        traversalKeylist.add(GHUtility.createEdgeKey(edge.getBaseNode(), edge.getAdjNode(), edge.getEdge(), false));
                    }
                    map.put("traversal_keys", traversalKeylist);
                }

                writeJson(httpReq, httpRes, objectMapper.convertValue(map, JsonNode.class));
            }
        }

        String str = httpReq.getQueryString() + ", " + infoStr + ", took:" + took + ", entries:" + gpxFile.getEntries().size() + ", " + matchGHRsp.getDebugInfo();
        if (matchGHRsp.hasErrors()) {
            if (matchGHRsp.getErrors().get(0) instanceof IllegalArgumentException) {
                logger.error(str + ", errors:" + matchGHRsp.getErrors());
            } else {
                logger.error(str + ", errors:" + matchGHRsp.getErrors(), matchGHRsp.getErrors().get(0));
            }
        } else {
            logger.info(str);
        }
    }

    private GPXFile parseGPX(HttpServletRequest httpReq) throws IOException {
        GPXFile file = new GPXFile();
        return file.doImport(httpReq.getInputStream(), 20);
    }
}
