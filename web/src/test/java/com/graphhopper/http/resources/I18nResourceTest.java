package com.graphhopper.http.resources;

import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.util.GraphHopperServerTestConfiguration;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.io.File;

import static com.graphhopper.http.util.TestUtils.clientTarget;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class I18nResourceTest {
    private static final String DIR = "./target/andorra-gh/";

    private static final GraphHopperServerTestConfiguration config = new GraphHopperServerTestConfiguration();

    static {
        config.getGraphHopperConfiguration().
                put("graph.flag_encoders", "car").
                put("datareader.file", "../core/files/andorra.osm.pbf").
                put("graph.location", DIR);
    }

    @ClassRule
    public static final DropwizardAppRule<GraphHopperServerTestConfiguration> app = new DropwizardAppRule(
            GraphHopperApplication.class, config);

    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void requestI18n() {
        Response response = clientTarget(app, "/i18n").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        String str = response.readEntity(String.class);
        assertTrue(str, str.contains("\"en\":") && str.contains("\"locale\":\"\""));

        response = clientTarget(app, "/i18n/de").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        str = response.readEntity(String.class);
        assertTrue(str, str.contains("\"default\":") && str.contains("\"locale\":\"de\""));
    }
}
