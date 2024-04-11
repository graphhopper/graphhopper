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
import com.graphhopper.jackson.ResponsePathDeserializerHelper;
import com.graphhopper.util.Helper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

import static com.graphhopper.api.GraphHopperWeb.X_GH_CLIENT_VERSION;
import static com.graphhopper.api.Version.GH_VERSION_FROM_MAVEN;

/**
 * @author Peter Karich
 */
public class GHMatrixBatchRequester extends GHMatrixAbstractRequester {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private int maxIterations = 100;
    private long sleepAfterGET = 1000;

    public GHMatrixBatchRequester() {
        this(MATRIX_URL);
    }

    public GHMatrixBatchRequester(String serviceUrl) {
        this(serviceUrl, new OkHttpClient.Builder().
                connectTimeout(5, TimeUnit.SECONDS).
                readTimeout(5, TimeUnit.SECONDS).build(), true);
    }

    public GHMatrixBatchRequester(String serviceUrl, OkHttpClient client, boolean doRequestGzip) {
        super(serviceUrl, client, doRequestGzip);
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
        JsonNode requestJson = createPostRequest(ghRequest);

        boolean withTimes = ghRequest.getOutArrays().contains("times");
        boolean withDistances = ghRequest.getOutArrays().contains("distances");
        boolean withWeights = ghRequest.getOutArrays().contains("weights");
        final MatrixResponse matrixResponse = new MatrixResponse(
                ghRequest.getPoints() == null ? ghRequest.getFromPoints().size() : ghRequest.getPoints().size(),
                ghRequest.getPoints() == null ? ghRequest.getToPoints().size() : ghRequest.getPoints().size(),
                withTimes, withDistances, withWeights);
        try {
            String postUrl = buildURLNoHints("/calculate", ghRequest);
            JsonResult jsonResult = postJson(postUrl, requestJson);
            matrixResponse.setHeaders(jsonResult.headers());
            boolean debug = ghRequest.getHints().getBool("debug", false);
            if (debug) {
                logger.info("POST URL:" + postUrl + ", request:" + requestJson + ", response: " + jsonResult);
            }

            JsonNode responseJson = fromStringToJSON(postUrl, jsonResult.body());
            if (responseJson.has("message")) {
                matrixResponse.setStatusCode(jsonResult.statusCode());
                matrixResponse.addErrors(ResponsePathDeserializerHelper.readErrors(objectMapper, responseJson));
                return matrixResponse;
            }
            if (!responseJson.has("job_id")) {
                throw new IllegalStateException("Response should contain job_id but was "
                        + jsonResult + ", request:" + requestJson + ",url:" + postUrl);
            }

            final String id = responseJson.get("job_id").asText();
            int i = 0;
            for (; i < maxIterations; i++) {
                // SLEEP a bit and GET solution
                if (sleepAfterGET > 0) {
                    Thread.sleep(sleepAfterGET);
                }
                String getUrl = buildURLNoHints("/solution/" + id, ghRequest);

                JsonResult rsp;
                try {
                    rsp = getJson(getUrl);
                } catch (SocketTimeoutException ex) {
                    // if timeout exception try once again:
                    rsp = getJson(getUrl);
                }

                JsonNode getResponseJson = fromStringToJSON(getUrl, rsp.body());
                if (debug) {
                    logger.info(i + " GET URL:" + getUrl + ", response: " + rsp);
                }
                matrixResponse.addErrors(ResponsePathDeserializerHelper.readErrors(objectMapper, getResponseJson));
                matrixResponse.setStatusCode(rsp.statusCode());
                if (matrixResponse.hasErrors()) {
                    break;
                }
                String status = getResponseJson.get("status").asText();

                if ("processing".equals(status) || "waiting".equals(status)) {
                    continue;
                }

                if ("finished".equals(status)) {
                    JsonNode solution = getResponseJson.get("solution");
                    matrixResponse.addErrors(readUsableEntityError(ghRequest.getOutArrays(), solution));
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

    protected JsonResult getJson(String url) throws IOException {
        Request okRequest = new Request.Builder().url(url)
                .header(X_GH_CLIENT_VERSION, GH_VERSION_FROM_MAVEN)
                .build();
        ResponseBody body = null;
        try {
            Response rsp = getDownloader().newCall(okRequest).execute();
            body = rsp.body();
            return new JsonResult(body.string(), rsp.code(), rsp.headers().toMultimap());
        } finally {
            Helper.close(body);
        }
    }
}
