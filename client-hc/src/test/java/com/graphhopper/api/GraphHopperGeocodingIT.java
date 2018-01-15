package com.graphhopper.api;

import com.graphhopper.api.model.GHGeocodingRequest;
import com.graphhopper.api.model.GHGeocodingResponse;
import org.junit.Before;
import org.junit.Test;

import java.net.SocketTimeoutException;

import static org.junit.Assert.*;

/**
 * @author Robin Boldt
 */
public class GraphHopperGeocodingIT {

    public static final String KEY = "614b8305-b4db-48c9-bf4a-40de90919939";

    GraphHopperGeocoding geocoding = new GraphHopperGeocoding();

    @Before
    public void setUp() {
        String key = System.getProperty("graphhopper.key", KEY);
        geocoding.setKey(key);
    }

    @Test
    public void testForwardGeocoding() {
        GHGeocodingResponse response = geocoding.geocode(new GHGeocodingRequest("Berlin", "en", 7));
        assertEquals(7, response.getHits().size());
        assertTrue(response.getHits().get(0).getName().contains("Berlin"));
    }

    @Test
    public void testForwardGeocodingNominatim() {
        GHGeocodingResponse response = geocoding.geocode(new GHGeocodingRequest(false, Double.NaN, Double.NaN, "Berlin", "en", 5, "nominatim", 5000));
        assertEquals(5, response.getHits().size());
        assertTrue(response.getHits().get(0).getName().contains("Berlin"));
    }

    @Test
    public void testReverseGeocoding() {
        GHGeocodingResponse response = geocoding.geocode(new GHGeocodingRequest(52.5170365, 13.3888599, "en", 7));
        assertEquals(7, response.getHits().size());
        assertTrue(response.getHits().get(0).getName().contains("Berlin"));
    }

    @Test
    public void testTimeout() {
        try {
            // We set the timeout to 1ms, it shouldn't be possible for the API to answer that quickly => we will receive a SocketTimeout
            geocoding.geocode(new GHGeocodingRequest(false, Double.NaN, Double.NaN, "Berlin", "en", 5, "default", 1));
        } catch (RuntimeException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                return;
            }
        }
        fail();
    }

}
