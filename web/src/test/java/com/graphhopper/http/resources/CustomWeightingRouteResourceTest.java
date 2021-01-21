package com.graphhopper.http.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.graphhopper.config.CHProfile;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.http.util.GraphHopperServerTestConfiguration;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static com.graphhopper.http.util.TestUtils.clientTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(DropwizardExtensionsSupport.class)
public class CustomWeightingRouteResourceTest {

    private static final String DIR = "./target/north-bayreuth-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "bike,car").
                putObject("prepare.min_network_size", 0).
                putObject("datareader.file", "../core/files/north-bayreuth.osm.gz").
                putObject("graph.location", DIR).
                putObject("graph.encoded_values", "max_height,max_weight,max_width,hazmat,toll,surface,track_type").
                setProfiles(Arrays.asList(
                        new CustomProfile("car").setCustomModel(new CustomModel()).setVehicle("car"),
                        new CustomProfile("bike").setCustomModel(new CustomModel()).setVehicle("bike"),
                        new CustomProfile("truck").setVehicle("car").
                                putHint("custom_model_file", "./src/test/resources/com/graphhopper/http/resources/truck.yml"),
                        new CustomProfile("cargo_bike").setVehicle("bike").
                                putHint("custom_model_file", "./src/test/resources/com/graphhopper/http/resources/cargo_bike.yml"),
                        new CustomProfile("json_bike").setVehicle("bike").
                                putHint("custom_model_file", "./src/test/resources/com/graphhopper/http/resources/json_bike.json"))).
                setCHProfiles(Collections.singletonList(new CHProfile("truck")));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testCHTruckQuery() {
        String jsonQuery = "{" +
                " \"points\": [[11.58199, 50.0141], [11.5865, 50.0095]]," +
                " \"profile\": \"truck\"" +
                "}";
        final Response response = clientTarget(app, "/route").request().post(Entity.json(jsonQuery));
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 1500, 10);
        assertEquals(path.get("time").asLong(), 151_000, 1_000);
    }

    @ParameterizedTest
    @CsvSource(value = {"0.05,3073", "0.5,1498"})
    public void testAvoidArea(double priority, double expectedDistance) {
        String yamlQuery = "points: [[11.58199, 50.0141], [11.5865, 50.0095]]\n" +
                "profile: car\n";

        JsonNode yamlNode = clientTarget(app, "/route-custom").request().post(Entity.json(yamlToJson(yamlQuery))).readEntity(JsonNode.class);
        JsonNode path = yamlNode.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 661, 10);

        // 'blocking' the area either leads to a route that still crosses it (but on a faster road) or to a road
        // going all the way around it depending on the priority, see #2021
        yamlQuery += "" +
                "priority:\n" +
                // a faster road (see #2021)? or maybe do both?
                "  - if: in_area_custom1\n" +
                "    multiply by: " + priority + "\n" +
                "areas:\n" +
                "  custom1:\n" +
                "    type: \"Feature\"\n" +
                "    geometry: { type: \"Polygon\", coordinates: [[[11.5818,50.0126], [11.5818,50.0119], [11.5861,50.0119], [11.5861,50.0126], [11.5818,50.0126]]] }";
        yamlNode = clientTarget(app, "/route-custom").request().post(Entity.json(yamlToJson(yamlQuery))).readEntity(JsonNode.class);
        path = yamlNode.get("paths").get(0);
        assertEquals(expectedDistance, path.get("distance").asDouble(), 10);
    }

    @Test
    public void testCargoBike() throws IOException {
        String yamlQuery = "points: [[11.58199, 50.0141], [11.5865, 50.0095]]\n" +
                "profile: bike\n";
        JsonNode yamlNode = clientTarget(app, "/route-custom").request().post(Entity.json(yamlToJson(yamlQuery))).readEntity(JsonNode.class);
        JsonNode path = yamlNode.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 661, 5);

        String queryYamlFromFile = Helper.isToString(getClass().getResourceAsStream("cargo_bike.yml"));
        yamlQuery = "points: [[11.58199, 50.0141], [11.5865, 50.0095]]\n" +
                "profile: bike\n" +
                queryYamlFromFile;
        yamlNode = clientTarget(app, "/route-custom").request().post(Entity.json(yamlToJson(yamlQuery))).readEntity(JsonNode.class);
        path = yamlNode.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 1007, 5);

        // results should be identical be it via server-side profile or query profile:
        yamlQuery = "points: [[11.58199, 50.0141], [11.5865, 50.0095]]\n" +
                "profile: cargo_bike";
        yamlNode = clientTarget(app, "/route-custom").request().post(Entity.json(yamlToJson(yamlQuery))).readEntity(JsonNode.class);
        JsonNode path2 = yamlNode.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), path2.get("distance").asDouble(), 1);
    }

    @Test
    public void testJsonBike() {
        String jsonQuery = "{" +
                " \"points\": [[11.58199, 50.0141], [11.5865, 50.0095]]," +
                " \"profile\": \"json_bike\"" +
                "}";
        final Response response = clientTarget(app, "/route-custom").request().post(Entity.json(jsonQuery));
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 660, 10);
    }

    static String yamlToJson(String yaml) {
        try {
            ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
            Object obj = yamlReader.readValue(yaml, Object.class);

            ObjectMapper jsonWriter = new ObjectMapper();
            return jsonWriter.writeValueAsString(obj);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}