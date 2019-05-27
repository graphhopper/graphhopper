package com.graphhopper.http.isochrone;

import com.graphhopper.directions.api.client.ApiClient;
import com.graphhopper.directions.api.client.api.IsochroneApi;
import com.graphhopper.directions.api.client.model.IsochroneResponse;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.Polygon;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.io.File;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.assertFalse;
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
    public void requestPointList() {
        Response rsp = app.client().target("http://localhost:8080/spt?point=42.531073,1.573792&time_limit=300&result=pointlist").request().buildGet().invoke();
        Map map = rsp.readEntity(Map.class);
        List<String> header = (List<String>) map.get("header");
        assertEquals("[longitude, latitude, time, distance]", header.toString());
        List<List> items = (List<List>) map.get("items");
        List row = items.get(0);
        assertEquals(1.5552, ((Number) row.get(0)).doubleValue(), 0.0001);
        assertEquals(42.5179, ((Number) row.get(1)).doubleValue(), 0.0001);
        assertEquals(118, ((Number) row.get(2)).intValue(), 1);
        assertEquals(2263, ((Number) row.get(3)).intValue(), 1);

        rsp = app.client().target("http://localhost:8080/spt?point=42.531073,1.573792&time_limit=300&result=pointlist&pointlist_ext_header=prev_time").request().buildGet().invoke();
        map = rsp.readEntity(Map.class);
        header = (List<String>) map.get("header");
        int prevTimeIndex = header.indexOf("prev_time");
        assertNotEquals(-1, prevTimeIndex);
        items = (List) map.get("items");
        assertEquals(115, ((Number) items.get(0).get(prevTimeIndex)).intValue(), 1);
    }
}