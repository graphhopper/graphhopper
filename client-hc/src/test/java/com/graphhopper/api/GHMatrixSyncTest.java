package com.graphhopper.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * @author Peter Karich
 */
public class GHMatrixSyncTest extends AbstractGHMatrixWebTester {

    @Override
    GraphHopperMatrixWeb createMatrixClient(String jsonStr, int errorCode) throws IOException {
        JsonNode json = objectMapper.readTree(jsonStr);

        // for test we grab the solution from the "batch json"
        if (json.has("solution")) {
            json = json.get("solution");
        }

        final String finalJsonStr = json.toString();
        return new GraphHopperMatrixWeb(new GHMatrixSyncRequester("") {

            @Override
            protected JsonResult postJson(String url, JsonNode data) {
                return new JsonResult(finalJsonStr, errorCode);
            }
        });
    }

    @Override
    GHMatrixAbstractRequester createRequester(String url) {
        return new GHMatrixSyncRequester(url);
    }
}
