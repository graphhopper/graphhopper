package com.graphhopper.api;

import com.graphhopper.util.shapes.GHPoint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
@RunWith(Parameterized.class)
public class GraphHopperMatrixIT {

    protected GraphHopperMatrixWeb ghMatrix;

    public GraphHopperMatrixIT(boolean batch, int maxUnzippedLength) {
        GHMatrixAbstractRequester requester = batch ? new GHMatrixSyncRequester() : new GHMatrixSyncRequester();
        requester.maxUnzippedLength = maxUnzippedLength;
        ghMatrix = new GraphHopperMatrixWeb(requester);
        String key = System.getProperty("graphhopper.key", GraphHopperWebIT.KEY);
        ghMatrix.setKey(key);
    }

    @Parameterized.Parameters(name = "BATCH = {0}, maxUnzippedLength = {1}")
    public static Collection<Object[]> configs() {
        return Arrays.asList(new Object[][]{
                {true, 1000},
                {true, 0},
                {false, 1000},
                {false, 0}
        });
    }

    @Test
    public void testMatrix() {
        GHMRequest req = AbstractGHMatrixWebTester.createRequest();
        MatrixResponse res = ghMatrix.route(req);

        // no distances available
        try {
            assertEquals(0, res.getDistance(1, 2), 1);
            fail();
        } catch (Exception ex) {
        }

        // ... only weight:
        assertEquals(1840, res.getWeight(1, 2), 10);

        req = AbstractGHMatrixWebTester.createRequest();
        req.addOutArray("weights");
        req.addOutArray("distances");
        res = ghMatrix.route(req);

        assertEquals(9800, res.getDistance(1, 2), 50);
        assertEquals(1840, res.getWeight(1, 2), 10);
    }

    @Test
    public void testBikeMatrix() {
        GHMRequest req = AbstractGHMatrixWebTester.createRequest();
        req.setVehicle("bike");
        req.addOutArray("times");

        MatrixResponse res = ghMatrix.route(req);
        assertEquals(2200, res.getTime(1, 2) / 1000, 200);
    }

    @Test
    public void testNxM_issue45() {
        // https://github.com/graphhopper/directions-api-java-client/issues/45
        GHMRequest ghmRequest = new GHMRequest();
        ghmRequest.addOutArray("distances");
        ghmRequest.addOutArray("times");
        ghmRequest.setVehicle("car");
        ghmRequest.setFromPoints(Arrays.asList(new GHPoint(52.557151, 13.515244)))
                .setToPoints(Arrays.asList(new GHPoint(52.557151, 13.515244), new GHPoint(52.454545, 13.295517)));

        MatrixResponse res = ghMatrix.route(ghmRequest);
        assertEquals(2480, res.getTime(0, 1) / 1000, 30);
    }

    @Test
    public void testPOSTMatrixQueryWithPointHints() {
        GHMRequest req = new GHMRequest();
        req.addPoint(new GHPoint(52.517004, 13.389416));
        req.addPoint(new GHPoint(52.485707, 13.435249));
        req.addPoint(new GHPoint(52.516848, 13.424606));
        req.addOutArray("distances");
        MatrixResponse res = ghMatrix.route(req);
        assertEquals(4833, res.getDistance(1, 2), 30);
        assertEquals(5162, res.getDistance(2, 1), 30);

        req = new GHMRequest();
        req.addPoint(new GHPoint(52.517004, 13.389416));
        req.addPoint(new GHPoint(52.485707, 13.435249));
        req.addPoint(new GHPoint(52.516848, 13.424606));
        req.addOutArray("distances");
        req.setPointHints(Arrays.asList("", "", "ifflandstr"));
        res = ghMatrix.route(req);
        assertEquals(4953, res.getDistance(1, 2), 30);
        assertEquals(4927, res.getDistance(2, 1), 30);

        req = new GHMRequest();
        req.addPoint(new GHPoint(52.517004, 13.389416));
        req.addPoint(new GHPoint(52.485707, 13.435249));
        req.addPoint(new GHPoint(52.516848, 13.424606));
        // wrong count
        req.setPointHints(Arrays.asList("", "ifflandstr"));
        res = ghMatrix.route(req);
        assertTrue(res.hasErrors());
        assertEquals("Array length of point_hints must match length of points (or from/to equivalent)", res.getErrors().get(0).getMessage());
    }

