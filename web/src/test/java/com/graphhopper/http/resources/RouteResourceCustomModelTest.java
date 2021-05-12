package com.graphhopper.http.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
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
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collections;

import static com.graphhopper.http.util.TestUtils.clientTarget;
import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
public class RouteResourceCustomModelTest {

    private static final String DIR = "./target/north-bayreuth-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "bike,car,foot").
                putObject("prepare.min_network_size", 200).
                putObject("datareader.file", "../core/files/north-bayreuth.osm.gz").
                putObject("graph.location", DIR).
                putObject("graph.encoded_values", "max_height,max_weight,max_width,hazmat,toll,surface,track_type").
                putObject("custom_model_folder", "./src/test/resources/com/graphhopper/http/resources").
                setProfiles(Arrays.asList(
                        new CustomProfile("car").setCustomModel(new CustomModel()).setVehicle("car"),
                        new CustomProfile("bike").setCustomModel(new CustomModel()).setVehicle("bike"),
                        new CustomProfile("truck").setVehicle("car").
                                putHint("custom_model_file", "truck.yml"),
                        new CustomProfile("cargo_bike").setVehicle("bike").
                                putHint("custom_model_file", "cargo_bike.yml"),
                        new CustomProfile("json_bike").setVehicle("bike").
                                putHint("custom_model_file", "json_bike.json"),
                        new Profile("foot_profile").setVehicle("foot").setWeighting("fastest"),
                        new CustomProfile("car_no_unclassified").setCustomModel(
                                new CustomModel(new CustomModel().
                                        addToPriority(If("road_class == UNCLASSIFIED", LIMIT, 0)))).
                                setVehicle("car"),
                        new CustomProfile("custom_bike").
                                setCustomModel(new CustomModel().
                                        addToSpeed(If("road_class == PRIMARY", LIMIT, 28)).
                                        addToPriority(If("max_width < 1.2", MULTIPLY, 0))).
                                setVehicle("bike"),
                        new CustomProfile("custom_bike2").setCustomModel(
                                new CustomModel(new CustomModel().
                                        addToPriority(If("road_class == TERTIARY || road_class == TRACK", MULTIPLY, 0)))).
                                setVehicle("bike"))).
                setCHProfiles(Arrays.asList(new CHProfile("truck"), new CHProfile("car_no_unclassified")));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testBlockAreaNotAllowed() {
        String body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"car\", \"custom_model\": {}, \"block_area\": \"abc\", \"ch.disable\": true}";
        JsonNode jsonNode = query(body, 400).readEntity(JsonNode.class);
        assertMessageStartsWith(jsonNode, "When using `custom_model` do not use `block_area`. Use `areas` in the custom model instead");
    }

