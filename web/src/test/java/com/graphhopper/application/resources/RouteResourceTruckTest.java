package com.graphhopper.application.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.application.GraphHopperApplication;
import com.graphhopper.application.GraphHopperServerConfiguration;
import com.graphhopper.application.util.GraphHopperServerTestConfiguration;
import com.graphhopper.config.CHProfile;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Arrays;

import static com.graphhopper.application.util.TestUtils.clientTarget;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
public class RouteResourceTruckTest {
    private static final String DIR = "./target/north-bayreuth-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.vehicles", "roads|transportation_mode=HGV,car").
                putObject("prepare.min_network_size", 200).
                putObject("datareader.file", "../core/files/north-bayreuth.osm.gz").
                putObject("graph.location", DIR).
                putObject("graph.encoded_values", "max_height,max_weight,max_width,hazmat,toll,surface,hgv").
                putObject("import.osm.ignored_highways", "").
                putObject("custom_model_folder", "./src/test/resources/com/graphhopper/application/resources").
                setProfiles(Arrays.asList(new CustomProfile("truck").setVehicle("roads").putHint("custom_model_file", "truck.json"))).
                setCHProfiles(Arrays.asList(new CHProfile("truck")));
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
        JsonNode json = query(body, 400).readEntity(JsonNode.class);
        assertMessageStartsWith(json, "The 'custom_model' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`.");

        // ... even when the custom model is just an empty object
        body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"truck\", \"custom_model\": {}}";
        json = query(body, 400).readEntity(JsonNode.class);
        assertMessageStartsWith(json, "The 'custom_model' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`.");

        // ... but when we disable CH it works
        body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"truck\", \"custom_model\": {}, \"ch.disable\": true}";
        JsonNode path = query(body, 200).readEntity(JsonNode.class).get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 1008, 10);
        assertEquals(path.get("time").asLong(), 49_000, 1_000);
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
}
