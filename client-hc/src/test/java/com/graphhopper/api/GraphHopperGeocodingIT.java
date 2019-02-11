package com.graphhopper.api;

import com.graphhopper.api.model.GHGeocodingEntry;
import com.graphhopper.api.model.GHGeocodingRequest;
import com.graphhopper.api.model.GHGeocodingResponse;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.Before;
import org.junit.Test;

import java.net.SocketTimeoutException;

import static com.graphhopper.api.GraphHopperWebIT.KEY;
import static org.junit.Assert.*;

/**
 * @author Robin Boldt
 */
public class GraphHopperGeocodingIT {

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
    public void testExtent() {
        GHGeocodingResponse response = geocoding.geocode(new GHGeocodingRequest("new york", "en", 7));
        BBox extent = response.getHits().get(0).getExtendBBox();
        assertTrue(extent.isValid());
        assertTrue(extent.minLon < -79);
        assertTrue(extent.maxLon > -72);
        assertTrue(extent.minLat < 40.5);
        assertTrue(extent.maxLat > 45);
    }

    @Test
    public void testForwardGeocodingNominatim() {
        GHGeocodingResponse response = geocoding.geocode(new GHGeocodingRequest(false, null, "Berlin", "en", 5, "nominatim", 5000));
        int size = response.getHits().size();
        assertTrue("Unexpected response hit count " + size, size == 4 || size == 5);
        assertTrue(response.getHits().get(0).getName().contains("Berlin"));
    }

    @Test
    public void testReverseGeocoding() {
        GHGeocodingResponse response = geocoding.geocode(new GHGeocodingRequest(52.5170365, 13.3888599, "en", 5));
        assertEquals(5, response.getHits().size());
        GHGeocodingEntry entry = response.getHits().get(0);
        assertTrue(entry.getName().contains("Berlin"));
        assertEquals("place", entry.getOsmKey());
        assertEquals(0, entry.getExtent().length);

    }

    @Test
    public void testTimeout() {
        try {
            // We set the timeout to 1ms, it shouldn't be possible for the API to answer that quickly => we will receive a SocketTimeout
            geocoding.geocode(new GHGeocodingRequest(false, null, "Berlin", "en", 5, "default", 1));
        } catch (RuntimeException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                return;
            }
        }
        fail();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testForwardException() {
        geocoding.geocode(new GHGeocodingRequest(false, new GHPoint(1, 1), null, "en", 5, "default", 1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBackwadException() {
        geocoding.geocode(new GHGeocodingRequest(true, null, "Berlin", "en", 5, "default", 1));
    }

}
