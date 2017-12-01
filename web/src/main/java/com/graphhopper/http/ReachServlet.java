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
package com.graphhopper.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GraphHopper;
import com.graphhopper.reach.algorithm.RasterHullBuilder;
import com.graphhopper.reach.algorithm.Reachability;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.GHPoint;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

/**
 * @author Peter Karich
 */
public class ReachServlet extends GHBaseServlet {

    @Inject
    private GraphHopper hopper;

    @Inject
    private RasterHullBuilder rasterHullBuilder;

    @Inject
    private ObjectMapper objectMapper;

    @Inject
    private CmdArgs cmdArgs;

    @Override
    public void doGet(HttpServletRequest httpReq, HttpServletResponse httpRes) throws ServletException, IOException {
        try {
            Map<String, Object> map = doReachCalc(httpReq, httpRes);
            writeJson(httpReq, httpRes, objectMapper.getNodeFactory().pojoNode(map));
        } catch (Exception ex) {
            Map<String, Object> json = new HashMap<>();
            json.put("message", getMessage(ex));
            writeJsonError(httpRes, SC_BAD_REQUEST, objectMapper.getNodeFactory().pojoNode(json));
        }
    }

    private String getMessage(Throwable t) {
        if (t.getMessage() == null)
            return t.getClass().getSimpleName();
        else
            return t.getMessage();
    }

    Map<String, Object> doReachCalc(HttpServletRequest hReq, HttpServletResponse hRes) throws IOException {
        String vehicle = getParam(hReq, "vehicle", "car");
        int buckets = (int) getLongParam(hReq, "buckets", 1L);
        boolean reverseFlow = getBooleanParam(hReq, "reverse_flow", false);
        String queryStr = getParam(hReq, "point", "");
        String resultStr = getParam(hReq, "result", "polygon");
        if (buckets > 20 || buckets < 1)
            throw new IllegalArgumentException("Number of buckets has to be in the range [1, 20]");

        GHPoint query = GHPoint.parse(queryStr);
        if (query == null) {
            throw new IllegalArgumentException("Specify a valid start coordinate as query parameter. It was:" + queryStr);
        }

        StopWatch sw = new StopWatch().start();

        EncodingManager encodingManager = hopper.getEncodingManager();
        if (!encodingManager.supports(vehicle)) {
            throw new IllegalArgumentException("vehicle not supported:" + vehicle);
        }

        FlagEncoder encoder = encodingManager.getEncoder(vehicle);
        EdgeFilter edgeFilter = new DefaultEdgeFilter(encoder);
        QueryResult qr = hopper.getLocationIndex().findClosest(query.lat, query.lon, edgeFilter);
        if (!qr.isValid()) {
            throw new IllegalArgumentException("Point not found:" + query);
        }

        Graph graph = hopper.getGraphHopperStorage();
        QueryGraph queryGraph = new QueryGraph(graph);
        queryGraph.lookup(Collections.singletonList(qr));

        HintsMap hintsMap = new HintsMap();
        initHints(hintsMap, hReq.getParameterMap());
        Weighting weighting = hopper.createWeighting(hintsMap, encoder, graph);
        Reachability reach = new Reachability(queryGraph, weighting, reverseFlow);
        double distanceInMeter = getDoubleParam(hReq, "distance_limit", -1);
        if (distanceInMeter > 0) {
            double maxMeter = 50 * 1000;
            if (distanceInMeter > maxMeter)
                throw new IllegalArgumentException("Specify a limit of less than " + maxMeter / 1000f + "km");
            if (buckets > (distanceInMeter / 500))
                throw new IllegalArgumentException("Specify buckets less than the number of explored kilometers");

            reach.setDistanceLimit(distanceInMeter);
        } else {
            long timeLimitInSeconds = getLongParam(hReq, "time_limit", 600L);
            long maxSeconds = 80 * 60;
            if (timeLimitInSeconds > maxSeconds)
                throw new IllegalArgumentException("Specify a limit of less than " + maxSeconds + " seconds");
            if (buckets > (timeLimitInSeconds / 60))
                throw new IllegalArgumentException("Specify buckets less than the number of explored minutes");

            reach.setTimeLimit(timeLimitInSeconds);
        }

        List<List<Double[]>> list = reach.searchGPS(qr.getClosestNode(), buckets);
        if (reach.getVisitedNodes() > hopper.getMaxVisitedNodes() / 5) {
            throw new IllegalArgumentException("Server side reset: too many junction nodes would have to explored (" + reach.getVisitedNodes() + "). Let us know if you need this increased.");
        }

        int counter = 0;
        for (List<Double[]> tmp : list) {
            if (tmp.size() < 2) {
                throw new IllegalArgumentException("Too few points found for bucket " + counter + ". "
                        + "Please try a different 'point', a smaller 'buckets' count or a larger 'time_limit'. "
                        + "And let us know if you think this is a bug!");
            }
            counter++;
        }

        Object calcRes;
        if ("pointlist".equalsIgnoreCase(resultStr)) {
            calcRes = list;

        } else if ("polygon".equalsIgnoreCase(resultStr)) {
            // bigger raster distance => bigger raster => less points => stranger buffer results, but faster
            double rasterDistance = cmdArgs.getDouble("isochrone.raster_distance", 0.75);
            // bigger buffer distance => less holes, lower means less points!
            double bufferDistance = cmdArgs.getDouble("isochrone.buffer_distance", 0.003);
            // precision of the 'circles'
            int quadrantSegments = cmdArgs.getInt("isochrone.quadrant_segments", 3);

            list = rasterHullBuilder.calcList(list, list.size() - 1, rasterDistance, bufferDistance, quadrantSegments);

            ArrayList polyList = new ArrayList();
            int index = 0;
            for (List<Double[]> polygon : list) {
                HashMap<String, Object> geoJsonMap = new HashMap<>();
                HashMap<String, Object> propMap = new HashMap<>();
                HashMap<String, Object> geometryMap = new HashMap<>();
                polyList.add(geoJsonMap);
                geoJsonMap.put("type", "Feature");
                geoJsonMap.put("properties", propMap);
                geoJsonMap.put("geometry", geometryMap);

                propMap.put("bucket", index);
                geometryMap.put("type", "Polygon");
                // we have no holes => embed in yet another list
                geometryMap.put("coordinates", Collections.singletonList(polygon));
                index++;
            }
            calcRes = polyList;
        } else {
            throw new IllegalArgumentException("type not supported:" + resultStr);
        }

        Map<String, Object> map = new HashMap<>();
        map.put("polygons", calcRes);
        map.put("copyrights", Arrays.asList("GraphHopper", "OpenStreetMap contributors"));
        hRes.setHeader("X-GH-Took", "" + sw.stop().getSeconds() * 1000);

        logger.info("took: " + sw.getSeconds() + ", visited nodes:" + reach.getVisitedNodes() + ", " + hReq.getQueryString());
        return map;
    }
}
