package com.graphhopper.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Peter Karich
 */
public abstract class AbstractGHMatrixWebTester {

    protected final ObjectMapper objectMapper = new ObjectMapper();

    abstract GraphHopperMatrixWeb createMatrixClient(String json) throws IOException;

    abstract GHMatrixAbstractRequester createRequester(String url);

    public static GHMRequest createRequest() {
        GHMRequest req = new GHMRequest();
        req.addPoint(new GHPoint(51.534377, -0.087891));
        req.addPoint(new GHPoint(51.467697, -0.090637));
        req.addPoint(new GHPoint(51.521241, -0.171833));
        req.addPoint(new GHPoint(51.473685, -0.211487));
        return req;
    }

    @Test
    public void testReadingMatrixWithError() throws IOException {
        String ghMatrix = readFile(new InputStreamReader(getClass().getResourceAsStream("matrix_error.json")));
        GraphHopperMatrixWeb matrixWeb = createMatrixClient(ghMatrix);

        GHMRequest req = createRequest();
        MatrixResponse rsp = matrixWeb.route(req);

        assertTrue(rsp.hasErrors());
        assertEquals(2, rsp.getErrors().size());
    }

    @Test
    public void testReadingWeights() throws IOException {
        String ghMatrix = readFile(new InputStreamReader(getClass().getResourceAsStream("matrix-weights-only.json")));
        GraphHopperMatrixWeb matrixWeb = createMatrixClient(ghMatrix);

        GHMRequest req = createRequest();
        MatrixResponse rsp = matrixWeb.route(req);
        assertFalse(rsp.hasErrors());

        assertEquals(885.9, rsp.getWeight(0, 1), .1);

        try {
            assertEquals(0., rsp.getDistance(0, 1), .1);
            fail("there should have been an exception");
        } catch (Exception ex) {
        }
    }

    @Test
    public void testReadingMatrixConnectionsNotFound_noFailFast() throws IOException {
        String ghMatrix = readFile(new InputStreamReader(getClass().getResourceAsStream("matrix-connection-not-found-fail-fast.json")));
        GraphHopperMatrixWeb matrixWeb = createMatrixClient(ghMatrix);

        GHMRequest req = new GHMRequest();
        req.addPoint(new GHPoint(0, 1));
        req.addPoint(new GHPoint(2, 3));
        req.addOutArray("weights");
        req.addOutArray("distances");
        req.addOutArray("times");
        req.setFailFast(false);

        MatrixResponse rsp = matrixWeb.route(req);
        assertFalse(rsp.hasErrors());
        assertTrue(rsp.hasProblems());

        assertEquals(Double.MAX_VALUE, rsp.getWeight(0, 1), 1.e-3);
        assertEquals(0, rsp.getWeight(0, 0), 1.e-3);

        assertEquals(Double.MAX_VALUE, rsp.getDistance(0, 1), 1.e-3);
        assertEquals(1, rsp.getDistance(0, 0), 1.e-3);

        assertEquals(Long.MAX_VALUE, rsp.getTime(0, 1));
        assertEquals(2 * 1000, rsp.getTime(0, 0));

        assertEquals("[[0, 1], [1, 0]]", rsp.getDisconnectedPoints().toString());
    }