    @Test
    public void testMatrixSnapPrevention() {
        GHMRequest req = new GHMRequest();
        req.addPoint(new GHPoint(52.480271, 13.418941));
        req.addPoint(new GHPoint(52.462834, 13.438854));
        req.addOutArray("distances");
        req.setSnapPreventions(Arrays.asList("motorway"));
        MatrixResponse res = ghMatrix.route(req);
        assertEquals(2860, res.getDistance(0, 1), 30);
    }

    @Test
    public void testConnectionNotFound() {
        GHMRequest req = new GHMRequest();
        req.addPoint(new GHPoint(-7.126486, -34.833741));
        req.addPoint(new GHPoint(9.657616, -13.565369));
        req.addPoint(new GHPoint(18.928696, -70.400047));
        req.addPoint(new GHPoint(-7.323564, -35.32774));

        MatrixResponse matrix = ghMatrix.route(req);
        assertTrue(matrix.hasErrors());
        assertEquals(1, matrix.getErrors().size());
        assertTrue(matrix.getErrors().get(0).getMessage().contains("0->1"));
        assertFalse(matrix.isConnected(0, 1));
        try {
            matrix.getWeight(0, 1);
            fail("getWeight should throw an exception if errors were found");
        } catch (Exception e) {
            // ok
        }
    }

