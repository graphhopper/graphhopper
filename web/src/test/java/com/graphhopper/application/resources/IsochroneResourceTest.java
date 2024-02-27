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
import com.graphhopper.config.Profile;
import com.graphhopper.config.TurnCostsConfig;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.util.Helper;
import com.graphhopper.util.JsonFeatureCollection;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import static com.graphhopper.application.util.TestUtils.clientTarget;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
public class IsochroneResourceTest {
    private static final String DIR = "./target/andorra-gh/";
    public static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("datareader.file", "../core/files/andorra.osm.pbf").
                putObject("import.osm.ignored_highways", "").
                putObject("graph.location", DIR).
                setProfiles(Arrays.asList(
                        Profile.create("car", true).setName("fast_car"),
                        TestProfiles.accessAndSpeed("short_car", "car").setTurnCostsConfig(new TurnCostsConfig(List.of("motorcar", "motor_vehicle"))),
                        TestProfiles.accessAndSpeed("fast_car_no_turn_restrictions", "car")
                ));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Test
    public void requestByTimeLimit() {
        Response rsp = clientTarget(app, "/isochrone")
                .queryParam("profile", "fast_car")
                .queryParam("point", "42.531073,1.573792")
                .queryParam("time_limit", 5 * 60)
                .queryParam("buckets", 2)
                .queryParam("type", "geojson")
                .request().buildGet().invoke();
        JsonFeatureCollection featureCollection = rsp.readEntity(JsonFeatureCollection.class);

        assertEquals(2, featureCollection.getFeatures().size());
        Geometry polygon0 = featureCollection.getFeatures().get(0).getGeometry();
        Geometry polygon1 = featureCollection.getFeatures().get(1).getGeometry();

        assertTrue(polygon0.contains(geometryFactory.createPoint(new Coordinate(1.587224, 42.5386))));
        assertFalse(polygon0.contains(geometryFactory.createPoint(new Coordinate(1.589756, 42.558012))));

        assertTrue(polygon1.contains(geometryFactory.createPoint(new Coordinate(1.589756, 42.558012))));
        assertFalse(polygon1.contains(geometryFactory.createPoint(new Coordinate(1.635246, 42.53841))));
    }

    @Test
    public void requestByTimeLimitNoTurnRestrictions() {
        Response rsp = clientTarget(app, "/isochrone")
                .queryParam("profile", "fast_car_no_turn_restrictions")
                .queryParam("point", "42.531073,1.573792")
                .queryParam("time_limit", 5 * 60)
                .queryParam("buckets", 2)
                .queryParam("type", "geojson")
                .request().buildGet().invoke();
        JsonFeatureCollection featureCollection = rsp.readEntity(JsonFeatureCollection.class);

        assertEquals(2, featureCollection.getFeatures().size());
        Geometry polygon0 = featureCollection.getFeatures().get(0).getGeometry();
        Geometry polygon1 = featureCollection.getFeatures().get(1).getGeometry();

        assertTrue(polygon0.contains(geometryFactory.createPoint(new Coordinate(1.587224, 42.5386))));
        assertFalse(polygon0.contains(geometryFactory.createPoint(new Coordinate(1.589756, 42.558012))));

        assertTrue(polygon1.contains(geometryFactory.createPoint(new Coordinate(1.589756, 42.558012))));
        assertFalse(polygon1.contains(geometryFactory.createPoint(new Coordinate(1.635246, 42.53841))));
    }

