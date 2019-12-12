package com.graphhopper.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.jackson.PathWrapperDeserializer;
import okhttp3.OkHttpClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
                readTimeout(5, TimeUnit.SECONDS).
                addInterceptor(new GzipRequestInterceptor()). // gzip the request
                build());
    }

    public GHMatrixSyncRequester(String serviceUrl, OkHttpClient client) {
        super(serviceUrl, client);
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
                matrixResponse.addErrors(PathWrapperDeserializer.readErrors(objectMapper, responseJson));
                return matrixResponse;
            }

            matrixResponse.addErrors(PathWrapperDeserializer.readErrors(objectMapper, responseJson));
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
