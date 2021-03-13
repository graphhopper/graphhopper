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
import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static org.junit.jupiter.api.Assertions.*;

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
                                putHint("custom_model_file", "./src/test/resources/com/graphhopper/http/resources/json_bike.json"),
                        new CustomProfile("custom_bike").
                                setCustomModel(new CustomModel().
                                        addToSpeed(If("road_class == PRIMARY", LIMIT, 28)).
                                        addToPriority(If("max_width < 1.2", MULTIPLY, 0))).
                                setVehicle("bike"))).
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
        final Response response = query(jsonQuery, 200);
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
        String pointsAndProfile = "\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"car\"";
        JsonNode jsonNode = query("{" +  pointsAndProfile + "}", 200).readEntity(JsonNode.class);
        JsonNode path = jsonNode.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 661, 10);

        // 'blocking' the area either leads to a route that still crosses it (but on a faster road) or to a road
        // going all the way around it depending on the priority, see #2021
        String body = "{" + pointsAndProfile + ", " +
                "\"priority\":[{" +
                // a faster road (see #2021)? or maybe do both?
                "   \"if\": \"in_custom1\"," +
                "   \"multiply_by\": " + priority +
                "}], " +
                "\"areas\":{" +
                "  \"custom1\":{" +
                "    \"type\": \"Feature\"," +
                "    \"geometry\": { \"type\": \"Polygon\", \"coordinates\": [[[11.5818,50.0126], [11.5818,50.0119], [11.5861,50.0119], [11.5861,50.0126], [11.5818,50.0126]]] }" +
                "   }" +
                "}" +
                "}";
        jsonNode = query(body, 200).readEntity(JsonNode.class);
        path = jsonNode.get("paths").get(0);
        assertEquals(expectedDistance, path.get("distance").asDouble(), 10);
    }

    @Test
    public void testCargoBike() throws IOException {
        String body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"bike\"}";
        JsonNode jsonNode = query(body, 200).readEntity(JsonNode.class);
        JsonNode path = jsonNode.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 661, 5);

        String jsonFromYamlFile = yamlToJson(Helper.isToString(getClass().getResourceAsStream("cargo_bike.yml")));
        assertTrue(jsonFromYamlFile.startsWith("{"));
        body = "{" +"\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"bike\", " + jsonFromYamlFile.substring(1);
        jsonNode = query(body, 200).readEntity(JsonNode.class);
        path = jsonNode.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 1007, 5);

        // results should be identical be it via server-side profile or query profile:
        body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"cargo_bike\"}";
        jsonNode = query(body, 200).readEntity(JsonNode.class);
        JsonNode path2 = jsonNode.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), path2.get("distance").asDouble(), 1);
    }

    @Test
    public void testJsonBike() {
        String jsonQuery = "{" +
                " \"points\": [[11.58199, 50.0141], [11.5865, 50.0095]]," +
                " \"profile\": \"json_bike\"" +
                "}";
        final Response response = query(jsonQuery, 200);
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 660, 10);
    }

    @Test
    public void customBikeShouldBeLikeJsonBike() {
        String jsonQuery = "{" +
                " \"points\": [[11.58199, 50.0141], [11.5865, 50.0095]]," +
                " \"profile\": \"custom_bike\"" +
                "}";
        final Response response = query(jsonQuery, 200);
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 660, 10);
    }

    Response query(String body, int code) {
        Response response = clientTarget(app, "/route-custom").request().post(Entity.json(body));
        assertEquals(code, response.getStatus());
        return response;
    }

    private static String yamlToJson(String yaml) {
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