    @Test
    public void requestByDistanceLimit() {
        Response rsp = clientTarget(app, "/isochrone")
                .queryParam("profile", "fast_car")
                .queryParam("point", "42.531073,1.573792")
                .queryParam("distance_limit", 3_000)
                .queryParam("buckets", 2)
                .queryParam("type", "geojson")
                .request().buildGet().invoke();
        JsonFeatureCollection featureCollection = rsp.readEntity(JsonFeatureCollection.class);

        assertEquals(2, featureCollection.getFeatures().size());
        Geometry polygon0 = featureCollection.getFeatures().get(0).getGeometry();
        Geometry polygon1 = featureCollection.getFeatures().get(1).getGeometry();

        assertTrue(polygon0.contains(geometryFactory.createPoint(new Coordinate(1.57937, 42.531706))));
        assertFalse(polygon0.contains(geometryFactory.createPoint(new Coordinate(1.587224, 42.5386))));

        assertTrue(polygon1.contains(geometryFactory.createPoint(new Coordinate(1.591644, 42.543216))));
        assertFalse(polygon1.contains(geometryFactory.createPoint(new Coordinate(1.589756, 42.558012))));
    }

    @Test
    public void requestByWeightLimit() {
        WebTarget commonTarget = clientTarget(app, "/isochrone")
                .queryParam("profile", "fast_car")
                .queryParam("point", "42.531073,1.573792")
                .queryParam("type", "geojson");

        long limit = 10 * 60;

        Response distanceLimitRsp = commonTarget
                .queryParam("time_limit", limit)
                .request().buildGet().invoke();
        JsonFeatureCollection distanceLimitFeatureCollection = distanceLimitRsp.readEntity(JsonFeatureCollection.class);
        Geometry distanceLimitPolygon = distanceLimitFeatureCollection.getFeatures().get(0).getGeometry();

        Response weightLimitRsp = commonTarget
                .queryParam("weight_limit", limit)
                .request().buildGet().invoke();
        JsonFeatureCollection weightLimitFeatureCollection = weightLimitRsp.readEntity(JsonFeatureCollection.class);
        Geometry weightLimitPolygon = weightLimitFeatureCollection.getFeatures().get(0).getGeometry();

        assertEquals(distanceLimitPolygon.getNumPoints(), weightLimitPolygon.getNumPoints());
        assertTrue(weightLimitPolygon.equalsTopo(distanceLimitPolygon));
    }

    @Test
    public void requestReverseFlow() {
        Response rsp = clientTarget(app, "/isochrone")
                .queryParam("profile", "fast_car")
                .queryParam("point", "42.531073,1.573792")
                .queryParam("reverse_flow", true)
                .queryParam("time_limit", 5 * 60)
                .queryParam("buckets", 2)
                .queryParam("type", "geojson")
                .request().buildGet().invoke();
        JsonFeatureCollection featureCollection = rsp.readEntity(JsonFeatureCollection.class);

        assertEquals(2, featureCollection.getFeatures().size());
        Geometry polygon0 = featureCollection.getFeatures().get(0).getGeometry();
        Geometry polygon1 = featureCollection.getFeatures().get(1).getGeometry();

        assertTrue(polygon0.contains(geometryFactory.createPoint(new Coordinate(1.587224, 42.5386))));
        assertFalse(polygon0.contains(geometryFactory.createPoint(new Coordinate(1.589756, 42.558012))));

        assertTrue(polygon1.contains(geometryFactory.createPoint(new Coordinate(1.589756, 42.558012))));
        assertFalse(polygon1.contains(geometryFactory.createPoint(new Coordinate(1.635246, 42.53841))));

        int bucketNumber = (Integer) featureCollection.getFeatures().get(0).getProperties().get("bucket");
        assertEquals(0, bucketNumber);
    }

    @Test
    public void requestBadRequest() {
        Response response = clientTarget(app, "/isochrone?profile=fast_car&point=-1.816719,51.557148").request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        String error = response.readEntity(String.class);
        assertTrue(error.contains("Point not found:-1.816719,51.557148"), error);
    }

    @Test
    public void profileWithLegacyParametersNotAllowed() {
        assertNotAllowed("&profile=fast_car&weighting=fastest", "The 'weighting' parameter is no longer supported. You used 'weighting=fastest'");
        assertNotAllowed("&vehicle=car", "profile parameter required");
    }

