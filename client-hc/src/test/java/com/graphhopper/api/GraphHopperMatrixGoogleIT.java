package com.graphhopper.api;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class GraphHopperMatrixGoogleIT {

    protected GraphHopperMatrixWeb ghMatrix;

    @Before
    public void setUp() {
        // skip setKey as it is the graphhopper key
        ghMatrix = createMatrixWeb();
    }

    GraphHopperMatrixWeb createMatrixWeb() {
        return new GraphHopperMatrixWeb(new GoogleMatrixSyncRequester("https://maps.googleapis.com/maps/api/distancematrix/json"));
    }

    @Test
    public void testMatrix() {
        GHMRequest req = AbstractGHMatrixWebTester.createRequest();
        MatrixResponse res = ghMatrix.route(req);

        assertEquals(11000, res.getDistance(1, 2), 2000);
        assertEquals(2500, res.getTime(1, 2) / 1000, 1000);
    }
}
