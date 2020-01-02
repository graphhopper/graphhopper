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
import javax.ws.rs.core.MediaType;
import java.io.File;

import static com.graphhopper.http.resources.FlexResourceTest.assertBetween;

public class FlexResource2Test {

    private static final String DIR = "./target/north-bayreuth-gh/";
    private static final GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();

    static {
        config.getGraphHopperConfiguration().merge(new CmdArgs().
                put("graph.flag_encoders", "bike").
                put("prepare.ch.weightings", "no").
                put("prepare.min_network_size", "0").
                put("prepare.min_one_way_network_size", "0").
                // TODO NOW should we throw an exception if we did forget to include this?
                put("graph.encoded_values", "max_height").
                put("datareader.file", "../core/files/north-bayreuth.osm.gz").
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
    public void testCargoBike() {
        String yamlQuery = "points: [[11.58199, 50.0141], [11.5865, 50.0095]]\n" +
                "model:\n" +
                "  base: bike\n";
        JsonNode yamlNode = app.client().target("http://localhost:8080/flex").request().post(Entity.entity(yamlQuery,
                new MediaType("application", "yaml"))).readEntity(JsonNode.class);
        JsonNode path = yamlNode.get("paths").get(0);
        assertBetween("distance wasn't correct", path.get("distance").asDouble(), 600, 700);

        yamlQuery = "points: [[11.58199, 50.0141], [11.5865, 50.0095]]\n" +
                "model:\n" +
                // default is 0.1
                "  min_priority: 0\n" +
                "  base: bike\n" +
                // only one tunnel is mapped in this osm file with max_height=1.7 => https://www.openstreetmap.org/way/132908255
                "  height: 2\n";
        yamlNode = app.client().target("http://localhost:8080/flex").request().post(Entity.entity(yamlQuery,
                new MediaType("application", "yaml"))).readEntity(JsonNode.class);
        path = yamlNode.get("paths").get(0);
        assertBetween("distance wasn't correct", path.get("distance").asDouble(), 1000, 2000);
    }
}