    @Test
    public void testReadingMatrixPointsNotFound_noFailFast() throws IOException {
        String ghMatrix = readFile(new InputStreamReader(getClass().getResourceAsStream("matrix-point-not-found-fail-fast.json")));
        GraphHopperMatrixWeb matrixWeb = createMatrixClient(ghMatrix);

        GHMRequest req = new GHMRequest();
        req.addPoint(new GHPoint(0, 1));
        req.addPoint(new GHPoint(2, 3));
        req.addPoint(new GHPoint(4, 5));
        req.addOutArray("weights");
        req.addOutArray("distances");
        req.addOutArray("times");
        req.setFailFast(false);

        MatrixResponse rsp = matrixWeb.route(req);
        assertFalse(rsp.hasErrors());
        assertTrue(rsp.hasProblems());

        assertEquals(Double.MAX_VALUE, rsp.getWeight(1, 0), 1.e-3);
        assertEquals(Double.MAX_VALUE, rsp.getWeight(1, 1), 1.e-3);
        assertEquals(Double.MAX_VALUE, rsp.getWeight(1, 2), 1.e-3);
        assertEquals(Double.MAX_VALUE, rsp.getWeight(0, 1), 1.e-3);
        assertEquals(Double.MAX_VALUE, rsp.getWeight(2, 1), 1.e-3);

        assertEquals(0, rsp.getWeight(0, 0), 1.e-3);
        assertEquals(1, rsp.getWeight(0, 2), 1.e-3);
        assertEquals(2, rsp.getWeight(2, 0), 1.e-3);
        assertEquals(3, rsp.getWeight(2, 2), 1.e-3);

        assertEquals(Double.MAX_VALUE, rsp.getDistance(1, 0), 1.e-3);
        assertEquals(Double.MAX_VALUE, rsp.getDistance(1, 1), 1.e-3);
        assertEquals(Double.MAX_VALUE, rsp.getDistance(1, 2), 1.e-3);
        assertEquals(Double.MAX_VALUE, rsp.getDistance(0, 1), 1.e-3);
        assertEquals(Double.MAX_VALUE, rsp.getDistance(2, 1), 1.e-3);

        assertEquals(4, rsp.getDistance(0, 0), 1.e-3);
        assertEquals(5, rsp.getDistance(0, 2), 1.e-3);
        assertEquals(6, rsp.getDistance(2, 0), 1.e-3);
        assertEquals(7, rsp.getDistance(2, 2), 1.e-3);

        assertEquals(Long.MAX_VALUE, rsp.getTime(1, 0));
        assertEquals(Long.MAX_VALUE, rsp.getTime(1, 1));
        assertEquals(Long.MAX_VALUE, rsp.getTime(1, 2));
        assertEquals(Long.MAX_VALUE, rsp.getTime(0, 1));
        assertEquals(Long.MAX_VALUE, rsp.getTime(2, 1));

        assertEquals(8 * 1000, rsp.getTime(0, 0));
        assertEquals(9 * 1000, rsp.getTime(0, 2));
        assertEquals(10 * 1000, rsp.getTime(2, 0));
        assertEquals(11 * 1000, rsp.getTime(2, 2));

        assertEquals("[[0, 1], [1, 0], [1, 1], [1, 2], [2, 1]]", rsp.getDisconnectedPoints().toString());
        assertEquals(Collections.singletonList(1), rsp.getInvalidFromPoints());
        assertEquals(Collections.singletonList(1), rsp.getInvalidToPoints());
    }

    @Test
    public void testReadingGoogleThrowsException() throws IOException {
        String ghMatrix = readFile(new InputStreamReader(getClass().getResourceAsStream("google-matrix1.json")));
        GraphHopperMatrixWeb matrixWeb = createMatrixClient(ghMatrix);
        GHMRequest req = createRequest();
        MatrixResponse rsp = matrixWeb.route(req);
        assertTrue(rsp.hasErrors());
    }

    @Test
    public void testReadingWeights_TimesAndDistances() throws IOException {
        String ghMatrix = readFile(new InputStreamReader(getClass().getResourceAsStream("matrix.json")));
        GraphHopperMatrixWeb matrixWeb = createMatrixClient(ghMatrix);

        GHMRequest req = createRequest();
        req.addOutArray("weights");
        req.addOutArray("distances");
        req.addOutArray("times");
        MatrixResponse rsp = matrixWeb.route(req);

        assertFalse(rsp.hasErrors());

        assertEquals(9475., rsp.getDistance(0, 1), .1);
        assertEquals(9734., rsp.getDistance(1, 2), .1);
        assertEquals(0., rsp.getDistance(1, 1), .1);

        assertEquals(885.867, rsp.getWeight(0, 1), .1);
        assertEquals(807.167, rsp.getWeight(1, 2), .1);
        assertEquals(0., rsp.getWeight(1, 1), .1);

        assertEquals(886, rsp.getTime(0, 1) / 1000);
    }

    @Test
    public void noVehicleWhenNotSpecified() {
        GHMatrixBatchRequester requester = new GHMatrixBatchRequester("url");
        JsonNode json = requester.createPostRequest(new GHMRequest(5), Collections.singletonList("weights"));
        assertEquals("{\"out_arrays\":[\"weights\"],\"fail_fast\":true}", json.toString());
    }

    @Test
    public void hasHintsWhenSpecified() {
        GHMatrixAbstractRequester requester = createRequester("url");
        GHMRequest ghmRequest = new GHMRequest(5);
        ghmRequest.putHint("vehicle", "my_car").putHint("profile", "my_profile");
        JsonNode json = requester.createPostRequest(ghmRequest, Collections.singletonList("weights"));
        assertEquals("{\"out_arrays\":[\"weights\"],\"fail_fast\":true,\"vehicle\":\"my_car\",\"profile\":\"my_profile\"}", json.toString());
    }

    public static String readFile(Reader simpleReader) throws IOException {
        try (BufferedReader reader = new BufferedReader(simpleReader)) {
            String res = "";
            String line;
            while ((line = reader.readLine()) != null) {
                res += line;
            }
            return res;
        }
    }
}
