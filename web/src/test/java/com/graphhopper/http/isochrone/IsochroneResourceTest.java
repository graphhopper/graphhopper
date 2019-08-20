package com.graphhopper.http.isochrone;

import com.graphhopper.directions.api.client.ApiClient;
import com.graphhopper.directions.api.client.api.IsochroneApi;
import com.graphhopper.directions.api.client.model.IsochroneResponse;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.Polygon;
import io.dropwizard.testing.junit.DropwizardAppRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class IsochroneResourceTest {
    private static final String DIR = "./target/andorra-gh/";

    private static final GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();

    private static IsochroneApi client;

    static {
        config.getGraphHopperConfiguration().merge(new CmdArgs().
                put("prepare.ch.weightings", "no").
                put("graph.flag_encoders", "car").
                put("datareader.file", "../core/files/andorra.osm.pbf").
                put("graph.location", DIR));
        client = new IsochroneApi();
        client.setApiClient(new ApiClient().setBasePath("http://localhost:8080"));
    }

    @ClassRule
    public static final DropwizardAppRule<GraphHopperServerConfiguration> app = new DropwizardAppRule<>(
            GraphHopperApplication.class, config);

    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void requestByTimeLimit() throws Exception {
        IsochroneResponse rsp = client.isochroneGet("42.531073,1.573792", "no_key_necessary",
                5 * 60, -1, "car", 2, false, "fastest");
        assertEquals(2, rsp.getPolygons().size());
        List polygon0 = rsp.getPolygons().get(0).getGeometry().getCoordinates().get(0);
        List polygon1 = rsp.getPolygons().get(1).getGeometry().getCoordinates().get(0);

        assertTrue(contains(polygon0, 42.5386, 1.587224));
        assertFalse(contains(polygon0, 42.558012, 1.589756));

        assertTrue(contains(polygon1, 42.558012, 1.589756));
        assertFalse(contains(polygon1, 42.53841, 1.635246));
    }

    @Test
    public void requestByDistanceLimit() throws Exception {
        IsochroneResponse rsp = client.isochroneGet("42.531073,1.573792", "no_key_necessary", -1,
                3_000, "car", 2, false, "fastest");
        assertEquals(2, rsp.getPolygons().size());
        List polygon0 = rsp.getPolygons().get(0).getGeometry().getCoordinates().get(0);
        List polygon1 = rsp.getPolygons().get(1).getGeometry().getCoordinates().get(0);

        assertTrue(contains(polygon0, 42.531706, 1.57937));
        assertFalse(contains(polygon0, 42.5386, 1.587224));

        assertTrue(contains(polygon1, 42.543216, 1.591644));
        assertFalse(contains(polygon1, 42.558012, 1.589756));
    }

    @Test
    public void requestReverseFlow() throws Exception {
        IsochroneResponse rsp = client.isochroneGet("42.531073,1.573792", "no_key_necessary",
                5 * 60, -1, "car", 2, true, "fastest");
        assertEquals(2, rsp.getPolygons().size());
        List polygon0 = rsp.getPolygons().get(0).getGeometry().getCoordinates().get(0);
        List polygon1 = rsp.getPolygons().get(1).getGeometry().getCoordinates().get(0);

        assertTrue(contains(polygon0, 42.5386, 1.587224));
        assertFalse(contains(polygon0, 42.558012, 1.589756));

        assertTrue(contains(polygon1, 42.558012, 1.589756));
        assertFalse(contains(polygon1, 42.53841, 1.635246));
    }

    @Test
    public void requestBadRequest() {
        Response response = app.client().target("http://localhost:8080/route?point=-1.816719,51.557148").request().buildGet().invoke();
        assertEquals(400, response.getStatus());
    }

    @Test
    public void requestWithShortest() throws Exception {
        IsochroneResponse rsp = client.isochroneGet("42.509644,1.540554", "no_key_necessary", 130,
                -1, "car", 1, false, "shortest");
        assertEquals(1, rsp.getPolygons().size());
        List polygon0 = rsp.getPolygons().get(0).getGeometry().getCoordinates().get(0);

        assertTrue(contains(polygon0, 42.507145, 1.527057));
        assertFalse(contains(polygon0, 42.507081, 1.525404));

        // more like a circle => shorter is expected
        assertTrue(polygon0.size() < 185);
        rsp = client.isochroneGet("42.509644,1.540554", "no_key_necessary", 130,
                -1, "car", 1, false, "fastest");
        polygon0 = rsp.getPolygons().get(0).getGeometry().getCoordinates().get(0);
        assertTrue(polygon0.size() >= 190);
    }
    
    @Test
    public void requestJsonBadType() throws IOException {
        Response response = requestIsochrone("/isochrone?point=42.531073,1.573792&time_limit=130&type=xml");

        JsonNode json = parseRequestResponse(response);
        String message = json.path("message").asText();

        assertEquals(message, "Format not supported:xml");
    }
    
    
    @Test
    public void requestJsonWithType() throws IOException {
        Response response = requestIsochrone("/isochrone?point=42.531073,1.573792&time_limit=130&type=json");
        JsonNode json = parseRequestResponse(response);
        assertTrue(json.has("polygons"));
        assertTrue(json.has("info"));
    }
    
    @Test
    public void requestJsonNoType() throws IOException {
        Response response = requestIsochrone("/isochrone?point=42.531073,1.573792&time_limit=130");
        JsonNode json = parseRequestResponse(response);
        assertTrue(json.has("polygons"));
        assertTrue(json.has("info"));
    }
    
    @Test
    public void requestGeoJsonPolygons() throws IOException {        
        Response response = requestIsochrone("/isochrone?point=42.531073,1.573792&time_limit=130&type=geojson");
        JsonNode json = parseRequestResponse(response);
        
        assertFalse(json.has("polygons"));
        assertFalse(json.has("info"));
        
        assertTrue(json.has("type"));
        assertEquals(json.path("type").asText(), "FeatureCollection");
        
        assertTrue(json.has("features"));
        
        JsonNode firstFeature = json.path("features").path(0);
        assertTrue(firstFeature.isObject());
        
        assertTrue(firstFeature.path("properties").has("bucket"));
        assertTrue(firstFeature.path("properties").has("copyrights"));
        
        assertEquals(firstFeature.path("type").asText(), "Feature");
        assertEquals(firstFeature.path("geometry").path("type").asText(), "Polygon");
    }
    
    @Test
    public void requestGeoJsonPolygonsBuckets() throws IOException {        
        Response response = requestIsochrone("/isochrone?point=42.531073,1.573792&time_limit=130&type=geojson&buckets=3");
        JsonNode json = parseRequestResponse(response);
        
        JsonNode features = json.path("features");
        int length = features.size();
        JsonNode firstFeature = features.path(0);
        JsonNode lastFeature = features.path(length - 1);
        
        assertEquals(firstFeature.path("properties").path("bucket").asInt(), 0);
        assertEquals(firstFeature.path("geometry").path("type").asText(), "Polygon");
        
        assertEquals(lastFeature.path("properties").path("bucket").asInt(), 2);
        assertEquals(lastFeature.path("geometry").path("type").asText(), "Polygon");
    }    
    
    private Response requestIsochrone(String path) {
    	String url = "http://localhost:8080" + path;
        return app.client().target(url).request().buildGet().invoke();
    }
    
    private JsonNode parseRequestResponse (Response response) throws IOException {
        String body = response.readEntity(String.class);
        
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readTree(body);
    }
    
    private boolean contains(List polygon, double lat, double lon) {
        int index = 0;
        double lats[] = new double[polygon.size()];
        double lons[] = new double[polygon.size()];

        for (Object o : polygon) {
            List latlon = (List) o;
            lons[index] = ((Number) latlon.get(0)).doubleValue();
            lats[index] = ((Number) latlon.get(1)).doubleValue();
            index++;
        }
        return new Polygon(lats, lons).contains(lat, lon);
    }

}