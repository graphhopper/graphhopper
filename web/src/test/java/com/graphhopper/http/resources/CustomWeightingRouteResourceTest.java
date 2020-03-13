package com.graphhopper.http.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.config.CHProfileConfig;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.routing.weighting.custom.CustomProfileConfig;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static com.graphhopper.http.resources.CustomWeightingRouteResourceLMTest.assertBetween;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CustomWeightingRouteResourceTest {

    private static final String DIR = "./target/north-bayreuth-gh/";
    private static final GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();

    static {
        config.getGraphHopperConfiguration().
                put("graph.flag_encoders", "bike,car").
                put("routing.ch.disabling_allowed", "true").
                put("prepare.min_network_size", "0").
                put("prepare.min_one_way_network_size", "0").
                put("datareader.file", "../core/files/north-bayreuth.osm.gz").
                put("graph.location", DIR).
                // for the custom_profiles more than the default encoded values are necessary
                        put("graph.encoded_values", "max_height,max_weight,max_width,hazmat,toll,surface,track_type").
                setProfiles(Arrays.asList(
                        new CustomProfileConfig("car").setCustomModel(new CustomModel()).setVehicle("car"),
                        new CustomProfileConfig("bike").setCustomModel(new CustomModel()).setVehicle("bike"),
                        new CustomProfileConfig("truck").setVehicle("car").
                                putHint("custom_model_file", "./src/test/resources/com/graphhopper/http/resources/truck.yml"),
                        new CustomProfileConfig("cargo_bike").setVehicle("bike").
                                putHint("custom_model_file", "./src/test/resources/com/graphhopper/http/resources/cargo_bike.yml"))).
                setCHProfiles(Collections.singletonList(new CHProfileConfig("truck")));
    }

    @ClassRule
    public static final DropwizardAppRule<GraphHopperServerConfiguration> app = new DropwizardAppRule<>(GraphHopperApplication.class, config);

    @BeforeClass
    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testCHTruckQuery() {
        String jsonQuery = "{" +
                " \"points\": [[11.58199, 50.0141], [11.5865, 50.0095]]," +
                " \"profile\": \"truck\"" +
                "}";
        final Response response = app.client().target("http://localhost:8080/route").request().post(Entity.json(jsonQuery));
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        assertBetween("distance wasn't correct", path.get("distance").asDouble(), 1400, 1600);
        assertBetween("time wasn't correct", path.get("time").asLong() / 1000.0, 120, 180);
    }

    @Test
    public void testAvoidArea() throws IOException {
        String yamlQuery = "points: [[11.58199, 50.0141], [11.5865, 50.0095]]\n" +
                "profile: car\n";

        JsonNode yamlNode = app.client().target("http://localhost:8080/custom").request().post(Entity.entity(yamlQuery,
                new MediaType("application", "yaml"))).readEntity(JsonNode.class);
        JsonNode path = yamlNode.get("paths").get(0);
        assertBetween("distance wasn't correct", path.get("distance").asDouble(), 500, 900);

        yamlQuery += "priority:\n" +
                "  area_custom1: 0.5\n" +
                "areas:\n" +
                "  custom1:\n" +
                "    type: \"Feature\"\n" +
                "    geometry: { type: \"Polygon\", coordinates: [[[11.5818,50.0126], [11.5818,50.0119], [11.5861,50.0119], [11.5861,50.0126], [11.5818,50.0126]]] }";
        yamlNode = app.client().target("http://localhost:8080/custom").request().post(Entity.entity(yamlQuery,
                new MediaType("application", "yaml"))).readEntity(JsonNode.class);
        path = yamlNode.get("paths").get(0);
        assertBetween("distance wasn't correct", path.get("distance").asDouble(), 1400, 1600);
    }

    @Test
    public void testCargoBike() throws IOException {
        String yamlQuery = "points: [[11.58199, 50.0141], [11.5865, 50.0095]]\n" +
                "profile: bike\n";
        JsonNode yamlNode = app.client().target("http://localhost:8080/custom").request().post(Entity.entity(yamlQuery,
                new MediaType("application", "yaml"))).readEntity(JsonNode.class);
        JsonNode path = yamlNode.get("paths").get(0);
        assertBetween("distance wasn't correct", path.get("distance").asDouble(), 600, 700);

        String queryYamlFromFile = Helper.isToString(getClass().getResourceAsStream("cargo_bike.yml"));
        yamlQuery = "points: [[11.58199, 50.0141], [11.5865, 50.0095]]\n" +
                "profile: bike\n" +
                queryYamlFromFile;
        yamlNode = app.client().target("http://localhost:8080/custom").request().post(Entity.entity(yamlQuery,
                new MediaType("application", "yaml"))).readEntity(JsonNode.class);
        path = yamlNode.get("paths").get(0);
        assertBetween("distance wasn't correct", path.get("distance").asDouble(), 1000, 2000);

        // results should be identical be it via server-side profile or query profile:
        yamlQuery = "points: [[11.58199, 50.0141], [11.5865, 50.0095]]\n" +
                "profile: cargo_bike";
        yamlNode = app.client().target("http://localhost:8080/custom").request().post(Entity.entity(yamlQuery,
                new MediaType("application", "yaml"))).readEntity(JsonNode.class);
        JsonNode path2 = yamlNode.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), path2.get("distance").asDouble(), 1);
    }
}