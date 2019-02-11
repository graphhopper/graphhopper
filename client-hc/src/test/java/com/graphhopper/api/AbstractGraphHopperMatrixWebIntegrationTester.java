package com.graphhopper.api;

import com.graphhopper.util.shapes.GHPoint;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static com.graphhopper.api.GraphHopperMatrixWeb.SERVICE_URL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Peter Karich
 */
public abstract class AbstractGraphHopperMatrixWebIntegrationTester {

    protected GraphHopperMatrixWeb ghMatrix;

    abstract GraphHopperMatrixWeb createMatrixWeb();

    @Before
    public void setUp() {
        String key = System.getProperty("graphhopper.key", GraphHopperWebIT.KEY);
        ghMatrix = createMatrixWeb();
        ghMatrix.setKey(key);
    }

    @Test
    public void testMatrix() {
        GHMRequest req = AbstractGHMatrixWebTester.createRequest();
        MatrixResponse res = ghMatrix.route(req);

        // no distances available
        try {
            assertEquals(0, res.getDistance(1, 2), 1);
            assertTrue(false);
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
}
