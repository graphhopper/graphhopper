package com.graphhopper.http.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.graphhopper.config.CHProfileConfig;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.http.util.GraphHopperServerTestConfiguration;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.routing.weighting.custom.CustomProfileConfig;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
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
                putObject("routing.ch.disabling_allowed", true).
                putObject("prepare.min_network_size", 0).
                putObject("prepare.min_one_way_network_size", 0).
                putObject("datareader.file", "../core/files/north-bayreuth.osm.gz").
                putObject("graph.location", DIR).
                putObject("graph.encoded_values", "max_height,max_weight,max_width,hazmat,toll,surface,track_type").
                setProfiles(Arrays.asList(
                        new CustomProfileConfig("car").setCustomModel(new CustomModel()).setVehicle("car"),
                        new CustomProfileConfig("bike").setCustomModel(new CustomModel()).setVehicle("bike"),
                        new CustomProfileConfig("truck").setVehicle("car").
                                putHint("custom_model_file", "./src/test/resources/com/graphhopper/http/resources/truck.yml"),
                        new CustomProfileConfig("cargo_bike").setVehicle("bike").
                                putHint("custom_model_file", "./src/test/resources/com/graphhopper/http/resources/cargo_bike.yml"))).
                setCHProfiles(Collections.singletonList(new CHProfileConfig("truck")));
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

    @Test
    public void testAvoidArea() {
        String yamlQuery = "points: [[11.58199, 50.0141], [11.5865, 50.0095]]\n" +
                "profile: car\n";

        JsonNode yamlNode = clientTarget(app, "/route-custom").request().post(Entity.json(yamlToJson(yamlQuery))).readEntity(JsonNode.class);
        JsonNode path = yamlNode.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 661, 10);

        yamlQuery += "priority:\n" +
                // todonow: shall we increase the priority here to get the route that crosses the polygon, but uses
                // a faster road (see #2021)? or maybe do both?
                "  area_custom1: 0.05\n" +
                "areas:\n" +
                "  custom1:\n" +
                "    type: \"Feature\"\n" +
                "    geometry: { type: \"Polygon\", coordinates: [[[11.5818,50.0126], [11.5818,50.0119], [11.5861,50.0119], [11.5861,50.0126], [11.5818,50.0126]]] }";
        yamlNode = clientTarget(app, "/route-custom").request().post(Entity.json(yamlToJson(yamlQuery))).readEntity(JsonNode.class);
        path = yamlNode.get("paths").get(0);
        assertEquals(3073, path.get("distance").asDouble(), 10);
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