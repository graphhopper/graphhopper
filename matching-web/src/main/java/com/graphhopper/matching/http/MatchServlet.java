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

import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.http.GraphHopperServlet;
import com.graphhopper.http.RouteSerializer;
import com.graphhopper.matching.*;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.inject.Inject;
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
    private LocationIndexMatch locationIndexMatch;
    @Inject
    private RouteSerializer routeSerializer;
    @Inject
    private TranslationMap trMap;

    @Override
    public void doPost(HttpServletRequest httpReq, HttpServletResponse httpRes)
            throws ServletException, IOException {

        String infoStr = httpReq.getRemoteAddr() + " " + httpReq.getLocale();
        String type = httpReq.getContentType();
        GPXFile gpxFile;
        if (type.contains("application/xml") || type.contains("application/gpx+xml")) {
            try {
                gpxFile = parseGPX(httpReq);
            } catch (Exception ex) {
                logger.warn("Cannot parse XML for " + httpReq.getQueryString() + ", " + infoStr);
                httpRes.setStatus(SC_BAD_REQUEST);
                httpRes.getWriter().append(errorsToXML(Collections.<Throwable>singletonList(ex)));
                return;
            }
        } else {
            throw new IllegalArgumentException("content type not supported " + type);
        }
        final String format = getParam(httpReq, "type", "json");
        boolean writeGPX = GPX_FORMAT.equals(format);
        boolean pointsEncoded = getBooleanParam(httpReq, "points_encoded", true);
        boolean enableInstructions = writeGPX || getBooleanParam(httpReq, "instructions", true);
        boolean enableElevation = getBooleanParam(httpReq, "elevation", false);

        // TODO export OSM IDs instead, use https://github.com/karussell/graphhopper-osm-id-mapping
        boolean enableTraversalKeys = getBooleanParam(httpReq, "traversal_keys", false);

        String vehicle = getParam(httpReq, "vehicle", "car");
        int maxVisitedNodes = Math.min(getIntParam(httpReq, "max_visited_nodes", 800), 5000);
        int gpsAccuracy = Math.max(getIntParam(httpReq, "gps_accuracy", 50), 10);
        Locale locale = Helper.getLocale(getParam(httpReq, "locale", "en"));
        PathWrapper matchGHRsp = new PathWrapper();
        MatchResult matchRsp = null;
        StopWatch sw = new StopWatch().start();

        try {
            FlagEncoder encoder = hopper.getEncodingManager().getEncoder(vehicle);
            MapMatching matching = new MapMatching(hopper.getGraphHopperStorage(), locationIndexMatch, encoder);
            matching.setMaxVisitedNodes(maxVisitedNodes);
            matching.setMeasurementErrorSigma(gpsAccuracy);
            matchRsp = matching.doWork(gpxFile.getEntries());

            // fill GHResponse for identical structure            
            Path path = matching.calcPath(matchRsp);
            Translation tr = trMap.getWithFallBack(locale);
            new PathMerger().doWork(matchGHRsp, Collections.singletonList(path), tr);

        } catch (Exception ex) {
            matchGHRsp.addError(ex);
        }

        float took = sw.stop().getSeconds();
        logger.info(httpReq.getQueryString() + ", " + infoStr + ", took:" + took + ", entries:" + gpxFile.getEntries().size() + ", " + matchGHRsp.getDebugInfo());

        httpRes.setHeader("X-GH-Took", "" + Math.round(took * 1000));
        if (EXTENDED_JSON_FORMAT.equals(format)) {
            if (matchGHRsp.hasErrors()) {
                httpRes.setStatus(SC_BAD_REQUEST);
                httpRes.getWriter().append(new JSONArray(matchGHRsp.getErrors()).toString());
            } else {
                httpRes.getWriter().write(new MatchResultToJson(matchRsp).exportTo().toString());
            }

        } else if (GPX_FORMAT.equals(format)) {
            String xml = createGPXString(httpReq, httpRes, matchGHRsp);
            if (matchGHRsp.hasErrors()) {
                httpRes.setStatus(SC_BAD_REQUEST);
                httpRes.getWriter().append(xml);
            } else {
                writeResponse(httpRes, xml);
            }
        } else {
            GHResponse rsp = new GHResponse();
            rsp.add(matchGHRsp);
            Map<String, Object> map = routeSerializer.toJSON(rsp, true, pointsEncoded,
                    enableElevation, enableInstructions);

            if (rsp.hasErrors()) {
                writeJsonError(httpRes, SC_BAD_REQUEST, new JSONObject(map));
            } else {

                if (enableTraversalKeys) {
                    if (matchRsp == null) {
                        throw new IllegalStateException("match response has to be none-null if no error happened");
                    }

                    // encode edges as traversal keys which includes orientation
                    // decode simply by multiplying with 0.5
                    List<Integer> traversalKeylist = new ArrayList<Integer>();
                    for (EdgeMatch em : matchRsp.getEdgeMatches()) {
                        EdgeIteratorState edge = em.getEdgeState();
                        traversalKeylist.add(GHUtility.createEdgeKey(edge.getBaseNode(), edge.getAdjNode(), edge.getEdge(), false));
                    }
                    map.put("traversal_keys", traversalKeylist);
                }

                writeJson(httpReq, httpRes, new JSONObject(map));
            }
        }
    }

    private GPXFile parseJSON(HttpServletRequest httpReq) throws IOException {
        JSONObject json = new JSONObject(Helper.isToString(httpReq.getInputStream()));
        // TODO think about format first:
        // type: bike
        // locale
        // encoded points or not
        // points_encoded
        // points: lat,lon[,ele,millis] 
        throw new IllegalStateException("json input not yet supported");
    }

    private GPXFile parseGPX(HttpServletRequest httpReq) throws IOException {
        GPXFile file = new GPXFile();
        return file.doImport(httpReq.getInputStream());
    }
}
