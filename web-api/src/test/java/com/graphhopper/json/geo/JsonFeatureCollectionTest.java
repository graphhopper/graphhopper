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
package com.graphhopper.json.geo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.BBox;
import org.junit.Assert;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class JsonFeatureCollectionTest {
    private final ObjectMapper objectMapper = Jackson.newObjectMapper();

    @Test
    public void testSerialization() throws IOException {
        GeometryFactory geometryFactory = new GeometryFactory();

        JsonFeatureCollection jsonFeatureCollection = new JsonFeatureCollection();
        {
            JsonFeature jsonFeature = new JsonFeature();
            jsonFeature.setId("1");
            HashMap<String, Object> properties = new HashMap<>();
            properties.put("prop0", "value0");
            jsonFeature.setProperties(properties);
            jsonFeature.setGeometry(geometryFactory.createPoint(new Coordinate(102.0,0.5)));
            jsonFeatureCollection.getFeatures().add(jsonFeature);
        }
        {
            JsonFeature jsonFeature = new JsonFeature();
            jsonFeature.setId("2");
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("prop0", "value1");
            properties.put("prop1", 2);
            jsonFeature.setProperties(properties);
            jsonFeature.setGeometry(geometryFactory.createLineString(new Coordinate[]{
                    new Coordinate(102.0, 0.0),
                    new Coordinate(103.0, 1.0),
                    new Coordinate(104.0, 0.0),
                    new Coordinate(105.0, 1.0)}));
            jsonFeatureCollection.getFeatures().add(jsonFeature);
        }
        {
            JsonFeature jsonFeature = new JsonFeature();
            jsonFeature.setId("3");
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("prop0", "value0");
            Map<String, String> prop1 = new LinkedHashMap<>();
            prop1.put("test", "a");
            properties.put("prop1", prop1);
            jsonFeature.setProperties(properties);
            jsonFeature.setBbox(new BBox(102.0, 103.0, 0.0, 1));
            jsonFeatureCollection.getFeatures().add(jsonFeature);
        }

        String expected = objectMapper.writeValueAsString(
                objectMapper.readValue(fixture("fixtures/geojson1.json"), JsonFeatureCollection.class));
        assertEquals(objectMapper.writeValueAsString(jsonFeatureCollection), expected);
    }

    @Test
    public void testDeserialization() throws IOException {
        JsonFeatureCollection data = objectMapper.readValue(fixture("fixtures/geojson1.json"), JsonFeatureCollection.class);
        Assert.assertEquals(3, data.getFeatures().size());

        JsonFeature f1 = data.getFeatures().get(0);
        Assert.assertEquals("1", f1.getId());
        Assert.assertEquals("value0", f1.getProperty("prop0"));
        Assert.assertEquals(0.5, f1.getGeometry().getCoordinate().y, .1);
        Assert.assertEquals(102.0, f1.getGeometry().getCoordinate().x, .1);

        JsonFeature f2 = data.getFeatures().get(1);
        // read as string despite the 2 (not a string) in json
        Assert.assertEquals("2", f2.getId());
        Assert.assertEquals(4, f2.getGeometry().getNumPoints());
        assertEquals(0.0, PointList.fromLineString((LineString) f2.getGeometry()).getLat(0), .1);
        assertEquals(102.0, PointList.fromLineString((LineString) f2.getGeometry()).getLon(0), .1);
        assertEquals(1.0, PointList.fromLineString((LineString) f2.getGeometry()).getLat(1), .1);
        assertEquals(103.0, PointList.fromLineString((LineString) f2.getGeometry()).getLon(1), .1);

        JsonFeature f3 = data.getFeatures().get(2);
        assertEquals(0.0, f3.getBBox().minLat, 0.0);
        assertEquals(102.0, f3.getBBox().minLon, 0.0);
        assertEquals(1.0, f3.getBBox().maxLat, 0.0);
        assertEquals(103.0, f3.getBBox().maxLon, 0.0);

        assertEquals("a", ((Map) f3.getProperty("prop1")).get("test"));
    }

}
