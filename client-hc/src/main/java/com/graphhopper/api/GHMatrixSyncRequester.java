package com.graphhopper.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.jackson.ResponsePathDeserializerHelper;
import okhttp3.OkHttpClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * @author Peter Karich
 */
public class GHMatrixSyncRequester extends GHMatrixAbstractRequester {

    public GHMatrixSyncRequester() {
        this(MATRIX_URL);
    }

    public GHMatrixSyncRequester(String serviceUrl) {
        this(serviceUrl, new OkHttpClient.Builder().
                connectTimeout(5, TimeUnit.SECONDS).
                readTimeout(15, TimeUnit.SECONDS).build(), true);
    }

    public GHMatrixSyncRequester(String serviceUrl, OkHttpClient client, boolean doRequestGzip) {
        super(serviceUrl, client, doRequestGzip);
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
            String postUrl = buildURLNoHints("", ghRequest);
            JsonResult jsonResult = postJson(postUrl, requestJson);
            JsonNode responseJson = fromStringToJSON(postUrl, jsonResult.body());
            matrixResponse.setHeaders(jsonResult.headers());
            matrixResponse.setStatusCode(jsonResult.statusCode());
            if (responseJson.has("message")) {
                matrixResponse.addErrors(ResponsePathDeserializerHelper.readErrors(objectMapper, responseJson));
                return matrixResponse;
            }

            matrixResponse.addErrors(ResponsePathDeserializerHelper.readErrors(objectMapper, responseJson));
            if (!matrixResponse.hasErrors())
                matrixResponse.addErrors(readUsableEntityError(ghRequest.getOutArrays(), responseJson));

            if (!matrixResponse.hasErrors())
                fillResponseFromJson(matrixResponse, responseJson, ghRequest.getFailFast());
            return matrixResponse;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
