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

package com.graphhopper.http.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.config.ProfileConfig;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.util.GraphHopperServerTestConfiguration;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Arrays;

import static com.graphhopper.http.util.TestUtils.clientTarget;
import static com.graphhopper.util.Parameters.Routing.BLOCK_AREA;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;

public class IsochroneResourceTest {
    private static final String DIR = "./target/andorra-gh/";

    private static final GraphHopperServerTestConfiguration config = new GraphHopperServerTestConfiguration();

    static {
        config.getGraphHopperConfiguration().
                // isochrone does not support turn costs yet, but use it anyway to make sure this is handled correctly
                        putObject("graph.flag_encoders", "car|turn_costs=true").
                putObject("datareader.file", "../core/files/andorra.osm.pbf").
                putObject("graph.location", DIR).
                setProfiles(Arrays.asList(
                        new ProfileConfig("fast_car").setVehicle("car").setWeighting("fastest").setTurnCosts(true),
                        new ProfileConfig("short_car").setVehicle("car").setWeighting("shortest").setTurnCosts(true)
                ));
    }

    @ClassRule
    public static final DropwizardAppRule<GraphHopperServerTestConfiguration> app = new DropwizardAppRule(
            GraphHopperApplication.class, config);

    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    private GeometryFactory geometryFactory = new GeometryFactory();

    @Test
    public void requestByTimeLimit() {
        Response rsp = clientTarget(app, "/isochrone")
                .queryParam("weighting", "fastest")
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
                .queryParam("weighting", "fastest")
                .queryParam("point", "42.531073,1.573792")
                .queryParam("distance_limit", 3_000)
                .queryParam("buckets", 2)
                // explicitly disabling turn costs should be ok
                .queryParam("turn_costs", false)
                .queryParam("edge_based", false)
                // explicitly disabling speed mode should be ok
                .queryParam("ch.disable", true)
                .queryParam("lm.disable", true)
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
    public void requestReverseFlow() {
        Response rsp = clientTarget(app, "/isochrone")
                .queryParam("weighting", "fastest")
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
        Response response = clientTarget(app, "/route?weighting=fastest&point=-1.816719,51.557148").request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        assertTrue(json.get("message").toString().contains("Point 0 is out of bounds"));
    }

    @Test
    public void certainParametersNotAllowed() {
        assertNotAllowed("&ch.disable=false", "Currently you cannot use speed mode for /isochrone");
        assertNotAllowed("&lm.disable=false", "Currently you cannot use hybrid mode for /isochrone");
        assertNotAllowed("&turn_costs=true", "Currently you cannot use turn costs for /isochrone");
        assertNotAllowed("&edge_based=true", "Currently you cannot use edge-based for /isochrone");
    }

    private void assertNotAllowed(String hint, String error) {
        Response rsp = clientTarget(app, "/isochrone?weighting=fastest&point=42.531073,1.573792" + hint).request().buildGet().invoke();
        assertEquals(400, rsp.getStatus());
        JsonNode json = rsp.readEntity(JsonNode.class);
        assertTrue(json.toString(), json.get("message").toString().contains(error));
    }

    @Test
    public void requestWithShortest() {
        Response rsp = clientTarget(app, "/isochrone")
                .queryParam("point", "42.509644,1.540554")
                .queryParam("time_limit", 130)
                .queryParam("buckets", 1)
                .queryParam("weighting", "shortest")
                .queryParam("type", "geojson")
                .request().buildGet().invoke();
        JsonFeatureCollection featureCollection = rsp.readEntity(JsonFeatureCollection.class);

        assertEquals(1, featureCollection.getFeatures().size());
        Geometry polygon0 = featureCollection.getFeatures().get(0).getGeometry();
        assertIs2D(polygon0);

        assertTrue(polygon0.contains(geometryFactory.createPoint(new Coordinate(1.527057, 42.507145))));
        assertFalse(polygon0.contains(geometryFactory.createPoint(new Coordinate(1.525404, 42.507081))));

        rsp = clientTarget(app, "/isochrone")
                .queryParam("point", "42.509644,1.540554")
                .queryParam("time_limit", 130)
                .queryParam("buckets", 1)
                .queryParam("weighting", "fastest")
                .queryParam("type", "geojson")
                .request().buildGet().invoke();
        featureCollection = rsp.readEntity(JsonFeatureCollection.class);
        polygon0 = featureCollection.getFeatures().get(0).getGeometry();
        assertTrue(polygon0.getCoordinates().length >= 190);
    }

    private static void assertIs2D(Geometry geometry) {
        assertAll(Arrays.stream(geometry.getCoordinates()).map(coord -> () -> assertTrue(Double.isNaN(coord.z))));
    }

    @Test
    public void requestJsonBadType() {
        Response response = clientTarget(app, "/isochrone?weighting=fastest&point=42.531073,1.573792&time_limit=130&type=xml")
                .request().buildGet().invoke();

        JsonNode json = response.readEntity(JsonNode.class);
        String message = json.path("message").asText();

        assertEquals(message, "Format not supported:xml");
    }

    @Test
    public void requestWithBlockArea() {
        Response rsp = clientTarget(app, "/isochrone")
                .queryParam("weighting", "fastest")
                .queryParam("point", "42.531073,1.573792")
                .queryParam("time_limit", 5 * 60)
                .queryParam("buckets", 2)
                .queryParam("type", "geojson")
                .queryParam(BLOCK_AREA, "42.558067,1.589429,100")
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
        Response response = clientTarget(app, "/isochrone?weighting=fastest&point=42.531073,1.573792&time_limit=130&type=json")
                .request().buildGet().invoke();
        JsonNode json = response.readEntity(JsonNode.class);
        assertTrue(json.has("polygons"));
        assertTrue(json.has("info"));
    }

    @Test
    public void requestJsonNoType() {
        Response response = clientTarget(app, "/isochrone?weighting=fastest&point=42.531073,1.573792&time_limit=130")
                .request().buildGet().invoke();
        JsonNode json = response.readEntity(JsonNode.class);
        assertTrue(json.has("polygons"));
        assertTrue(json.has("info"));
    }

    @Test
    public void requestGeoJsonPolygons() {
        Response response = clientTarget(app, "/isochrone?weighting=fastest&point=42.531073,1.573792&time_limit=130&type=geojson")
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
        Response response = clientTarget(app, "/isochrone?weighting=fastest&point=42.531073,1.573792&time_limit=130&type=geojson&buckets=3")
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

}
