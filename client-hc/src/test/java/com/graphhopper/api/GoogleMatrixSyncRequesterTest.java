package com.graphhopper.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStreamReader;

import static com.graphhopper.api.AbstractGHMatrixWebTester.createRequest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Peter Karich
 */
public class GoogleMatrixSyncRequesterTest {
    final ObjectMapper objectMapper = new ObjectMapper();

    GraphHopperMatrixWeb createMatrixClient(String jsonStr) throws IOException {
        JsonNode json = objectMapper.readTree(jsonStr);
        final String finalJsonStr = json.toString();
        return new GraphHopperMatrixWeb(new GoogleMatrixSyncRequester("") {

            @Override
            protected String postJson(String url, JsonNode data) throws IOException {
                return "{\"job_id\": \"1\"}";
            }

            @Override
            protected String getJson(String url) throws IOException {
                return finalJsonStr;
            }
        });
    }

    @Test
    public void testMatrix() throws IOException {
        GHMRequest req = new GHMRequest();
        req.addFromPoint(new GHPoint(51.534377, -0.087891));
        req.addFromPoint(new GHPoint(51.467697, -0.090637));

        req.addToPoint(new GHPoint(51.521241, -0.171833));
        req.addToPoint(new GHPoint(51.467697, -0.090637));
        req.addToPoint(new GHPoint(51.534377, -0.087891));

        GraphHopperMatrixWeb matrix = createMatrixClient(AbstractGHMatrixWebTester.readFile(
                new InputStreamReader(getClass().getResourceAsStream("google-matrix1.json"))));
        MatrixResponse rsp = matrix.route(req);

        assertEquals(712692, rsp.getDistance(0, 1), .1);
        assertEquals(25995, rsp.getTime(0, 1) / 1000);

        assertEquals(806813, rsp.getDistance(1, 2), .1);
        assertEquals(28737, rsp.getTime(1, 2) / 1000);
    }

    @Test
    public void testMatrixWithError() throws IOException {
        GHMRequest req = createRequest();

        GraphHopperMatrixWeb matrix = createMatrixClient(AbstractGHMatrixWebTester.readFile(
                new InputStreamReader(getClass().getResourceAsStream("google-error1.json"))));
        MatrixResponse rsp = matrix.route(req);
        assertTrue(rsp.hasErrors());
    }
}