    @Test
    public void testConnectionNotFound_doNotFailFast() {
        GHMRequest req = new GHMRequest();
        req.addPoint(new GHPoint(-7.126486, -34.833741));
        req.addPoint(new GHPoint(9.657616, -13.565369));
        req.addPoint(new GHPoint(18.928696, -70.400047));
        req.addPoint(new GHPoint(-7.323564, -35.32774));
        req.addOutArray("weights");
        req.addOutArray("distances");
        req.addOutArray("times");
        req.setFailFast(false);

        MatrixResponse matrix = ghMatrix.route(req);

        // if fail_fast is false we do not consider disconnected points to be errors and instead we expect the full
        // matrix to be returned where disconnected points yield weight = Double.MAX_VALUE
        assertFalse(matrix.hasErrors());
        assertTrue(matrix.hasProblems());
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                double weight = matrix.getWeight(i, j);
                double distance = matrix.getDistance(i, j);
                long time = matrix.getTime(i, j);
                boolean connected = matrix.isConnected(i, j);
                if (i == j) {
                    assertTrue(connected);
                    assertEquals(0, weight, 1.e-3);
                    assertEquals(0, distance, 1.e-3);
                    assertEquals(0, time);
                } else if (i == 0 && j == 3) {
                    assertTrue(connected);
                    assertEquals(4931, weight, 100);
                    assertEquals(78812, distance, 100);
                    assertEquals(3752000, time, 100000);
                } else if (i == 3 && j == 0) {
                    assertTrue(connected);
                    assertEquals(4745, weight, 100);
                    assertEquals(75480, distance, 100);
                    assertEquals(3613000, time, 100000);
                } else {
                    assertFalse(connected);
                    assertEquals("expected maximum weight for matrix element (" + i + ", " + j + "), but was: " + weight, Double.MAX_VALUE, weight, 1.e-3);
                    assertEquals("expected maximum distance for matrix element (" + i + ", " + j + "), but was: " + distance, Double.MAX_VALUE, weight, 1.e-3);
                    assertEquals("expected maximum time for matrix element (" + i + ", " + j + "), but was: " + time, Long.MAX_VALUE, time);
                }
            }
        }
        assertEquals(10, matrix.getDisconnectedPoints().size());
        assertEquals("[[0, 1], [0, 2], [1, 0], [1, 2], [1, 3], [2, 0], [2, 1], [2, 3], [3, 1], [3, 2]]", matrix.getDisconnectedPoints().toString());
    }

    @Test
    public void testPointsNotFound() {
        GHMRequest req = new GHMRequest();
        req.addPoint(new GHPoint(42.506021, 1.643829));
        req.addPoint(new GHPoint(42.541382, 1.516349));
        req.addPoint(new GHPoint(42.497289, 1.762276));
        req.addPoint(new GHPoint(42.566293, 1.597867));

        MatrixResponse matrix = ghMatrix.route(req);
        assertTrue(matrix.hasErrors());
        assertEquals(2, matrix.getErrors().size());
        assertTrue(matrix.getErrors().get(0).getMessage().contains("Cannot find from_points: 0, 2"));
        assertTrue(matrix.getErrors().get(1).getMessage().contains("Cannot find to_points: 0, 2"));
        assertFalse(matrix.isConnected(0, 1));
        try {
            matrix.getWeight(0, 1);
            fail("getWeight should throw an exception if errors were found");
        } catch (Exception e) {
            // ok
        }
    }

    @Test
    public void testPointsNotFound_doNotFailFast() {
        GHMRequest req = new GHMRequest();
        req.addPoint(new GHPoint(42.506021, 1.643829));
        req.addPoint(new GHPoint(42.541382, 1.516349));
        req.addPoint(new GHPoint(42.497289, 1.762276));
        req.addPoint(new GHPoint(42.566293, 1.597867));
        req.addOutArray("weights");
        req.addOutArray("distances");
        req.addOutArray("times");
        req.setFailFast(false);

        MatrixResponse matrix = ghMatrix.route(req);
        assertFalse(matrix.hasErrors());
        assertTrue(matrix.hasProblems());
        assertEquals(0, matrix.getErrors().size());
        assertEquals(Arrays.asList(0, 2), matrix.getInvalidFromPoints());
        assertEquals(Arrays.asList(0, 2), matrix.getInvalidToPoints());
        assertEquals("[[0, 0], [0, 1], [0, 2], [0, 3], [1, 0], [1, 2], [2, 0], [2, 1], [2, 2], [2, 3], [3, 0], [3, 2]]",
                matrix.getDisconnectedPoints().toString());
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                double weight = matrix.getWeight(i, j);
                double distance = matrix.getDistance(i, j);
                long time = matrix.getTime(i, j);
                boolean connected = matrix.isConnected(i, j);
                if (i == 1 && j == 1 || i == 3 && j == 3) {
                    assertEquals(0, weight, 1.e-3);
                    assertEquals(0, distance, 1.e-3);
                    assertEquals(0, time);
                    assertTrue(connected);
                } else if (i == 1 && j == 3) {
                    assertEquals(1087, weight, 10);
                    assertEquals(13926, distance, 100);
                    assertEquals(878000, time, 10000);
                    assertTrue(connected);
                } else if (i == 3 && j == 1) {
                    assertEquals(1083, weight, 10);
                    assertEquals(13856, distance, 100);
                    assertEquals(875000, time, 1000);
                    assertTrue(connected);
                } else {
                    assertEquals(Double.MAX_VALUE, weight, 1.e-3);
                    assertEquals(Double.MAX_VALUE, distance, 1.e-3);
                    assertEquals(Long.MAX_VALUE, time);
                    assertFalse(connected);
                }
            }
        }
    }

    @Test
    public void testPointsNotFound_doNotFailFast_noPointsFound() {
        GHMRequest req = new GHMRequest();
        req.addPoint(new GHPoint(42.506021, 1.643829));
        req.addPoint(new GHPoint(42.497289, 1.762276));
        req.setFailFast(false);

        MatrixResponse matrix = ghMatrix.route(req);
        assertFalse(matrix.hasErrors());
        assertTrue(matrix.hasProblems());
        assertEquals(0, matrix.getErrors().size());
        assertEquals(Arrays.asList(0, 1), matrix.getInvalidFromPoints());
        assertEquals(Arrays.asList(0, 1), matrix.getInvalidToPoints());
        assertEquals("[[0, 0], [0, 1], [1, 0], [1, 1]]", matrix.getDisconnectedPoints().toString());
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                double weight = matrix.getWeight(i, j);
                assertFalse(matrix.isConnected(i, j));
                assertEquals(Double.MAX_VALUE, weight, 1.e-3);
            }
        }
    }

}
