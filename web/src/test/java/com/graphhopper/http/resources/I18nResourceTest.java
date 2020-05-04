package com.graphhopper.http.resources;

import com.graphhopper.config.Profile;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.http.util.GraphHopperServerTestConfiguration;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Collections;

import static com.graphhopper.http.util.TestUtils.clientTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(DropwizardExtensionsSupport.class)
public class I18nResourceTest {
    private static final String DIR = "./target/andorra-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "car").
                putObject("datareader.file", "../core/files/andorra.osm.pbf").
                putObject("graph.location", DIR).
                setProfiles(Collections.singletonList(new Profile("car").setVehicle("car").setWeighting("fastest")));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void requestI18n() {
        Response response = clientTarget(app, "/i18n").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        String str = response.readEntity(String.class);
        assertTrue(str.contains("\"en\":") && str.contains("\"locale\":\"\""), str);

        response = clientTarget(app, "/i18n/de").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        str = response.readEntity(String.class);
        assertTrue(str.contains("\"default\":") && str.contains("\"locale\":\"de\""), str);
    }
}
