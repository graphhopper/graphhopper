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
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.util.CmdArgs;
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

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public class IsochroneResourceTest {
    private static final String DIR = "./target/andorra-gh/";

    private static final GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();

    static {
        config.getGraphHopperConfiguration().merge(new CmdArgs().
                put("prepare.ch.weightings", "no").
                put("graph.flag_encoders", "car").
                put("datareader.file", "../core/files/andorra.osm.pbf").
                put("graph.location", DIR));
    }

    @ClassRule
    public static final DropwizardAppRule<GraphHopperServerConfiguration> app = new DropwizardAppRule<>(
            GraphHopperApplication.class, config);

    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    private GeometryFactory geometryFactory = new GeometryFactory();

    @Test
    public void requestByTimeLimit() {
        Response rsp = app.client().target("http://localhost:8080/isochrone")
                .queryParam("point","42.531073,1.573792")
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
        Response rsp = app.client().target("http://localhost:8080/isochrone")
                .queryParam("point","42.531073,1.573792")
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
    public void requestReverseFlow() {
        Response rsp = app.client().target("http://localhost:8080/isochrone")
                .queryParam("point","42.531073,1.573792")
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
        Response response = app.client().target("http://localhost:8080/route?point=-1.816719,51.557148").request().buildGet().invoke();
        assertEquals(400, response.getStatus());
    }

    @Test
    public void requestWithShortest() {
        Response rsp = app.client().target("http://localhost:8080/isochrone")
                .queryParam("point","42.509644,1.540554")
                .queryParam("time_limit", 130)
                .queryParam("buckets", 1)
                .queryParam("weighting", "shortest")
                .queryParam("type", "geojson")
                .request().buildGet().invoke();
        JsonFeatureCollection featureCollection = rsp.readEntity(JsonFeatureCollection.class);

        assertEquals(1, featureCollection.getFeatures().size());
        Geometry polygon0 = featureCollection.getFeatures().get(0).getGeometry();

        assertTrue(polygon0.contains(geometryFactory.createPoint(new Coordinate(1.527057, 42.507145))));
        assertFalse(polygon0.contains(geometryFactory.createPoint(new Coordinate(1.525404, 42.507081))));

        // more like a circle => shorter is expected
        assertTrue(polygon0.getCoordinates().length < 185);

        rsp = app.client().target("http://localhost:8080/isochrone")
                .queryParam("point","42.509644,1.540554")
                .queryParam("time_limit", 130)
                .queryParam("buckets", 1)
                .queryParam("weighting", "fastest")
                .queryParam("type", "geojson")
                .request().buildGet().invoke();
        featureCollection = rsp.readEntity(JsonFeatureCollection.class);
        polygon0 = featureCollection.getFeatures().get(0).getGeometry();
        assertTrue(polygon0.getCoordinates().length >= 190);
    }
    
    @Test
    public void requestJsonBadType() {
        Response response = app.client().target("http://localhost:8080/isochrone?point=42.531073,1.573792&time_limit=130&type=xml")
                .request().buildGet().invoke();

        JsonNode json = response.readEntity(JsonNode.class);
        String message = json.path("message").asText();

        assertEquals(message, "Format not supported:xml");
    }
    
    
    @Test
    public void requestJsonWithType() {
        Response response = app.client().target("http://localhost:8080/isochrone?point=42.531073,1.573792&time_limit=130&type=json")
                .request().buildGet().invoke();
        JsonNode json = response.readEntity(JsonNode.class);
        assertTrue(json.has("polygons"));
        assertTrue(json.has("info"));
    }
    
    @Test
    public void requestJsonNoType() {
        Response response = app.client().target("http://localhost:8080/isochrone?point=42.531073,1.573792&time_limit=130")
                .request().buildGet().invoke();
        JsonNode json = response.readEntity(JsonNode.class);
        assertTrue(json.has("polygons"));
        assertTrue(json.has("info"));
    }

    @Test
    public void requestGeoJsonPolygons() {
        Response response = app.client().target("http://localhost:8080/isochrone?point=42.531073,1.573792&time_limit=130&type=geojson")
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
        Response response = app.client().target("http://localhost:8080/isochrone?point=42.531073,1.573792&time_limit=130&type=geojson&buckets=3")
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