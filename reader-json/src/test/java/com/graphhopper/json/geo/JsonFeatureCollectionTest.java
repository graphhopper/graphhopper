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

import com.graphhopper.json.GHJson;
import com.graphhopper.json.GHJsonBuilder;
import com.graphhopper.util.Helper;
import org.junit.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class JsonFeatureCollectionTest {
    private final GHJson ghson = new GHJsonBuilder().create();

    @Test
    public void testDeserialization() {
        JsonFeatureCollection data = getJson("geojson1.json");
        assertEquals(3, data.getFeatures().size());

        JsonFeature f1 = data.getFeatures().get(0);
        assertEquals("1", f1.getId());
        assertEquals("value0", f1.getProperty("prop0"));
        assertEquals(0.5, f1.getGeometry().asPoint().lat, .1);
        assertEquals(102.0, f1.getGeometry().asPoint().lon, .1);

        JsonFeature f2 = data.getFeatures().get(1);
        // read as string despite the 2 (not a string) in json
        assertEquals("2", f2.getId());
        assertEquals(4, f2.getGeometry().asPointList().size());
        assertEquals(0.0, f2.getGeometry().asPointList().getLat(0), .1);
        assertEquals(102.0, f2.getGeometry().asPointList().getLon(0), .1);
        assertEquals(1.0, f2.getGeometry().asPointList().getLat(1), .1);
        assertEquals(103.0, f2.getGeometry().asPointList().getLon(1), .1);

        JsonFeature f3 = data.getFeatures().get(2);
        assertEquals("0.0,102.0,1.0,103.0", f3.getBBox().toString());
        assertEquals("a", ((Map) f3.getProperty("prop1")).get("test"));
    }

    JsonFeatureCollection getJson(String name) {
        Reader reader = new InputStreamReader(getClass().getResourceAsStream(name), Helper.UTF_CS);
        return ghson.fromJson(reader, JsonFeatureCollection.class);
    }
}
