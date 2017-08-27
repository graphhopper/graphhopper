package com.graphhopper.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public abstract class AbstractGHMatrixWebTester {

    protected final ObjectMapper objectMapper = new ObjectMapper();

    abstract GraphHopperMatrixWeb createMatrixClient(String json) throws IOException;

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
            assertTrue(false);
        } catch (Exception ex) {
        }
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