    @Test
    public void missingPoint() {
        Response rsp = clientTarget(app, "/isochrone").request().buildGet().invoke();
        assertEquals(400, rsp.getStatus());
        JsonNode json = rsp.readEntity(JsonNode.class);
        assertTrue(json.get("message").toString().contains("query param point must not be null"), json.toString());
    }

    private void assertNotAllowed(String hint, String error) {
        Response rsp = clientTarget(app, "/isochrone?point=42.531073,1.573792" + hint).request().buildGet().invoke();
        assertEquals(400, rsp.getStatus());
        JsonNode json = rsp.readEntity(JsonNode.class);
        assertTrue(json.get("message").toString().contains(error), json.toString());
    }

    @Test
    public void requestWithShortest() {
        Response rsp = clientTarget(app, "/isochrone")
                .queryParam("profile", "short_car")
                .queryParam("point", "42.509644,1.540554")
                .queryParam("time_limit", 130)
                .queryParam("buckets", 1)
                .queryParam("type", "geojson")
                .request().buildGet().invoke();
        JsonFeatureCollection featureCollection = rsp.readEntity(JsonFeatureCollection.class);

        assertEquals(1, featureCollection.getFeatures().size());
        Geometry polygon0 = featureCollection.getFeatures().get(0).getGeometry();
        assertIs2D(polygon0);

        assertTrue(polygon0.contains(geometryFactory.createPoint(new Coordinate(1.527057, 42.507145))));
        assertFalse(polygon0.contains(geometryFactory.createPoint(new Coordinate(1.525404, 42.507081))));
    }

    private static void assertIs2D(Geometry geometry) {
        assertAll(Arrays.stream(geometry.getCoordinates()).map(coord -> () -> assertTrue(Double.isNaN(coord.z))));
    }

    @Test
    public void requestBadType() {
        Response response = clientTarget(app, "/isochrone?profile=fast_car&point=42.531073,1.573792&time_limit=130&type=xml")
                .request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        String message = json.path("message").asText();

        assertEquals("query param type must be one of [json, geojson]", message);
    }

    @Test
    public void testTypeIsCaseInsensitive() {
        Response response = clientTarget(app, "/isochrone?profile=fast_car&point=42.531073,1.573792&time_limit=130&type=GEOJSON")
                .request().buildGet().invoke();
        assertEquals(200, response.getStatus());
    }

