package com.graphhopper.http.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.io.File;

import static org.junit.Assert.*;

public class FlexResourceCHTest {

    private static final String DIR = "./target/andorra-gh/";
    private static final GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();

    static {
        config.getGraphHopperConfiguration().merge(new CmdArgs().
                put("graph.flag_encoders", "car").
                put("graph.flex_model.location", "./src/test/resources/com/graphhopper/http/").
                put("prepare.min_network_size", "0").
                put("prepare.min_one_way_network_size", "0").
                put("datareader.file", "../core/files/andorra.osm.pbf").
                put("graph.location", DIR));
    }

    @ClassRule
    public static final DropwizardAppRule<GraphHopperServerConfiguration> app = new DropwizardAppRule<>(GraphHopperApplication.class, config);

    @BeforeClass
    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testBasicQuery() {
        String jsonQuery = "{" +
                " \"points\": [[1.518946,42.531453],[1.54006,42.511178]]," +
                // TODO NOW this is ugly, but only the base FlagEncoder car exists:
                " \"vehicle\": \"car\"," +
                " \"weighting\": \"flex|truck\"" +
                "}";
        final Response response = app.client().target("http://localhost:8080/route").request().post(Entity.json(jsonQuery));
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        assertBetween("distance wasn't correct", path.get("distance").asDouble(), 3100, 3300);
        assertBetween("time wasn't correct", path.get("time").asLong() / 1000.0, 230, 280);
    }

    static void assertBetween(String msg, double val, double from, double to) {
        assertTrue(msg + " :" + val, val > from);
        assertTrue(msg + " :" + val, val < to);
    }
}