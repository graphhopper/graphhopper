package com.graphhopper.application.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.application.GraphHopperApplication;
import com.graphhopper.application.GraphHopperServerConfiguration;
import com.graphhopper.application.util.GraphHopperServerTestConfiguration;
import com.graphhopper.navigation.NavigateResource;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.graphhopper.application.util.TestUtils.clientTarget;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
public class NavigateResourceTest {

    // for this test we use a non-standard profile name
    private static final Map<String, String> mapboxResolver = new HashMap<>() {
        {
            put("driving", "my_car");
            put("driving-traffic", "my_car");
        }
    };

    private static final String DIR = "./target/andorra-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("profiles_mapbox", mapboxResolver).
                putObject("prepare.min_network_size", 0).
                putObject("datareader.file", "../core/files/andorra.osm.pbf").
                putObject("import.osm.ignored_highways", "").
                putObject("graph.location", DIR).
                putObject("graph.encoded_values", "road_class, surface, road_environment, max_speed, country, " +
                        "car_access, car_average_speed, " +
                        "foot_access, foot_priority, foot_average_speed").
                setProfiles(List.of(TestProfiles.accessAndSpeed("my_car", "car"),
                        TestProfiles.accessSpeedAndPriority("foot")));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testBasicNavigationQuery() {
        JsonNode json = clientTarget(app, "/navigate/directions/v5/gh/driving/1.537174,42.507145;1.539116,42.511368?" +
                "access_token=pk.my_api_key&alternatives=true&geometries=polyline6&overview=full&steps=true&continue_straight=true&" +
                "annotations=congestion%2Cdistance&language=en&roundabout_exits=true&voice_instructions=true&banner_instructions=true&voice_units=metric").
                request().get(JsonNode.class);
        assertEquals(1256, json.get("routes").get(0).get("distance").asDouble(), 20);

        json = clientTarget(app, "/navigate/directions/v5/gh/driving/1.540596,42.509443;1.538236,42.510071?" +
                "access_token=pk.my_api_key&alternatives=true&geometries=polyline6&overview=full&steps=true&continue_straight=true&" +
                "annotations=congestion%2Cdistance&language=en&roundabout_exits=true&voice_instructions=true&banner_instructions=true&voice_units=metric").
                request().get(JsonNode.class);
        assertTrue(json.toString().contains("\"type\":\"exit roundabout\""));

        json = clientTarget(app, "/navigate/directions/v5/gh/driving/1.540596,42.509443;1.538236,42.510071?" +
                "access_token=pk.my_api_key&alternatives=true&geometries=polyline6&overview=full&steps=true&continue_straight=true&" +
                "annotations=congestion%2Cdistance&language=en&roundabout_exits=false&voice_instructions=true&banner_instructions=true&voice_units=metric").
                request().get(JsonNode.class);
        assertFalse(json.toString().contains("\"type\":\"exit roundabout\""));
    }

    @Test
    public void voiceInstructionsTest() {
        List<Double> bearings = NavigateResource.getBearing("");
        assertEquals(0, bearings.size());
        assertEquals(Collections.EMPTY_LIST, bearings);

        bearings = NavigateResource.getBearing("100,1");
        assertEquals(1, bearings.size());
        assertEquals(100, bearings.get(0), .1);

        bearings = NavigateResource.getBearing(";100,1;;");
        assertEquals(4, bearings.size());
        assertEquals(100, bearings.get(1), .1);
    }

//    public static WebTarget clientTarget(DropwizardAppExtension<? extends Configuration> app, String path) {
//        String url = "http://localhost:" + app.getLocalPort() + path;
//        return app.client().target(url);
//    }
}