    @Test
    public void requestNotANumber() {
        Response response = clientTarget(app, "/isochrone?profile=fast_car&point=42.531073,1.573792&time_limit=wurst")
                .request().buildGet().invoke();

        assertEquals(404, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        String message = json.path("message").asText();

        assertEquals("HTTP 404 Not Found", message);
    }

    @Disabled("block_area is no longer supported and to use custom models we'd need a POST endpoint for isochrones")
    @Test
    public void requestWithBlockArea() {
        Response rsp = clientTarget(app, "/isochrone")
                .queryParam("profile", "fast_car")
                .queryParam("point", "42.531073,1.573792")
                .queryParam("time_limit", 5 * 60)
                .queryParam("buckets", 2)
                .queryParam("type", "geojson")
                .queryParam("block_area", "42.558067,1.589429,100")
                .request().buildGet().invoke();
        JsonFeatureCollection featureCollection = rsp.readEntity(JsonFeatureCollection.class);

        assertEquals(2, featureCollection.getFeatures().size());
        Geometry polygon0 = featureCollection.getFeatures().get(0).getGeometry();
        Geometry polygon1 = featureCollection.getFeatures().get(1).getGeometry();

        assertTrue(polygon0.contains(geometryFactory.createPoint(new Coordinate(1.587224, 42.5386))));
        assertFalse(polygon0.contains(geometryFactory.createPoint(new Coordinate(1.589756, 42.558012))));

        assertFalse(polygon1.contains(geometryFactory.createPoint(new Coordinate(1.589756, 42.558012))));
        assertFalse(polygon1.contains(geometryFactory.createPoint(new Coordinate(1.635246, 42.53841))));

        assertTrue(polygon1.contains(geometryFactory.createPoint(new Coordinate(1.58864, 42.554582))));
    }

    @Test
    public void requestJsonWithType() {
        Response response = clientTarget(app, "/isochrone?profile=fast_car&point=42.531073,1.573792&time_limit=130&type=json")
                .request().buildGet().invoke();
        JsonNode json = response.readEntity(JsonNode.class);
        assertTrue(json.has("polygons"));
        assertTrue(json.has("info"));
    }

    @Test
    public void requestJsonNoType() {
        Response response = clientTarget(app, "/isochrone?profile=fast_car&point=42.531073,1.573792&time_limit=130")
                .request().buildGet().invoke();
        JsonNode json = response.readEntity(JsonNode.class);
        assertTrue(json.has("polygons"));
        assertTrue(json.has("info"));
    }

    @Test
    public void requestGeoJsonPolygons() {
        Response response = clientTarget(app, "/isochrone?profile=fast_car&point=42.531073,1.573792&time_limit=130&type=geojson")
                .request().buildGet().invoke();
        JsonNode json = response.readEntity(JsonNode.class);

        assertFalse(json.has("polygons"));
        assertFalse(json.has("info"));

        assertTrue(json.has("type"));
        assertEquals(json.path("type").asText(), "FeatureCollection");

        assertTrue(json.has("features"));

        JsonNode firstFeature = json.path("features").path(0);
        assertTrue(firstFeature.isObject());

        assertTrue(firstFeature.path("properties").has("bucket"));
        assertTrue(firstFeature.path("properties").has("copyrights"));

        assertEquals(firstFeature.path("type").asText(), "Feature");
        assertEquals(firstFeature.path("geometry").path("type").asText(), "Polygon");
    }

    @Test
    public void requestGeoJsonPolygonsBuckets() {
        Response response = clientTarget(app, "/isochrone?profile=fast_car&point=42.531073,1.573792&time_limit=130&type=geojson&buckets=3")
                .request().buildGet().invoke();
        JsonNode json = response.readEntity(JsonNode.class);

        JsonNode features = json.path("features");
        JsonNode firstFeature = features.path(0);
        JsonNode lastFeature = features.path(features.size() - 1);

        assertEquals(firstFeature.path("properties").path("bucket").asInt(), 0);
        assertEquals(firstFeature.path("geometry").path("type").asText(), "Polygon");

        assertEquals(lastFeature.path("properties").path("bucket").asInt(), 2);
        assertEquals(lastFeature.path("geometry").path("type").asText(), "Polygon");
    }

    @Test
    public void requestTenBucketsIssue2094() {
        Response response = clientTarget(app, "/isochrone?profile=fast_car&point=42.510008,1.530018&time_limit=400&type=geojson&buckets=10")
                .request().buildGet().invoke();
        JsonFeatureCollection collection = response.readEntity(JsonFeatureCollection.class);
        Polygon lastPolygon = (Polygon) collection.getFeatures().get(collection.getFeatures().size() - 1).getGeometry();
        assertTrue(lastPolygon.contains(geometryFactory.createPoint(new Coordinate(1.580229, 42.533161))));
        assertFalse(lastPolygon.contains(geometryFactory.createPoint(new Coordinate(1.584606, 42.535121))));

        Polygon beforeLastPolygon = (Polygon) collection.getFeatures().get(collection.getFeatures().size() - 2).getGeometry();
        assertTrue(beforeLastPolygon.contains(geometryFactory.createPoint(new Coordinate(1.564136, 42.524938))));
        assertFalse(beforeLastPolygon.contains(geometryFactory.createPoint(new Coordinate(1.571474, 42.529176))));
    }
}
