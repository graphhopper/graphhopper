package com.graphhopper.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.jackson.ResponsePathDeserializer;
import okhttp3.OkHttpClient;

import java.io.IOException;
import java.util.Collection;
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
                readTimeout(5, TimeUnit.SECONDS).build(), true);
    }

    public GHMatrixSyncRequester(String serviceUrl, OkHttpClient client, boolean doRequestGzip) {
        super(serviceUrl, client, doRequestGzip);
    }

    @Override
    public MatrixResponse route(GHMRequest ghRequest) {
        Collection<String> outArraysList = createOutArrayList(ghRequest);
        JsonNode requestJson = createPostRequest(ghRequest, outArraysList);

        boolean withTimes = outArraysList.contains("times");
        boolean withDistances = outArraysList.contains("distances");
        boolean withWeights = outArraysList.contains("weights");
        final MatrixResponse matrixResponse = new MatrixResponse(
                ghRequest.getFromPoints().size(),
                ghRequest.getToPoints().size(), withTimes, withDistances, withWeights);

        try {
            String postUrl = buildURLNoHints("/", ghRequest);
            JsonNode responseJson = fromStringToJSON(postUrl, postJson(postUrl, requestJson));
            if (responseJson.has("message")) {
                matrixResponse.addErrors(ResponsePathDeserializer.readErrors(objectMapper, responseJson));
                return matrixResponse;
            }

            matrixResponse.addErrors(ResponsePathDeserializer.readErrors(objectMapper, responseJson));
            if (!matrixResponse.hasErrors())
                matrixResponse.addErrors(readUsableEntityError(outArraysList, responseJson));

            if (!matrixResponse.hasErrors())
                fillResponseFromJson(matrixResponse, responseJson, ghRequest.getFailFast());
            return matrixResponse;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
