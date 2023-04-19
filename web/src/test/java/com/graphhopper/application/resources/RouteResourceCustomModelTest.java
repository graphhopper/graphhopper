/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.application.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.application.GraphHopperApplication;
import com.graphhopper.application.GraphHopperServerConfiguration;
import com.graphhopper.application.util.GraphHopperServerTestConfiguration;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static com.graphhopper.application.util.TestUtils.clientTarget;
import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
public class RouteResourceCustomModelTest {

    private static final String DIR = "./target/north-bayreuth-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.vehicles", "bike,car,foot,wheelchair,roads").
                putObject("prepare.min_network_size", 200).
                putObject("datareader.file", "../core/files/north-bayreuth.osm.gz").
                putObject("graph.location", DIR).
                putObject("graph.encoded_values", "max_height,max_weight,max_width,hazmat,toll,surface,track_type,hgv,average_slope,max_slope").
                putObject("custom_models.directory", "../custom_models/").
                putObject("custom_areas.directory", "./src/test/resources/com/graphhopper/application/resources/areas").
                putObject("import.osm.ignored_highways", "").
                setProfiles(Arrays.asList(
                        new Profile("wheelchair"),
                        new CustomProfile("roads").setCustomModel(new CustomModel()).setVehicle("roads"),
                        new CustomProfile("car").setCustomModel(new CustomModel().setDistanceInfluence(70d)).setVehicle("car"),
                        new CustomProfile("car_with_area").setCustomModel(new CustomModel().addToPriority(If("in_external_area52", MULTIPLY, "0.05"))),
                        new CustomProfile("bike").setCustomModel(new CustomModel().setDistanceInfluence(0d)).setVehicle("bike"),
                        new Profile("bike_fastest").setWeighting("fastest").setVehicle("bike"),
                        new CustomProfile("bus").setVehicle("roads").putHint("custom_model_files", Arrays.asList("bus.json")),
                        new CustomProfile("cargo_bike").setVehicle("bike").
                                putHint("custom_model_files", Arrays.asList("cargo_bike.json")),
                        new CustomProfile("json_bike").setVehicle("roads").
                                putHint("custom_model_files", Arrays.asList("bike.json", "bike_elevation.json")),
                        new Profile("foot_profile").setVehicle("foot").setWeighting("fastest"),
                        new CustomProfile("car_no_unclassified").setCustomModel(
                                        new CustomModel(new CustomModel().
                                                addToPriority(If("road_class == UNCLASSIFIED", LIMIT, "0")))).
                                setVehicle("car"),
                        new CustomProfile("custom_bike").
                                setCustomModel(new CustomModel().
                                        addToSpeed(If("road_class == PRIMARY", LIMIT, "28")).
                                        addToPriority(If("max_width < 1.2", MULTIPLY, "0"))).
                                setVehicle("bike"),
                        new CustomProfile("custom_bike2").setCustomModel(
                                        new CustomModel(new CustomModel().setDistanceInfluence(70d).
                                                addToPriority(If("road_class == TERTIARY || road_class == TRACK", MULTIPLY, "0")))).
                                setVehicle("bike"),
                        new CustomProfile("custom_bike3").setCustomModel(
                                        new CustomModel(new CustomModel().
                                                addToSpeed(If("road_class == TERTIARY || road_class == TRACK", MULTIPLY, "10")).
                                                addToSpeed(If("true", LIMIT, "40")))).
                                setVehicle("bike"))).
                setCHProfiles(Arrays.asList(new CHProfile("bus"), new CHProfile("car_no_unclassified")));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testBlockAreaNotAllowed() {
        String body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"car\", \"block_area\": \"abc\", \"ch.disable\": true}";
        JsonNode jsonNode = query(body, 400).readEntity(JsonNode.class);
        assertMessageStartsWith(jsonNode, "The `block_area` parameter is no longer supported. Use a custom model with `areas` instead.");
    }

    @Test
    public void testBus() {
        // the bus profile is a custom profile and we can use its CH preparation as long as we do not add a custom model
        String body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"bus\"}";
        JsonNode path = getPath(body);
        assertEquals(path.get("distance").asDouble(), 1500, 10);
        assertEquals(path.get("time").asLong(), 168_000, 1_000);
    }

    @Test
    public void testRoadsFlagEncoder() {
        String body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"roads\", \"ch.disable\": true, " +
                "\"custom_model\": {" +
                "  \"speed\": [{\"if\": \"road_class == PRIMARY\", \"multiply_by\": 0.9}, " +
                "            {\"if\": \"true\", \"limit_to\": 120}" +
                "           ]" +
                "}}";
        JsonNode path = getPath(body);
        assertEquals(path.get("distance").asDouble(), 660, 10);
        assertEquals(path.get("time").asLong(), 20_000, 1_000);
    }

    @Test
    public void testMissingProfile() {
        String body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"custom_model\": {}, \"ch.disable\": true}";
        JsonNode jsonNode = query(body, 400).readEntity(JsonNode.class);
        assertMessageStartsWith(jsonNode, "The 'profile' parameter is required when you use the `custom_model` parameter");
    }

    @Test
    public void testUnknownProfile() {
        String body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"unknown\", \"custom_model\": {}, \"ch.disable\": true}";
        JsonNode jsonNode = query(body, 400).readEntity(JsonNode.class);
        assertMessageStartsWith(jsonNode, "The requested profile 'unknown' does not exist.\nAvailable profiles: ");
    }

    @Test
    public void testCustomWeightingRequired() {
        String body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"foot_profile\", \"custom_model\": {}, \"ch.disable\": true}";
        JsonNode jsonNode = query(body, 400).readEntity(JsonNode.class);
        assertEquals("The requested profile 'foot_profile' cannot be used with `custom_model`, because it has weighting=fastest", jsonNode.get("message").asText());
    }

    @Test
    public void testVehicleAndWeightingNotAllowed() {
        String body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"truck\",\"custom_model\": {}, \"ch.disable\": true, \"vehicle\": \"truck\"}";
        JsonNode jsonNode = query(body, 400).readEntity(JsonNode.class);
        assertEquals("The 'vehicle' parameter is no longer supported. You used 'vehicle=truck'", jsonNode.get("message").asText());

        body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"truck\",\"custom_model\": {}, \"ch.disable\": true, \"weighting\": \"custom\"}";
        jsonNode = query(body, 400).readEntity(JsonNode.class);
        assertEquals("The 'weighting' parameter is no longer supported. You used 'weighting=custom'", jsonNode.get("message").asText());
    }

    @ParameterizedTest
    @CsvSource(value = {"0.05,3073", "0.5,1498"})
    public void testAvoidArea(double priority, double expectedDistance) {
        String bodyFragment = "\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"car\", \"ch.disable\": true";
        JsonNode path = getPath("{" + bodyFragment + ", \"custom_model\": {}}");
        assertEquals(path.get("distance").asDouble(), 661, 10);

        // 'blocking' the area either leads to a route that still crosses it (but on a faster road) or to a road
        // going all the way around it depending on the priority, see #2021
        String body = "{" + bodyFragment + ", \"custom_model\": {" +
                "\"priority\":[{" +
                // a faster road (see #2021)? or maybe do both?
                "   \"if\": \"in_custom1\"," +
                "   \"multiply_by\": " + priority +
                "}], " +
                "\"areas\":{" +
                "  \"custom1\":{" +
                "    \"type\": \"Feature\"," +
                "    \"geometry\": { \"type\": \"Polygon\", \"coordinates\": [[[11.5818,50.0126], [11.5818,50.0119], [11.5861,50.0119], [11.5861,50.0126], [11.5818,50.0126]]] }" +
                "   }" +
                "}}" +
                "}";
        path = getPath(body);
        assertEquals(expectedDistance, path.get("distance").asDouble(), 10);
    }

    @Test
    public void testAvoidArea() {
        JsonNode path = getPath("{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"car_with_area\", \"ch.disable\": true, \"custom_model\": {}}");
        assertEquals(path.get("distance").asDouble(), 3073, 10);
    }

    @Test
    public void testCargoBike() throws IOException {
        String body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"bike\", \"custom_model\": {}, \"ch.disable\": true}";
        JsonNode path = getPath(body);
        assertEquals(path.get("distance").asDouble(), 661, 5);

        String json = Helper.readJSONFileWithoutComments(new File("../custom_models/cargo_bike.json").getAbsolutePath());
        body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"bike\", \"custom_model\":" + json + ", \"ch.disable\": true}";
        path = getPath(body);
        assertEquals(path.get("distance").asDouble(), 1007, 5);

        // results should be identical be it via server-side profile or query profile:
        body = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]], \"profile\": \"cargo_bike\", \"custom_model\": {}, \"ch.disable\": true}";
        JsonNode path2 = getPath(body);
        assertEquals(path.get("distance").asDouble(), path2.get("distance").asDouble(), 1);
    }

    @Test
    public void testJsonBike() {
        String jsonQuery = "{\"points\": [[11.58199, 50.0141], [11.5865, 50.0095]]," +
                " \"profile\": \"json_bike\", \"ch.disable\": true }";
        JsonNode path = getPath(jsonQuery);
        assertEquals(660, path.get("distance").asDouble(), 10);

        // check reverse oneway is allowed for short distances
        jsonQuery = "{\"points\": [[11.545768,50.020137], [11.545728,50.020115]]," +
                " \"profile\": \"json_bike\", \"ch.disable\": true }";
        path = getPath(jsonQuery);
        assertEquals(4, path.get("distance").asDouble(), 1);
    }

    @Test
    public void testBikeWithPriority() {
        String coords = " \"points\": [[11.539421, 50.018274], [11.593966, 50.007739]],";
        String jsonQuery = "{" +
                coords +
                " \"profile\": \"bike_fastest\"," +
                " \"ch.disable\": true" +
                "}";
        JsonNode path = getPath(jsonQuery);
        double expectedDistance = path.get("distance").asDouble();
        assertEquals(4781, expectedDistance, 10);

        // base profile bike has to use distance_influence = 0 (unlike default) otherwise not comparable to "fastest"
        jsonQuery = "{" +
                coords +
                " \"profile\": \"bike\"," +
                " \"ch.disable\": true" +
                "}";
        path = getPath(jsonQuery);
        assertEquals(4781, path.get("distance").asDouble(), 10);
    }

    @Test
    public void customBikeShouldBeLikeJsonBike() {
        String jsonQuery = "{" +
                " \"points\": [[11.58199, 50.0141], [11.5865, 50.0095]]," +
                " \"profile\": \"custom_bike\"," +
                " \"custom_model\": {}," +
                " \"ch.disable\": true" +
                "}";
        JsonNode path = getPath(jsonQuery);
        assertEquals(path.get("distance").asDouble(), 660, 10);
    }

    @Test
    public void testSubnetworkRemovalPerProfile() {
        // none-CH
        String body = "{\"points\": [[11.556416,50.007739], [11.528864,50.021638]]," +
                " \"profile\": \"car_no_unclassified\"," +
                " \"ch.disable\": true" +
                "}";
        JsonNode path = getPath(body);
        assertEquals(8754, path.get("distance").asDouble(), 5);

        // CH
        body = "{\"points\": [[11.556416,50.007739], [11.528864,50.021638]]," +
                " \"profile\": \"car_no_unclassified\"" +
                "}";
        path = getPath(body);
        assertEquals(8754, path.get("distance").asDouble(), 5);

        // different profile
        body = "{\"points\": [[11.494446, 50.027814], [11.511483, 49.987628]]," +
                " \"profile\": \"custom_bike2\"," +
                " \"ch.disable\": true" +
                "}";
        path = getPath(body);
        assertEquals(5827, path.get("distance").asDouble(), 5);
    }

    @Test
    public void customBikeWithHighSpeed() {
        // encoder.getMaxSpeed is 30km/h and limit_to statement is 40km/h in server-side custom model
        // since #2335 do no longer throw exception in case too high limit_to is used
        String jsonQuery = "{" +
                " \"points\": [[11.58199, 50.0141], [11.5865, 50.0095]]," +
                " \"profile\": \"custom_bike3\"," +
                " \"custom_model\": { " +
                "   \"speed\": [{" +
                "       \"if\": \"true\", \"limit_to\": 55" +
                "     }] " +
                " }," +
                " \"ch.disable\": true" +
                "}";

        JsonNode path = getPath(jsonQuery);
        assertEquals(660, path.get("distance").asDouble(), 10);
        assertEquals(69, path.get("time").asLong() / 1000, 1);

        // now limit_to increases time
        jsonQuery = "{" +
                " \"points\": [[11.58199, 50.0141], [11.5865, 50.0095]]," +
                " \"profile\": \"custom_bike3\"," +
                " \"custom_model\": { " +
                "   \"speed\": [{" +
                "      \"if\": \"true\", \"limit_to\": 35" +
                "   }] " +
                " }," +
                " \"ch.disable\": true" +
                "}";
        path = getPath(jsonQuery);
        assertEquals(660, path.get("distance").asDouble(), 10);
        assertEquals(77, path.get("time").asLong() / 1000, 1);
    }

    @Test
    public void wheelchair() {
        String jsonQuery = "{" +
                " \"points\": [[11.58199, 50.0141], [11.5865, 50.0095]]," +
                " \"profile\": \"wheelchair\"," +
                " \"ch.disable\": true" +
                "}";
        JsonNode path = getPath(jsonQuery);
        assertEquals(1500, path.get("distance").asDouble(), 10);
    }

    @Test
    public void testHgv() {
        String body = "{\"points\": [[11.603998, 50.014554], [11.594095, 50.023334]], \"profile\": \"roads\", \"ch.disable\":true," +
                "\"custom_model\": {" +
                "   \"speed\": [{\"if\":\"true\", \"limit_to\":\"car_average_speed * 0.9\"}], \n" +
                "   \"priority\": [{\"if\": \"car_access == false || hgv == NO || max_width < 3 || max_height < 4\", \"multiply_by\": \"0\"}]}}";
        JsonNode path = getPath(body);
        assertEquals(7314, path.get("distance").asDouble(), 10);
        assertEquals(957 * 1000, path.get("time").asLong(), 1_000);
    }

    private void assertMessageStartsWith(JsonNode jsonNode, String message) {
        assertNotNull(jsonNode.get("message"));
        assertTrue(jsonNode.get("message").asText().startsWith(message), "Expected error message to start with:\n" +
                message + "\nbut got:\n" + jsonNode.get("message").asText());
    }

    JsonNode getPath(String body) {
        final Response response = query(body, 200);
        JsonNode json = response.readEntity(JsonNode.class);
        assertFalse(json.get("info").has("errors"));
        return json.get("paths").get(0);
    }

    Response query(String body, int code) {
        Response response = clientTarget(app, "/route").request().post(Entity.json(body));
        response.bufferEntity();
        JsonNode jsonNode = response.readEntity(JsonNode.class);
        assertEquals(code, response.getStatus(), jsonNode.has("message") ? jsonNode.get("message").toString() : "no error message");
        return response;
    }
}