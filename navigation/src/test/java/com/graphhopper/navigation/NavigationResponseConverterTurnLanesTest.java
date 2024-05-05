package com.graphhopper.navigation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeCreator;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import com.graphhopper.util.TranslationMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

class NavigationResponseConverterTurnLanesTest {

    private static final String graphFolder = "target/graphhopper-turn-lanes-test-car";
    private static final String osmFile = "../core/files/bautzen.osm";
    private static GraphHopper hopper;
    private static final String profile = "my_car";

    private final TranslationMap trMap = hopper.getTranslationMap();
    private final DistanceConfig distanceConfig = new DistanceConfig(DistanceUtils.Unit.METRIC, trMap, Locale.ENGLISH);

    @BeforeAll
    public static void beforeClass() {
        // make sure we are using fresh files with correct vehicle
        Helper.removeDir(new File(graphFolder));
        hopper = new GraphHopper().setStoreOnFlush(true);
        hopper = hopper.init(new GraphHopperConfig()
                .putObject("graph.location", graphFolder)
                .putObject("datareader.file", osmFile)
                .putObject("import.osm.ignored_highways", "footway,cycleway,path,pedestrian,steps")
                .putObject("datareader.turn_lanes_profiles", profile)
                .setProfiles(List.of(TestProfiles.accessAndSpeed(profile, "car")))
        ).importOrLoad();
    }

    @AfterAll
    public static void afterClass() {
        Helper.removeDir(new File(graphFolder));
    }

    @Test
    public void intersectionsTest() {
        GHResponse rsp = hopper.route(new GHRequest(51.186861,14.412755,51.18958,14.41242).
        setProfile(profile).setPathDetails(Arrays.asList("intersection")));

        ObjectNode json = NavigateResponseConverter.convertFromGHResponse(rsp, trMap, Locale.ENGLISH, distanceConfig);
        JsonNode lanes = json.get("routes").get(0).get("legs").get(0).get("steps")
                .get(1).get("intersections").get(0).get("lanes");
        
        assertEquals(2, lanes.size());

        JsonNode firstLane = lanes.get(0);
        assertEquals(1, firstLane.get("indications").size());
        assertEquals("left", firstLane.get("indications").get(0).asText());
        assertEquals(true, firstLane.get("valid").asBoolean());
        assertEquals(true, firstLane.get("active").asBoolean());
        assertEquals("left", firstLane.get("active_indication").asText());

        JsonNode secondLane = lanes.get(1);
        assertEquals(1, secondLane.get("indications").size());
        assertEquals("straight", secondLane.get("indications").get(0).asText());
        assertEquals(false, secondLane.get("valid").asBoolean());
        assertEquals(false, secondLane.get("active").asBoolean());
    }

    @Test
    public void bannerInstructionsTest() {
        GHResponse rsp = hopper.route(new GHRequest(51.186861,14.412755,51.18958,14.41242).
        setProfile(profile).setPathDetails(Arrays.asList("intersection")));

        ObjectNode json = NavigateResponseConverter.convertFromGHResponse(rsp, trMap, Locale.ENGLISH, distanceConfig);
        JsonNode bannerInstructions = json.get("routes").get(0).get("legs").get(0).get("steps")
                .get(0).get("bannerInstructions");
 
        assertEquals(2, bannerInstructions.size());

        JsonNode firstBannerInstruction = bannerInstructions.get(0);

        assertEquals("Turn left and take A 4 toward Dresden", firstBannerInstruction.get("primary").get("text").asText());
        assertEquals("left", firstBannerInstruction.get("primary").get("modifier").asText());
        assertEquals("left", firstBannerInstruction.get("primary").get("active_direction").asText());
        assertEquals("turn", firstBannerInstruction.get("primary").get("type").asText());
        assertEquals("[{\"text\":\"Turn left and take A 4 toward Dresden\",\"type\":\"text\"}]", firstBannerInstruction.get("primary").get("components").toString());
        assertNull(firstBannerInstruction.get("sub"));
        assertEquals(597, firstBannerInstruction.get("distanceAlongGeometry").asDouble(), 0.001);

        JsonNode secondBannerInstruction = bannerInstructions.get(1);
        assertEquals("Turn left and take A 4 toward Dresden", secondBannerInstruction.get("primary").get("text").asText());
        assertEquals("left", secondBannerInstruction.get("primary").get("modifier").asText());
        assertEquals("turn", secondBannerInstruction.get("primary").get("type").asText());
        assertEquals("[{\"text\":\"Turn left and take A 4 toward Dresden\",\"type\":\"text\"}]", secondBannerInstruction.get("primary").get("components").toString());
        assertEquals(161.593, secondBannerInstruction.get("distanceAlongGeometry").asDouble(), 0.001);

        assertEquals("", secondBannerInstruction.get("sub").get("text").asText());
        assertEquals("[{\"text\":\"\",\"type\":\"lane\",\"active\":true,\"directions\":[\"left\"]},{\"text\":\"\",\"type\":\"lane\",\"active\":false,\"directions\":[\"straight\"]}]", 
            secondBannerInstruction.get("sub").get("components").toString());
    }
}