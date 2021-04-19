package com.graphhopper.api;

import com.graphhopper.GHRequest;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This class unit tests the class. For integration tests against a real server see RouteResourceClientHCTest.
 */
public class GraphHopperWebTest {

    @ParameterizedTest(name = "POST={0}")
    @ValueSource(booleans = {true, false})
    public void testGetClientForRequest(boolean usePost) {
        GraphHopperWeb gh = new GraphHopperWeb(null).setPostRequest(usePost);
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(42.509225, 1.534728)).
                addPoint(new GHPoint(42.512602, 1.551558)).
                putHint("vehicle", "car");
        req.putHint(GraphHopperWeb.TIMEOUT, 5);

        assertEquals(5, gh.getClientForRequest(req).connectTimeoutMillis());
    }

    @Test
    public void vehicleIncludedAsGiven() {
        GraphHopperWeb hopper = new GraphHopperWeb("https://localhost:8000/route");
        // no vehicle -> no vehicle
        assertEquals("https://localhost:8000/route?&profile=&type=json&instructions=true&points_encoded=true" +
                        "&calc_points=true&algorithm=&locale=en_US&elevation=false&optimize=false",
                hopper.createGetRequest(new GHRequest()).url().toString());

        // vehicle given -> vehicle used in url
        assertEquals("https://localhost:8000/route?&profile=my_profile&type=json&instructions=true&points_encoded=true" +
                        "&calc_points=true&algorithm=&locale=en_US&elevation=false&optimize=false&vehicle=my_car",
                hopper.createGetRequest(new GHRequest().putHint("vehicle", "my_car").setProfile("my_profile")).url().toString());
    }

}