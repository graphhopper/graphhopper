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
package com.graphhopper.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.jackson.PathWrapperDeserializer;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.graphhopper.api.GraphHopperMatrixWeb.MT_JSON;

/**
 * @author Peter Karich
 */
public class GHMatrixBatchRequester extends GHMatrixAbstractRequester {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private int maxIterations = 100;
    private long sleepAfterGET = 1000;
    int unzippedLength = 1000;

    public GHMatrixBatchRequester() {
        this(MATRIX_URL);
    }

    public GHMatrixBatchRequester(String serviceUrl) {
        this(serviceUrl, new OkHttpClient.Builder().
                connectTimeout(5, TimeUnit.SECONDS).
                readTimeout(5, TimeUnit.SECONDS).
                addInterceptor(new GzipRequestInterceptor()). // gzip the request
                build());
    }

    public GHMatrixBatchRequester(String serviceUrl, OkHttpClient client) {
        super(serviceUrl, client);
    }

    /**
     * Internal parameter. Increase only if you have very large matrices.
     */
    public GHMatrixBatchRequester setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }

    /**
     * Internal parameter. Increase only if you have very large matrices.
     */
    public GHMatrixBatchRequester setSleepAfterGET(long sleepAfterGETMillis) {
        this.sleepAfterGET = sleepAfterGETMillis;
        return this;
    }

    @Override
    public MatrixResponse route(GHMRequest ghRequest) {
        ObjectNode requestJson = objectMapper.createObjectNode();

        List<String> outArraysList = new ArrayList<>(ghRequest.getOutArrays());
        if (outArraysList.isEmpty()) {
            outArraysList.add("weights");
        }

        boolean hasElevation = false;
        if (ghRequest.identicalLists) {
            createPointArray(requestJson, "points", ghRequest.getFromPoints());
            createStringArray(requestJson, "point_hints", ghRequest.getFromPointHints());
            createStringArray(requestJson, "curbsides", ghRequest.getFromCurbsides());
        } else {
            createPointArray(requestJson, "from_points", ghRequest.getFromPoints());
            createStringArray(requestJson, "from_point_hints", ghRequest.getFromPointHints());

            createPointArray(requestJson, "to_points", ghRequest.getToPoints());
            createStringArray(requestJson, "to_point_hints", ghRequest.getToPointHints());

            createStringArray(requestJson, "from_curbsides", ghRequest.getFromCurbsides());
            createStringArray(requestJson, "to_curbsides", ghRequest.getToCurbsides());
        }

        createStringArray(requestJson, "snap_preventions", ghRequest.getSnapPreventions());
        createStringArray(requestJson, "out_arrays", outArraysList);
        requestJson.put("vehicle", ghRequest.getVehicle());
        requestJson.put("elevation", hasElevation);
        requestJson.put("fail_fast", ghRequest.getFailFast());

        Map<String, String> hintsMap = ghRequest.getHints().toMap();
        for (String hintKey : hintsMap.keySet()) {
            if (ignoreSet.contains(hintKey))
                continue;

            String hint = hintsMap.get(hintKey);
            requestJson.put(hintKey, hint);
        }

        boolean withTimes = outArraysList.contains("times");
        boolean withDistances = outArraysList.contains("distances");
        boolean withWeights = outArraysList.contains("weights");
        final MatrixResponse matrixResponse = new MatrixResponse(
                ghRequest.getFromPoints().size(),
                ghRequest.getToPoints().size(), withTimes, withDistances, withWeights);

        String postUrl = buildURLNoHints("/calculate", ghRequest);

        try {
            String postResponseStr = postJson(postUrl, requestJson);
            boolean debug = ghRequest.getHints().getBool("debug", false);
            if (debug) {
                logger.info("POST URL:" + postUrl + ", request:" + requestJson + ", response: " + postResponseStr);
            }

            JsonNode responseJson = toJSON(postUrl, postResponseStr);
            if (responseJson.has("message")) {
                matrixResponse.addErrors(PathWrapperDeserializer.readErrors(objectMapper, responseJson));
                return matrixResponse;
            }
            if (!responseJson.has("job_id")) {
                throw new IllegalStateException("Response should contain job_id but was "
                        + postResponseStr + ", json:" + requestJson + ",url:" + postUrl);
            }

            final String id = responseJson.get("job_id").asText();
            int i = 0;
            for (; i < maxIterations; i++) {
                // SLEEP a bit and GET solution
                if (sleepAfterGET > 0) {
                    Thread.sleep(sleepAfterGET);
                }
                String getUrl = buildURLNoHints("/solution/" + id, ghRequest);

                String getResponseStr;
                try {
                    getResponseStr = getJson(getUrl);
                } catch (SocketTimeoutException ex) {
                    // if timeout exception try once again:
                    getResponseStr = getJson(getUrl);
                }

                JsonNode getResponseJson = toJSON(getUrl, getResponseStr);
                if (debug) {
                    logger.info(i + " GET URL:" + getUrl + ", response: " + getResponseStr);
                }
                matrixResponse.addErrors(PathWrapperDeserializer.readErrors(objectMapper, getResponseJson));
                if (matrixResponse.hasErrors()) {
                    break;
                }
                String status = getResponseJson.get("status").asText();

                if ("processing".equals(status) || "waiting".equals(status)) {
                    continue;
                }

                if ("finished".equals(status)) {
                    JsonNode solution = getResponseJson.get("solution");
                    matrixResponse.addErrors(readUsableEntityError(outArraysList, solution));
                    if (!matrixResponse.hasErrors())
                        fillResponseFromJson(matrixResponse, solution, ghRequest.getFailFast());

                    break;
                }

                matrixResponse.addError(new RuntimeException("Status not supported: " + status + " - illegal JSON format?"));
                break;
            }

            if (i >= maxIterations) {
                throw new IllegalStateException("Maximum number of iterations reached " + maxIterations + ", increasing should only be necessary for big matrices. For smaller ones this is a bug, please contact us");
            }

        } catch (InterruptedException | IOException ex) {
            throw new RuntimeException(ex);
        }

        return matrixResponse;
    }

    protected String postJson(String url, JsonNode data) throws IOException {
        String stringData = data.toString();
        Request.Builder builder = new Request.Builder().url(url).post(RequestBody.create(MT_JSON, stringData));
        // force avoiding our GzipRequestInterceptor for smaller requests ~30 locations
        if (stringData.length() < unzippedLength)
            builder.header("Content-Encoding", "identity");
        Request okRequest = builder.build();
        ResponseBody body = null;
        try {
            body = getDownloader().newCall(okRequest).execute().body();
            return body.string();
        } finally {
            Helper.close(body);
        }
    }

    private void createStringArray(ObjectNode requestJson, String name, List<String> stringList) {
        if (stringList.isEmpty())
            return;
        ArrayNode outList = objectMapper.createArrayNode();
        for (String str : stringList) {
            outList.add(str);
        }
        requestJson.putArray(name).addAll(outList);
    }

    private void createPointArray(ObjectNode requestJson, String name, List<GHPoint> pList) {
        if (pList.isEmpty())
            return;
        ArrayNode outList = objectMapper.createArrayNode();
        for (GHPoint p : pList) {
            ArrayNode entry = objectMapper.createArrayNode();
            entry.add(p.lon);
            entry.add(p.lat);
            outList.add(entry);
        }
        requestJson.putArray(name).addAll(outList);
    }
}