    @Test
    public void testCHPossibleWithoutCustomModel() {
        // the truck profile is a custom profile and we can use its CH preparation as long as we do not add a custom model
        String body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"truck\"}";
        JsonNode json = query(body, 200).readEntity(JsonNode.class);
        JsonNode path = json.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 1500, 10);
        assertEquals(path.get("time").asLong(), 151_000, 1_000);
    }

    @Test
    public void testDisableCHAndUseCustomModel() {
        // If we specify a custom model we get an error, because it does not work with CH.
        String body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"truck\", \"custom_model\": {" +
                "\"speed\": [{\"if\": \"road_class == PRIMARY\", \"multiply_by\": 0.9}]" +
                "}}";
        JsonNode json = query(body, 400).readEntity(JsonNode.class);
        assertMessageStartsWith(json, "The 'custom_model' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`.");

        // ... even when the custom model is just an empty object
        body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"truck\", \"custom_model\": {}}";
        json = query(body, 400).readEntity(JsonNode.class);
        assertMessageStartsWith(json, "The 'custom_model' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`.");

        // ... but when we disable CH it works of course
        body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"truck\", \"custom_model\": {}, \"ch.disable\": true}";
        json = query(body, 200).readEntity(JsonNode.class);
        JsonNode path = json.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 1500, 10);
        assertEquals(path.get("time").asLong(), 151_000, 1_000);
    }

    @Test
    public void testMissingProfile() {
        String body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"custom_model\": {}, \"ch.disable\": true}";
        JsonNode jsonNode = query(body, 400).readEntity(JsonNode.class);
        assertMessageStartsWith(jsonNode, "The 'profile' parameter is required when you use the `custom_model` parameter");
    }

    @Test
    public void testUnknownProfile() {
        String body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"unknown\", \"custom_model\": {}, \"ch.disable\": true}";
        JsonNode jsonNode = query(body, 400).readEntity(JsonNode.class);
        assertMessageStartsWith(jsonNode, "The requested profile 'unknown' does not exist.\nAvailable profiles: [car, bike, truck, cargo_bike, json_bike, foot_profile, car_no_unclassified, custom_bike, custom_bike2]");
    }

    @Test
    public void testCustomWeightingRequired() {
        String body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"foot_profile\", \"custom_model\": {}, \"ch.disable\": true}";
        JsonNode jsonNode = query(body, 400).readEntity(JsonNode.class);
        assertEquals("The requested profile 'foot_profile' cannot be used with `custom_model`, because it has weighting=fastest", jsonNode.get("message").asText());
    }

    @Test
    public void testWeightingAndVehicleNotAllowed() {
        String body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"truck\"," +
                " \"custom_model\": {}, \"ch.disable\": true, \"vehicle\": \"truck\"}";
        JsonNode jsonNode = query(body, 400).readEntity(JsonNode.class);
        assertEquals("Since you are using the 'profile' parameter, do not use the 'vehicle' parameter. You used 'vehicle=truck'", jsonNode.get("message").asText());

        body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"truck\"," +
                " \"custom_model\": {}, \"ch.disable\": true, \"weighting\": \"custom\"}";
        jsonNode = query(body, 400).readEntity(JsonNode.class);
        assertEquals("Since you are using the 'profile' parameter, do not use the 'weighting' parameter. You used 'weighting=custom'", jsonNode.get("message").asText());
    }

    @ParameterizedTest
    @CsvSource(value = {"0.05,3073", "0.5,1498"})
    public void testAvoidArea(double priority, double expectedDistance) {
        String bodyFragment = "\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"car\", \"ch.disable\": true";
        JsonNode jsonNode = query("{" + bodyFragment + ", \"custom_model\": {}}", 200).readEntity(JsonNode.class);
        JsonNode path = jsonNode.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 661, 10);

        // 'blocking' the area either leads to a route that still crosses it (but on a faster road) or to a road
        // going all the way around it depending on the priority, see #2021
        String body = "{" + bodyFragment + ", \"custom_model\": {" +
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
                "}}" +
                "}";
        jsonNode = query(body, 200).readEntity(JsonNode.class);
        path = jsonNode.get("paths").get(0);
        assertEquals(expectedDistance, path.get("distance").asDouble(), 10);
    }

    @Test
    public void testCargoBike() throws IOException {
        String body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"bike\", \"custom_model\": {}, \"ch.disable\": true}";
        JsonNode jsonNode = query(body, 200).readEntity(JsonNode.class);
        JsonNode path = jsonNode.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 661, 5);

        String jsonFromYamlFile = yamlToJson(Helper.isToString(getClass().getResourceAsStream("cargo_bike.yml")));
        body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"bike\", \"custom_model\":" + jsonFromYamlFile + ", \"ch.disable\": true}";
        jsonNode = query(body, 200).readEntity(JsonNode.class);
        path = jsonNode.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 1007, 5);

        // results should be identical be it via server-side profile or query profile:
        body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"cargo_bike\", \"custom_model\": {}, \"ch.disable\": true}";
        jsonNode = query(body, 200).readEntity(JsonNode.class);
        JsonNode path2 = jsonNode.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), path2.get("distance").asDouble(), 1);
    }

    @Test
    public void testJsonBike() {
        String jsonQuery = "{" +
                " \"points\": [[11.58199, 50.0141], [11.5865, 50.0095]]," +
                " \"profile\": \"json_bike\"," +
                " \"custom_model\": {}," +
                " \"ch.disable\": true" +
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
                " \"profile\": \"custom_bike\"," +
                " \"custom_model\": {}," +
                " \"ch.disable\": true" +
                "}";
        final Response response = query(jsonQuery, 200);
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 660, 10);
    }

    @Test
    public void testSubnetworkRemovalPerProfile() {
        // none-CH
        String body = "{\"points\": [[11.556416,50.007739], [11.528864,50.021638]]," +
                " \"profile\": \"car_no_unclassified\"," +
                " \"ch.disable\": true" +
                "}";
        JsonNode jsonNode = query(body, 200).readEntity(JsonNode.class);
        JsonNode path = jsonNode.get("paths").get(0);
        assertEquals(8754, path.get("distance").asDouble(), 5);

        // CH
        body = "{\"points\": [[11.556416,50.007739], [11.528864,50.021638]]," +
                " \"profile\": \"car_no_unclassified\"" +
                "}";
        jsonNode = query(body, 200).readEntity(JsonNode.class);
        path = jsonNode.get("paths").get(0);
        assertEquals(8754, path.get("distance").asDouble(), 5);

        // different profile
        body = "{\"points\": [[11.494446, 50.027814], [11.511483, 49.987628]]," +
                " \"profile\": \"custom_bike2\"," +
                " \"ch.disable\": true" +
                "}";
        jsonNode = query(body, 200).readEntity(JsonNode.class);
        path = jsonNode.get("paths").get(0);
        assertEquals(5370, path.get("distance").asDouble(), 5);
    }

    private void assertMessageStartsWith(JsonNode jsonNode, String message) {
        assertNotNull(jsonNode.get("message"));
        assertTrue(jsonNode.get("message").asText().startsWith(message), "Expected error message to start with:\n" +
                message + "\nbut got:\n" + jsonNode.get("message").asText());
    }

    Response query(String body, int code) {
        Response response = clientTarget(app, "/route").request().post(Entity.json(body));
        response.bufferEntity();
        JsonNode jsonNode = response.readEntity(JsonNode.class);
        assertEquals(code, response.getStatus(), jsonNode.has("message") ? jsonNode.get("message").toString() : "no error message");
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