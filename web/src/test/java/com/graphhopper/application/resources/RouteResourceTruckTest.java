package com.graphhopper.application.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.application.GraphHopperApplication;
import com.graphhopper.application.GraphHopperServerConfiguration;
import com.graphhopper.application.util.GraphHopperServerTestConfiguration;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.util.BodyAndStatus;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.util.List;

import static com.graphhopper.application.util.TestUtils.clientTarget;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
public class RouteResourceTruckTest {
    private static final String DIR = "./target/north-bayreuth-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("prepare.min_network_size", 200).
                putObject("datareader.file", "../core/files/north-bayreuth.osm.gz").
                putObject("graph.location", DIR).
                putObject("graph.encoded_values", "max_height,max_weight,max_width,hazmat,toll,surface,hgv").
                putObject("import.osm.ignored_highways", "").
                putObject("custom_models.directory", "./src/test/resources/com/graphhopper/application/resources").
                putObject("graph.encoded_values", "max_height, max_weight, max_width, hazmat, toll, surface, hgv, road_class, road_access, road_class_link, road_environment\n").
                setProfiles(List.of(new Profile("truck").setCustomModel(null).
                        putHint("custom_model_files", List.of("test_truck.json")))).
                setCHProfiles(List.of(new CHProfile("truck")));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testDisableCHAndUseCustomModel() {
        // If we specify a custom model we get an error, because it does not work with CH.
        String body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"truck\", \"custom_model\": {" +
                "\"speed\": [{\"if\": \"road_class == PRIMARY\", \"multiply_by\": 0.9}]" +
                "}}";
        JsonNode json = query(body, 400);
        assertMessageStartsWith(json, "The 'custom_model' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`.");

        // ... even when the custom model is just an empty object
        body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"truck\", \"custom_model\": {}}";
        json = query(body, 400);
        assertMessageStartsWith(json, "The 'custom_model' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`.");

        // ... but when we disable CH it works
        body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"truck\", \"custom_model\": {}, \"ch.disable\": true}";
        JsonNode path = query(body, 200).get("paths").get(0);
        assertEquals(1_500, path.get("distance").asDouble(), 10);
        assertEquals(54_000, path.get("time").asLong(), 1_000);
    }

    private void assertMessageStartsWith(JsonNode jsonNode, String message) {
        assertNotNull(jsonNode.get("message"));
        assertTrue(jsonNode.get("message").asText().startsWith(message), "Expected error message to start with:\n" +
                message + "\nbut got:\n" + jsonNode.get("message").asText());
    }

    JsonNode query(String body, int code) {
        BodyAndStatus response = Util.postWithStatus(clientTarget(app, "/route"), body);
        JsonNode jsonNode = response.getBody();
        assertEquals(code, response.getStatus(), jsonNode.has("message") ? jsonNode.get("message").toString() : "no error message");
        return jsonNode;
    }
}
