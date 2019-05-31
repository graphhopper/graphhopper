package com.graphhopper.http.isochrone;

import com.graphhopper.directions.api.client.ApiClient;
import com.graphhopper.directions.api.client.api.IsochroneApi;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SPTResourceTest {
    private static final String DIR = "./target/spt-gh/";

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
    public void requestSPT() {
        Response rsp = app.client().target("http://localhost:8080/spt?point=42.531073,1.573792&time_limit=300").request().buildGet().invoke();
        String rspCsvString = rsp.readEntity(String.class);
        String[] lines = rspCsvString.split("\n");
        assertTrue(lines.length > 500);
        List<String> headers = Arrays.asList(lines[0].split(","));
        assertEquals("[longitude, latitude, time, distance]", headers.toString());
        String[] row = lines[1].split(",");
        assertEquals(1.5552, Double.parseDouble(row[0]), 0.0001);
        assertEquals(42.5179, Double.parseDouble(row[1]), 0.0001);
        assertEquals(118, Integer.parseInt(row[2]) / 1000, 1);
        assertEquals(2263, Integer.parseInt(row[3]), 1);

        rsp = app.client().target("http://localhost:8080/spt?point=42.531073,1.573792&columns=prev_time").request().buildGet().invoke();
        rspCsvString = rsp.readEntity(String.class);
        lines = rspCsvString.split("\n");
        assertTrue(lines.length > 500);
        headers = Arrays.asList(lines[0].split(","));
        int prevTimeIndex = headers.indexOf("prev_time");
        assertNotEquals(-1, prevTimeIndex);

        row = lines[1].split(",");
        assertEquals(115, Integer.parseInt(row[prevTimeIndex]) / 1000);
    }
}