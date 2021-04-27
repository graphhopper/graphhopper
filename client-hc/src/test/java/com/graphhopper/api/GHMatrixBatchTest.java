package com.graphhopper.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * @author Peter Karich
 */
public class GHMatrixBatchTest extends AbstractGHMatrixWebTester {

    @Override
    GraphHopperMatrixWeb createMatrixClient(final String jsonTmp) {
        return new GraphHopperMatrixWeb(new GHMatrixBatchRequester("") {

            private final String json = jsonTmp;

            @Override
            protected String postJson(String url, JsonNode data) throws IOException {
                return "{\"job_id\": \"1\"}";
            }

            @Override
            protected String getJson(String url) throws IOException {
                return json;
            }
        }.setSleepAfterGET(0));
    }

    @Override
    GHMatrixAbstractRequester createRequester(String url) {
        return new GHMatrixBatchRequester(url);
    }
}
