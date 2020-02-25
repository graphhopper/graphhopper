package com.graphhopper.http.resources;

import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.io.File;
import static java.lang.String.format;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class I18nResourceTest {
    private static final String DIR = "./target/andorra-gh/";

    private static final GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();

    static {
        config.getGraphHopperConfiguration().
                put("graph.flag_encoders", "car").
                put("datareader.file", "../core/files/andorra.osm.pbf").
                put("graph.location", DIR);
    }

    @ClassRule
    public static final DropwizardAppRule<GraphHopperServerConfiguration> app = new DropwizardAppRule(
            GraphHopperApplication.class, config);

    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void requestI18n() {
        Response response = app.client().target(format("http://localhost:%s/i18n", app.getLocalPort())).request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        String str = response.readEntity(String.class);
        assertTrue(str, str.contains("\"en\":") && str.contains("\"locale\":\"\""));

        response = app.client().target(format("http://localhost:%s/i18n/de", app.getLocalPort())).request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        str = response.readEntity(String.class);
        assertTrue(str, str.contains("\"default\":") && str.contains("\"locale\":\"de\""));
    }
}
