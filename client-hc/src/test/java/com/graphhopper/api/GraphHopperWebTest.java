package com.graphhopper.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GHRequest;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.json.Statement;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.JsonFeature;
import com.graphhopper.util.JsonFeatureCollection;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;

import static com.graphhopper.json.SingleStatement.If;
import static org.junit.jupiter.api.Assertions.*;

/**
 * This class unit tests the class. For integration tests against a real server see RouteResourceClientHCTest.
 */
public class GraphHopperWebTest {

    @ParameterizedTest(name = "POST={0}")
    @ValueSource(booleans = {true, false})
    public void testGetClientForRequest(boolean usePost) {
        GraphHopperWeb gh = new GraphHopperWeb(null).setPostRequest(usePost);
        GHRequest req = new GHRequest(new GHPoint(42.509225, 1.534728), new GHPoint(42.512602, 1.551558)).
                setProfile("car");
        req.putHint(GraphHopperWeb.TIMEOUT, 5);

        assertEquals(5, gh.getClientForRequest(req).connectTimeoutMillis());
    }

    @Test
    public void profileIncludedAsGiven() {
        GraphHopperWeb hopper = new GraphHopperWeb("https://localhost:8000/route");
        // no vehicle -> no vehicle
        assertEquals("https://localhost:8000/route?profile=&type=json&instructions=true&points_encoded=true&points_encoded_multiplier=1000000" +
                        "&calc_points=true&algorithm=&locale=en_US&elevation=false&optimize=false",
                hopper.createGetRequest(new GHRequest()).url().toString());

        // vehicle given -> vehicle used in url
        assertEquals("https://localhost:8000/route?profile=my_car&type=json&instructions=true&points_encoded=true&points_encoded_multiplier=1000000" +
                        "&calc_points=true&algorithm=&locale=en_US&elevation=false&optimize=false",
                hopper.createGetRequest(new GHRequest().setProfile("my_car")).url().toString());
    }

    @Test
    public void headings() {
        GraphHopperWeb hopper = new GraphHopperWeb("http://localhost:8080/route");
        GHRequest req = new GHRequest(new GHPoint(42.509225, 1.534728), new GHPoint(42.512602, 1.551558)).
                setHeadings(Arrays.asList(10.0, 90.0)).
                setProfile("car");
        assertEquals("http://localhost:8080/route?profile=car&point=42.509225,1.534728&point=42.512602,1.551558&type=json&instructions=true&points_encoded=true&points_encoded_multiplier=1000000" +
                "&calc_points=true&algorithm=&locale=en_US&elevation=false&optimize=false&heading=10.0&heading=90.0", hopper.createGetRequest(req).url().toString());
    }

    @Test
    public void customModel() throws JsonProcessingException {
        GraphHopperWeb client = new GraphHopperWeb("http://localhost:8080/route");
        JsonFeatureCollection areas = new JsonFeatureCollection();
        Coordinate[] area_1_coordinates = new Coordinate[]{
                new Coordinate(48.019324184801185, 11.28021240234375),
                new Coordinate(48.019324184801185, 11.53564453125),
                new Coordinate(48.11843396091691, 11.53564453125),
                new Coordinate(48.11843396091691, 11.28021240234375),
                new Coordinate(48.019324184801185, 11.28021240234375),
        };
        Coordinate[] area_2_coordinates = new Coordinate[]{
                new Coordinate(48.15509285476017, 11.53289794921875),
                new Coordinate(48.15509285476017, 11.8212890625),
                new Coordinate(48.281365151571755, 11.8212890625),
                new Coordinate(48.281365151571755, 11.53289794921875),
                new Coordinate(48.15509285476017, 11.53289794921875),
        };
        areas.getFeatures().add(new JsonFeature("area_1",
                "Feature",
                null,
                new GeometryFactory().createPolygon(area_1_coordinates),
                new HashMap<>()));
        areas.getFeatures().add(new JsonFeature("area_2",
                "Feature",
                null,
                new GeometryFactory().createPolygon(area_2_coordinates),
                new HashMap<>()));
        CustomModel customModel = new CustomModel()
                .addToSpeed(If("road_class == MOTORWAY", Statement.Op.LIMIT, "80"))
                .addToPriority(If("surface == DIRT", Statement.Op.MULTIPLY, "0.7"))
                .addToPriority(If("surface == SAND", Statement.Op.MULTIPLY, "0.6"))
                .setDistanceInfluence(69d)
                .setHeadingPenalty(22)
                .setAreas(areas);
        GHRequest req = new GHRequest(new GHPoint(42.509225, 1.534728), new GHPoint(42.512602, 1.551558))
                .setCustomModel(customModel)
                .setProfile("car");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> client.createGetRequest(req));
        assertEquals("Custom models cannot be used for GET requests. Use setPostRequest(true)", e.getMessage());

        ObjectNode postRequest = client.requestToJson(req);
        JsonNode customModelJson = postRequest.get("custom_model");
        ObjectMapper objectMapper = Jackson.newObjectMapper();
        JsonNode expected = objectMapper.readTree("{\"distance_influence\":69.0,\"heading_penalty\":22.0,\"internal\":false,\"areas\":{" +
                "\"type\":\"FeatureCollection\",\"features\":["+
                "{\"id\":\"area_1\",\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[48.019324184801185,11.28021240234375],[48.019324184801185,11.53564453125],[48.11843396091691,11.53564453125],[48.11843396091691,11.28021240234375],[48.019324184801185,11.28021240234375]]]},\"properties\":{}}," +
                "{\"id\":\"area_2\",\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[48.15509285476017,11.53289794921875],[48.15509285476017,11.8212890625],[48.281365151571755,11.8212890625],[48.281365151571755,11.53289794921875],[48.15509285476017,11.53289794921875]]]},\"properties\":{}}]}," +
                "\"speed\":[{\"if\":\"road_class == MOTORWAY\",\"limit_to\":\"80\"}]," +
                "\"priority\":[{\"if\":\"surface == DIRT\",\"multiply_by\":\"0.7\"},{\"if\":\"surface == SAND\",\"multiply_by\":\"0.6\"}]}");
        assertEquals(expected, objectMapper.valueToTree(customModelJson));

        CustomModel cm = objectMapper.readValue("{\"distance_influence\":null}", CustomModel.class);
        assertNull(cm.getDistanceInfluence());
    }
}
