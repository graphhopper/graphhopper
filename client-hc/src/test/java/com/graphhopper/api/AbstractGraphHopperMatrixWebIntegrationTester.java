package com.graphhopper.api;

import com.graphhopper.util.shapes.GHPoint;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

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
        assertEquals(1690, res.getWeight(1, 2), 10);

        req = AbstractGHMatrixWebTester.createRequest();
        req.addOutArray("weights");
        req.addOutArray("distances");
        res = ghMatrix.route(req);

        assertEquals(9800, res.getDistance(1, 2), 50);
        assertEquals(1695, res.getWeight(1, 2), 10);
    }

    @Test
    public void testBikeMatrix() {
        GHMRequest req = AbstractGHMatrixWebTester.createRequest();
        req.setVehicle("bike");
        req.addOutArray("times");

        MatrixResponse res = ghMatrix.route(req);
        assertEquals(2450, res.getTime(1, 2) / 1000, 110);
    }

    @Test
    public void testNxM_issue45() {
        GHMRequest ghmRequest = new GHMRequest();
        ghmRequest.addOutArray("distances");
        ghmRequest.addOutArray("times");
        ghmRequest.setVehicle("car");
        ghmRequest.addFromPoints(Arrays.asList(new GHPoint(52.557151, 13.515244)))
                .addToPoints(Arrays.asList(new GHPoint(52.557151, 13.515244), new GHPoint(52.454545, 13.295517)));

        MatrixResponse res = ghMatrix.route(ghmRequest);
        assertEquals(2415, res.getTime(0, 1) / 1000, 20);
    }
}
